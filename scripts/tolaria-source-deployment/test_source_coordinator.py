import copy
import hashlib
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import coordinator_validation as validation
import candidate_custody as custody
import source_coordinator as coordination

E='a'*40; G='b'*40; NOW=1000
ANCESTORS={'engine':'0'*40,'gym':'1'*40}
ACK={'registrySha256':'9'*64,'count':1}
TUPLE=dict(engineSha=E,gymSha=G,jarSha256='2'*64,frontendTreeSha256='3'*64,
 recorderClosureSha256='4'*64,serviceActivationSha256='5'*64,qualificationReleaseId='6'*64,recordingSchemaVersion=1)


def observation():
 checks={}
 for role,sha in (('engine',E),('gym',G)):
  rows=[dict(id=i+1,name=name,head_sha=sha,app={'slug':'github-actions'},status='completed',conclusion='success') for i,name in enumerate(sorted(validation.REQUIRED_CI[role]))]
  checks[role]=dict(total_count=len(rows),check_runs=rows)
 return dict(observedUnix=NOW,engineSha=E,gymSha=G,checks=checks,
  ancestry={role:dict(baseSha=base,headSha=E if role=='engine' else G,mergeBaseSha=base,status='ahead') for role,base in ANCESTORS.items()})


def current(engine='c'*40,gym='d'*40):
 return dict(observedUnix=NOW,engineSha=engine,gymSha=gym,releaseId='7'*64,bootId='current-boot',recordingHealthy=True,recoveryComplete=True,pendingRecordWrites=0)


def evidence(kind,tuple_value=None):
 value=dict(schemaVersion=1,kind=kind,tuple=copy.deepcopy(tuple_value or TUPLE),observedUnix=NOW,
  outcome=dict(paidCalls=False,userGamesLaunched=False))
 flags={'native':('fixtureComplete','manifestVerified','engineTerminalVerified'),
  'restart':('recorderRecovered','newBootVerified','admissionClosed'),
  'crash':('unexpectedExitHeld','admissionClosed','supervisorVerified'),
  'restore':('byteIntegrityRestoreVerified','exactSnapshotVerified'),
  'acknowledgement':('sourceUnchanged','rootControlUnchanged','guardedStoppedAfter')}
 for flag in flags.get(kind,()):value['outcome'][flag]=True
 if kind=='cold':
  for mode in ('native-only','full-journal'):
   value['outcome'][mode]=dict(zeroExit=True,timeoutKill=False,sourceUnchanged=True,copiedBytesUnchanged=True,manifestAbsent=True,stopSeconds=.5)
 if kind=='acknowledgement':value['outcome'].update(acknowledgedIncomplete=1,pendingRecordWrites=0,recordingHealthy=True,recordingComplete=False,registrySha256=ACK['registrySha256'])
 if kind in ('native','restart','crash'):
  value['observedLifecycle']=[dict(engineSha=E,gymSha=G,recordingSchemaVersion=1,releaseId=value['tuple']['qualificationReleaseId'],bootId='qa-boot',observedUnix=NOW)]
 return value


