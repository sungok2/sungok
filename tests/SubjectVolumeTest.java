import kr.openscan.scan.SubjectVolume;
public class SubjectVolumeTest {
    public static void main(String[] a) {
        SubjectVolume volume=new SubjectVolume(1f);
        if(!volume.contains(1,2,100))throw new AssertionError("uninitialized volume must not crop");
        if(!volume.initializeIfNeeded(0,0,0))throw new AssertionError("initialization");
        if(!volume.contains(0.5f,0.2f,0.1f))throw new AssertionError("near object rejected");
        if(volume.contains(0,0,1.5f))throw new AssertionError("far background accepted");
        if(volume.initializeIfNeeded(5,5,5))throw new AssertionError("centre should be fixed");
        volume.reset();
        if(!volume.initializeIfNeeded(2,2,2)||!volume.contains(2,2,2))throw new AssertionError("reset");
        System.out.println("PASS SubjectVolume anchors once, clips background and resets");
    }
}
