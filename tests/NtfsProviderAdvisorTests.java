package com.galaxyuniversalio;
public final class NtfsProviderAdvisorTests {
    static int count;
    static void ok(boolean v){if(!v)throw new AssertionError("Advisor regression");count++;}
    public static void main(String[] args) {
        NtfsProviderAdvisor.Assessment primary=
                NtfsProviderAdvisor.assess("com.android.externalstorage.documents","primary:Download",true,8);
        ok(primary.definitelyInternal);
        ok(primary.providerAllowsCreate);
        NtfsProviderAdvisor.Assessment ntfsBlocked=
                NtfsProviderAdvisor.assess("com.android.externalstorage.documents","USB:folder",true,0);
        ok(!ntfsBlocked.providerAllowsCreate);
        ok(ntfsBlocked.details.contains("NTFS"));
        NtfsProviderAdvisor.Assessment otg=
                NtfsProviderAdvisor.assess("app.fayaz.otgmaster.documents","disk:1",true,8);
        ok(otg.providerAllowsCreate);
        ok(!otg.definitelyInternal);
        NtfsProviderAdvisor.Assessment file=
                NtfsProviderAdvisor.assess("app.fayaz.otgmaster.documents","disk:1",false,8);
        ok(!file.providerAllowsCreate);
        NtfsProviderAdvisor.Assessment unknown=
                NtfsProviderAdvisor.assess(null,null,true,8);
        ok(unknown.providerAllowsCreate);
        ok(!unknown.definitelyInternal);
        NtfsProviderAdvisor.Assessment root=
                NtfsProviderAdvisor.assess("com.android.externalstorage.documents","primary:",true,8);
        ok(root.definitelyInternal);
        System.out.println("NTFS destination advisor checks passed: "+count);
    }
}
