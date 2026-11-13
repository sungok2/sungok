package com.galaxyuniversalio;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class NtfsTransactionHostTests {
    private static int passed;
    @FunctionalInterface interface Action { void run() throws Exception; }
    static void check(boolean value, String what) {
        if (!value) throw new AssertionError(what);
        ++passed;
    }
    static void fails(Action action, String description) throws Exception {
        try { action.run(); }
        catch (IOException expected) { ++passed; return; }
        throw new AssertionError("Expected IOException: " + description);
    }

    static final class Fake implements SafVerifiedTransaction.Backend {
        byte[] source = "한글 파일 및 바이너리 검증".getBytes(StandardCharsets.UTF_8);
        final Map<String,byte[]> documents = new HashMap<>();
        final Set<String> published = new HashSet<>();
        String tempId, journal, finalName;
        int nameChecks, cleanupErrors, creates, deletes;
        boolean corruptRead, failWrite, failDelete, renameToAlternate;
        boolean failCreateBeforeReturn, failJournal, conflictOnSecondCheck, failClearJournal;

        @Override public void ensureFinalAbsent(String name) throws IOException {
            nameChecks++;
            if (published.contains(name) || (conflictOnSecondCheck && nameChecks == 2)) {
                throw new IOException("Filename already exists");
            }
        }
        @Override public void journal(String temporaryName,String intendedName) throws IOException {
            if (failJournal) throw new IOException("durable journal unavailable");
            if (journal != null) throw new IOException("old journal unresolved");
            journal = temporaryName;
        }
        @Override public String createTemporary(String name) throws IOException {
            creates++;
            tempId=name;
            documents.put(name,new byte[0]);
            if (failCreateBeforeReturn) throw new IOException("provider create response lost");
            return name;
        }
        @Override public void requireWritableAndRenameable(String id) throws IOException {}
        @Override public InputStream openSource() throws IOException {
            return new ByteArrayInputStream(source);
        }
        @Override public OutputStream openTemporaryForWrite(String id) throws IOException {
            return new ByteArrayOutputStream() {
                @Override public void write(byte[] b,int off,int len) {
                    if(failWrite) throw new UncheckedIOException(new IOException("write interrupted"));
                    super.write(b,off,len);
                }
                @Override public void close() throws IOException {
                    documents.put(id,toByteArray());
                    super.close();
                }
            };
        }
        @Override public InputStream openTemporaryForRead(String id) throws IOException {
            byte[] b = documents.get(id);
            if (corruptRead && b.length > 0) {
                b = Arrays.copyOf(b,b.length);
                b[0] ^= 0x35;
            }
            return new ByteArrayInputStream(b);
        }
        @Override public void beforeReadback() {}
        @Override public String rename(String id,String name) throws IOException {
            String assigned=renameToAlternate ? name + " (1)" : name;
            documents.put(assigned,documents.remove(id));
            published.add(assigned);
            finalName=assigned;
            return assigned;
        }
        @Override public String displayName(String id) { return id; }
        @Override public boolean deleteTemporary(String id) throws IOException {
            deletes++;
            if (failDelete) return false;
            return documents.remove(id) != null;
        }
        @Override public boolean clearJournal() {
            if (failClearJournal) return false;
            journal=null;
            return true;
        }
        @Override public void cleanupError(Exception e) { cleanupErrors++; }
    }

    static SafVerifiedTransaction.Receipt send(Fake f) throws IOException {
        return SafVerifiedTransaction.execute(f,"myfile.txt",f.source.length,
                new AtomicBoolean(false), bytes -> {});
    }

    public static void main(String[] args) throws Exception {
        Fake good=new Fake();
        SafVerifiedTransaction.Receipt receipt=send(good);
        check(receipt.bytes==good.source.length,"size");
        check(receipt.sha256.length()==64,"SHA256 encoded");
        check(Arrays.equals(good.documents.get("myfile.txt"),good.source),"published bytes match");
        check(good.journal==null,"success clears journal");
        check(good.deletes==0,"do not delete published file");

        Fake existing=new Fake();
        existing.published.add("myfile.txt");
        fails(() -> send(existing),"no overwrite");
        check(existing.creates==0 && existing.journal==null,"reject before writing");

        Fake corrupt=new Fake(); corrupt.corruptRead=true;
        fails(() -> send(corrupt),"same-size different content");
        check(!corrupt.published.contains("myfile.txt"),"corrupt not published");
        check(corrupt.documents.isEmpty() && corrupt.journal==null,"corrupt temporary deleted");

        Fake badLength=new Fake();
        fails(() -> SafVerifiedTransaction.execute(badLength,"myfile.txt",badLength.source.length+1,
                new AtomicBoolean(false), count->{}),"size mismatch");
        check(badLength.documents.isEmpty()&&badLength.journal==null,"size mismatch cleanup");

        Fake providerRace=new Fake();providerRace.conflictOnSecondCheck=true;
        fails(() -> send(providerRace),"concurrent same-name conflict");
        check(providerRace.published.isEmpty()&&providerRace.journal==null,"race detected pre-publish");

        Fake changedName=new Fake();changedName.renameToAlternate=true;
        fails(() -> send(changedName),"provider changes requested name");
        check(changedName.documents.containsKey("myfile.txt (1)"),"alternate name not destroyed");
        check(changedName.journal==null,"journal cleared after published alternate");

        Fake lostResponse=new Fake();lostResponse.failCreateBeforeReturn=true;
        fails(() -> send(lostResponse),"ambiguous create response");
        check(lostResponse.journal!=null,"retain journal on uncertain creation");
        check(lostResponse.documents.containsKey(lostResponse.tempId),"unresolved partial is visible");

        Fake cleanupFailure=new Fake();cleanupFailure.corruptRead=true;cleanupFailure.failDelete=true;
        fails(() -> send(cleanupFailure),"cleanup fails");
        check(cleanupFailure.journal!=null && cleanupFailure.cleanupErrors>0,"recovery marker persists");

        Fake canceled=new Fake();AtomicBoolean cancel=new AtomicBoolean(true);
        fails(() -> SafVerifiedTransaction.execute(canceled,"myfile.txt",
                canceled.source.length,cancel,null),"pre-cancel");
        check(canceled.creates==0 && canceled.journal==null,"pre-cancel leaves nothing");

        Fake cancelMid=new Fake();AtomicBoolean mid=new AtomicBoolean();
        fails(() -> SafVerifiedTransaction.execute(cancelMid,"myfile.txt",
                cancelMid.source.length,mid,n->mid.set(true)),"cancel during transfer");
        check(cancelMid.documents.isEmpty()&&cancelMid.journal==null,"mid-transfer cleanup");

        Fake noJournal=new Fake();noJournal.failJournal=true;
        fails(() -> send(noJournal),"journal cannot be saved");
        check(noJournal.creates==0,"no mutation without durable journal");

        Fake journalClearFailure=new Fake();journalClearFailure.failClearJournal=true;
        fails(() -> send(journalClearFailure),"published but journal clear failed");
        check(journalClearFailure.documents.containsKey("myfile.txt"),
                "published bytes not rolled back on journal failure");

        System.out.println("NTFS transaction checks passed: "+passed);
    }
}
