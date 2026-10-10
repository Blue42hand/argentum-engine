import copy
import hashlib
from pathlib import Path
import unittest

import candidate_lifecycle as lifecycle
import source_coordinator as coordination
import candidate_custody as custody
import coordinator_validation as validation
import test_source_coordinator as fixtures
from test_source_coordinator import E, G, NOW, ANCESTORS, ACK, TUPLE, observation, current, evidence


def stop_proof(reason='build-failed'):
    return dict(observedUnix=NOW, noUnfinishedTransaction=True, reason=reason, confirmed=True,
        **{name:dict(state='inactive',pid=0,cgroupEmpty=True,result='exit-code')
           for name in ('builder','qualification','qualification-recorder')})


class LifecycleTests(fixtures.CustodyTests):
    def coordinator(self):
        root=self.base/'journal';root.mkdir(mode=0o700)
        value=coordination.Coordinator(root,dict(ancestors=ANCESTORS,maxSourceAgeSeconds=10,maxEvidenceAgeSeconds=100,acknowledgement=ACK))
        obs=observation();obs['selectionAuthority']={r:dict(kind='main') for r in ('engine','gym')}
        value.observe(obs,current(),now=NOW)
        return value,obs

    def build(self,coord):
        archives={}
        for role in ('engine','gym'):
            path=self.base/(role+'-archive.json')
            custody.root_write(path,custody.archive_index(self.archive_paths[role],role,self.job[role+'Sha'],self.job['archives'][role]));archives[role]=path
        coord.prepare_job(archives,now=NOW);coord.begin_build(self.job_path,now=NOW)
        index=self.ingest();coord.record_build(Path(index['retainedPath'])/'index.json',now=NOW)
        staged=dict(TUPLE,jarSha256=index['jarSha256'],frontendTreeSha256=index['frontendTreeSha256'])
        staged['qualificationReleaseId']=lifecycle.qualification_id({k:v for k,v in staged.items() if k!='qualificationReleaseId'},coord._state()['attemptId'])
        path=self.base/'tuple.json';custody.root_write(path,staged);coord.record_tuple(path,now=NOW)
        for kind in validation.KINDS:
            path=self.base/(kind+'.json');custody.root_write(path,evidence(kind,staged));coord.record_evidence(path,now=NOW)
        return staged

    def test_legacy_entries_are_readable_and_attempt_is_stable(self):
        coord,_=self.coordinator();before=coord._state()
        reopened=coordination.Coordinator(coord.root,coord.config)
        self.assertEqual(before,reopened._state())
        self.assertEqual(before['discoveredUnix'],NOW)
        self.assertEqual(len(before['attemptId']),64)
        self.assertEqual(coord._chain()[0][0]['schemaVersion'],1)

    def test_old_helper_rejects_new_authority_event(self):
        coord,_=self.coordinator()
        old_events={'discovered','building','built','tuple','evidence','superseded','observed','prepared'}
        self.assertFalse(all(e['event'] in old_events for e in coord._chain()[0]))

    def test_main_advance_does_not_change_candidate_at_each_phase(self):
        coord,obs=self.coordinator();attempt=coord._state()['attemptId']
        for _ in range(3):
            changed=copy.deepcopy(obs);changed['engineSha']='f'*40
            for row in changed['checks']['engine']['check_runs']:row['head_sha']='f'*40
            changed['ancestry']['engine']['headSha']='f'*40
            with self.assertRaises(ValueError):coord.observe(changed,current(),now=NOW)
            coord.observe(obs,current(),now=NOW)
        self.build(coord)
        coord.observe(obs,current(),now=NOW)
        self.assertEqual(coord._state()['attemptId'],attempt)
        self.assertEqual(coord._state()['source']['engineSha'],E)

    def test_pin_authority_cannot_change_mid_attempt(self):
        coord,obs=self.coordinator()
        changed=copy.deepcopy(obs);changed['selectionAuthority']['gym']=dict(kind='protected-pin',pinSha256='8'*64)
        with self.assertRaises(ValueError):coord.observe(changed,current(),now=NOW)
        self.assertEqual(coord._state()['selectionAuthority'],obs['selectionAuthority'])

    def test_positive_failure_retirement_and_suppression_survive_restart(self):
        coord,obs=self.coordinator();path=self.base/'failure.json';custody.root_write(path,stop_proof())
        coord.finish(path,now=NOW,reason='build-failed')
        reopened=coordination.Coordinator(coord.root,coord.config)
        self.assertIsNone(reopened._state())
        self.assertEqual(reopened.observe(obs,current(),now=NOW)['state'],'retired-source-held')
        changed=copy.deepcopy(obs);changed['engineSha']='f'*40
        for row in changed['checks']['engine']['check_runs']:row['head_sha']='f'*40
        changed['ancestry']['engine']['headSha']='f'*40
        self.assertEqual(reopened.observe(changed,current(),now=NOW)['state'],'discovered')

    def test_busy_unknown_or_unfinished_work_cannot_retire(self):
        coord,_=self.coordinator()
        for index,mutation in enumerate(({'pid':1},{'state':'activating'},{'cgroupEmpty':False},{'pid':False})):
            proof=stop_proof();proof['builder'].update(mutation)
            path=self.base/('proof-'+str(index)+'.json');custody.root_write(path,proof)
            with self.assertRaises(ValueError):coord.finish(path,now=NOW,reason='build-failed')
        proof=stop_proof();proof['noUnfinishedTransaction']=False
        path=self.base/'unfinished.json';custody.root_write(path,proof)
        with self.assertRaises(ValueError):coord.finish(path,now=NOW,reason='build-failed')
        self.assertIsNotNone(coord._state())

    def test_transient_reasons_cannot_retire(self):
        for reason in ('network-error','pending-ci','missing-approval','active-games'):
            with self.assertRaises(ValueError):lifecycle.retirement(reason,stop_proof(reason),now=NOW,max_age=10)

    def test_retry_identity_rejects_old_evidence_and_approval_digest(self):
        a=dict(TUPLE);a['qualificationReleaseId']=lifecycle.qualification_id({k:v for k,v in a.items() if k!='qualificationReleaseId'},'1'*64)
        b=dict(a);b['qualificationReleaseId']=lifecycle.qualification_id({k:v for k,v in b.items() if k!='qualificationReleaseId'},'2'*64)
        self.assertNotEqual(validation.digest(a),validation.digest(b))
        with self.assertRaises(ValueError):validation.validate_evidence(evidence('native',a),b,'native',now=NOW,max_age=100)

    def test_crash_after_promotion_reconciles_exact_release_without_republishing(self):
        coord,_=self.coordinator();value=self.build(coord)
        manifest={k:value[k] for k in ('engineSha','gymSha','jarSha256','frontendTreeSha256','recordingSchemaVersion')};manifest['releaseId']='8'*64
        path=self.base/'manifest.json';custody.root_write(path,manifest);coord.bind_promotion(path)
        live=dict(current(E,G),recordingSchemaVersion=1,releaseId=manifest['releaseId'],acceptingNewGames=True,activeGames=2)
        proof=dict(observedUnix=NOW,manifestSha256=validation.digest(manifest),tupleSha256=validation.digest(value),live=live,
            **{k:True for k in ('manifestVerified','artifactsVerified','activationVerified','receiptVerified','noUnfinishedTransaction')})
        proof_path=self.base/'completion.json';custody.root_write(proof_path,proof)
        reopened=coordination.Coordinator(coord.root,coord.config)
        self.assertEqual(reopened.finish(proof_path,now=NOW)['state'],'completed')
        self.assertIsNone(reopened._state())
        self.assertEqual(reopened.observe(observation(),live,now=NOW)['state'],'already-current')

    def test_source_equality_and_unverified_completion_are_insufficient(self):
        coord,obs=self.coordinator();value=self.build(coord)
        self.assertEqual(coord.observe(obs,current(E,G),now=NOW)['state'],'qualified')
        manifest={k:value[k] for k in ('engineSha','gymSha','jarSha256','frontendTreeSha256','recordingSchemaVersion')};manifest['releaseId']='8'*64
        with self.assertRaises(ValueError):lifecycle.completion(value,manifest,dict(observedUnix=NOW),now=NOW,max_age=10)
        self.assertIsNotNone(coord._state())

if __name__=='__main__':unittest.main()
