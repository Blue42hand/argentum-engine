"""Serialized root journal and explicit source/build/qualification action plans.

No supervisor, timer, symlink switch, promotion, network or builder code execution
is implemented here. The root orchestrator alone carries out returned actions.
"""
from candidate_lifecycle import completion, retirement, selection_authority
from contextlib import contextmanager
import fcntl
import hashlib
import os
from pathlib import Path
import stat
import uuid
from candidate_custody import archive_index, identity, require_root, root_read, root_write, trusted, unique_json, validate_candidate_index
from coordinator_validation import (KINDS, canonical, digest, fresh, pair_id, plan, require,
    validate_evidence, validate_job, validate_sources, validate_tuple)


class Coordinator:
    def __init__(self, root, config):
        require_root(); self.root = Path(root); trusted(self.root, True)
        require(set(config) == {'ancestors', 'maxSourceAgeSeconds', 'maxEvidenceAgeSeconds', 'acknowledgement'}, 'explicit coordinator policy required')
        require(set(config['ancestors']) == {'engine', 'gym'} and
            type(config['maxSourceAgeSeconds']) in (int, float) and 0 < config['maxSourceAgeSeconds'] <= 60 and
            type(config['maxEvidenceAgeSeconds']) in (int, float) and 0 < config['maxEvidenceAgeSeconds'] <= 86400,
            'bounded freshness policy required')
        self.config = unique_json(canonical(config))
        policy_path = self.root / 'policy.json'
        try: root_write(policy_path, self.config)
        except FileExistsError:
            require(root_read(policy_path) == canonical(self.config) + b'\n', 'retained coordinator policy changed')
        for name in ('events', 'sources', 'jobs'):
            path = self.root / name
            try: path.mkdir(mode=0o700)
            except FileExistsError: pass
            trusted(path, True)
        self.lock_path = self.root / 'coordinator.lock'
        try:
            fd = os.open(self.lock_path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
            os.fchmod(fd, 0o600); os.fsync(fd); os.close(fd)
        except FileExistsError: pass
        trusted(self.lock_path)

    @contextmanager
    def exclusive(self):
        trusted(self.lock_path)
        fd = os.open(self.lock_path, os.O_RDWR | os.O_NOFOLLOW)
        try:
            require(identity(os.fstat(fd)) == identity(self.lock_path.lstat()) and
                stat.S_IMODE(os.fstat(fd).st_mode) == 0o600, 'coordinator lock identity held')
            fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
            yield
            require(identity(os.fstat(fd)) == identity(self.lock_path.lstat()), 'coordinator lock replaced')
        finally: os.close(fd)

    def _chain(self):
        entries = sorted((self.root / 'events').iterdir())
        previous = None; values = []
        for number, path in enumerate(entries):
            require(path.name == f'{number:08d}.json', 'complete serial retained event journal required')
            raw = root_read(path); value = unique_json(raw)
            require(type(value) is dict and set(value) == {'schemaVersion', 'sequence', 'previousSha256', 'event', 'payload'} and
                type(value['schemaVersion']) is int and value['schemaVersion'] == 1 and
                type(value['sequence']) is int and value['sequence'] == number and value['previousSha256'] == previous and
                value['event'] in ('discovered', 'building', 'built', 'tuple', 'evidence', 'superseded', 'observed', 'prepared', 'authority', 'promotion', 'completed', 'retired'), 'event chain identity held')
            previous = hashlib.sha256(raw).hexdigest(); values.append(value)
        return values, previous

    def _append(self, kind, payload):
        entries, previous = self._chain()
        root_write(self.root / 'events' / f'{len(entries):08d}.json', dict(schemaVersion=1,
            sequence=len(entries), previousSha256=previous, event=kind, payload=payload))

    def _state(self):
        entries, _ = self._chain(); state = None
        for entry in entries:
            kind, payload = entry['event'], entry['payload']
            if kind == 'superseded':
                require(state is not None and payload.get('pairId') == state['pairId'], 'invalid supersede event')
                state = None; continue
            if kind == 'discovered':
                require(state is None, 'overlapping source candidates in journal')
                source = self._indexed(payload['sourcePath'], payload['sourceSha256'])
                require(source.get('pairId') == payload['pairId'] and pair_id(source['engineSha'], source['gymSha']) == payload['pairId'], 'source index identity held')
                state = dict(pairId=payload['pairId'], phase='discovered', source=source, evidence={},
                    attemptId=digest(entry), discoveredUnix=source['observedUnix'], selectionAuthority=None)
            elif kind == 'authority':
                require(state is not None and state['selectionAuthority'] is None and payload.get('attemptId') == state['attemptId'], 'authority binding ordering held')
                state['selectionAuthority'] = selection_authority(payload['selectionAuthority'])
            elif kind == 'promotion':
                require(state is not None and 'tuple' in state and 'manifest' not in state and payload.get('attemptId') == state['attemptId'], 'promotion binding ordering held')
                state['manifest'] = self._indexed(payload['path'], payload['sha256'])
            elif kind in ('completed', 'retired'):
                require(state is not None and payload.get('attemptId') == state['attemptId'] and payload.get('pairId') == state['pairId'], 'terminal attempt identity held')
                proof = self._indexed(payload['proofPath'], payload['proofSha256'])
                if kind == 'completed':
                    completion(state['tuple'], state['manifest'], proof, now=payload['terminalUnix'], max_age=self.config['maxSourceAgeSeconds'])
                else:
                    retirement(payload['reason'], proof, now=payload['terminalUnix'], max_age=self.config['maxSourceAgeSeconds'])
                state = None
            elif kind == 'observed':
                require(state is not None and payload.get('pairId') == state['pairId'], 'fresh source observation ordering held')
                source = self._indexed(payload['sourcePath'], payload['sourceSha256'])
                require(source.get('pairId') == state['pairId'], 'fresh source pair mismatch')
                state['source'] = source
            elif kind == 'prepared':
                require(state is not None and state['phase'] == 'discovered', 'invalid job preparation ordering')
                job = self._indexed(payload['jobPath'], payload['jobSha256']); validate_job(job)
                require(job['pairId'] == state['pairId'], 'prepared job source identity held')
                state.update(phase='prepared', preparedJob=job, preparedJobPath=payload['jobPath'])
            elif kind == 'building':
                require(state is not None and state['phase'] == 'prepared', 'invalid build intent ordering')
                job = self._indexed(payload['jobPath'], payload['jobSha256'])
                validate_job(job)
                require(job['pairId'] == state['pairId'] and job == state['preparedJob'], 'job source identity held')
                state.update(phase='building', job=job, installedJobSha256=payload['jobSha256'])
            elif kind == 'built':
                require(state is not None and state['phase'] == 'building', 'invalid build ingestion ordering')
                index = self._indexed(payload['indexPath'], payload['indexSha256'])
                require(validate_candidate_index(payload['indexPath']) == index, 'candidate custody verification held')
                require(index.get('accepted') is True and index.get('job') == state['job'] and
                    index.get('jobSha256') == state['installedJobSha256'], 'root ingestion job binding held')
                state.update(phase='built', candidate=index)
            elif kind == 'tuple':
                require(state is not None and state['phase'] == 'built', 'invalid qualification tuple ordering')
                value = self._indexed(payload['tuplePath'], payload['tupleSha256']); validate_tuple(value)
                require(all(value[k] == state['job'][k] for k in ('engineSha', 'gymSha')) and
                    all(value[k] == state['candidate'][k] for k in ('jarSha256', 'frontendTreeSha256')), 'trusted staged tuple artifact/source held')
                state.update(phase='qualifying', tuple=value)
            elif kind == 'evidence':
                require(state is not None and state['phase'] == 'qualifying' and
                    payload['kind'] == next(k for k in KINDS if k not in state['evidence']), 'serial qualification order held')
                value = self._indexed(payload['path'], payload['sha256'])
                require(value.get('kind') == payload['kind'] and value.get('tuple') == state['tuple'], 'stored evidence identity held')
                state['evidence'][payload['kind']] = value
        return state

    def _indexed(self, path, expected):
        raw = root_read(path)
        require(hashlib.sha256(raw).hexdigest() == expected, 'immutable root index changed')
        return unique_json(raw)

    def observe(self, observation, current, *, now):
        with self.exclusive():
            sources = validate_sources(observation, self.config['ancestors'], now=now, max_age=self.config['maxSourceAgeSeconds'])
            state = self._state()
            require(state is None or state['pairId'] == sources['pairId'], 'fixed active source pair required')
            if state is None and self._suppressed(sources['pairId']):
                return dict(state='retired-source-held', pairId=sources['pairId'], actions=[])
            action = plan(state, sources, current, now=now, max_live_age=self.config['maxSourceAgeSeconds'],
                candidate_tuple=None if state is None else state.get('tuple'), evidence=None if state is None else state['evidence'],
                max_evidence_age=self.config['maxEvidenceAgeSeconds'], acknowledgement=self.config['acknowledgement'])
            if action['state'] != 'already-current':
                path = self.root / 'sources' / (sources['pairId'] + '-' + uuid.uuid4().hex + '.json')
                sha = root_write(path, sources)
                self._append('discovered' if state is None else 'observed', dict(pairId=sources['pairId'], sourcePath=str(path), sourceSha256=sha))
            if action['state'] != 'already-current' and observation.get('selectionAuthority') is not None:
                active = self._state()
                authority = selection_authority(observation['selectionAuthority'])
                if active['selectionAuthority'] is None:
                    self._append('authority', dict(attemptId=active['attemptId'], selectionAuthority=authority))
                else:
                    require(active['selectionAuthority'] == authority, 'active selection authority changed')
            return action

    def _suppressed(self, pair):
        # A validated discovery of another pair ends suppression of the old pair.
        blocked = None
        for event in self._chain()[0]:
            payload = event['payload']
            if event['event'] == 'retired': blocked = payload['pairId']
            elif event['event'] == 'discovered' and payload['pairId'] != blocked: blocked = None
        return pair == blocked

    def bind_promotion(self, path):
        with self.exclusive():
            state = self._state()
            require(state is not None and 'tuple' in state, 'qualified candidate required')
            require(set(state['evidence']) == set(KINDS), 'complete qualification required')
            raw = root_read(path)
            manifest = unique_json(raw)
            require(all(manifest.get(k) == state['tuple'][k] for k in ('engineSha','gymSha','jarSha256','frontendTreeSha256','recordingSchemaVersion')), 'promotion tuple mismatch')
            if 'manifest' in state:
                require(state['manifest'] == manifest, 'published promotion changed')
                return
            self._append('promotion', dict(attemptId=state['attemptId'], path=str(path), sha256=hashlib.sha256(raw).hexdigest()))

    def finish(self, proof_path, *, now, reason=None):
        with self.exclusive():
            state = self._state(); require(state is not None, 'active candidate required')
            proof = unique_json(root_read(proof_path))
            if reason is None:
                completion(state['tuple'], state['manifest'], proof, now=now, max_age=self.config['maxSourceAgeSeconds'])
            else:
                retirement(reason, proof, now=now, max_age=self.config['maxSourceAgeSeconds'])
            payload = dict(pairId=state['pairId'], attemptId=state['attemptId'], terminalUnix=now,
                proofPath=str(proof_path), proofSha256=hashlib.sha256(root_read(proof_path)).hexdigest())
            if reason is not None: payload['reason'] = reason
            self._append('completed' if reason is None else 'retired', payload)
            return dict(state='completed' if reason is None else 'retired', pairId=state['pairId'])

    def prepare_job(self, archive_indexes, *, now):
        with self.exclusive():
            state = self._state(); require(state is not None and state['phase'] == 'discovered', 'discovered serial source required')
            fresh(state['source']['observedUnix'], now, self.config['maxSourceAgeSeconds'])
            require(set(archive_indexes) == {'engine', 'gym'}, 'both root archive indexes required')
            archives = {}
            for role, path in archive_indexes.items():
                cached = unique_json(root_read(path))
                source_sha = state['source'][role + 'Sha']
                require(cached.get('role') == role and cached.get('sourceSha') == source_sha, 'archive role/source mismatch')
                expected = dict(sha256=cached['archiveSha256'], bytes=cached['archiveBytes'])
                require(archive_index(cached['path'], role, source_sha, expected) == cached, 'immutable cached archive changed')
                archives[role] = expected
            job = dict(engineSha=state['source']['engineSha'], gymSha=state['source']['gymSha'], pairId=state['pairId'], archives=archives)
            validate_job(job)
            path = self.root / 'jobs' / (state['pairId'] + '-' + uuid.uuid4().hex + '.json')
            sha = root_write(path, job)
            self._append('prepared', dict(jobPath=str(path), jobSha256=sha))
            return dict(action='publish-exact-build-job', jobPath=str(path), jobSha256=sha, job=job)

    def begin_build(self, installed_job_path, *, now):
        with self.exclusive():
            state = self._state(); require(state is not None and state['phase'] == 'prepared', 'serial build start required')
            fresh(state['source']['observedUnix'], now, self.config['maxSourceAgeSeconds'])
            raw = root_read(installed_job_path); job = unique_json(raw); validate_job(job)
            # Installed job must be a byte-identical copy of one prepared immutable job.
            prepared = {state['preparedJobPath']: root_read(state['preparedJobPath'])}
            require(raw in prepared.values() and job['pairId'] == state['pairId'], 'installed job not root prepared source')
            immutable_job_path = next(path for path, prepared_raw in prepared.items() if prepared_raw == raw)
            self._append('building', dict(jobPath=immutable_job_path, jobSha256=hashlib.sha256(raw).hexdigest()))
            return dict(action='run-isolated-build', pairId=state['pairId'])

    def record_build(self, index_path, *, now):
        with self.exclusive():
            state = self._state(); require(state is not None and state['phase'] == 'building', 'serial build ingestion required')
            fresh(state['source']['observedUnix'], now, self.config['maxSourceAgeSeconds'])
            raw = root_read(index_path); index = validate_candidate_index(index_path)
            require(index.get('accepted') is True and index.get('job') == state['job'] and
                index.get('jobSha256') == state['installedJobSha256'], 'root build index/job mismatch')
            self._append('built', dict(indexPath=str(index_path), indexSha256=hashlib.sha256(raw).hexdigest()))
            return dict(action='stage-recorder-from-root-gym-archive-and-verify-closure', pairId=state['pairId'])

    def record_tuple(self, tuple_path, *, now):
        with self.exclusive():
            state = self._state(); require(state is not None and state['phase'] == 'built', 'serial trusted tuple required')
            fresh(state['source']['observedUnix'], now, self.config['maxSourceAgeSeconds'])
            raw = root_read(tuple_path); value = unique_json(raw); validate_tuple(value)
            require(all(value[k] == state['job'][k] for k in ('engineSha', 'gymSha')) and
                all(value[k] == state['candidate'][k] for k in ('jarSha256', 'frontendTreeSha256')), 'candidate tuple/artifact mismatch')
            self._append('tuple', dict(tuplePath=str(tuple_path), tupleSha256=hashlib.sha256(raw).hexdigest()))
            return dict(action='qualify-native', pairId=state['pairId'])

    def record_evidence(self, evidence_path, *, now):
        with self.exclusive():
            state = self._state(); require(state is not None and state['phase'] == 'qualifying', 'serial qualification required')
            fresh(state['source']['observedUnix'], now, self.config['maxSourceAgeSeconds'])
            require(len(state['evidence']) < len(KINDS), 'qualification already complete')
            kind = next(k for k in KINDS if k not in state['evidence'])
            raw = root_read(evidence_path); value = unique_json(raw)
            validate_evidence(value, state['tuple'], kind, now=now, max_age=self.config['maxEvidenceAgeSeconds'], acknowledgement=self.config['acknowledgement'])
            self._append('evidence', dict(kind=kind, path=str(evidence_path), sha256=hashlib.sha256(raw).hexdigest()))
            return dict(action=('qualify-' + KINDS[len(state['evidence'])+1]) if len(state['evidence'])+1 < len(KINDS) else 'reobserve-live-source-and-evaluate-existing-rollout-gates')
