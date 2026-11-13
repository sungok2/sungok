package com.galaxyuniversalio;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

public final class NtfsWriteProbeTests {
    static int passed;
    static void ok(boolean value,String label) {
        if(!value) throw new AssertionError(label);
        passed++;
    }
    @FunctionalInterface interface Action {void run() throws Exception;}
    static void fails(Action a,String label) throws Exception {
        try {a.run();}catch(IOException expected){passed++;return;}
        throw new AssertionError("Should reject "+label);
    }
    static final class Fake implements NtfsWriteProbe.Backend {
        String journal, id;
        byte[] stored = null;
        int created, deleted, cleanupErrors;
        boolean readOnly, noWritableFile, writeFails, corrupt, noRead,
                deleteFails, ambiguousCreate, journalFails, journalClearFails;
        @Override public void assertWritableDirectory() throws IOException {
            if(readOnly)throw new IOException("Destination is read only");
        }
        @Override public void journal(String name) throws IOException {
            if(journalFails) throw new IOException("journal save failure");
            journal=name;
        }
        @Override public String createTemporary(String name) throws IOException {
            created++;
            id=name;
            stored=new byte[0];
            if(ambiguousCreate) throw new IOException("response lost");
            return name;
        }
        @Override public void assertWritableFile(String id) throws IOException {
            if(noWritableFile)throw new IOException("file write not supported");
        }
        @Override public OutputStream openWrite(String id) throws IOException {
            if(writeFails) throw new IOException("USB write failure");
            return new ByteArrayOutputStream(){
                @Override public void close() throws IOException {
                    stored=toByteArray();
                    super.close();
                }
            };
        }
        @Override public InputStream openRead(String id) throws IOException {
            if(noRead)throw new IOException("USB readback unavailable");
            byte[] data=stored;
            if(corrupt) {
                data=Arrays.copyOf(stored,stored.length);
                if(data.length>0)data[data.length-1]^=0x01;
            }
            return new ByteArrayInputStream(data);
        }
        @Override public boolean deleteTemporary(String id) throws IOException {
            deleted++;
            if(deleteFails) return false;
            stored=null;
            return true;
        }
        @Override public boolean clearJournal() {
            if(journalClearFails)return false;
            journal=null;
            return true;
        }
        @Override public void cleanupError(Exception e){cleanupErrors++;}
    }
    static NtfsWriteProbe.Result run(Fake f) throws IOException {
        return NtfsWriteProbe.execute(f,new AtomicBoolean());
    }
    public static void main(String[] args) throws Exception {
        Fake normal=new Fake();
        NtfsWriteProbe.Result result=run(normal);
        ok(result.bytes==8192,"writes 8KiB");
        ok(result.sha256.length()==64,"checks SHA256");
        ok(normal.journal==null,"success clears journal");
        ok(normal.stored==null&&normal.deleted==1,"probe leaves no file");

        Fake readonly=new Fake();readonly.readOnly=true;
        fails(()->run(readonly),"read only");
        ok(readonly.created==0&&readonly.journal==null,"read only does not create");

        Fake journal=new Fake();journal.journalFails=true;
        fails(()->run(journal),"durable journal failure");
        ok(journal.created==0,"no create without recovery journal");

        Fake write=new Fake();write.writeFails=true;
        fails(()->run(write),"write error");
        ok(write.stored==null&&write.journal==null,"write error cleanup");

        Fake noFlag=new Fake();noFlag.noWritableFile=true;
        fails(()->run(noFlag),"flag lacks write");
        ok(noFlag.stored==null&&noFlag.journal==null,"missing write permission cleanup");

        Fake mutated=new Fake();mutated.corrupt=true;
        fails(()->run(mutated),"readback differs");
        ok(mutated.stored==null&&mutated.journal==null,"corrupt probe cleanup");

        Fake noRead=new Fake();noRead.noRead=true;
        fails(()->run(noRead),"cannot read back");
        ok(noRead.stored==null&&noRead.journal==null,"readback error cleanup");

        Fake noDelete=new Fake();noDelete.deleteFails=true;
        fails(()->run(noDelete),"cannot delete probe");
        ok(noDelete.journal!=null&&noDelete.stored!=null,"recovery retained");
        ok(noDelete.cleanupErrors>0,"cleanup diagnostic emitted");

        Fake lost=new Fake();lost.ambiguousCreate=true;
        fails(()->run(lost),"provider response lost");
        ok(lost.journal!=null&&lost.stored!=null,"recovery retained on ambiguous create");

        Fake noClear=new Fake();noClear.journalClearFails=true;
        fails(()->run(noClear),"could not clear journal");
        ok(noClear.journal!=null&&noClear.stored==null,"data removed, journal may linger");

        Fake cancel=new Fake();
        fails(()->NtfsWriteProbe.execute(cancel,new AtomicBoolean(true)),"pre-cancel");
        ok(cancel.journal==null&&cancel.created==0,"pre-cancel no mutation");
        System.out.println("NTFS active write probe checks passed: "+passed);
    }
}
