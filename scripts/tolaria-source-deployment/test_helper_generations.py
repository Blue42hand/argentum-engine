import hashlib
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import helper_generations as g


def pair(letter):return {key:letter*64 for key in ('releaseId','generation','qualificationSha256','nativeIndexSha256','sourceIndexSha256','bindingSha256')}


class FakeHost:
    def __init__(self,previous,candidate,fault=None):
        self.previous=previous;self.candidate=candidate;self.selected=previous;self.binding=previous
        self.running=True;self.closed=True;self.events=[];self.fault=fault
    def event(self,name):
        self.events.append(name)
        if name==self.fault:raise OSError('synthetic boundary')
    def verify_pair(self,value):self.event('verify-pair');assert value in (self.previous,self.candidate)
    def stop_consumers(self):self.event('stop');self.running=False
    def verify_stopped(self):self.event('stopped');assert not self.running
    def bind(self,pair):self.event('bind');self.binding=pair
    def verify_binding(self,pair):self.event('verify-binding');assert self.binding==pair
    def select(self,pair):self.event('select');self.selected=pair
    def verify_selected(self,pair):self.event('verify-selected');assert self.selected==pair
    def restart_closed(self):self.event('restart');assert self.binding==self.selected;self.running=True
    def verify_closed(self,pair):self.event('verify-closed');assert self.running and self.closed and self.selected==pair
    def verify_healthy(self,pair):self.event('health');assert self.running and self.closed and self.selected==pair


class GenerationStoreTests(unittest.TestCase):
    def fixture(self,base):
        files={name:('# synthetic '+name+'\n').encode() for name in g.NATIVE_FILES if name!='service_activation.py'}
        files['service_activation.py']=('SOURCE_CLOSURE = '+repr({name:g.digest(raw) for name,raw in files.items()})+'\n').encode()
        generation=g.digest(files['service_activation.py']);parent=base/generation;native=parent/'native';native.mkdir(parents=True)
        for name,raw in files.items():(native/name).write_bytes(raw)
        index=dict(schemaVersion=1,generation=generation,files={name:g.digest(raw) for name,raw in files.items()})
        (parent/'index.json').write_bytes(g.canonical(index))
        return generation,parent,native,index
    def test_complete_generation_is_bound_by_own_activation_hash(self):
        with tempfile.TemporaryDirectory() as tmp,patch.object(g,'protected'):
            generation,parent,native,index=self.fixture(Path(tmp));value=g.GenerationStore(Path(tmp)).from_receipt(dict(serviceActivationSha256=generation))
            self.assertEqual(value['native'],str(native));self.assertEqual(value['indexSha256'],g.digest(g.canonical(index)))
    def test_extra_bytecode_or_tampered_file_is_rejected(self):
        for action in ('extra','cache','tamper','wrong-index','wrong-closure'):
            with self.subTest(action=action),tempfile.TemporaryDirectory() as tmp,patch.object(g,'protected'):
                generation,parent,native,index=self.fixture(Path(tmp))
                if action=='extra':(native/'extra.py').write_text('')
                elif action=='cache':(native/'__pycache__').mkdir()
                elif action=='tamper':(native/'host_adapter.py').write_text('modified')
                elif action=='wrong-index':index['files']['service_activation.py']='f'*64;(parent/'index.json').write_bytes(g.canonical(index))
                elif action=='wrong-closure':index['files']['host_adapter.py']='f'*64;(parent/'index.json').write_bytes(g.canonical(index))
                with self.assertRaises(ValueError):g.GenerationStore(Path(tmp)).resolve(generation)
    def test_symlink_open_and_hardlink_custody_are_rejected(self):
        with tempfile.TemporaryDirectory() as tmp,patch.object(g,'protected'):
            generation,parent,native,index=self.fixture(Path(tmp));target=native/'host_adapter.py';saved=Path(tmp)/'saved';target.rename(saved);target.symlink_to(saved)
            with self.assertRaises(OSError):g.GenerationStore(Path(tmp)).resolve(generation)
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp).resolve();target=root/'x';target.write_text('');os.link(target,root/'y')
            original=Path.lstat
            def metadata(path):
                value=list(original(path));value[4]=value[5]=0;value[0]&=~0o022;return os.stat_result(value)
            with patch.object(Path,'lstat',metadata):
                with self.assertRaises(ValueError):g.protected(target)
    def test_free_form_paths_and_duplicate_metadata_fail(self):
        with tempfile.TemporaryDirectory() as tmp,patch.object(g,'protected'):
            generation,parent,native,index=self.fixture(Path(tmp))
            for bad in ('../escape','',True):
                with self.assertRaises(ValueError):g.GenerationStore(Path(tmp)).resolve(bad)
            (parent/'index.json').write_text('{"schemaVersion":1,"schemaVersion":1}')
            with self.assertRaises(ValueError):g.GenerationStore(Path(tmp)).resolve(generation)


