package com.storyenglish.preview;

import org.junit.Test;
import static org.junit.Assert.*;

public final class SpectralAnalyzerJUnitTest {
    private short[] wave(double frequency,double seconds){
        int n=(int)(16000*seconds);short[] x=new short[n];
        for(int i=0;i<n;i++){
            double env=0.5-0.5*Math.cos(2*Math.PI*i/(n-1.0));
            x[i]=(short)(12000*env*Math.sin(2*Math.PI*frequency*i/16000));
        }
        return x;
    }
    @Test public void identicalSignalsScoreHigherThanDifferentTones(){
        short[] a=wave(420,2.2),c=wave(1010,2.2);
        SpectralAnalyzer.Result same=SpectralAnalyzer.compare(a,a),other=SpectralAnalyzer.compare(a,c);
        assertTrue(same.similarity>other.similarity);assertTrue(same.similarity>=90);
    }
    @Test public void actualSpectrogramDataIsProduced(){
        SpectralAnalyzer.Result result=SpectralAnalyzer.compare(wave(350,2.2),wave(380,2.2));
        assertTrue(result.referenceImage.length>5);
        assertEquals(48,result.referenceImage[0].length);
        assertTrue(result.learnerImage.length>5);
    }
    @Test public void silenceIsRejected(){assertEquals(-1,SpectralAnalyzer.compare(new short[16000],wave(420,2)).similarity);}
    @Test public void tooShortCaptureIsRejected(){assertEquals(-1,SpectralAnalyzer.compare(new short[1000],wave(420,2)).similarity);}
    @Test public void zeroMicrophoneRateIsRejected(){assertEquals(0,SpectralAnalyzer.resample(wave(420,2),0,1).length);}
    @Test public void resamplingHandlesMono(){short[] s=wave(450,2.0);assertEquals(s.length,SpectralAnalyzer.resample(s,16000,1).length);}
    @Test public void resamplingHandlesStereo(){short[] a=wave(420,1.0),s=new short[a.length*2];for(int i=0;i<a.length;i++){s[2*i]=a[i];s[2*i+1]=a[i];}assertEquals(a.length,SpectralAnalyzer.resample(s,16000,2).length);}
    @Test public void resamplingHandlesHigherSampleRate(){short[] a=wave(420,2.0);assertEquals(a.length/2,SpectralAnalyzer.resample(a,32000,1).length);}
    @Test public void silentSecondSignalRejected(){assertEquals(-1,SpectralAnalyzer.compare(wave(420,2),new short[32000]).similarity);}
}