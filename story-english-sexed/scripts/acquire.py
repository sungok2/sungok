#!/usr/bin/env python3
"""Curated Wikimedia originals. Offline assets with attribution/rights metadata; no scraping of adult sites."""
import hashlib, json, os, sys, time, urllib.parse, urllib.request
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
from PIL import Image, ImageOps
from io import BytesIO

BASE=Path(__file__).resolve().parent.parent
SOURCES=json.loads((BASE/'catalog/asset_sources.json').read_text())
TARGET=BASE/'dist'
ORIG=TARGET/'upstream'
IMAGES=TARGET/'images'
for d in (ORIG,IMAGES):d.mkdir(parents=True,exist_ok=True)

HOST='https://upload.wikimedia.org/wikipedia/commons'
def url_for(n):
    u=n.replace(' ','_')
    h=hashlib.md5(u.encode('utf-8')).hexdigest()
    return HOST+'/'+h[:1]+'/'+h[:2]+'/'+urllib.parse.quote(u,safe='-_.')

def download(url,max_size=12*1024*1024):
    request=urllib.request.Request(url,headers={'User-Agent':'StoryEnglishOfflineEducation/1.0 (educational reuse; contact via Commons file page)'})
    with urllib.request.urlopen(request,timeout=35) as r:
        length=int(r.headers.get('Content-Length','0') or 0)
        if length>max_size: raise ValueError('Too large: '+str(length))
        output=bytearray()
        while True:
            b=r.read(65536)
            if not b:break
            output+=b
            if len(output)>max_size: raise ValueError('Too large')
    return bytes(output)

def png(b,filename):
    ext=Path(filename).suffix.lower()
    if ext=='.svg':
        import cairosvg
        # CairoSVG draws from trusted Commons media; data is not rehosted from unsafe sources.
        b=cairosvg.svg2png(bytestring=b,output_width=1152)
        ext='.png'
    if ext=='.gif':
        with Image.open(BytesIO(b)) as im:
            im.seek(0)
            v=im.convert('RGB')
            v.thumbnail((1280,1280))
            output=BytesIO();v.save(output,'PNG',optimize=True)
            return output.getvalue()
    with Image.open(BytesIO(b)) as im:
        # EXIF orientation; normalize all app display assets for reliable rendering.
        v=ImageOps.exif_transpose(im).convert('RGB')
        v.thumbnail((1280,1280),Image.Resampling.LANCZOS)
        output=BytesIO()
        v.save(output,'PNG',optimize=True)
        return output.getvalue()

entries=[];failed=[];hashes=set()
for num,entry in enumerate(SOURCES,1):
    e=dict(entry)
    source=url_for(e['filename']);e['url']=source
    e['commons_page']='https://commons.wikimedia.org/wiki/File:'+urllib.parse.quote(e['filename'].replace(' ','_'))
    try:
        raw=download(source)
        digest=hashlib.sha256(raw).hexdigest()
        if digest in hashes:
            e.update(status='duplicate',sha256_source=digest)
            entries.append(e);continue
        hashes.add(digest)
        suffix=Path(e['filename']).suffix.lower()
        if suffix=='.png' and raw[:8]!=b'\x89PNG\r\n\x1a\n':raise ValueError('Invalid PNG')
        if suffix=='.jpg' and raw[:3]!=b'\xff\xd8\xff':raise ValueError('Invalid JPEG')
        if suffix=='.svg' and b'<svg' not in raw[:2048]:raise ValueError('Invalid SVG')
        if suffix=='.gif' and raw[:3]!=b'GIF':raise ValueError('Invalid GIF')
        source_file=ORIG/(e['id']+suffix)
        source_file.write_bytes(raw)
        normalized=png(raw,e['filename'])
        image_file=IMAGES/(e['id']+'.png')
        image_file.write_bytes(normalized)
        with Image.open(image_file) as x:
            width,height=x.size
        e.update(status='included',source_file=str(source_file.relative_to(TARGET)),asset_file=str(image_file.relative_to(TARGET)),sha256_source=digest,sha256_asset=hashlib.sha256(normalized).hexdigest(),source_size=len(raw),asset_size=len(normalized),width=width,height=height,derivative='PNG resized to max 1280px; rendering/thumbnail conversion',quality_status='visual medical-content review pending')
        print('OK',e['id'],len(raw),'->',len(normalized),flush=True)
    except Exception as ex:
        e.update(status='failed',error=str(ex)[:320])
        failed.append(e['id'])
        print('FAILED',e['id'],str(ex)[:200],flush=True)
    entries.append(e)
manifest={'package':'STORY ENGLISH sexual health media educational pack','created_at':datetime.now(timezone.utc).isoformat(),'sourcing':'Wikimedia Commons individual file pages; manual license pre-screen + retrieval hashes','status':'educational-preview','clinical_review':'not performed; do not use as medical advice','release_notice':'Check author, original and derivative license, attribution, moral/personality rights for each source before commercial release. CC BY-SA share-alike may apply to adapted artwork.','assets':entries,'counts':dict(Counter(x['status'] for x in entries))}
(TARGET/'LICENSE_AND_ASSET_MANIFEST.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2))
with open(TARGET/'SHA256SUMS.txt','w') as f:
    for x in entries:
        if x['status']=='included':
            f.write(x['sha256_asset']+'  '+x['asset_file']+'\n')
with open(TARGET/'ATTRIBUTION.md','w') as f:
    f.write('# Original graphics attribution and derivative notice\n\n')
    for x in entries:
        if x['status']=='included':
            f.write(f"- **{x['id']}** — {x['filename']} — {x['author']} — [{x['license']}]({x['license_url']}) — [source]({x['commons_page']}); modification: {x['derivative']}.\n")
    f.write('\nNo endorsement by authors or Wikimedia Commons is implied.\n')
print('COUNTS',manifest['counts']);print('FAILED',failed)
if sum(1 for x in entries if x['status']=='included')<8: sys.exit('Too few verified assets for a usable pack')