import kr.openscan.scan.RawDepthFrameGate;
/** Validates that frame and depth timestamps need NOT be identical. */
public final class RawDepthFrameGateTest {
    public static void main(String[] ignored) {
        RawDepthFrameGate g = new RawDepthFrameGate();
        if (!g.shouldIntegrate(1000L)) throw new AssertionError("fresh depth rejected");
        if (g.shouldIntegrate(1000L)) throw new AssertionError("duplicate integrated");
        if (!g.shouldIntegrate(1100L)) throw new AssertionError("new depth rejected");
        g.reset();
        if (!g.shouldIntegrate(1100L)) throw new AssertionError("reset failure");
        System.out.println("PASS raw depth timestamps compared against previous RAW depth, independent of camera frame");
    }
}
