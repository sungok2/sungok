package com.galaxyuniversalio;

public final class NtfsAuditHostTests {
    public static void main(String[] args) throws Exception {
        NtfsTransferAudit audit = new NtfsTransferAudit();
        SafVerifiedTransaction.Receipt one =
                new SafVerifiedTransaction.Receipt(4, "a".repeat(64));
        audit.success("검증.txt", one);
        String report = audit.finish(2, false, "USB\nDisconnected");
        if (!report.contains("VERIFIED\t검증.txt\t4\t"+ "a".repeat(64))) throw new AssertionError("receipt missing");
        if (!report.contains("PARTIAL") || !report.contains("VERIFIED_FILES=1")) throw new AssertionError("partial status");
        if (report.contains("USB\nDisconnected")) throw new AssertionError("newline injection");
        if (audit.count()!=1 || audit.totalBytes()!=4) throw new AssertionError("statistics");
        boolean rejected=false;
        try {audit.success("CON.txt",one);}
        catch(java.io.IOException e) {rejected=true;}
        if(!rejected) throw new AssertionError("unsafe name must not be audited");
        String completed=audit.finish(1,true,null);
        if(!completed.contains("SUMMARY\tCOMPLETE")) throw new AssertionError("completion");
        System.out.println("NTFS audit checks passed: 6");
    }
}
