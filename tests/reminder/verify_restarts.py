"""Verify ordinary process exit and a real reboot on a dedicated test emulator.

Run AFTER the main Instrumentation mode and BEFORE the permission-denied modes.
The test creates fixture state; do not run this against a personal device.
"""
from pathlib import Path
import argparse
import json
import re
import subprocess
import time
import xml.etree.ElementTree as ET

parser=argparse.ArgumentParser()
parser.add_argument('--adb',default='adb')
parser.add_argument('--serial',required=True)
parser.add_argument('--out',required=True)
args=parser.parse_args()
out=Path(args.out).resolve();out.mkdir(parents=True,exist_ok=True)
adb=[args.adb,'-s',args.serial]
def run(*parts):
    return subprocess.check_output(adb+list(parts),stderr=subprocess.STDOUT,timeout=25)
def alarm_matches(data,date):
    return re.search(r'RTC_WAKEUP #\d+: Alarm\{[^\n]*origWhen '+str(date)+r'[^\n]*net\.micode\.notes',data) is not None
def instrument(mode):
    result=run('shell','am','instrument','-w','-e','mode',mode,
        'net.micode.notes.test/net.micode.notes.tests.ReminderInstrumentation').decode()
    (out/(mode+'.log')).write_text(result,encoding='utf-8')
    assert 'RESULT: 1 passed, 0 failed' in result,result
def open_note(note_id):
    run('shell','am','start','-n','net.micode.notes/.ui.NoteEditActivity','-a',
        'android.intent.action.VIEW','--el','android.intent.extra.UID',str(note_id))
    time.sleep(2)

preferences=ET.fromstring(run('shell','run-as','net.micode.notes','cat','shared_prefs/b_reminder_test.xml'))
fixture={node.get('name'):int(node.get('value')) for node in preferences}
open_note(fixture['id']);run('shell','input','keyevent','3');time.sleep(1)
run('shell','am','kill','net.micode.notes')
alarms=run('shell','dumpsys','alarm').decode()
(out/'alarm-after-app-kill.txt').write_text(alarms,encoding='utf-8')
assert alarm_matches(alarms,fixture['date']),'System alarm missing after ordinary process kill'
instrument('after_restart')
# Instrumentation start/finish kills processes: reopen normally before real BOOT_COMPLETED.
open_note(fixture['id']);run('shell','input','keyevent','3')
assert 'stopped=false' in run('shell','dumpsys','package','net.micode.notes').decode()
boot_before=run('shell','cat','/proc/sys/kernel/random/boot_id').decode().strip()
run('reboot');start=time.monotonic();restored=False;alarms=''
while time.monotonic()-start<150:
    time.sleep(2)
    try:
        alarms=run('shell','dumpsys','alarm').decode()
        if alarm_matches(alarms,fixture['date']):restored=True;break
    except (subprocess.SubprocessError,UnicodeDecodeError):
        pass
(out/'alarm-after-reboot.txt').write_text(alarms,encoding='utf-8')
boot_after=run('shell','cat','/proc/sys/kernel/random/boot_id').decode().strip()
result=dict(note_id=fixture['id'],expected_alert_date=fixture['date'],
    ordinary_process_exit_keeps_system_alarm=True,package_not_stopped_before_reboot=True,
    boot_id_changed=boot_before!=boot_after,same_timestamp_restored=restored,
    wait_seconds=round(time.monotonic()-start,1))
(out/'restart-results.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
assert restored and boot_before!=boot_after,result
instrument('after_reboot')
print(json.dumps(result))
