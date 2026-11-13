package com.galaxyuniversalio;
public final class NtfsFailureAdviceTests {
    static int count;
    static void expected(String cause,String phrase) {
        String result=NtfsFailureAdvice.nextStep(cause);
        if(!result.contains(phrase))throw new AssertionError(cause+" => "+result);
        count++;
    }
    public static void main(String[] args) {
        expected("Digest mismatch: SHA-256","재읽기");
        expected("Device is read-only","쓰기 전환");
        expected("폴더 쓰기를 허용하지 않습니다","쓰기");
        expected("SecurityException: permission denied","권한");
        expected("ENOSPC space","공간");
        expected("Rename binder failed","이름 변경");
        expected("같은 이름의 파일","충돌");
        expected("USB disconnected","USB");
        expected("복구 기록 남음","복구");
        expected("some unknown provider exception","추가 확인");
        expected(null,"추가 확인");
        System.out.println("NTFS failure advice checks passed: "+count);
    }
}