class GenerationJournalTests(unittest.TestCase):
    def fixture(self,base):
        journal=g.GenerationJournal(base/'transaction.json');previous=pair('a');candidate=pair('b')
        journal.begin(previous,candidate,dict(scheduling={'synthetic.timer':{'ActiveState':'active'}}))
        return journal,FakeHost(previous,candidate)
    def test_switch_restart_health_and_completion_preserve_exact_intent(self):
        with tempfile.TemporaryDirectory() as tmp,patch.object(g,'protected'):
            journal,host=self.fixture(Path(tmp));intent=journal.state()
            journal.select(host.candidate,host);journal.restart(host);host.verify_healthy(host.candidate)
            journal.phase('healthy-closed');journal.phase('admission-intent');journal.phase('completed');journal.assert_finished()
            state=journal.state();self.assertEqual(state['previous'],intent['previous']);self.assertEqual(state['context'],intent['context'])
    def test_each_external_boundary_before_admission_rolls_back_both_exact_pairs(self):
        for boundary in ('stop','stopped','bind','verify-binding','select','verify-selected','restart','verify-closed','health'):
            with self.subTest(boundary=boundary),tempfile.TemporaryDirectory() as tmp,patch.object(g,'protected'):
                journal,host=self.fixture(Path(tmp));host.fault=boundary
                with self.assertRaises(OSError):
                    journal.select(host.candidate,host);journal.restart(host);host.verify_healthy(host.candidate)
                host.fault=None;journal.recover_closed(host)
                self.assertEqual(host.selected,host.previous);self.assertEqual(host.binding,host.previous)
                self.assertTrue(host.closed);self.assertEqual(journal.state()['phase'],'rolled-back-closed')
    def test_crash_at_every_journal_boundary_never_launches_a_mixed_pair(self):
        phases=['stop-intent','stopped','binding-intent','bound','selection-intent','selected','restart-intent','running-closed']
        for phase in phases:
            with self.subTest(phase=phase),tempfile.TemporaryDirectory() as tmp,patch.object(g,'protected'):
                journal,host=self.fixture(Path(tmp));original=journal.phase;fired=[]
                def checkpoint(value):
                    original(value)
                    if value==phase and not fired:fired.append(True);raise OSError('durable checkpoint crash')
                with patch.object(journal,'phase',side_effect=checkpoint):
                    with self.assertRaises(OSError):journal.select(host.candidate,host);journal.restart(host)
                journal.recover_closed(host);self.assertEqual(host.binding,host.selected);self.assertEqual(host.selected,host.previous)
    def test_admission_intent_refuses_all_automatic_rollback(self):
        with tempfile.TemporaryDirectory() as tmp,patch.object(g,'protected'):
            journal,host=self.fixture(Path(tmp));journal.select(host.candidate,host);journal.restart(host);journal.phase('healthy-closed');journal.phase('admission-intent')
            with self.assertRaises(ValueError):journal.recover_closed(host)
            with self.assertRaises(ValueError):journal.select(host.previous,host,rollback=True)
            with self.assertRaises(ValueError):journal.phase('rollback-stop-intent')
            self.assertEqual(host.selected,host.candidate)
    def test_journal_fsync_and_replace_failure_holds_without_any_external_action(self):
        for boundary in ('file-fsync','replace','directory-fsync'):
            with self.subTest(boundary=boundary),tempfile.TemporaryDirectory() as tmp,patch.object(g,'protected'):
                base=Path(tmp);journal=g.GenerationJournal(base/'transaction.json')
                if boundary=='file-fsync':context=patch.object(g.os,'fsync',side_effect=OSError('synthetic fsync'))
                elif boundary=='replace':context=patch.object(g.os,'replace',side_effect=OSError('synthetic rename'))
                else:context=patch.object(g,'sync',side_effect=OSError('synthetic directory fsync'))
                with context:
                    with self.assertRaises(OSError):journal.begin(pair('a'),pair('b'))
                with self.assertRaises(ValueError):journal.assert_finished()
    def test_each_rollback_boundary_failure_stays_held_and_can_resume_recorded_old_pair(self):
        for boundary in ('stop','bind','select','restart','health'):
            with self.subTest(boundary=boundary),tempfile.TemporaryDirectory() as tmp,patch.object(g,'protected'):
                journal,host=self.fixture(Path(tmp));journal.select(host.candidate,host);journal.restart(host);host.fault=boundary
                with self.assertRaises(OSError):journal.recover_closed(host)
                with self.assertRaises(ValueError):journal.assert_finished()
                host.fault=None;journal.recover_closed(host);self.assertEqual(host.selected,host.previous);self.assertEqual(host.binding,host.previous)
    def test_pending_phase_from_fsync_or_rename_crash_is_reconciled_before_closed_recovery(self):
        for phase in ('stop-intent','binding-intent','selection-intent','restart-intent','admission-intent'):
            with self.subTest(phase=phase),tempfile.TemporaryDirectory() as tmp,patch.object(g,'protected'):
                journal,host=self.fixture(Path(tmp));state=journal.state();pending=journal.path.with_name(journal.path.name+'.next')
                raw=g.canonical(dict(state,phase=phase))+b'\n';pending.write_bytes(raw);pending.chmod(0o600)
                if phase=='admission-intent':
                    with self.assertRaises(ValueError):journal.recover_closed(host)
                    self.assertEqual(journal.state()['phase'],'admission-intent');self.assertEqual(host.events,[])
                else:
                    journal.recover_closed(host);self.assertEqual(host.selected,host.previous);self.assertFalse(pending.exists())
    def test_pending_identity_changes_or_symlink_are_never_adopted(self):
        with tempfile.TemporaryDirectory() as tmp,patch.object(g,'protected'):
            journal,host=self.fixture(Path(tmp));state=journal.state();pending=journal.path.with_name(journal.path.name+'.next')
            pending.write_bytes(g.canonical(dict(state,previous=pair('c'))));pending.chmod(0o600)
            with self.assertRaises(ValueError):journal.recover_closed(host)
            self.assertEqual(host.events,[])
            pending.unlink();pending.symlink_to('/nonexistent-uncustodied-journal')
            with self.assertRaises(ValueError):journal.assert_finished()

    def test_unrelated_pair_cannot_be_selected_and_completed_intents_are_archived(self):
        with tempfile.TemporaryDirectory() as tmp,patch.object(g,'protected'):
            journal,host=self.fixture(Path(tmp))
            with self.assertRaises(ValueError):journal.select(pair('c'),host)
            journal.recover_closed(host);journal.begin(host.previous,host.candidate)
            self.assertEqual(len(list(Path(tmp).glob('generation-history-*.json'))),1)

if __name__=='__main__':unittest.main()
