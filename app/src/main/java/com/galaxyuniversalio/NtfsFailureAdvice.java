package com.galaxyuniversalio;

import java.util.Locale;

/** A diagnostic suggestion is not a proven hardware fault or filesystem identity. */
final class NtfsFailureAdvice {
    private NtfsFailureAdvice() {}

    static String nextStep(String details) {
        String s = details == null ? "" : details.toLowerCase(Locale.ROOT);
        if (has(s,"sha-256","digest mismatch","checksum","hash mismatch","검증 실패","다릅니다")) {
            return "가능 원인: 전송 또는 재읽기 내용 불일치. 원본은 유지하고 USB·케이블·연결 제공자의 오류를 조사하세요. 오류 파일을 신뢰하지 마세요.";
        }
        if (has(s,"read only","readonly","read-only","erofs","쓰기를 허용하지","읽기 전용","파일 생성 허용하지")) {
            return "확인 사항: 대상 제공자가 쓰기를 허용하는지 검사하세요. 삼성 기본 NTFS 읽기 전용 경로라면 이 앱의 권한만으로 쓰기 전환되지 않습니다.";
        }
        if (has(s,"securityexception","permission","access denied","권한","보안 예외")) {
            return "가능 원인: Android 문서 URI 권한이 유효하지 않거나 제공자가 접근을 거부했습니다. USB 연결 앱에서 다시 마운트한 후 폴더 권한을 다시 선택하세요.";
        }
        if (has(s,"no space","enospc","storage full","disk full","공간 부족")) {
            return "가능 원인: USB에 공간이 부족합니다. PC에서 실제 남은 공간과 파일시스템 상태를 확인한 뒤 작은 파일로 재검증하세요.";
        }
        if (has(s,"rename","이름 변경","published file")) {
            return "확인 사항: 이름 변경이 이미 완료됐을 수 있습니다. USB 대상 파일을 검사하기 전에는 같은 이름으로 다시 복사하거나 임시 파일을 자동 삭제하지 마세요.";
        }
        if (has(s,"already exists","same name","conflict","덮어쓰기 방지","같은 이름","중복")) {
            return "확인 사항: USB 대상 파일의 이름이 충돌합니다. 원본 파일을 덮어쓰지 않았습니다. 다른 대상 폴더를 선택하세요.";
        }
        if (has(s,"disconnected","broken pipe","i/o error","eio","device gone","연결 해제","분리")) {
            return "가능 원인: USB 접촉 불량·전력 부족·제공자 종료·I/O 오류. USB를 안전하게 분리하고 연결 상태를 확인하세요. 복구 기록을 먼저 확인해야 합니다.";
        }
        if (has(s,"journal","복구 기록")) {
            return "확인 사항: 이전 임시 전송의 복구 기록을 검토하세요. 기록 삭제는 USB에 남은 파일의 삭제나 검증을 의미하지 않습니다.";
        }
        return "추가 확인 필요: 선택한 경로가 실제 USB이고 읽기·쓰기 가능한 DocumentsProvider인지 확인하세요. 이 진단만으로 NTFS 볼륨의 정확한 원인은 확정할 수 없습니다.";
    }

    private static boolean has(String haystack,String... need) {
        for(String token:need)if(haystack.contains(token))return true;
        return false;
    }
}
