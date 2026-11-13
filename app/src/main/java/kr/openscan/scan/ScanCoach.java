package kr.openscan.scan;

/**
 * Human guidance from observable capture state. Stage is a data-coverage checklist,
 * never an accuracy score; only on-device comparison against a physical object can
 * establish whether a reconstruction looks correct.
 */
public final class ScanCoach {
    private ScanCoach() {}

    public static final class Advice {
        public final int stage; // 0-4, an indicative data collection stage
        public final String heading, instruction, stageLabel, modeHint;
        public final boolean needsAttention;
        Advice(int stage,String heading,String instruction,String stageLabel,
               String modeHint,boolean needsAttention){
            this.stage=stage;this.heading=heading;this.instruction=instruction;
            this.stageLabel=stageLabel;this.modeHint=modeHint;this.needsAttention=needsAttention;
        }
    }

    public static String modeHint(ScanModel.Mode mode) {
        switch(mode){
            case OBJECT: return "사물 · 0.5~2m · 중앙에 두고 천천히 한 바퀴";
            case ROOM: return "실내 · 벽과 가구를 천천히 지나며 겹치게 촬영";
            default: return "야외 · 가까운 구조물 위주, 먼 배경은 복원 한계";
        }
    }

    public static String modeGuide(ScanModel.Mode mode){
        switch(mode){
            case OBJECT: return "사물 촬영 안내\n\n① 작은 상자·의자처럼 무광택이고 움직이지 않는 물체부터 시험합니다.\n② 0.5~2m 거리에서 대상이 중앙에 보이도록 맞춥니다.\n③ 물체 주위를 천천히 이동하면서 이전 화면과 겹치게 촬영합니다.\n④ 상단의 추적 상태·포인트 수를 확인하고, 저장 후 형태를 확인합니다.";
            case ROOM: return "실내 촬영 안내\n\n① 조명을 켜고 단색 벽보다 가구·무늬가 있는 곳을 비춥니다.\n② 한 벽면에서 시작해 천천히 이동하고, 연속 화면이 겹치도록 합니다.\n③ 반사되는 유리·거울·어두운 곳은 누락될 수 있습니다.\n④ 공간 전체를 한 번에 정밀한 메시로 만드는 기능은 아직 제공되지 않습니다.";
            default: return "야외 촬영 안내\n\n① 먼저 가까운 벤치·조형물처럼 한정된 구조물로 시험합니다.\n② 빛이 일정하고 그림자가 적은 방향을 선택합니다.\n③ 카메라를 천천히 이동하고, 먼 건물·나무·하늘이 프레임 대부분을 차지하지 않도록 합니다.\n④ 넓은 공원·건물 전체의 사진 기반 정밀 복원은 현 버전에서 지원하지 않습니다.";
        }
    }

    public static Advice assess(ScanModel.Mode mode,boolean scanning,boolean hasSession,
             boolean tracking,int frames,int points,int faces,String diagnostic,boolean saving){
        String hint=modeHint(mode);
        int stage = coverageStage(frames,points,faces);
        if(saving)return new Advice(stage,"파일 저장 중","저장 작업이 끝나면 3D 미리보기에서 확인할 수 있습니다.",
                "데이터 수집 단계 · "+stage+"/4",hint,false);
        if(!hasSession)return new Advice(stage,"카메라 연결 확인","카메라 권한과 Google Play AR 서비스를 확인하세요.",
                "카메라 준비 대기",hint,true);
        if(!tracking)return new Advice(stage,"추적 대기","카메라를 천천히 움직여 질감 있는 표면을 비추세요.",
                "AR 추적 연결 대기",hint,true);
        if(!scanning && frames==0 && points==0)return new Advice(0,"스캔 준비","시작을 누르고 대상에 가까이 다가가 천천히 촬영하세요.",
                "데이터 수집 단계 · 시작 전",hint,false);
        if(!scanning)return new Advice(stage,"스캔 일시정지","3D 모델을 확인하거나, 다른 각도에서 계속 촬영하세요.",
                "데이터 수집 단계 · "+stage+"/4 (일시정지)",hint,false);
        if(points==0)return new Advice(0,"깊이 데이터 대기",
                diagnostic==null||diagnostic.trim().isEmpty()?"밝은 조명에서 0.5~2m 거리의 표면을 비추세요.":diagnostic,
                "데이터 수집 단계 · 아직 기록 없음",hint,true);
        if(stage<=1)return new Advice(stage,"첫 표면 수집됨","대상을 화면 가운데 두고 다른 각도에서 계속 천천히 이동하세요.",
                "데이터 수집 단계 · "+stage+"/4",hint,false);
        if(stage==2)return new Advice(stage,"표면 확대 수집 중","바닥·배경만 모으지 말고 대상의 측면과 뒤쪽도 촬영하세요.",
                "데이터 수집 단계 · 2/4",hint,false);
        if(stage==3)return new Advice(stage,"3D 모델 확인 권장","데이터가 늘고 있습니다. 저장 후 표면과 점군을 비교해 보세요.",
                "데이터 수집 단계 · 3/4",hint,false);
        return new Advice(4,"저장 후 3D 형태 확인","수집량은 충분할 수 있지만 형태 정확도는 미검증입니다. 저장 후 확인하세요.",
                "데이터 수집 단계 · 4/4 (정확도 아님)",hint,false);
    }

    public static int coverageStage(int frames,int points,int faces){
        if(points<=0||frames<=0)return 0;
        if(frames>=25 && points>=5000 && faces>=800)return 4;
        if(frames>=10 && points>=1500 && faces>=250)return 3;
        if(frames>=3 && points>=200)return 2;
        return 1;
    }
}