class PureValidationTests(unittest.TestCase):
 def test_exact_required_coverage_and_newest_ci(self):
  data=observation();self.assertEqual(len(validation.REQUIRED_CI['engine']),11);self.assertEqual(len(validation.REQUIRED_CI['gym']),2)
  validation.validate_sources(data,ANCESTORS,now=NOW,max_age=10)
  engine=data['checks']['engine'];engine['check_runs']=[row for row in engine['check_runs'] if row['name']!='coverage'];engine['total_count']-=1
  with self.assertRaises(ValueError):validation.validate_sources(data,ANCESTORS,now=NOW,max_age=10)
  data=observation();ci=data['checks']['gym'];ci['check_runs'].append(dict(ci['check_runs'][0],id=999,conclusion='failure'));ci['total_count']+=1
  with self.assertRaises(ValueError):validation.validate_sources(data,ANCESTORS,now=NOW,max_age=10)

 def test_source_ancestry_staleness_wrong_commit_and_duplicate_id(self):
  for mutate in (lambda d:d['ancestry']['engine'].update(status='diverged'),lambda d:d.update(observedUnix=NOW+1),lambda d:d['checks']['gym']['check_runs'][0].update(head_sha='c'*40),lambda d:d['checks']['gym']['check_runs'][1].update(id=d['checks']['gym']['check_runs'][0]['id'])):
   value=observation();mutate(value)
   with self.assertRaises(ValueError):validation.validate_sources(value,ANCESTORS,now=NOW,max_age=10)

 def test_noop_live_current_and_explicit_no_mutation_plans(self):
  sources=validation.validate_sources(observation(),ANCESTORS,now=NOW,max_age=10)
  self.assertEqual(validation.plan(None,sources,current(E,G),now=NOW,max_live_age=10)['actions'],[])
  self.assertEqual(validation.plan(None,sources,current(),now=NOW,max_live_age=10)['state'],'discovered')
  unhealthy=current(E,G);unhealthy['recordingHealthy']=False
  with self.assertRaises(ValueError):validation.plan(None,sources,unhealthy,now=NOW,max_live_age=10)
  with self.assertRaises(ValueError):validation.plan(dict(pairId='0'*64,phase='built'),sources,current(),now=NOW,max_live_age=10)

 def test_six_exact_fresh_qualifications_and_failed_facts(self):
  all_evidence={kind:evidence(kind) for kind in validation.KINDS}
  for kind,value in all_evidence.items():
   validation.validate_evidence(value,TUPLE,kind,now=NOW,max_age=100,acknowledgement=ACK)
   bad=copy.deepcopy(value);bad['tuple']['gymSha']='c'*40
   with self.assertRaises(ValueError):validation.validate_evidence(bad,TUPLE,kind,now=NOW,max_age=100,acknowledgement=ACK)
   bad=copy.deepcopy(value);bad['observedUnix']=0
   with self.assertRaises(ValueError):validation.validate_evidence(bad,TUPLE,kind,now=NOW,max_age=100,acknowledgement=ACK)
  bad=evidence('cold');bad['outcome']['full-journal']['timeoutKill']=True
  with self.assertRaises(ValueError):validation.validate_evidence(bad,TUPLE,'cold',now=NOW,max_age=100)
  bad=evidence('acknowledgement');bad['outcome']['acknowledgedIncomplete']=0
  with self.assertRaises(ValueError):validation.validate_evidence(bad,TUPLE,'acknowledgement',now=NOW,max_age=100,acknowledgement=ACK)
  sources=validation.validate_sources(observation(),ANCESTORS,now=NOW,max_age=10)
  state=dict(pairId=sources['pairId'],phase='qualifying')
  self.assertEqual(validation.plan(state,sources,current(),now=NOW,max_live_age=10,candidate_tuple=TUPLE,evidence=all_evidence,max_evidence_age=100,acknowledgement=ACK)['actions'],['evaluate-existing-rollout-gates'])
  del all_evidence['acknowledgement']
  self.assertEqual(validation.plan(state,sources,current(),now=NOW,max_live_age=10,candidate_tuple=TUPLE,evidence=all_evidence,max_evidence_age=100,acknowledgement=ACK)['actions'],['qualify-acknowledgement'])


