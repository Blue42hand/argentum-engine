"""Pure fail-closed validation for paired native source and qualification plans.

Callers supply trusted discovery, supervisor and root-custody observations. This
module performs no I/O and never installs, starts, promotes or schedules anything.
"""
import hashlib
import json
import math
import re

SHA = re.compile(r'[0-9a-f]{40}\Z')
DIGEST = re.compile(r'[0-9a-f]{64}\Z')
REQUIRED_CI = {
    'engine': frozenset(('backend', 'frontend', 'test', 'coverage', 'test (engine)',
        'test (server)', 'test (tools)', 'test (content)', 'test (scenarios-old)',
        'test (scenarios-2023-24)', 'test (scenarios-2025-26)')),
    'gym': frozenset(('python', 'jvm-adapter')),
}
KINDS = ('native', 'restart', 'crash', 'restore', 'cold', 'acknowledgement')
TUPLE_KEYS = frozenset(('engineSha', 'gymSha', 'jarSha256', 'frontendTreeSha256',
    'recorderClosureSha256', 'serviceActivationSha256', 'qualificationReleaseId', 'recordingSchemaVersion'))


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(',', ':'), allow_nan=False).encode()


def digest(value):
    return hashlib.sha256(canonical(value)).hexdigest()


def require(condition, reason):
    if not condition:
        raise ValueError(reason)


def fresh(observed, now, max_age):
    require(type(observed) in (int, float) and math.isfinite(observed) and
        type(now) in (int, float) and math.isfinite(now) and
        type(max_age) in (int, float) and 0 < max_age <= 86400 and
        0 <= now - observed <= max_age, 'fresh observation required')


def pair_id(engine, gym):
    require(type(engine) is str and SHA.fullmatch(engine) and type(gym) is str and SHA.fullmatch(gym), 'exact source pair required')
    return hashlib.sha256((engine + '\n' + gym + '\n').encode()).hexdigest()


def validate_ci(role, sha, response):
    require(role in REQUIRED_CI and type(sha) is str and SHA.fullmatch(sha), 'CI source role/pin required')
    rows = response.get('check_runs')
    require(type(rows) is list and type(response.get('total_count')) is int and
        response['total_count'] == len(rows) and len(rows) <= 100, 'complete bounded CI response required')
    latest = {}; seen = set()
    for row in rows:
        require(type(row) is dict and type(row.get('id')) is int and row['id'] > 0 and
            row['id'] not in seen and type(row.get('name')) is str and row['name'] and
            row.get('head_sha') == sha and row.get('app', {}).get('slug') == 'github-actions', 'CI identity required')
        seen.add(row['id'])
        if row['name'] not in latest or latest[row['name']]['id'] < row['id']:
            latest[row['name']] = row
    require(REQUIRED_CI[role] <= latest.keys(), 'required CI missing')
    for name, row in latest.items():
        require(row.get('status') == 'completed' and row.get('conclusion') in ('success', 'skipped'), 'latest CI not completed successfully')
        if name in REQUIRED_CI[role]:
            require(row['conclusion'] == 'success', 'required CI skipped')
    return {name: {'id': row['id'], 'conclusion': row['conclusion']} for name, row in sorted(latest.items())}


def validate_sources(value, ancestors, *, now, max_age):
    require(set(ancestors) == {'engine', 'gym'}, 'both configured ancestors required')
    fresh(value.get('observedUnix'), now, max_age)
    pair = pair_id(value.get('engineSha'), value.get('gymSha'))
    checks = {}; ancestry = {}
    for role in ('engine', 'gym'):
        sha = value[role + 'Sha']; base = ancestors[role]
        require(type(base) is str and SHA.fullmatch(base), 'configured ancestor pin required')
        checks[role] = validate_ci(role, sha, value['checks'][role])
        evidence = value['ancestry'][role]
        require(evidence.get('baseSha') == base and evidence.get('headSha') == sha and
            evidence.get('mergeBaseSha') == base and evidence.get('status') in ('ahead', 'identical') and
            (evidence['status'] != 'identical' or base == sha), 'required ancestor not verified')
        ancestry[role] = dict(evidence)
    return dict(schemaVersion=1, pairId=pair, engineSha=value['engineSha'], gymSha=value['gymSha'],
        observedUnix=value['observedUnix'], checks=checks, ancestry=ancestry)


