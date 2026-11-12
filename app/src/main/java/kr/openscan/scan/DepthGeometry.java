package kr.openscan.scan;

/** Pure pinhole depth geometry; tested independently of Android and ARCore. */
public final class DepthGeometry {
    private DepthGeometry() {}

    /** Scale TEXTURE (not CPU IMAGE) intrinsics to the native raw-depth pixel grid. */
    public static float[] scaleIntrinsics(float fx,float fy,float cx,float cy,
                                          int texW,int texH,int depthW,int depthH) {
        if(texW<=0||texH<=0||depthW<=0||depthH<=0||fx<=0||fy<=0)
            throw new IllegalArgumentException("Invalid camera calibration");
        float sx=depthW/(float)texW,sy=depthH/(float)texH;
        return new float[]{fx*sx,fy*sy,cx*sx,cy*sy};
    }

    /** Pixel sample in texture-oriented raw depth; camera looks along -Z. */
    public static void unproject(float x,float y,float depthMeters,float[] k,float[] result) {
        if(k.length<4||result.length<3||!(depthMeters>0))
            throw new IllegalArgumentException("Invalid unprojection input");
        result[0]=(x-k[2])*depthMeters/k[0];
        result[1]=(k[3]-y)*depthMeters/k[1];
        result[2]=-depthMeters;
    }
}
