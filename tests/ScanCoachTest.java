import kr.openscan.scan.ScanCoach;
import kr.openscan.scan.ScanModel;

public final class ScanCoachTest {
    private static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
    public static void main(String[] args){
        check(ScanCoach.coverageStage(0,61000,1200)==0,"frames required");
        check(ScanCoach.coverageStage(25,61569,0)<3,"points alone do not imply a good model");
        check(ScanCoach.coverageStage(25,6000,900)==4,"data-stage threshold");
        ScanCoach.Advice wait=ScanCoach.assess(ScanModel.Mode.OBJECT,true,true,true,0,0,0,"RAW depth unavailable",false);
        check(wait.needsAttention && wait.instruction.contains("RAW"),"diagnostics must be shown");
        ScanCoach.Advice paused=ScanCoach.assess(ScanModel.Mode.OUTDOOR,false,true,true,10,1600,250,"",false);
        check(paused.heading.contains("일시정지") && paused.stage==3,"paused keeps stage");
        for(ScanModel.Mode m:ScanModel.Mode.values()){
            check(ScanCoach.modeGuide(m).contains("①"),"guide missing");
            check(ScanCoach.modeHint(m).length()>15,"mode hint missing");
        }
        System.out.println("PASS ScanCoach 7 assertions");
    }
}
