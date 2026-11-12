#!/usr/bin/env python3
"""Offline verifications; DOES NOT build or test the Android APK."""
from pathlib import Path
import subprocess, tempfile, re, struct, xml.etree.ElementTree as ET
ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'app/src/main/java/kr/openscan/scan'
with tempfile.TemporaryDirectory() as td:
    target = Path(td)
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(target),
                    str(SRC/'ScanModel.java'),str(SRC/'ScanFileFormats.java'),str(SRC/'PlyMeshReader.java'),
                    str(ROOT/'tests/ScanModelTest.java')], check=True)
    result = subprocess.run(['java','-cp',str(target),'ScanModelTest'],check=True,capture_output=True,text=True)
    print(result.stdout.strip())
    ply = Path(re.search(r'^PLY=(.*)$',result.stdout,re.M).group(1))
    obj = Path(re.search(r'^OBJ=(.*)$',result.stdout,re.M).group(1))
    raw=ply.read_bytes();hdr, payload = raw.split(b'end_header\n',1)
    assert b'element vertex 4' in hdr
    assert b'element face 2' in hdr
    assert len(payload)==4*15+2*13,(len(payload),4*15+2*13)
    coords=struct.unpack('<fffBBB',payload[:15]);assert coords[:3]==(0.,0.,0.)
    idx=struct.unpack('<Biii',payload[4*15:4*15+13]);assert idx==(3,0,1,2)
    txt=obj.read_text('ascii')
    assert txt.count('\nv ')==4 and txt.count('\nf ')==2
    print('PASS PLY header, 4×15-byte vertex records, 2×13-byte faces, OBJ structure')
    ply.unlink();obj.unlink()
for f in [ROOT/'app/src/main/AndroidManifest.xml',ROOT/'app/src/main/res/xml/file_paths.xml']:
    ET.parse(f)
print('PASS AndroidManifest.xml and FileProvider XML well-formed')
manifest = (ROOT/'app/src/main/AndroidManifest.xml').read_text()
assert 'android.permission.INTERNET' not in manifest
assert 'com.google.ar.core' in manifest
assert 'android.permission.CAMERA' in manifest
print('PASS camera/ARCore declarations and no INTERNET permission')

with tempfile.TemporaryDirectory() as td:
    dest=Path(td)
    subprocess.run(['javac','-encoding','UTF-8','-d',str(dest),str(SRC/'DepthGeometry.java'),str(ROOT/'tests/DepthGeometryTest.java')],check=True)
    depthResult=subprocess.run(['java','-cp',str(dest),'DepthGeometryTest'],capture_output=True,text=True,check=True)
    print(depthResult.stdout.strip())
