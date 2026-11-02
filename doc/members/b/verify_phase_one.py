"""Read-only evidence checks for B's phase-one comment and reading commit."""
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[3]
BASE = '81a66024fe48eaa2206b854430856fe3875ff3ba'
FILES = [
    'src/net/micode/notes/widget/NoteWidgetProvider.java',
    'src/net/micode/notes/tool/DataUtils.java',
    'src/net/micode/notes/ui/DateTimePickerDialog.java',
]

def git(*args):
    return subprocess.check_output(['git', '-C', str(ROOT), *args]).decode('utf-8')

def tokens(text):
    # Strings and character literals are matched before comments, preserving // in URIs.
    pattern = r'"(?:\\.|[^"\\])*"|\x27(?:\\.|[^\x27\\])*\x27|/\*.*?\*/|//[^\r\n]*|\s+|[\w$]+|[^\s]'
    result = []
    for match in re.finditer(pattern, text, re.S):
        part = match.group()
        if not part.isspace() and not part.startswith(('/*', '//')):
            result.append(part)
    return result

print('B phase-one verification')
print('BASE:', BASE)
print('Branch:', git('branch', '--show-current').strip())
print('Annotation counts are net added physical comment lines, including Javadoc delimiters.')
for filename in FILES:
    original = git('show', f'{BASE}:{filename}')
    current = (ROOT / filename).read_text(encoding='utf-8')
    assert tokens(original) == tokens(current), f'Executable token changed: {filename}'
    delta = len(current.splitlines()) - len(original.splitlines())
    print(f'PASS executable tokens unchanged; +{delta} comment lines: {filename}')

baseline_files = git('ls-tree', '-r', '--name-only', BASE, 'src').splitlines()
java_files = [p for p in baseline_files if p.endswith('.java')]
source = [git('show', f'{BASE}:{p}') for p in java_files]
packages = {re.search(r'^package\s+([^;]+)', s, re.M).group(1) for s in source}
print(f'Baseline inventory: {len(java_files)} Java source files, {len(packages)} packages, '
      f'{sum(len(s.splitlines()) for s in source)} physical lines (comments and blank lines included).')
for filename, needle in [
    (FILES[2], 'flag |= mIs24HourView'),
    (FILES[1], 'return cursor.getLong(0);'),
]:
    for n, line in enumerate((ROOT / filename).read_text(encoding='utf-8').splitlines(), 1):
        if needle in line:
            print(f'Evidence: {filename}:{n}: {line.strip()}')
check = subprocess.run(['git', '-C', str(ROOT), 'diff', '--check', BASE], capture_output=True)
assert check.returncode == 0, check.stdout.decode('utf-8', errors='replace')
print('PASS git diff --check BASE')
print('Android build, unit tests, device tests and SonarQube: NOT RUN / build entry unavailable.')
