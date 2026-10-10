"""Pure candidate lifecycle boundaries; callers supply fresh trusted observations."""
from coordinator_validation import DIGEST, canonical, digest, fresh, pair_id, require, validate_tuple

REASONS = frozenset(('source-withdrawn', 'invalid-ancestry', 'failed-ci', 'build-failed', 'qualification-failed'))


def selection_authority(value):
    require(type(value) is dict and set(value) == {'engine', 'gym'}, 'both selection authorities required')
    for entry in value.values():
        require(type(entry) is dict and entry.get('kind') in ('main', 'protected-pin'), 'selection authority required')
        require(set(entry) == ({'kind'} if entry['kind'] == 'main' else {'kind', 'pinSha256'}), 'exact selection authority fields required')
        if entry['kind'] == 'protected-pin':
            require(type(entry['pinSha256']) is str and DIGEST.fullmatch(entry['pinSha256']), 'protected pin identity required')
    return value


def qualification_id(artifact_tuple, attempt_id):
    require(type(attempt_id) is str and DIGEST.fullmatch(attempt_id), 'immutable attempt identity required')
    require('qualificationReleaseId' not in artifact_tuple, 'qualification identity already assigned')
    value = dict(artifact_tuple, qualificationReleaseId='0' * 64)
    validate_tuple(value)
    return digest(dict(artifacts=artifact_tuple, attemptId=attempt_id))


def stopped(proof, *, now, max_age):
    fresh(proof.get('observedUnix'), now, max_age)
    require(proof.get('noUnfinishedTransaction') is True, 'unfinished rollout held')
    for name in ('builder', 'qualification', 'qualification-recorder'):
        unit = proof.get(name, {})
        require(unit.get('state') in ('inactive', 'failed') and type(unit.get('pid')) is int and
                unit['pid'] == 0 and unit.get('cgroupEmpty') is True, 'positively stopped work required')


def retirement(reason, proof, *, now, max_age):
    require(reason in REASONS, 'classified terminal failure required')
    stopped(proof, now=now, max_age=max_age)
    require(proof.get('reason') == reason and proof.get('confirmed') is True, 'positive terminal evidence required')
    if reason == 'build-failed':
        require(proof['builder'].get('result') not in (None, '', 'success'), 'positive failed builder result required')
    return proof


def completion(value, manifest, proof, *, now, max_age):
    validate_tuple(value)
    require(type(manifest) is dict and type(manifest.get('releaseId')) is str and DIGEST.fullmatch(manifest['releaseId']), 'exact published release required')
    require(all(manifest.get(k) == value[k] for k in ('engineSha', 'gymSha', 'jarSha256', 'frontendTreeSha256', 'recordingSchemaVersion')), 'published manifest tuple mismatch')
    fresh(proof.get('observedUnix'), now, max_age)
    require(proof.get('manifestSha256') == digest(manifest), 'verified manifest identity required')
    require(all(proof.get(k) is True for k in ('manifestVerified', 'artifactsVerified', 'activationVerified', 'receiptVerified', 'noUnfinishedTransaction')), 'exact installed verification required')
    require(proof.get('tupleSha256') == digest(value), 'verified qualification tuple required')
    live = proof.get('live', {})
    fresh(live.get('observedUnix'), now, max_age)
    require(all(live.get(k) == manifest[k] for k in ('releaseId', 'engineSha', 'gymSha', 'recordingSchemaVersion')) and
            type(live.get('bootId')) is str and bool(live['bootId']) and live.get('recordingHealthy') is True and live.get('recoveryComplete') is True,
            'healthy exact current release required')
    require(type(live.get('pendingRecordWrites')) is int and live['pendingRecordWrites'] == 0, 'recording flush required')
    # Existing games are allowed after a completed promotion; completion never drains.
    require(live.get('acceptingNewGames') is True, 'completed admission required')
    return proof
