package com.galaxyuniversalio;

import java.util.Locale;

/** Diagnostic only: never infer filesystem type from a DocumentsProvider URI. */
final class NtfsProviderAdvisor {
    private NtfsProviderAdvisor() {}
    static final class Assessment {
        final String title, details;
        final boolean definitelyInternal;
        final boolean providerAllowsCreate;
        Assessment(String title,String details,boolean internal,boolean create) {
            this.title=title;
            this.details=details;
            this.definitelyInternal=internal;
            this.providerAllowsCreate=create;
        }
    }

    static Assessment assess(String authority, String treeDocumentId, boolean directory, int flags) {
        String auth=authority==null?"":authority.toLowerCase(Locale.ROOT);
        String id=treeDocumentId==null?"":treeDocumentId.toLowerCase(Locale.ROOT);
        boolean canCreate=directory&&(flags&8)!=0;  // FLAG_DIR_SUPPORTS_CREATE
        boolean internal=id.equals("primary:") || id.startsWith("primary:");
        if(internal) return new Assessment("휴대전화 내부 저장공간 선택",
                "USB 대상이 아닙니다. OTG Master 등에서 연결한 USB 폴더를 선택하세요.",
                true,canCreate);
        boolean otg=auth.equals("app.fayaz.otgmaster.documents");
        boolean android=auth.equals("com.android.externalstorage.documents");
        if(!directory) return new Assessment("폴더가 아닌 항목",
                "파일이 아닌 쓰기 가능한 폴더를 선택해야 합니다.",false,false);
        if(!canCreate) {
            if(android) return new Assessment("Android 기본 저장소 제공자가 쓰기를 허용하지 않음",
                    "NTFS USB가 읽기 전용일 가능성이 있습니다. 삼성 내 파일이 제공하는 폴더에 " +
                    "다시 쓰기를 시도해도 권한이 생기지 않습니다. 독립 NTFS 드라이버 앱에서 읽기·쓰기로 연결하세요.",
                    false,false);
            return new Assessment("대상 제공자가 새 파일 생성을 허용하지 않음",
                    "읽기 전용·미연결·잠긴 볼륨이거나 제공자의 지원 범위 밖일 수 있습니다.",false,false);
        }
        if(otg)return new Assessment("OTG Master 제공자에서 폴더 생성 허용",
                "실제 쓰기 가능 여부는 작은 임시 파일을 써서 재읽고 삭제해 보아야 압니다. NTFS 여부는 URI만으로 확정할 수 없습니다.",
                false,true);
        if(android)return new Assessment("Android 기본 저장소 제공자의 폴더 생성 허용",
                "이 경로의 파일 생성 기능이 표시됩니다. 실제 파일시스템이 NTFS인지는 알 수 없습니다. 임시 파일 쓰기 검사로 확인합니다.",
                false,true);
        return new Assessment("외부 문서 제공자에서 폴더 생성 허용",
                "제공자의 표시만으로 파일 쓰기 성공 또는 NTFS 유형을 확인할 수 없습니다. 임시 파일 검사를 수행합니다.",
                false,true);
    }
}