def validate_job(job):
    require(type(job) is dict and set(job) == {'engineSha', 'gymSha', 'pairId', 'archives'}, 'exact build job schema required')
    require(pair_id(job['engineSha'], job['gymSha']) == job['pairId'] and set(job['archives']) == {'engine', 'gym'}, 'build job source mismatch')
    for role, entry in job['archives'].items():
        require(type(entry) is dict and set(entry) == {'sha256', 'bytes'} and
            type(entry['sha256']) is str and DIGEST.fullmatch(entry['sha256']) and
            type(entry['bytes']) is int and 0 < entry['bytes'] <= 512 * 1024**2, 'archive binding required')
    return digest(job)


def validate_result(result, job, attempt):
    validate_job(job)
    expected = {'engineSha', 'gymSha', 'pairId', 'attempt', 'jarSha256', 'frontendTreeSha256', 'frontendFiles', 'paidProvidersEnabled'}
    require(type(result) is dict and set(result) == expected, 'exact builder result schema required')
    require(re.fullmatch(re.escape(job['pairId']) + r'-[0-9]{1,20}', attempt) and result['attempt'] == attempt and
        all(result[k] == job[k] for k in ('engineSha', 'gymSha', 'pairId')), 'builder job/source/attempt mismatch')
    require(result['paidProvidersEnabled'] is False and type(result['frontendFiles']) is int and
        0 < result['frontendFiles'] <= 50000, 'builder shape required')
    for key in ('jarSha256', 'frontendTreeSha256'):
        require(type(result[key]) is str and DIGEST.fullmatch(result[key]), 'builder artifact digest required')
    return dict(result)


def validate_tuple(value):
    require(type(value) is dict and set(value) == TUPLE_KEYS, 'full exact qualification tuple required')
    pair_id(value['engineSha'], value['gymSha'])
    for key in TUPLE_KEYS - {'engineSha', 'gymSha', 'recordingSchemaVersion'}:
        require(type(value[key]) is str and DIGEST.fullmatch(value[key]), 'tuple digest required')
    require(type(value['recordingSchemaVersion']) is int and value['recordingSchemaVersion'] == 1, 'recording schema required')
    return dict(value)


def validate_evidence(value, tuple_value, kind, *, now, max_age, acknowledgement=None):
    validate_tuple(tuple_value)
    require(kind in KINDS and type(value) is dict and value.get('schemaVersion') == 1 and
        type(value.get('schemaVersion')) is int and value.get('kind') == kind and
        value.get('tuple') == tuple_value, 'exact qualification evidence identity required')
    fresh(value.get('observedUnix'), now, max_age)
    outcome = value.get('outcome')
    require(type(outcome) is dict and outcome.get('paidCalls') is False and outcome.get('userGamesLaunched') is False, 'keyless qualification required')
    flags = {
        'native': ('fixtureComplete', 'manifestVerified', 'engineTerminalVerified'),
        'restart': ('recorderRecovered', 'newBootVerified', 'admissionClosed'),
        'crash': ('unexpectedExitHeld', 'admissionClosed', 'supervisorVerified'),
        'restore': ('byteIntegrityRestoreVerified', 'exactSnapshotVerified'),
        'acknowledgement': ('sourceUnchanged', 'rootControlUnchanged', 'guardedStoppedAfter'),
    }
    for flag in flags.get(kind, ()):
        require(outcome.get(flag) is True, 'mandatory qualification fact absent')
    if kind == 'cold':
        for mode in ('native-only', 'full-journal'):
            case = outcome.get(mode, {})
            require(case.get('zeroExit') is True and case.get('timeoutKill') is False and
                case.get('sourceUnchanged') is True and case.get('copiedBytesUnchanged') is True and
                case.get('manifestAbsent') is True and type(case.get('stopSeconds')) in (int, float) and
                math.isfinite(case['stopSeconds']) and 0 <= case['stopSeconds'] < 30, 'both bounded cold cases required')
    if kind == 'acknowledgement':
        require(type(acknowledgement) is dict and set(acknowledgement) == {'registrySha256', 'count'} and
            type(acknowledgement['count']) is int and acknowledgement['count'] >= 0 and
            outcome.get('registrySha256') == acknowledgement['registrySha256'] and
            outcome.get('acknowledgedIncomplete') == acknowledgement['count'], 'exact root acknowledgement policy required')
        require(type(outcome.get('acknowledgedIncomplete')) is int and outcome['acknowledgedIncomplete'] >= 0 and
            type(outcome.get('pendingRecordWrites')) is int and outcome['pendingRecordWrites'] == 0 and
            outcome.get('recordingHealthy') is True and outcome.get('recordingComplete') is False, 'historical acknowledgement proof required')
        require(type(outcome.get('registrySha256')) is str and DIGEST.fullmatch(outcome['registrySha256']), 'root registry pin required')
    if kind in ('native', 'restart', 'crash'):
        observations = value.get('observedLifecycle')
        require(type(observations) is list and bool(observations), 'actual lifecycle observations required')
        for observed in observations:
            require(all(observed.get(k) == tuple_value[k] for k in ('engineSha', 'gymSha', 'recordingSchemaVersion')) and
                observed.get('releaseId') == tuple_value['qualificationReleaseId'] and
                type(observed.get('bootId')) is str and bool(observed['bootId']), 'lifecycle tuple/boot required')
            fresh(observed.get('observedUnix'), now, max_age)
    return dict(value)


