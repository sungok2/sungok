#!/usr/bin/env python3
"""Fail-closed STORY ENGLISH Android source preflight.

Never builds an unrelated Android app from the parent repository.
"""
import argparse
from pathlib import Path
import sys

EXPECTED_PACKAGE = 'com.storyenglish.app'


def detect(root: Path):
    project = root / 'story-english' / 'source' / 'android'
    if not project.is_dir():
        raise ValueError('Missing original source: expected story-english/source/android/')
    if not any((project / s).is_file() for s in ('settings.gradle', 'settings.gradle.kts')):
        raise ValueError('Original Android project missing settings.gradle(.kts)')
    gradle = list(project.rglob('build.gradle')) + list(project.rglob('build.gradle.kts'))
    manifests = list(project.rglob('src/main/AndroidManifest.xml'))
    if not gradle or not manifests:
        raise ValueError('Android module build script and src/main/AndroidManifest.xml required')
    files = gradle + manifests
    files += list(project.rglob('*.kt'))[:2000]
    source = '\n'.join(f.read_text(encoding='utf-8', errors='replace') for f in files)
    if EXPECTED_PACKAGE not in source:
        raise ValueError('Not STORY ENGLISH: expected package/namespace com.storyenglish.app')
    return project


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--root', default='.')
    p.add_argument('--github-output')
    args = p.parse_args()
    root = Path(args.root).resolve()
    try:
        project = detect(root)
    except ValueError as exc:
        print('SOURCE CHECK FAILED:', exc, file=sys.stderr)
        return 2
    relative = project.relative_to(root).as_posix()
    print('STORY ENGLISH Android source found at', relative)
    if args.github_output:
        with open(args.github_output, 'a', encoding='utf-8') as f:
            f.write('project_dir=' + relative + '\n')
    return 0


if __name__ == '__main__':
    sys.exit(main())
