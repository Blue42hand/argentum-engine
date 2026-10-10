"""Sealed native helper generations and a durable closed-admission transaction.

No services, network, provider calls or deployment paths are supplied here.
The trusted host supplies fixed namespace bindings and authenticates receipts.
"""
import ast
import hashlib
import json
import os
from pathlib import Path
import re
import stat

DIGEST = re.compile(r'[0-9a-f]{64}')
NATIVE_FILES = frozenset({'service_activation.py', 'host_adapter.py', 'native_server.py',
                          'rollout.py', 'release_gate.py', 'storage_health.py'})


def require(value, reason='helper generation held'):
    if not value:
        raise ValueError(reason)


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(',', ':'), allow_nan=False).encode()


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def unique(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, 'duplicate generation metadata')
        result[key] = value
    return result


def protected(path, directory=False, mode=None):
    path = Path(path)
    for entry in (path, *path.parents):
        info = entry.lstat()
        kind = stat.S_ISDIR if entry != path or directory else stat.S_ISREG
        require(kind(info.st_mode) and info.st_uid == 0 and info.st_gid == 0
                and not info.st_mode & 0o022, 'generation custody held')
        if entry == path and not directory:
            require(info.st_nlink == 1 and (mode is None or stat.S_IMODE(info.st_mode) == mode))


def read(path, *, mode=0o644, limit=2 * 1024**2):
    protected(path, mode=mode)
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW)
    try:
        before = os.fstat(fd)
        require(before.st_size <= limit)
        raw = b''
        while block := os.read(fd, 65536):
            raw += block
            require(len(raw) <= limit)
        after = os.fstat(fd)
        fields = ('st_dev', 'st_ino', 'st_size', 'st_mtime_ns', 'st_ctime_ns')
        require(all(getattr(before, key) == getattr(after, key) for key in fields))
        visible = path.lstat()
        require((visible.st_dev, visible.st_ino) == (after.st_dev, after.st_ino))
        return raw
    finally:
        os.close(fd)


class GenerationStore:
    """An immutable native directory plus an index outside its import root."""
    def __init__(self, root):
        self.root = Path(root)

    def resolve(self, generation):
        require(type(generation) is str and DIGEST.fullmatch(generation))
        parent = self.root / generation
        protected(parent, directory=True)
        require({path.name for path in parent.iterdir()} == {'native', 'index.json'})
        native = parent / 'native'
        protected(native, directory=True)
        index = json.loads(read(parent / 'index.json'), object_pairs_hook=unique)
        require(type(index) is dict and set(index) == {'schemaVersion', 'generation', 'files'})
        require(type(index['schemaVersion']) is int and index['schemaVersion'] == 1
                and index['generation'] == generation and type(index['files']) is dict)
        files = index['files']
        require(set(files) == NATIVE_FILES and {path.name for path in native.iterdir()} == NATIVE_FILES)
        payload = {}
        for name, want in files.items():
            require(type(want) is str and DIGEST.fullmatch(want))
            payload[name] = read(native / name)
            require(digest(payload[name]) == want, 'generation bytes changed')
        require(files['service_activation.py'] == generation)
        tree = ast.parse(payload['service_activation.py'])
        assignments = [node for node in tree.body if isinstance(node, ast.Assign)
                       and any(isinstance(target, ast.Name) and target.id == 'SOURCE_CLOSURE'
                               for target in node.targets)]
        require(len(assignments) == 1)
        closure = ast.literal_eval(assignments[0].value)
        require(closure == {name: want for name, want in files.items() if name != 'service_activation.py'},
                'activation source closure differs from generation')
        return dict(generation=generation, native=str(native), files=files,
                    indexSha256=digest(canonical(index)))

    def from_receipt(self, receipt):
        require(type(receipt) is dict)
        return self.resolve(receipt.get('serviceActivationSha256'))


def validate_pair(pair):
    require(type(pair) is dict and set(pair) == {
        'releaseId', 'generation', 'qualificationSha256', 'nativeIndexSha256',
        'sourceIndexSha256', 'bindingSha256'})
    require(all(type(value) is str and DIGEST.fullmatch(value) for value in pair.values()))
    return pair


