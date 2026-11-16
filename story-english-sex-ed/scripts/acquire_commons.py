import hashlib,json,urllib.parse,urllib.request,html,re,os,pathlib,subprocess,time
from PIL import Image,ImageOps,ImageFile
Image.MAX_IMAGE_PIXELS=80_000_000
ImageFile.LOAD_TRUNCATED_IMAGES=False
FILES=[
 ("condom_application","Condom Application.png","barrier"),
 ("internal_condom","Female Condom.png","barrier"),
 ("condom_usage","Condom usage.png","barrier"),
 ("condom_front","Condom usage front view.png","barrier"),
 ("female_anatomy","Female reproductive anatomy.jpg","anatomy"),
 ("female_anatomy_front","Female Reproductive System - Front.jpg","anatomy"),
 ("female_internal_anatomy","Female internal genitalia unlabeled.svg","anatomy"),
 ("menstrual_cycle","Menstrual Life Cycle.jpg","physiology"),
 ("cycle_hormones","Hormone levels during the human menstrual cycle.jpg","physiology"),
 ("condom_education_motion","Condom.gif","barrier"),
 ("condom_education_animation","Condom use.gif","barrier"),
 ("condom_steps","Condom use steps.jpg","barrier")
]
OUT=pathlib.Path("assets/sex_ed");OUT.mkdir(parents=True,exist_ok=True)
UA="STORY-ENGLISH-curriculum/1.0 (educational; media-license-audit)"
def get(url):
 req=urllib.request.Request(url,headers={"User-Agent":UA})
 with urllib.request.urlopen(req,timeout=40) as resp: return resp.read()
manifest={"collection":"STORY ENGLISH: Adult Sexual Health Evidence Media","licence_check":"Wikimedia Commons MediaWiki file metadata at download, whitelist only","assets":[],"failures":[]}
for slug,title,topic in FILES:
 try:
  query=urllib.parse.urlencode({"action":"query","format":"json","prop":"imageinfo","titles":"File:"+title,"iiprop":"url|size|mime|extmetadata","iiurlwidth":1300})
  raw=get("https://commons.wikimedia.org/w/api.php?"+query)
  page=next(iter(json.loads(raw)["query"]["pages"].values()))
  if "imageinfo" not in page: raise ValueError("Missing image info")
  info=page["imageinfo"][0]
  meta=info.get("extmetadata",{})
  lic=html.unescape(re.sub("<[^>]*>"," ",meta.get("LicenseShortName",{}).get("value",""))).strip()
  licurl=meta.get("LicenseUrl",{}).get("value","")
  author=html.unescape(re.sub("<[^>]*>"," ",meta.get("Artist",{}).get("value",""))).strip()[:250]
  if not any(t in lic for t in ["CC BY-SA 4.0","CC BY 4.0","Public domain","CC0 1.0"]):raise ValueError("License not whitelisted: "+lic)
  url=info.get("thumburl") if info["mime"].startswith("image/") and info["mime"]!="image/gif" else info["url"]
  name="image_"+slug+".png" if not title.lower().endswith(".gif") else "video_"+slug+".mp4"
  data=get(url)
  if len(data)>14_000_000:raise ValueError("asset exceeds size cap")
  if title.lower().endswith(".gif"):
   gif=OUT/(slug+".gif");gif.write_bytes(data)
   dest=OUT/name
   cmd=["ffmpeg","-hide_banner","-nostdin","-loglevel","error","-y","-i",str(gif),"-vf","fps=12,scale='min(720,iw)':-2:flags=lanczos,format=yuv420p","-an","-c:v","libx264","-preset","fast","-crf","27","-movflags","+faststart",str(dest)]
   subprocess.run(cmd,check=True,timeout=85)
   gif.unlink()
   changes="GIF-to-H264 mute educational animation, 12fps scaled"
  elif title.lower().endswith(".svg"):
   # Store external SVG as-is, avoid executing any script or conversion into APK.
   dest=OUT/("image_"+slug+".svg")
   if b"<svg" not in data[:3000] or b"<script" in data.lower():raise ValueError("unsafe svg")
   dest.write_bytes(data)
   changes="No edits; packaged as raw educational vector"
  else:
   import io
   with Image.open(io.BytesIO(data)) as img:
    img=ImageOps.exif_transpose(img).convert("RGB")
    img.thumbnail((1300,1300),Image.Resampling.LANCZOS)
    dest=OUT/name;img.save(dest,format="PNG",optimize=True)
   changes="Resized to <=1300 px and converted to PNG"
  entry={"id":slug,"topic":topic,"filename":dest.name,"source_page":"https://commons.wikimedia.org/wiki/File:"+urllib.parse.quote(title.replace(" ","_"),safe="()"),"source_original":info["url"],"author":author,"license":lic,"license_url":licurl,"source_mime":info["mime"],"source_bytes":info["size"],"export_bytes":dest.stat().st_size,"sha256":hashlib.sha256(dest.read_bytes()).hexdigest(),"modification":changes}
  manifest["assets"].append(entry)
  print("OK",slug,lic,entry["export_bytes"],flush=True)
 except Exception as e:
  manifest["failures"].append({"file":title,"error":str(e)})
  print("SKIPPED",title,str(e),flush=True)
(OUT/"LICENSE_AND_SOURCE_MANIFEST.json").write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding="utf-8")
(OUT/"ASSET_SHA256.txt").write_text("\n".join(a["sha256"]+"  "+a["filename"] for a in manifest["assets"])+"\n",encoding="utf-8")
assert len(manifest["assets"])>=5, "insufficient rights-verified media"
print(json.dumps({"success":len(manifest["assets"]),"failed":len(manifest["failures"])},ensure_ascii=False))
