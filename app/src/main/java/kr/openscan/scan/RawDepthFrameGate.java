package kr.openscan.scan;

/** Accept fresh ARCore raw depth images by comparing against the preceding depth image. */
public final class RawDepthFrameGate {
    private long last = Long.MIN_VALUE;
    public boolean shouldIntegrate(long rawTimestamp) {
        if (rawTimestamp == last) return false;
        last = rawTimestamp;
        return true;
    }
    public void reset() { last = Long.MIN_VALUE; }
}
