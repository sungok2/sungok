#!/usr/bin/env python3
"""Render original Story English transcripts using Apache-2.0 Kokoro model.
Requires network during BUILD only. App remains offline. Each cue is a separate
neural generation; a 0.32 s pause is inserted between alternating speakers.
The output is intentionally voice-only so future soundtrack owners can remix.
"""
import base64
import hashlib
import json
import os
import pathlib
import sys
import wave
import zlib
from datetime import datetime, timezone

import numpy as np
import soundfile as sf
import torch
from kokoro import KPipeline

BASE=pathlib.Path(__file__).resolve().parent
OUT=BASE/"dist"
OUT.mkdir(parents=True,exist_ok=True)
data=BASE.joinpath("dialogues.txt.zlib.b64").read_text().strip()
dialogues=json.loads(zlib.decompress(base64.b64decode(data)).decode("utf-8"))
assert len(dialogues)==27
torch.set_num_threads(2)
pipeline=KPipeline(lang_code='a')
# Female and male are source-model stock voicepacks, not cloned speakers.
voices=("af_heart","am_michael")
sr=24000
manifest={"engine":"hexgrad/Kokoro-82M","model_license":"Apache-2.0",
 "sample_rate":sr,"voice_names":list(voices),"items":[]}

def speak(text,voice):
    chunks=[]
    for _, _, audio in pipeline(text,voice=voice,speed=0.98,split_pattern=r'\\n+'):
        if audio is not None:
            x=np.asarray(audio,dtype=np.float32)
            if x.size: chunks.append(x)
    if not chunks: raise ValueError("No audio synthesized for: "+text)
    result=np.concatenate(chunks)
    assert len(result)>sr//3 and np.all(np.isfinite(result))
    return np.clip(result,-0.96,0.96)

for row in dialogues:
    num=int(row["id"])
    if not 1<=num<=27: raise ValueError("id")
    segments=[speak(row["first"],voices[0]),speak(row["second"],voices[1])]
    waveform=np.concatenate([np.zeros(round(sr*0.13),dtype=np.float32),
         segments[0],np.zeros(round(sr*0.32),dtype=np.float32),
         segments[1],np.zeros(round(sr*0.24),dtype=np.float32)])
    path=OUT/("dialogue_%02d.wav"%num)
    sf.write(path,waveform,sr,subtype="PCM_16")
    with sf.SoundFile(path) as f:
        assert f.samplerate==sr and f.channels==1 and f.frames>sr
    row_out={"filename":path.name,"title":row["title"],
      "duration_sec":round(len(waveform)/sr,3),
      "sha256":hashlib.sha256(path.read_bytes()).hexdigest()}
    manifest["items"].append(row_out)
    print("%s %4.1f s"%(row_out["filename"],row_out["duration_sec"]),flush=True)
assert len(manifest["items"])==27
(OUT/"manifest.json").write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding="utf-8")
