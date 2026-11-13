#!/usr/bin/env python3
"""Validate or locate a visible Android UI node in a uiautomator XML dump.
Used by Android 13 CI smoke tests; never attempts to infer hardware compatibility.
"""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET


def main():
    if len(sys.argv) != 4 or sys.argv[1] not in ('assert', 'tap'):
        raise SystemExit('Usage: ui_nav_assert.py assert|tap window.xml exact-visible-text')
    mode, path, needle = sys.argv[1:]
    root = ET.parse(Path(path)).getroot()
    nodes = []
    for node in root.iter('node'):
        if node.attrib.get('displayed', 'true') == 'false':
            continue
        if node.attrib.get('text') == needle or node.attrib.get('content-desc') == needle:
            nodes.append(node.attrib)
    if not nodes:
        raise SystemExit(f'Visible UI element not found: {needle}')
    if mode == 'assert':
        print(f'PASS: found visible UI element: {needle}')
        return
    match = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', nodes[0].get('bounds', ''))
    if not match:
        raise SystemExit('UI element does not have usable bounds')
    x1, y1, x2, y2 = map(int, match.groups())
    if x1 >= x2 or y1 >= y2:
        raise SystemExit('UI element has empty tap bounds')
    print((x1 + x2) // 2, (y1 + y2) // 2)


if __name__ == '__main__':
    main()
