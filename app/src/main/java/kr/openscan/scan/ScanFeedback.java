package kr.openscan.scan;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.view.HapticFeedbackConstants;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Small, original synthesized UI chimes. No external audio or network service. */
public final class ScanFeedback {
    public enum Cue { START, PAUSE, FIRST_DEPTH, SAVED, ISSUE }
    private final Context app;
    private final SoundPool pool;
    private final int[] sounds=new int[Cue.values().length];
    private volatile boolean soundsEnabled, hapticsEnabled;
    private final AudioManager audio;
    private static final int SAMPLE_RATE=22050;

    public ScanFeedback(Context c){
        app=c.getApplicationContext();
        audio=(AudioManager)app.getSystemService(Context.AUDIO_SERVICE);
        SharedPreferences p=c.getSharedPreferences("scanner_feedback",Context.MODE_PRIVATE);
        soundsEnabled=p.getBoolean("sound",true);
        hapticsEnabled=p.getBoolean("haptics",true);
        AudioAttributes a=new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
        pool=new SoundPool.Builder().setMaxStreams(2).setAudioAttributes(a).build();
        for(Cue cue:Cue.values()){
            try{
                File file=new File(app.getCacheDir(),"scan_cue_"+cue.name().toLowerCase(java.util.Locale.ROOT)+"_v2.wav");
                if(!file.isFile()||file.length()<100)generate(file,cue);
                sounds[cue.ordinal()]=pool.load(file.getAbsolutePath(),1);
            }catch(Exception ignored){sounds[cue.ordinal()]=0;}
        }
    }
    public boolean soundEnabled(){return soundsEnabled;}
    public boolean hapticsEnabled(){return hapticsEnabled;}
    public void configure(boolean sound,boolean haptic){
        soundsEnabled=sound;hapticsEnabled=haptic;
        app.getSharedPreferences("scanner_feedback",Context.MODE_PRIVATE).edit()
            .putBoolean("sound",sound).putBoolean("haptics",haptic).apply();
    }
    public void cue(Cue cue,View anchor){
        if(hapticsEnabled && anchor!=null){
            anchor.performHapticFeedback(cue==Cue.SAVED?HapticFeedbackConstants.LONG_PRESS:HapticFeedbackConstants.VIRTUAL_KEY);
        }
        // Honor user silent and vibration modes rather than sounding over them.
        if(soundsEnabled && audio!=null && audio.getRingerMode()==AudioManager.RINGER_MODE_NORMAL){
            int id=sounds[cue.ordinal()];
            if(id!=0)pool.play(id,0.25f,0.25f,1,0,1f);
        }
    }
    public void close(){pool.release();}

    private static void writeLE(ByteBuffer b,short val){b.putShort(val);}
    private static void generate(File file,Cue cue)throws IOException{
        final double duration;
        final double[] pitches;
        switch(cue){
            case START:duration=0.19;pitches=new double[]{523.25,659.25};break;
            case PAUSE:duration=0.14;pitches=new double[]{493.88,392.00};break;
            case FIRST_DEPTH:duration=0.16;pitches=new double[]{659.25,783.99};break;
            case SAVED:duration=0.32;pitches=new double[]{523.25,659.25,783.99,1046.5};break;
            default:duration=0.20;pitches=new double[]{391.99,349.23};break;
        }
        int frames=(int)(SAMPLE_RATE*duration);
        ByteBuffer b=ByteBuffer.allocate(44+frames*2).order(ByteOrder.LITTLE_ENDIAN);
        b.put(new byte[]{'R','I','F','F'});b.putInt(36+frames*2);
        b.put(new byte[]{'W','A','V','E','f','m','t',' '});b.putInt(16);
        writeLE(b,(short)1);writeLE(b,(short)1);b.putInt(SAMPLE_RATE);b.putInt(SAMPLE_RATE*2);
        writeLE(b,(short)2);writeLE(b,(short)16);
        b.put(new byte[]{'d','a','t','a'});b.putInt(frames*2);
        double phase=0;
        for(int i=0;i<frames;i++){
            double t=i/(double)SAMPLE_RATE;
            double segment=t/duration*pitches.length;
            int idx=Math.min(pitches.length-1,(int)segment);
            double next=(idx+1<pitches.length)?pitches[idx+1]:pitches[idx];
            double pos=segment-idx;
            double freq=pitches[idx]*(1-Math.max(0,pos-0.8)*0.09)+next*Math.max(0,pos-0.8)*0.09;
            phase+=2*Math.PI*freq/SAMPLE_RATE;
            double attack=Math.min(1,t/0.012);
            double tail=Math.pow(Math.max(0,1-t/duration),1.6);
            double sample=(Math.sin(phase)+0.13*Math.sin(2*phase))*attack*tail*0.22;
            b.putShort((short)(Math.max(-1,Math.min(1,sample))*32767));
        }
        try(FileOutputStream out=new FileOutputStream(file)){out.write(b.array());}
    }
}
