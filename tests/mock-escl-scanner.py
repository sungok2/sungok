#!/usr/bin/env python3
r"""Strict local-only eSCL protocol simulator for manually testing the Android app.

Usage: py -3 tests\mock-escl-scanner.py --bind 192.168.1.20 --port 8765
Then on Galaxy (same trusted Wi-Fi), enter 192.168.1.20:8765.
Never expose this authentication-free test server on the Internet.
"""
import argparse
import ipaddress
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit
from uuid import uuid4

FIXTURES = Path(__file__).resolve().parent / 'fixtures'
CAPS = '''<?xml version="1.0" encoding="UTF-8"?>
<scan:ScannerCapabilities xmlns:scan="http://schemas.hp.com/imaging/escl/2011/05/03"
 xmlns:pwg="http://www.pwg.org/schemas/2010/12/sm">
  <scan:PlatenInputCaps>
    <pwg:MaxWidth>2550</pwg:MaxWidth><pwg:MaxHeight>3508</pwg:MaxHeight>
    <scan:SettingProfiles><scan:SettingProfile>
      <scan:ColorModes><scan:ColorMode>RGB24</scan:ColorMode></scan:ColorModes>
      <scan:SupportedResolutions><scan:DiscreteResolutions>
        <scan:DiscreteResolution><scan:XResolution>150</scan:XResolution><scan:YResolution>150</scan:YResolution></scan:DiscreteResolution>
        <scan:DiscreteResolution><scan:XResolution>300</scan:XResolution><scan:YResolution>300</scan:YResolution></scan:DiscreteResolution>
      </scan:DiscreteResolutions></scan:SupportedResolutions>
      <pwg:DocumentFormats><pwg:DocumentFormat>image/jpeg</pwg:DocumentFormat></pwg:DocumentFormats>
    </scan:SettingProfile></scan:SettingProfiles>
  </scan:PlatenInputCaps>
  <scan:AdfSimplexInputCaps>
    <pwg:MaxWidth>2480</pwg:MaxWidth><pwg:MaxHeight>3508</pwg:MaxHeight>
    <scan:SettingProfiles><scan:SettingProfile>
      <scan:ColorModes><scan:ColorMode>RGB24</scan:ColorMode></scan:ColorModes>
      <scan:SupportedResolutions><scan:DiscreteResolutions>
        <scan:DiscreteResolution><scan:XResolution>300</scan:XResolution><scan:YResolution>300</scan:YResolution></scan:DiscreteResolution>
      </scan:DiscreteResolutions></scan:SupportedResolutions>
      <pwg:DocumentFormats><pwg:DocumentFormat>image/jpeg</pwg:DocumentFormat></pwg:DocumentFormats>
    </scan:SettingProfile></scan:SettingProfiles>
  </scan:AdfSimplexInputCaps>
</scan:ScannerCapabilities>'''.encode('utf-8')

class Scanner(BaseHTTPRequestHandler):
    protocol_version = 'HTTP/1.1'
    jobs = {}
    document_format = 'image/jpeg'

    def log_message(self, fmt, *args):
        print('MOCK:', self.address_string(), fmt % args)

    def send_body(self, code, body=b'', media='text/xml', location=None):
        self.send_response(code)
        self.send_header('Content-Type', media)
        self.send_header('Content-Length', str(len(body)))
        self.send_header('Cache-Control', 'no-cache')
        if location:
            self.send_header('Location', location)
        self.end_headers()
        if body:
            self.wfile.write(body)

    def do_GET(self):
        path = urlsplit(self.path).path
        if path == '/eSCL/ScannerCapabilities':
            self.send_body(200, CAPS.replace(b'image/jpeg', self.document_format.encode('ascii')))
            return
        if path == '/eSCL/ScannerStatus':
            self.send_body(200, b'<ScannerStatus><State>Idle</State></ScannerStatus>')
            return
        if path.startswith('/eSCL/ScanJobs/') and path.endswith('/NextDocument'):
            key = path.split('/')[3]
            job = self.jobs.get(key)
            if not job or job['next'] >= job['pages']:
                self.send_body(404)
                return
            job['next'] += 1
            ext = 'png' if self.document_format == 'image/png' else 'jpg'
            data = (FIXTURES / f"mock-page-{job['next']}.{ext}").read_bytes()
            self.send_body(200, data, self.document_format)
            return
        self.send_body(404)

    def do_POST(self):
        if self.path != '/eSCL/ScanJobs':
            self.send_body(404)
            return
        try:
            n = int(self.headers.get('Content-Length', '0'))
        except ValueError:
            self.send_body(400)
            return
        if n < 1 or n > 102400:
            self.send_body(413)
            return
        request = self.rfile.read(n)
        if b'<scan:ScanSettings' not in request or self.document_format.encode('ascii') not in request:
            self.send_body(415)
            return
        feeder = b'<pwg:InputSource>Feeder</pwg:InputSource>' in request
        key = str(uuid4())
        self.jobs[key] = {'next':0, 'pages':2 if feeder else 1}
        self.send_body(201, b'', location=f'/eSCL/ScanJobs/{key}')

    def do_DELETE(self):
        key = self.path.split('/')[-1]
        self.jobs.pop(key,None)
        self.send_body(204, b'', media='text/plain')

if __name__ == '__main__':
    ap=argparse.ArgumentParser()
    ap.add_argument('--bind',required=True,help='Private IP of your test PC on trusted LAN')
    ap.add_argument('--port',type=int,default=8765)
    ap.add_argument('--format', choices=['jpeg','png'], default='jpeg')
    args=ap.parse_args()
    a=ipaddress.ip_address(args.bind)
    if a.version != 4 or not (a.is_private or a.is_link_local) or a.is_loopback:
        ap.error('Bind to a private IPv4 address, not loopback or a public address')
    Scanner.document_format = 'image/' + args.format
    print(f'MOCK TEST SCANNER ({Scanner.document_format}): http://{a}:{args.port}/eSCL/ScannerCapabilities')
    print('No authentication. Use trusted LAN only. Stop with Ctrl+C.')
    ThreadingHTTPServer((str(a),args.port),Scanner).serve_forever()
