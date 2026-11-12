from pathlib import Path
from tempfile import TemporaryDirectory
from verify_project import detect

with TemporaryDirectory() as tmp:
    root = Path(tmp)
    (root / 'settings.gradle').write_text('include(":app")')
    try:
        detect(root)
        raise AssertionError('missing STORY ENGLISH source unexpectedly accepted')
    except ValueError as e:
        assert 'Missing original source' in str(e)
    project = root / 'story-english/source/android'
    (project / 'app/src/main').mkdir(parents=True)
    (project / 'settings.gradle.kts').write_text('include(":app")')
    (project / 'app/build.gradle.kts').write_text('android { namespace = "com.storyenglish.app" }')
    (project / 'app/src/main/AndroidManifest.xml').write_text('<manifest />')
    assert detect(root) == project
print('PASS: unrelated root app rejected; original app structure recognized')