def plan(state, sources, current, *, now, max_live_age, candidate_tuple=None, evidence=None, max_evidence_age=None, acknowledgement=None):
    """Return explicit executor actions; never perform them or weaken live gates."""
    fresh(sources.get('observedUnix'), now, max_live_age)
    pair = pair_id(sources.get('engineSha'), sources.get('gymSha'))
    fresh(current.get('observedUnix'), now, max_live_age)
    pair_id(current.get('engineSha'), current.get('gymSha'))
    require(type(current.get('releaseId')) is str and DIGEST.fullmatch(current['releaseId']) and
        type(current.get('bootId')) is str and bool(current['bootId']) and current.get('recordingHealthy') is True and
        current.get('recoveryComplete') is True and type(current.get('pendingRecordWrites')) is int and
        current['pendingRecordWrites'] == 0, 'healthy live current source observation required')
    if state is None and all(current[k] == sources[k] for k in ('engineSha', 'gymSha')):
        return dict(state='already-current', pairId=pair, actions=[])
    if state is None:
        return dict(state='discovered', pairId=pair, actions=['index-exact-source-archives', 'prepare-root-build-job'])
    require(state.get('pairId') == pair, 'active attempt superseded by live source')
    phase = state.get('phase')
    if phase == 'discovered':
        return dict(state='discovered', pairId=pair, actions=['prepare-root-build-job'])
    if phase == 'prepared':
        return dict(state='prepared', pairId=pair, actions=['publish-exact-build-job', 'run-isolated-build'])
    if phase == 'building':
        return dict(state='building', pairId=pair, actions=['inspect-stopped-build-and-ingest'])
    require(phase in ('built', 'qualifying', 'qualified'), 'serial candidate state required')
    if phase == 'built' and candidate_tuple is None:
        return dict(state='built', pairId=pair, actions=['stage-root-source-recorder-and-index-exact-tuple'])
    validate_tuple(candidate_tuple)
    require(all(candidate_tuple[k] == sources[k] for k in ('engineSha', 'gymSha')), 'candidate source no longer current')
    evidence = evidence or {}
    require(set(evidence) <= set(KINDS), 'unknown qualification kind')
    for kind, value in evidence.items():
        validate_evidence(value, candidate_tuple, kind, now=now, max_age=max_evidence_age, acknowledgement=acknowledgement)
    missing = [kind for kind in KINDS if kind not in evidence]
    if missing:
        return dict(state='qualifying', pairId=pair, actions=['qualify-' + missing[0]], remaining=missing)
    return dict(state='qualified', pairId=pair, actions=['evaluate-existing-rollout-gates'], tuple=candidate_tuple)
