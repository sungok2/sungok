package kr.openscan.scan;

/** World-space capture volume for object scans, fixed to the initial subject centre.
 * A conservative sphere clips much of the distant background without claiming object segmentation.
 */
public final class SubjectVolume {
    private float x,y,z;
    private boolean initialized;
    private final float radiusMeters;
    public SubjectVolume(float radiusMeters) {
        if (!(radiusMeters > 0) || !Float.isFinite(radiusMeters)) throw new IllegalArgumentException("radius");
        this.radiusMeters = radiusMeters;
    }
    public synchronized boolean initializeIfNeeded(float px,float py,float pz) {
        if (initialized || !Float.isFinite(px+py+pz)) return false;
        x=px; y=py; z=pz; initialized=true; return true;
    }
    public synchronized boolean contains(float px,float py,float pz) {
        if (!initialized) return true;
        float dx=px-x,dy=py-y,dz=pz-z;
        return dx*dx+dy*dy+dz*dz<=radiusMeters*radiusMeters;
    }
    public synchronized boolean isInitialized(){return initialized;}
    public synchronized void reset(){initialized=false;}
}