def sync(directory):
    fd = os.open(directory, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
    try:
        os.fsync(fd)
    finally:
        os.close(fd)


class GenerationJournal:
    """One immutable intent and durable phases; unfinished work always holds.

    The host must acquire its existing locks before calling this object.
    Crash recovery never infers identities from latest source or current links.
    """
    TERMINAL = frozenset({'completed', 'rolled-back-closed'})
    PHASES = frozenset({'intent', 'stop-intent', 'stopped', 'binding-intent', 'bound',
                       'selection-intent', 'selected', 'restart-intent', 'running-closed',
                       'healthy-closed', 'admission-intent', 'completed',
                       'rollback-stop-intent', 'rollback-stopped', 'rollback-binding-intent',
                       'rollback-bound', 'rollback-selection-intent', 'rollback-selected',
                       'rollback-restart-intent', 'rollback-running-closed', 'rolled-back-closed',
                       'operator-review'})

    def __init__(self, path):
        self.path = Path(path)

    def load(self,path):
        raw = read(path, mode=0o600)
        state = json.loads(raw, object_pairs_hook=unique)
        require(type(state) is dict and set(state) == {'schemaVersion', 'previous', 'candidate', 'phase', 'context'})
        require(type(state['schemaVersion']) is int and state['schemaVersion'] == 1
                and state['phase'] in self.PHASES)
        validate_pair(state['previous']); validate_pair(state['candidate'])
        require(type(state['context']) is dict and len(canonical(state['context']))<=4096)
        return state

    def state(self):
        if not self.path.exists():
            require(not self.path.is_symlink())
            return None
        return self.load(self.path)

    def reconcile_pending(self):
        pending=self.path.with_name(self.path.name+'.next')
        if not pending.exists():require(not pending.is_symlink());return
        next_state=self.load(pending);current=self.state()
        if current is None or current['phase'] in self.TERMINAL:
            require(next_state['phase']=='intent','unrelated pending generation intent')
        else:
            require(all(current[key]==next_state[key] for key in ('schemaVersion','previous','candidate','context')),'pending generation identities changed')
            require(current['phase'] not in {'admission-intent','operator-review'} or next_state['phase'] in {'completed','operator-review'},'pending rollback after admission intent')
        # Adopt the durable authenticated pending phase. In particular, a pending
        # admission intent is held for operator review, never discarded for rollback.
        os.replace(pending,self.path);sync(self.path.parent)


    def save(self, state):
        protected(self.path.parent, directory=True)
        raw = canonical(state) + b'\n'
        temporary = self.path.with_name(self.path.name + '.next')
        require(not temporary.exists() and not temporary.is_symlink(), 'unfinished journal replacement held')
        fd = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
        with os.fdopen(fd, 'wb') as target:
            os.fchmod(target.fileno(), 0o600)
            target.write(raw); target.flush(); os.fsync(target.fileno())
        os.replace(temporary, self.path)
        sync(self.path.parent)

    def begin(self, previous, candidate, context=None):
        validate_pair(previous); validate_pair(candidate)
        state = self.state()
        require(state is None or state['phase'] in self.TERMINAL, 'unfinished generation transaction')
        if state is not None:
            # Retain every prior durable intent; never overwrite audit history.
            archive = self.path.parent / ('generation-history-' + digest(canonical(state)) + '.json')
            if archive.exists():
                require(read(archive, mode=0o600) == canonical(state) + b'\n')
            else:
                fd = os.open(archive, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
                with os.fdopen(fd, 'wb') as target:
                    target.write(canonical(state) + b'\n'); target.flush(); os.fsync(target.fileno())
                sync(archive.parent)
        context={} if context is None else context
        require(type(context) is dict and len(canonical(context))<=4096)
        self.save(dict(schemaVersion=1, previous=previous, candidate=candidate, phase='intent',context=context))

    def phase(self, phase):
        require(phase in self.PHASES)
        state = self.state(); require(state is not None)
        require(state['phase'] not in self.TERMINAL, 'terminal generation transaction')
        require(state['phase'] not in {'admission-intent', 'operator-review'} or phase in {'completed', 'operator-review'},
                'rollback after admission intent held')
        self.save(dict(state, phase=phase))

    def assert_finished(self):
        pending=self.path.with_name(self.path.name + '.next')
        require(not pending.exists() and not pending.is_symlink(), 'torn generation journal held')
        state = self.state()
        require(state is None or state['phase'] in self.TERMINAL, 'unfinished generation transaction')

    def select(self, pair, host, rollback=False):
        state = self.state(); require(state is not None)
        expected = state['previous' if rollback else 'candidate']
        require(pair == expected, 'switch outside recorded generation intent')
        require(state['phase'] not in {'admission-intent', 'operator-review'} and state['phase'] not in self.TERMINAL)
        prefix = 'rollback-' if rollback else ''
        host.verify_pair(pair)  # Authenticate complete bytes/receipt before side effects.
        self.phase(prefix + 'stop-intent'); host.stop_consumers(); host.verify_stopped()
        self.phase(prefix + 'stopped')
        self.phase(prefix + 'binding-intent'); host.bind(pair); host.verify_binding(pair)
        self.phase(prefix + 'bound')
        self.phase(prefix + 'selection-intent'); host.select(pair); host.verify_selected(pair)
        self.phase(prefix + 'selected')

    def restart(self, host, rollback=False):
        state = self.state(); require(state is not None)
        require(state['phase'] == ('rollback-selected' if rollback else 'selected'))
        pair = state['previous' if rollback else 'candidate']
        host.verify_pair(pair); host.verify_binding(pair); host.verify_selected(pair)
        prefix = 'rollback-' if rollback else ''
        self.phase(prefix + 'restart-intent'); host.restart_closed(); host.verify_closed(pair)
        self.phase(prefix + 'running-closed')

    def recover_closed(self, host):
        self.reconcile_pending()
        state = self.state(); require(state is not None)
        require(state['phase'] not in {'admission-intent', 'operator-review'}, 'admission uncertainty requires operator review')
        require(state['phase'] not in self.TERMINAL)
        self.select(state['previous'], host, rollback=True)
        self.restart(host, rollback=True)
        host.verify_healthy(state['previous'])
        self.phase('rolled-back-closed')