class CustodyTests(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.base=Path(self.tmp.name).resolve()
  self.patches=[patch.object(custody,'require_root'),patch.object(coordination,'require_root'),
   patch.object(custody,'trusted'),patch.object(coordination,'trusted'),patch.object(custody,'ROOT_UID',os.getuid())]
  for item in self.patches:item.start()
  self.boundary=self.base/'build';self.work=self.boundary/'work';self.work.mkdir(parents=True,mode=0o700)
  self.cache=self.base/'cache';self.cache.mkdir();self.retained=self.base/'retained';self.retained.mkdir(mode=0o700)
  self.archive_paths={};archives={}
  for role,pin in (('engine',E),('gym',G)):
   path=self.cache/(pin+'.tar.gz');path.write_bytes((role+'-immutable-public-source').encode());self.archive_paths[role]=path
   archives[role]=dict(sha256=hashlib.sha256(path.read_bytes()).hexdigest(),bytes=path.stat().st_size)
  self.job=dict(engineSha=E,gymSha=G,pairId=validation.pair_id(E,G),archives=archives)
  self.job_path=self.base/'job.json';self.job_path.write_bytes(validation.canonical(self.job)+b'\n')
  self.attempt=self.work/(self.job['pairId']+'-123')
  self.jar=self.attempt/'engine/game-server/build/libs/game-server.jar';self.jar.parent.mkdir(parents=True);self.jar.write_bytes(b'jar-artifact')
  self.web=self.attempt/'engine/web-client/dist';self.web.mkdir(parents=True);(self.web/'index.html').write_bytes(b'frontend-artifact');(self.web/'empty').mkdir()
  tree=hashlib.sha256(b'index.html\0'+hashlib.sha256((self.web/'index.html').read_bytes()).digest()).hexdigest()
  self.result=dict(engineSha=E,gymSha=G,pairId=self.job['pairId'],attempt=self.attempt.name,jarSha256=hashlib.sha256(self.jar.read_bytes()).hexdigest(),frontendTreeSha256=tree,frontendFiles=1,paidProvidersEnabled=False)
  (self.attempt/'result.json').write_bytes(validation.canonical(self.result)+b'\n')
  self.stop_calls=[]

 def tearDown(self):
  for item in reversed(self.patches):item.stop()
  self.tmp.cleanup()

 def ingest(self):
  return custody.ingest_attempt(job_path=self.job_path,archive_paths=self.archive_paths,attempt_path=self.attempt,
   build_boundary=self.boundary,builder_uid=os.getuid(),retained_root=self.retained,assert_builder_stopped=lambda:self.stop_calls.append('asserted'))

 def test_exact_root_snapshot_revalidated_and_builder_scratch_never_ingested(self):
  scratch=self.attempt/'engine/node_modules/.bin';scratch.mkdir(parents=True);(scratch/'untrusted').symlink_to('/nonexistent')
  index=self.ingest();self.assertEqual(len(self.stop_calls),2)
  path=Path(index['retainedPath'])/'index.json'
  self.assertEqual(custody.validate_candidate_index(path),index)
  self.assertTrue((Path(index['retainedPath'])/'bundle/engine/web-client/dist/empty').is_dir())
  self.assertFalse((Path(index['retainedPath'])/'bundle/engine/node_modules').exists())
  (Path(index['retainedPath'])/'bundle/engine/web-client/dist/index.html').write_bytes(b'tampered')
  with self.assertRaises(ValueError):custody.validate_candidate_index(path)

 def test_exact_job_source_artifact_result_and_archive_mismatch_rejected_retained(self):
  for mutate in (lambda:self.result.update(gymSha='c'*40),lambda:self.result.update(frontendTreeSha256='0'*64),lambda:self.result.update(paidProvidersEnabled=True),lambda:self.result.update(passed=True)):
   original=copy.deepcopy(self.result);mutate();(self.attempt/'result.json').write_bytes(validation.canonical(self.result))
   with self.assertRaises(ValueError):self.ingest()
   self.result=original
  (self.attempt/'result.json').write_bytes(validation.canonical(self.result))
  self.archive_paths['gym'].write_bytes(b'changed source')
  with self.assertRaises(ValueError):self.ingest()
  self.assertEqual(len(list(self.retained.glob('attempt-*/rejected.json'))),5)
  self.assertTrue(self.attempt.is_dir())

 def test_symlink_hardlink_and_special_artifact_rejected(self):
  path=self.web/'index.html';raw=path.read_bytes();path.unlink();path.symlink_to(self.jar)
  with self.assertRaises(OSError):self.ingest()
  path.unlink();os.link(self.jar,path)
  with self.assertRaises(ValueError):self.ingest()
  path.unlink();os.mkfifo(path)
  with self.assertRaises(ValueError):self.ingest()
  path.unlink();path.write_bytes(raw)

 def test_inode_rename_replacement_and_same_size_write_during_copy_rejected(self):
  original_copy=custody.copy_fd
  def tamper(source,destination):
   original_copy(source,destination)
   if not getattr(tamper,'done',False):
    tamper.done=True;path=self.web/'index.html';old=path.stat();raw=path.read_bytes()
    path.write_bytes(b'x'*len(raw));path.write_bytes(raw);os.utime(path,ns=(old.st_atime_ns,old.st_mtime_ns))
  with patch.object(custody,'copy_fd',side_effect=tamper),self.assertRaises(ValueError):self.ingest()
  def rename(source,destination):
   original_copy(source,destination)
   if not getattr(rename,'done',False):
    rename.done=True;self.attempt.rename(self.work/'retained-old-attempt');self.attempt.mkdir()
  with patch.object(custody,'copy_fd',side_effect=rename),self.assertRaises((ValueError,FileNotFoundError)):self.ingest()
  self.assertEqual(len(list(self.retained.glob('attempt-*/rejected.json'))),2)

 def test_changed_root_job_during_copy_rejected_even_same_content_inode(self):
  original_copy=custody.copy_fd
  def replace(source,destination):
   original_copy(source,destination)
   if not getattr(replace,'done',False):
    replace.done=True;raw=self.job_path.read_bytes();self.job_path.unlink();self.job_path.write_bytes(raw)
  with patch.object(custody,'copy_fd',side_effect=replace),self.assertRaises(ValueError):self.ingest()

 def test_root_bundle_extra_member_and_index_forgery_rejected(self):
  index=self.ingest();path=Path(index['retainedPath'])/'index.json'
  extra=Path(index['retainedPath'])/'bundle/extra.py';extra.write_bytes(b'untrusted')
  with self.assertRaises(ValueError):custody.validate_candidate_index(path)
  extra.unlink();forged=copy.deepcopy(index);forged['sourceSnapshotSha256']='0'*64;path.write_bytes(validation.canonical(forged))
  with self.assertRaises(ValueError):custody.validate_candidate_index(path)

 def test_coordinator_serial_full_flow_reobserves_source_and_noop(self):
  state_root=self.base/'state';state_root.mkdir(mode=0o700)
  config=dict(ancestors=ANCESTORS,maxSourceAgeSeconds=10,maxEvidenceAgeSeconds=100,acknowledgement=ACK)
  coordinator=coordination.Coordinator(state_root,config)
  self.assertEqual(coordinator.observe(observation(),current(),now=NOW)['state'],'discovered')
  archive_indexes={}
  for role in ('engine','gym'):
   value=custody.archive_index(self.archive_paths[role],role,self.job[role+'Sha'],self.job['archives'][role])
   path=self.base/(role+'-archive-index.json');custody.root_write(path,value);archive_indexes[role]=path
  prepared=coordinator.prepare_job(archive_indexes,now=NOW)
  self.assertEqual(prepared['job'],self.job)
  self.assertEqual(coordinator.begin_build(self.job_path,now=NOW)['action'],'run-isolated-build')
  with self.assertRaises(ValueError):coordinator.begin_build(self.job_path,now=NOW)
  index=self.ingest();index_path=Path(index['retainedPath'])/'index.json';coordinator.record_build(index_path,now=NOW)
  staged=dict(TUPLE,jarSha256=index['jarSha256'],frontendTreeSha256=index['frontendTreeSha256'])
  tuple_path=self.base/'tuple.json';custody.root_write(tuple_path,staged);coordinator.record_tuple(tuple_path,now=NOW)
  wrong=self.base/'wrong.json';custody.root_write(wrong,evidence('crash',staged))
  with self.assertRaises(ValueError):coordinator.record_evidence(wrong,now=NOW)
  for kind in validation.KINDS:
   path=self.base/(kind+'.json');custody.root_write(path,evidence(kind,staged));coordinator.record_evidence(path,now=NOW)
  self.assertEqual(coordinator.observe(observation(),current(),now=NOW)['actions'],['evaluate-existing-rollout-gates'])
  self.assertEqual(coordinator.observe(observation(),current(E,G),now=NOW)['state'],'qualified')
  with self.assertRaises(ValueError):coordinator.observe(observation(),current(),now=NOW+101)
  event=state_root/'events/00000000.json';event.write_bytes(event.read_bytes().replace(b'discovered',b'building'))
  with self.assertRaises(ValueError):coordinator.observe(observation(),current(),now=NOW)

 def test_state_steps_require_reobserved_source_and_retained_policy(self):
  state_root=self.base/'state';state_root.mkdir(mode=0o700)
  config=dict(ancestors=ANCESTORS,maxSourceAgeSeconds=10,maxEvidenceAgeSeconds=100,acknowledgement=ACK)
  coordinator=coordination.Coordinator(state_root,config);coordinator.observe(observation(),current(),now=NOW)
  with self.assertRaises(ValueError):coordinator.prepare_job({},now=NOW+11)
  with self.assertRaises(ValueError):coordinator.begin_build(self.job_path,now=NOW+11)
  changed=copy.deepcopy(config);changed['maxEvidenceAgeSeconds']=200
  with self.assertRaises(ValueError):coordination.Coordinator(state_root,changed)
  fresh=observation();fresh['observedUnix']=NOW+11;live=current();live['observedUnix']=NOW+11
  self.assertEqual(coordinator.observe(fresh,live,now=NOW+11)['state'],'discovered')
  self.assertEqual(coordinator._state()['source']['observedUnix'],NOW+11)

 def test_parallel_coordinator_lock_and_live_supersede(self):
  state_root=self.base/'state';state_root.mkdir(mode=0o700)
  config=dict(ancestors=ANCESTORS,maxSourceAgeSeconds=10,maxEvidenceAgeSeconds=100,acknowledgement=ACK)
  first=coordination.Coordinator(state_root,config);second=coordination.Coordinator(state_root,config)
  with first.exclusive():
   with self.assertRaises(BlockingIOError):
    with second.exclusive():pass
  first.observe(observation(),current(),now=NOW)
  changed=observation();changed['gymSha']='e'*40;changed['ancestry']['gym']['headSha']='e'*40
  for row in changed['checks']['gym']['check_runs']:row['head_sha']='e'*40
  with self.assertRaises(ValueError):first.observe(changed,current(),now=NOW)
  self.assertEqual(len(list((state_root/'events').iterdir())),1)


class ActualRootAuthorityTests(unittest.TestCase):
 @unittest.skipIf(os.geteuid()==0,'nonroot authority regression')
 def test_nonroot_cannot_run_root_ingestion_or_publish(self):
  with tempfile.TemporaryDirectory() as temp:
   with self.assertRaises(ValueError):custody.root_write(Path(temp)/'forged.json',{'accepted':True})
   with self.assertRaises(ValueError):custody.trusted(Path(temp),True)

if __name__=='__main__':unittest.main()
