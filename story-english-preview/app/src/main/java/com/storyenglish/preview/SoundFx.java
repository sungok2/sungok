package com.storyenglish.preview;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

/** Original short non-musical feedback tones: volume-limited, no copyrighted sample assets. */
public final class SoundFx {
    private SoundFx(){}
    public static void play(int similarity){
        final int[] notes = similarity>=78 ? new int[]{523,659,784} : similarity>=55 ? new int[]{440,554} : new int[]{392,349};
        new Thread(()->{
            final int rate=16000,noteSamples=2400,silence=500;
            short[] pcm=new short[notes.length*(noteSamples+silence)];
            for(int k=0;k<notes.length;k++)for(int i=0;i<noteSamples;i++){
                double env=Math.min(1,i/220.0)*Math.min(1,(noteSamples-i)/420.0);
                double phase=2*Math.PI*notes[k]*i/rate;
                pcm[k*(noteSamples+silence)+i]=(short)(Math.sin(phase)*env*5200);
            }
            AudioTrack track=null;
            try{
                int min=AudioTrack.getMinBufferSize(rate,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT);
                track=new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(new AudioFormat.Builder().setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                    .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(Math.max(min,8192)).build();
                track.setVolume(0.20f);track.play();track.write(pcm,0,pcm.length,AudioTrack.WRITE_BLOCKING);
                long end=System.currentTimeMillis()+900;
                while(track.getPlaybackHeadPosition()<pcm.length&&System.currentTimeMillis()<end)Thread.sleep(15);
            }catch(Exception ignored){}finally{if(track!=null){try{track.stop();}catch(Exception ignored){}track.release();}}
        },"story-feedback-tone").start();
    }
}