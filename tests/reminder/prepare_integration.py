"""Optional acceptance snapshot of the current committed merged branch.

Run from any directory: python tests/reminder/prepare_integration.py
Normal builds run in the repository root. Commit the merge before exporting this HEAD snapshot.
"""
from pathlib import Path
import io
import os
import shutil
import subprocess
from zipfile import ZipFile
from xml.etree import ElementTree as ET

ROOT=Path(__file__).resolve().parents[2]
BASE='HEAD'
OUT=ROOT.parent/'.work/b-current-snapshot'
def git(*args):
    return subprocess.check_output(['git','-C',str(ROOT),*args])
OUT.mkdir(parents=True,exist_ok=True)
with ZipFile(io.BytesIO(git('archive','--format=zip',BASE))) as archive:
    for entry in archive.namelist():
        assert (OUT/entry).resolve().is_relative_to(OUT.resolve())
    archive.extractall(OUT)
tracked=git('diff','--name-only','HEAD','--','src','res').decode().splitlines()
untracked=git('ls-files','--others','--exclude-standard').decode().splitlines()
for relative in sorted(set(tracked+untracked)):
    if not relative.startswith(('src/','res/','tests/reminder/')):
        continue
    source=ROOT/relative
    if source.is_file():
        target=OUT/relative
        target.parent.mkdir(parents=True,exist_ok=True)
        shutil.copyfile(source,target)
# Ensure committed tests are included as well as the first untracked run.
shutil.copytree(ROOT/'tests/reminder',OUT/'tests/reminder',dirs_exist_ok=True)
ns='http://schemas.android.com/apk/res/android'
ET.register_namespace('android',ns)
ET.register_namespace('tools','http://schemas.android.com/tools')
tag='{'+ns+'}name'
baseline=ET.parse(OUT/'AndroidManifest.xml')
member=ET.parse(ROOT/'AndroidManifest.xml')
existing={el.get(tag) for el in baseline.getroot().findall('uses-permission')}
for perm in member.getroot().findall('uses-permission'):
    if perm.get(tag) not in existing:
        baseline.getroot().insert(0,perm)
for el in baseline.getroot().findall('.//receiver'):
    if el.get(tag)=='.ui.AlarmInitReceiver':
        intent=el.find('intent-filter')
        name='android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED'
        if not any(action.get(tag)==name for action in intent.findall('action')):
            ET.SubElement(intent,'action',{tag:name})
baseline.write(OUT/'AndroidManifest.xml',encoding='utf-8',xml_declaration=True)
# A distinct test-only host avoids replacing NoteEditActivity's production layout.
ET.SubElement(baseline.getroot().find('application'),'activity',{
    tag:'net.micode.notes.tests.WidgetHostActivity','{'+ns+'}exported':'false'})
baseline.write(OUT/'AndroidManifest.xml',encoding='utf-8',xml_declaration=True)
sdk=os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or 'E:/Sdk'
sdk=sdk.replace('\\','/').replace(':','\\:')
(OUT/'local.properties').write_text('sdk.dir='+sdk+'\n',encoding='utf-8',newline='\n')
with (OUT/'build.gradle').open('a',encoding='utf-8') as output:
    output.write('''
// Task-local Android acceptance harness; production sources and resources are unchanged.
android {
    defaultConfig { testInstrumentationRunner 'net.micode.notes.tests.ReminderInstrumentation' }
    sourceSets {
        main.java.srcDirs += ['tests/reminder/host']
        androidTest {
            manifest.srcFile 'tests/reminder/AndroidManifest.xml'
            java.srcDirs = ['tests/reminder/android']
        }
    }
}
dependencies {
    androidTestCompileOnly files("${android.sdkDirectory}/platforms/android-34/optional/android.test.mock.jar")
}
''')
print('Integration base:',BASE)
print('B overlay:',ROOT)
print('Build output:',OUT)
