package com.storyenglish.preview;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Locale;

/**
 * On-device reference synthesis, configurable repetitions, automatic 3-2-1 shadowing,
 * real PCM microphone capture and offline STFT comparison. Does not upload or persist recordings.
 */
public final class SpeechLab {
    public interface Listener {
        void status(String text);
        void countdown(int second);
        void meter(float value);
        void reference(float[][] image);
        void comparison(SpectralAnalyzer.Result result);
        void playbackFinished();
    }
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private TextToSpeech tts;
    private volatile boolean ready=false;
    private volatile short[] referencePcm;
    private volatile String referencePhrase=null;
    private volatile int referenceVariant=-1;
    private volatile AudioRecord recorder;
    private volatile AudioTrack playing;
    private volatile int epoch=0;
    private int voiceVariant=0;
    private volatile String lastDialogueId=null;
    private File referenceFile;
    private final Object byteLock=new Object();
    private ByteArrayOutputStream ttsBytes;
    private int ttsRate=0,ttsEncoding=0,ttsChannels=0;
    private int ttsEpoch=-1;
    private volatile int pendingAutoRepeatEpoch=-1;
    private String queuedPhrase=null;
    private int queuedVariant=0,queuedCount=1,queuedGap=0,queuedEpoch=-1;
    private int pendingRepeatCount=1, pendingGapSeconds=0;
    public SpeechLab(Context c,Listener l){
        context=c.getApplicationContext();listener=l;
        tts=new TextToSpeech(context,status->{
            if(status==TextToSpeech.SUCCESS&&tts!=null){
                int lang=tts.setLanguage(Locale.US);
                ready=(lang!=TextToSpeech.LANG_NOT_SUPPORTED&&lang!=TextToSpeech.LANG_MISSING_DATA);
                postStatus(ready?"영어 기준 음성을 준비할 수 있습니다.":"기기에 영어 TTS 음성이 설치되지 않았습니다.");
                if(ready)main.post(()->{
                    if(queuedPhrase!=null && epoch==queuedEpoch){
                        String phrase=queuedPhrase;int variant=queuedVariant,count=queuedCount,gap=queuedGap;
                        queuedPhrase=null;
                        playOrPrepare(phrase,variant,count,gap);
                    }
                });
                else main.post(()->listener.playbackFinished());
            }else postStatus("TTS 엔진을 초기화하지 못했습니다.");
        });
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
            @Override public void onStart(String id){}
            @Override public void onBeginSynthesis(String id,int rate,int encoding,int channels){
                if(!isRef(id))return;
                synchronized(byteLock){ttsRate=rate;ttsEncoding=encoding;ttsChannels=channels;ttsBytes=new ByteArrayOutputStream();}
            }
            @Override public void onAudioAvailable(String id,byte[] data){
                if(!isRef(id))return;
                synchronized(byteLock){if(ttsBytes!=null&&ttsBytes.size()+data.length<2200000)ttsBytes.write(data,0,data.length);}
            }
            @Override public void onDone(String id){
                if(isRef(id))referenceDone(id);
                else if(id!=null && id.equals(lastDialogueId)){
                    main.post(()->{if(id.equals(lastDialogueId))listener.playbackFinished();});
                }
            }
            @Override public void onError(String id){
                if(isRef(id)){
                    pendingAutoRepeatEpoch=-1;
                    postStatus("음성 합성에 실패했습니다. TTS 엔진과 언어 설정을 확인하세요.");
                    main.post(()->listener.playbackFinished());
                } else if(id!=null && id.equals(lastDialogueId)){
                    main.post(()->listener.playbackFinished());
                }
            }
        });
    }
    private boolean isRef(String id){return id!=null&&id.startsWith("ref:");}
    private void postStatus(String s){main.post(()->listener.status(s));}
    private boolean fresh(int generation){return epoch==generation;}
    public boolean hasReference(){return referencePcm!=null&&referencePcm.length>6000;}
    public void setPhrase(String phrase,int variant){
        stop();voiceVariant=variant;
        referencePhrase=phrase;referenceVariant=variant;
        if(!ready||tts==null){postStatus("기기에 영어 TTS 음성이 필요합니다. 준비 후 다시 선택하세요.");return;}
        final int version=epoch;
        try {
            referenceFile=new File(context.getCacheDir(),"story_ref_"+version+".wav");
            synchronized(byteLock){ttsEpoch=version;ttsBytes=null;ttsRate=0;ttsEncoding=0;ttsChannels=0;}
            tts.setPitch(variant%2==0?1.05f:0.92f);tts.setSpeechRate(0.91f);
            int result=tts.synthesizeToFile(phrase,new Bundle(),referenceFile,"ref:"+version);
            postStatus(result==TextToSpeech.SUCCESS?"기준 음성 합성 중…":"TTS 파일 합성 요청이 실패했습니다.");
        }catch(Exception e){postStatus("TTS 기준 음성 준비 오류");}
    }
    private void referenceDone(String id){
        int generation;
        try{generation=Integer.parseInt(id.substring(4));}catch(NumberFormatException ex){return;}
        final File completedFile=new File(context.getCacheDir(),"story_ref_"+generation+".wav");
        if(!fresh(generation)){
            // A canceled/obsolete synthesis must not leave reference speech on disk.
            completedFile.delete();
            return;
        }
        final short[] decoded=readWav(completedFile);
        short[] output=decoded;
        if(output.length<3000){
            byte[] raw;int rate,channels,enc;
            synchronized(byteLock){raw=ttsBytes==null?new byte[0]:ttsBytes.toByteArray();rate=ttsRate;channels=ttsChannels;enc=ttsEncoding;}
            if(rate>=8000&&channels>=1){output=SpectralAnalyzer.resample(decodeRaw(raw,enc),rate,channels);}
        }
        completedFile.delete();
        if(completedFile.equals(referenceFile))referenceFile=null;
        if(output.length<6000){pendingAutoRepeatEpoch=-1;postStatus("현재 TTS 엔진에서 비교용 PCM을 얻지 못했습니다. 다른 영어 음성을 선택하세요.");return;}
        referencePcm=output;
        final short[] spectrumData=output;
        new Thread(()->{
            SpectralAnalyzer.Result result=SpectralAnalyzer.compare(spectrumData,spectrumData);
            if(fresh(generation))main.post(()->{
                if(!fresh(generation))return;
                listener.reference(result.referenceImage);
                listener.status("기준 음성이 준비됐습니다. 반복 듣기 또는 쉐도잉을 시작하세요.");
                if(pendingAutoRepeatEpoch==generation){
                    int count=pendingRepeatCount,gap=pendingGapSeconds;
                    pendingAutoRepeatEpoch=-1;
                    playRepeats(count,gap);
                }
            });
        },"reference-spectrum").start();
    }
    private static short[] decodeRaw(byte[] raw,int encoding){
        if(raw.length==0)return new short[0];
        if(encoding==AudioFormat.ENCODING_PCM_16BIT){
            short[] out=new short[raw.length/2];ByteBuffer b=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
            for(int i=0;i<out.length;i++)out[i]=b.getShort();return out;
        }
        if(encoding==AudioFormat.ENCODING_PCM_8BIT){short[] out=new short[raw.length];for(int i=0;i<raw.length;i++)out[i]=(short)(((raw[i]&0xff)-128)*256);return out;}
        if(encoding==AudioFormat.ENCODING_PCM_FLOAT){short[] out=new short[raw.length/4];ByteBuffer b=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
            for(int i=0;i<out.length;i++)out[i]=(short)Math.max(-32768,Math.min(32767,Math.round(b.getFloat()*32767)));return out;}
        return new short[0];
    }
    private static short[] readWav(File file){
        if(file==null||!file.isFile()||file.length()>4_000_000)return new short[0];
        try(FileInputStream input=new FileInputStream(file)){
            byte[] all=new byte[(int)file.length()];int read=0;
            while(read<all.length){int n=input.read(all,read,all.length-read);if(n<0)break;read+=n;}
            if(read<44||all[0]!='R'||all[1]!='I'||all[8]!='W'||all[9]!='A')return new short[0];
            ByteBuffer b=ByteBuffer.wrap(all).order(ByteOrder.LITTLE_ENDIAN);
            int off=12,channels=0,rate=0,bits=0,format=0,dataStart=-1,dataLen=0;
            while(off+8<=read){
                int len=b.getInt(off+4);if(len<0||off+8L+len>read)break;
                String tag=new String(all,off,4,java.nio.charset.StandardCharsets.US_ASCII);
                if(tag.equals("fmt ")&&len>=16){format=b.getShort(off+8)&0xffff;channels=b.getShort(off+10)&0xffff;rate=b.getInt(off+12);bits=b.getShort(off+22)&0xffff;}
                if(tag.equals("data")){dataStart=off+8;dataLen=len;break;}
                off+=(8+len+(len%2));
            }
            if(format!=1||bits!=16||channels<1||channels>2||rate<8000||rate>192000||dataStart<0)return new short[0];
            int sampleCount=dataLen/2;short[] samples=new short[sampleCount];
            for(int i=0;i<sampleCount;i++)samples[i]=b.getShort(dataStart+i*2);
            return SpectralAnalyzer.resample(samples,rate,channels);
        }catch(Exception e){return new short[0];}
    }
    /** A single tap starts playback, even when the TTS reference still needs synthesis. */
    public void playOrPrepare(String phrase,int variant,int repetitions,int gapSeconds){
        if(hasReference() && phrase!=null && phrase.equals(referencePhrase) && variant==referenceVariant){playRepeats(repetitions,gapSeconds);return;}
        if(!ready){
            queuedPhrase=phrase;queuedVariant=variant;queuedCount=repetitions;queuedGap=gapSeconds;queuedEpoch=epoch;
            postStatus("기기 영어 TTS 초기화가 완료되면 자동 재생합니다.");
            return;
        }
        setPhrase(phrase,variant);
        pendingRepeatCount=Math.max(1,Math.min(10,repetitions));
        pendingGapSeconds=Math.max(0,Math.min(10,gapSeconds));
        pendingAutoRepeatEpoch=epoch;
        postStatus("기준 음성 생성 후 자동으로 반복 재생합니다.");
    }
    public void playRepeats(int repetitions,int gapSeconds){
        short[] pcm=referencePcm;
        if(pcm==null){postStatus("먼저 기준 문장을 선택하고 합성이 완료될 때까지 확인하세요.");return;}
        stopPlaybackAndRecording();
        if(tts!=null)tts.stop();
        final int generation=epoch;
        final int count=Math.max(1,Math.min(10,repetitions)),gap=Math.max(0,Math.min(10,gapSeconds));
        new Thread(()->{
            for(int i=1;i<=count&&fresh(generation);i++){
                final int current=i;
                postStatus("기준 음성 듣기  "+current+" / "+count+"회");
                playPcm(pcm,generation);
                if(!fresh(generation))break;
                if(i<count){
                    for(int remaining=gap*10;remaining>0&&fresh(generation);remaining--){
                        final double secs=remaining/10.0;
                        postStatus("다음 재생까지 "+String.format(Locale.KOREA,"%.1f",secs)+"초  ·  "+current+"/"+count);
                        try{Thread.sleep(100);}catch(InterruptedException ignored){return;}
                    }
                }
            }
            if(fresh(generation))main.post(()->{
                if(fresh(generation)){
                    listener.status("선택한 반복 듣기가 끝났습니다. 이제 쉐도잉을 연습해 보세요.");
                    listener.playbackFinished();
                }
            });
        },"repeat-playback").start();
    }
    private void playPcm(short[] pcm,int generation){
        AudioTrack a=null;
        try{
            int min=AudioTrack.getMinBufferSize(16000,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT);
            a=new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
               .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(16000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
               .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(Math.max(8192,min)).build();
            playing=a;a.play();
            int pos=0;
            while(fresh(generation)&&pos<pcm.length){int n=a.write(pcm,pos,Math.min(1024,pcm.length-pos),AudioTrack.WRITE_BLOCKING);if(n<=0)break;pos+=n;}
            long timeout=System.currentTimeMillis()+Math.min(50000,3000+(long)pcm.length*1000/16000);
            while(fresh(generation)&&a.getPlaybackHeadPosition()<pos&&System.currentTimeMillis()<timeout)Thread.sleep(20);
        }catch(Exception e){postStatus("기준 음성 재생에 실패했습니다.");}
        finally{playing=null;if(a!=null){try{a.stop();}catch(Exception ignored){}a.release();}}
    }
    public void playDialogue(String[] lines){
        if(!ready||tts==null){postStatus("영어 TTS 음성을 준비할 수 없습니다.");return;}
        stopPlaybackAndRecording();
        tts.stop();
        tts.setSpeechRate(0.92f);
        final int dialogueEpoch=epoch;
        lastDialogueId="dialogue:"+(lines.length-1)+":"+dialogueEpoch;
        for(int i=0;i<lines.length;i++){
            tts.setPitch(i%2==0?1.05f:0.92f);
            tts.speak(lines[i], i==0?TextToSpeech.QUEUE_FLUSH:TextToSpeech.QUEUE_ADD,new Bundle(),"dialogue:"+i+":"+dialogueEpoch);
        }
        postStatus("성인 대화 음성을 재생합니다. 기기 합성음이므로 실제 성우 원음은 아닙니다.");
    }
    public void beginCountdown(){
        if(referencePcm==null){postStatus("기준 음성 준비가 필요합니다.");return;}
        stopPlaybackAndRecording();
        if(tts!=null)tts.stop();
        final int generation=epoch;
        new Thread(()->{
            for(int i=3;i>=1;i--){if(!fresh(generation))return;int count=i;main.post(()->listener.countdown(count));
                try{Thread.sleep(1000);}catch(InterruptedException ignored){return;}
            }
            if(!fresh(generation))return;
            main.post(()->listener.countdown(0));
            record(generation);
        },"shadow-countdown").start();
    }
    private void record(int generation){
        final int rate=16000;
        AudioRecord audio=null;
        short[] buffer=new short[rate*10];int written=0;
        try{
            int min=AudioRecord.getMinBufferSize(rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
            if(min<=0)throw new IllegalStateException("PCM16 microphone unavailable");
            audio=new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(8192,min));
            if(audio.getState()!=AudioRecord.STATE_INITIALIZED)throw new IllegalStateException("Microphone initialization failed");
            recorder=audio;audio.startRecording();
            boolean heardVoice=false;int lastSpeech=0;
            short[] chunk=new short[512];int tick=0;
            while(fresh(generation)&&written<buffer.length){
                int n=audio.read(chunk,0,Math.min(chunk.length,buffer.length-written),AudioRecord.READ_BLOCKING);
                if(n<=0){if(n<0)break;continue;}
                double power=0;
                for(int i=0;i<n;i++){buffer[written+i]=chunk[i];power+=(double)chunk[i]*chunk[i];}
                written+=n;double rms=Math.sqrt(power/n);
                if(rms>520){heardVoice=true;lastSpeech=written;}
                if((++tick)%4==0){final float level=(float)Math.min(1,rms/5000);main.post(()->{if(fresh(generation))listener.meter(level);});}
                if(heardVoice&&written-lastSpeech>rate*1.3&&written>rate*1.4)break;
            }
        }catch(SecurityException e){postStatus("마이크 권한이 없습니다. 설정에서 허용해 주세요.");}
        catch(Exception e){postStatus("녹음 시작/처리 오류: "+e.getClass().getSimpleName());}
        finally{recorder=null;if(audio!=null){try{audio.stop();}catch(Exception ignored){}audio.release();}}
        if(!fresh(generation))return;
        if(written<rate/2){postStatus("녹음이 너무 짧습니다. 다시 시도하세요.");return;}
        short[] learner=Arrays.copyOf(buffer,written);Arrays.fill(buffer,(short)0);
        postStatus("실제 음향 특징을 비교하는 중…");
        SpectralAnalyzer.Result comparison=SpectralAnalyzer.compare(referencePcm,learner);
        Arrays.fill(learner,(short)0);
        if(fresh(generation))main.post(()->{listener.countdown(-1);listener.comparison(comparison);listener.status(comparison.similarity<0?comparison.note:"녹음 완료 · 스펙트럼 유사도 "+comparison.similarity+" / 100");});
    }
    private void stopPlaybackAndRecording(){
        ++epoch;
        AudioTrack a=playing;if(a!=null){try{a.pause();a.flush();}catch(Exception ignored){}}
        AudioRecord r=recorder;if(r!=null){try{r.stop();}catch(Exception ignored){}}
    }
    public void pause(){stopPlaybackAndRecording();pendingAutoRepeatEpoch=-1;queuedPhrase=null;lastDialogueId=null;if(tts!=null)tts.stop();}
    public void stop(){
        stopPlaybackAndRecording();
        pendingAutoRepeatEpoch=-1;
        queuedPhrase=null;
        lastDialogueId=null;
        referencePcm=null;
        referencePhrase=null;
        referenceVariant=-1;
        if(tts!=null)tts.stop();
        if(referenceFile!=null){referenceFile.delete();referenceFile=null;}
    }
    public void shutdown(){stop();if(tts!=null){tts.stop();tts.shutdown();tts=null;}if(referenceFile!=null)referenceFile.delete();}
}