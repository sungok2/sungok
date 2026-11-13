package com.storyenglish.preview;

/** Offline spectral similarity. Not an ASR, pronunciation test or CEFR score. */
public final class SpectralAnalyzer {
    public static final int SAMPLE_RATE = 16000;
    private static final int FFT = 512, HOP = 160, MEL_BANDS = 24, VIS_BANDS = 48;
    private SpectralAnalyzer() {}
    public static final class Result {
        public final int similarity;
        public final float[][] referenceImage, learnerImage;
        public final String note;
        Result(int similarity, float[][] referenceImage, float[][] learnerImage, String note) {
            this.similarity=similarity; this.referenceImage=referenceImage; this.learnerImage=learnerImage; this.note=note;
        }
    }
    private static final class Frames {
        final double[][] features;
        final double[] energy;
        final float[][] image;
        Frames(double[][] features,double[] energy,float[][] image) {
            this.features=features;this.energy=energy;this.image=image;
        }
    }
    public static Result compare(short[] source, short[] learner) {
        if(source==null || learner==null || source.length<6000 || learner.length<6000) {
            return new Result(-1,new float[0][0],new float[0][0],"음성이 너무 짧아 비교할 수 없습니다.");
        }
        short[] a=trim(source), b=trim(learner);
        if(a.length<6000 || b.length<6000) {
            return new Result(-1,new float[0][0],new float[0][0],"음성 신호가 작거나 부족합니다. 조용한 곳에서 다시 녹음하세요.");
        }
        Frames af=extract(a),bf=extract(b);
        if(af.features.length==0 || bf.features.length==0) return new Result(-1,af.image,bf.image,"분석할 음성이 없습니다.");
        int n=af.features.length,m=bf.features.length;
        double[] previous=new double[m+1],current=new double[m+1];
        int[] prevSteps=new int[m+1],curSteps=new int[m+1];
        java.util.Arrays.fill(previous,Double.POSITIVE_INFINITY);previous[0]=0;
        for(int i=1;i<=n;i++) {
            java.util.Arrays.fill(current,Double.POSITIVE_INFINITY);
            java.util.Arrays.fill(curSteps,0);
            for(int j=1;j<=m;j++) {
                double dot=0;
                for(int k=0;k<MEL_BANDS;k++) dot+=af.features[i-1][k]*bf.features[j-1][k];
                double local=0.5*(1-Math.max(-1,Math.min(1,dot)));
                double best=previous[j-1]; int steps=prevSteps[j-1];
                if(previous[j]<best){best=previous[j];steps=prevSteps[j];}
                if(current[j-1]<best){best=current[j-1];steps=curSteps[j-1];}
                current[j]=local+best;curSteps[j]=steps+1;
            }
            double[] swap=previous;previous=current;current=swap;
            int[] sw=prevSteps;prevSteps=curSteps;curSteps=sw;
        }
        double cosine=1.0-Math.min(1.0,previous[m]/Math.max(1,prevSteps[m]));
        double duration=Math.min((double)a.length/b.length,(double)b.length/a.length);
        double rhythm=energyMatch(af.energy,bf.energy);
        double similarity=100*(0.64*cosine+0.21*rhythm+0.15*Math.sqrt(duration));
        int score=(int)Math.round(Math.max(0,Math.min(100,similarity)));
        return new Result(score,af.image,bf.image,
            "스펙트럼·리듬·길이의 음향 유사도입니다. 다른 화자의 음색, 소음, 마이크 특성에 영향을 받으며 발음 정확도나 CEFR 점수가 아닙니다.");
    }
    private static double energyMatch(double[] a,double[] b) {
        int n=40;double score=0;
        for(int t=0;t<n;t++){
            int ai=Math.min(a.length-1,(int)((double)t*a.length/n));
            int bi=Math.min(b.length-1,(int)((double)t*b.length/n));
            double x=a[ai],y=b[bi];score+=1-Math.min(1,Math.abs(x-y));
        }
        return score/n;
    }
    private static short[] trim(short[] samples){
        int block=160, blocks=samples.length/block;
        if(blocks==0)return new short[0];
        double[] rms=new double[blocks]; double max=0;
        for(int i=0;i<blocks;i++){
            double sum=0;for(int j=0;j<block;j++){double v=samples[i*block+j];sum+=v*v;}
            rms[i]=Math.sqrt(sum/block);max=Math.max(max,rms[i]);
        }
        if(max<180)return new short[0];
        double threshold=Math.max(140,max*0.12);
        int first=0,last=blocks-1;
        while(first<blocks&&rms[first]<threshold)first++;
        while(last>=first&&rms[last]<threshold)last--;
        if(last<first)return new short[0];
        first=Math.max(0,first-9);last=Math.min(blocks-1,last+9);
        return java.util.Arrays.copyOfRange(samples,first*block,Math.min(samples.length,(last+1)*block));
    }
    private static Frames extract(short[] pcm) {
        int fullFrames=Math.max(0,1+(pcm.length-FFT)/HOP);
        int step=Math.max(1,(int)Math.ceil(fullFrames/180.0));
        int count=(fullFrames+step-1)/step;
        double[][] feats=new double[count][MEL_BANDS];
        double[] energies=new double[count];
        double[][] rawVis=new double[count][VIS_BANDS];
        double maxDb=-1000;
        int ptr=0;
        for(int fi=0;fi<fullFrames;fi+=step){
            double[] re=new double[FFT],im=new double[FFT];
            double square=0;
            for(int j=0;j<FFT;j++){
                double v=pcm[fi*HOP+j]/32768.0;
                square+=v*v;
                re[j]=v*(0.5-0.5*Math.cos(2*Math.PI*j/(FFT-1)));
            }
            energies[ptr]=Math.sqrt(square/FFT);
            fft(re,im);
            double[] magnitudes=new double[FFT/2];
            for(int j=0;j<magnitudes.length;j++)magnitudes[j]=Math.sqrt(re[j]*re[j]+im[j]*im[j]);
            for(int band=0;band<VIS_BANDS;band++){
                // Logarithmic bands from 110 Hz to 6 kHz.
                double lo=110*Math.pow(6000.0/110,band/(double)VIS_BANDS);
                double hi=110*Math.pow(6000.0/110,(band+1.0)/VIS_BANDS);
                int start=Math.max(1,Math.min(magnitudes.length-1,(int)Math.floor(lo*FFT/SAMPLE_RATE)));
                int end=Math.max(start+1,Math.min(magnitudes.length,(int)Math.ceil(hi*FFT/SAMPLE_RATE)));
                double val=0;for(int j=start;j<end;j++)val+=magnitudes[j]*magnitudes[j];
                double db=10*Math.log10(1e-8+val/(end-start));
                rawVis[ptr][band]=db;maxDb=Math.max(maxDb,db);
            }
            double mean=0;
            for(int band=0;band<MEL_BANDS;band++){
                int low=band*VIS_BANDS/MEL_BANDS;
                int high=(band+1)*VIS_BANDS/MEL_BANDS;
                double val=0;for(int j=low;j<high;j++)val+=rawVis[ptr][j];
                feats[ptr][band]=val/(high-low);mean+=feats[ptr][band];
            }
            mean/=MEL_BANDS;
            double norm=0;
            for(int j=0;j<MEL_BANDS;j++){feats[ptr][j]-=mean;norm+=feats[ptr][j]*feats[ptr][j];}
            norm=Math.sqrt(norm)+1e-6;
            for(int j=0;j<MEL_BANDS;j++)feats[ptr][j]/=norm;
            ptr++;
        }
        double maxEnergy=0;for(double e:energies)maxEnergy=Math.max(maxEnergy,e);
        if(maxEnergy>0)for(int i=0;i<energies.length;i++)energies[i]/=maxEnergy;
        float[][] vis=new float[count][VIS_BANDS];
        for(int i=0;i<count;i++)for(int j=0;j<VIS_BANDS;j++)vis[i][j]=(float)Math.max(0,Math.min(1,(rawVis[i][j]-maxDb+55)/55));
        return new Frames(feats,energies,vis);
    }
    /** Cooley-Tukey iterative radix-2 FFT. */
    private static void fft(double[] re,double[] im){
        int n=re.length;
        for(int i=1,j=0;i<n;i++){
            int bit=n>>1;for(; (j&bit)!=0;bit>>=1)j^=bit;j^=bit;
            if(i<j){double x=re[i];re[i]=re[j];re[j]=x;x=im[i];im[i]=im[j];im[j]=x;}
        }
        for(int len=2;len<=n;len<<=1){
            double angle=-2*Math.PI/len,wrBase=Math.cos(angle),wiBase=Math.sin(angle);
            for(int start=0;start<n;start+=len){double wr=1,wi=0;
                for(int j=0;j<len/2;j++){
                    int a=start+j,b=a+len/2;
                    double vr=wr*re[b]-wi*im[b],vi=wr*im[b]+wi*re[b];
                    re[b]=re[a]-vr;im[b]=im[a]-vi;re[a]+=vr;im[a]+=vi;
                    double next=wr*wrBase-wi*wiBase;wi=wr*wiBase+wi*wrBase;wr=next;
                }
            }
        }
    }
    public static short[] resample(short[] source,int sourceRate,int sourceChannels){
        if(source==null || sourceRate<8000 || sourceRate>192000 || sourceChannels<1 || sourceChannels>2)return new short[0];
        int frames=source.length/sourceChannels;
        if(frames==0)return new short[0];
        int outFrames=(int)Math.min(10L*SAMPLE_RATE,Math.round(frames*(double)SAMPLE_RATE/sourceRate));
        short[] dest=new short[outFrames];
        for(int i=0;i<outFrames;i++){
            double pos=i*sourceRate/(double)SAMPLE_RATE;
            int left=Math.min(frames-1,(int)pos),right=Math.min(frames-1,left+1);
            double fraction=pos-left;
            double l=0,r=0;for(int c=0;c<sourceChannels;c++){l+=source[left*sourceChannels+c];r+=source[right*sourceChannels+c];}
            double value=(l*(1-fraction)+r*fraction)/sourceChannels;
            dest[i]=(short)Math.max(-32768,Math.min(32767,Math.round(value)));
        }
        return dest;
    }
}