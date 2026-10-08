package com.wenwen.ai.export.mail;

import com.wenwen.ai.cohort.NotificationSmtpPeer;
import com.wenwen.ai.export.ExportApplicationRecord;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExportNotificationSenderTest {
    static ExportApplicationRecord record(){
        ExportApplicationRecord r=new ExportApplicationRecord();r.put("owner",101L);r.put("applicationId",7L);r.put("source","COHORT");r.put("referenceId","00000000-0000-4000-8000-000000000007");r.put("purposeCode","RESEARCH");r.put("startDate","2026-01-01");r.put("endDate","2026-10-07");
        String versions="\"statistics\":\"synthetic\",\"descriptive\":\"synthetic\",\"qc\":\"synthetic\",\"crp\":\"synthetic\",\"now\":\"synthetic\",\"clinical\":\"synthetic\",\"serology\":\"synthetic\",\"treatment\":\"synthetic\",\"visitMatcher\":\"synthetic\",\"drugDictionary\":\"synthetic\"";
        r.put("summary","{\"schemaVersion\":\"dev-export-apply-v01\",\"source\":\"COHORT\",\"referenceId\":\"00000000-0000-4000-8000-000000000007\",\"payloadSha256\":\""+String.join("",Collections.nCopies(64,"a"))+"\",\"scopeFingerprint\":\""+String.join("",Collections.nCopies(64,"b"))+"\",\"selectedItems\":[\"sex\"],\"conditions\":{\"filters\":{\"studyCode\":\"RA\",\"at\":\"now\",\"act\":null,\"sex\":null,\"age\":null,\"sero\":null,\"cm\":null,\"tx\":null,\"data\":null},\"ids\":{\"mode\":\"ALL\",\"submittedUniqueIdsN\":null,\"effectiveIdsN\":null}},\"counts\":{\"cohortN\":37,\"aN\":null,\"bN\":null},\"blocked\":[{\"field\":\"sex\",\"side\":null,\"reason\":\"MISSING_SOURCE_DATE\",\"affectedN\":37}],\"freeStart\":\"2026-04-01\",\"today\":\"2026-10-07\",\"sourcePolicyVersions\":{"+versions+"}}");return r;
    }
    static ExportNotificationSettings settings(NotificationSmtpPeer peer){return new ExportNotificationSettings("true","127.0.0.1",String.valueOf(peer.port()),"sender@notification.invalid","admin@notification.invalid","","","LOOPBACK_PLAIN");}
    @Test void silentGreetingDeadlineIsUnknownAndOwnsRealSocketExit()throws Exception {
        try(NotificationSmtpPeer peer=new NotificationSmtpPeer(NotificationSmtpPeer.Mode.GREETING_SILENT);ExportNotificationSender sender=new ExportNotificationSender()){
            long start=System.nanoTime();ExportNotificationSender.Result result=sender.send(record(),settings(peer),Instant.parse("2026-10-07T02:00:00Z"));long elapsed=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);
            assertEquals(0,peer.waiting.getCount());assertEquals("UNKNOWN",result.state);assertEquals("DEADLINE",result.reason);assertTrue(result.ownerExited);assertTrue(peer.eof.await(500,TimeUnit.MILLISECONDS),"生产close而非fixture释放peer");assertTrue(elapsed>=9000&&elapsed<10500,"单次9500/10000预算，实际="+elapsed);assertEquals(0,peer.dataCommands.get());assertNull(peer.failure);
        }
    }

    static ExportNotificationSender.Flight flight(ExportNotificationSender sender)throws Exception {java.lang.reflect.Field field=ExportNotificationSender.class.getDeclaredField("active");field.setAccessible(true);return (ExportNotificationSender.Flight)((AtomicReference<?>)field.get(sender)).get();}
    @Test void callerInterruptClosesDataSocketAndActualOwnerBeforeCleanupAndReusesCapacity()throws Exception {
        try(NotificationSmtpPeer peer=new NotificationSmtpPeer(NotificationSmtpPeer.Mode.DATA_SILENT);ExportNotificationSender sender=new ExportNotificationSender()){
            AtomicReference<ExportNotificationSender.Result> result=new AtomicReference<>();AtomicBoolean flag=new AtomicBoolean();CountDownLatch done=new CountDownLatch(1);Thread caller=new Thread(()->{try{result.set(sender.send(record(),settings(peer),Instant.now()));flag.set(Thread.currentThread().isInterrupted());}finally{done.countDown();}},"synthetic-notification-caller");caller.start();assertTrue(peer.waiting.await(3,TimeUnit.SECONDS));ExportNotificationSender.Flight f=flight(sender);assertNotNull(f);Thread owner=f.worker;assertEquals(1,f.exited.getCount());assertEquals(1,peer.dataCommands.get());assertEquals("NOTIFICATION_BUSY",sender.send(record(),settings(peer),Instant.now()).reason);assertEquals(1,peer.connections.get());long start=System.nanoTime();caller.interrupt();assertTrue(done.await(500,TimeUnit.MILLISECONDS));long budget=TimeUnit.MILLISECONDS.toNanos(500)-(System.nanoTime()-start);assertTrue(peer.eof.await(Math.max(0,budget),TimeUnit.NANOSECONDS));assertEquals(0,f.exited.getCount(),"真实afterExecute已观察");assertTrue(flag.get());assertEquals("UNKNOWN",result.get().state);assertEquals("CALLER_INTERRUPTED",result.get().reason);assertEquals(0,peer.messages.size());assertNull(peer.failure);
            try(NotificationSmtpPeer next=new NotificationSmtpPeer(NotificationSmtpPeer.Mode.SUCCESS)){AtomicReference<Thread> nextOwner=new AtomicReference<>();next.onConnect=()->{try{nextOwner.set(flight(sender).worker);}catch(Exception e){throw new AssertionError(e);}};assertEquals("SENT",sender.send(record(),settings(next),Instant.now()).state);assertSame(owner,nextOwner.get(),"原单线程容量正常复用");assertEquals(1,next.accepted.get());assertNull(next.failure);}
        }
    }
    @Test void destroyRetiresGreetingSocketWithinFiveHundredMillisecondsBeforeCleanup()throws Exception {
        try(NotificationSmtpPeer peer=new NotificationSmtpPeer(NotificationSmtpPeer.Mode.GREETING_SILENT);ExportNotificationSender sender=new ExportNotificationSender()){
            AtomicReference<ExportNotificationSender.Result> result=new AtomicReference<>();CountDownLatch done=new CountDownLatch(1);Thread caller=new Thread(()->{try{result.set(sender.send(record(),settings(peer),Instant.now()));}finally{done.countDown();}});caller.start();assertTrue(peer.waiting.await(3,TimeUnit.SECONDS));ExportNotificationSender.Flight f=flight(sender);assertNotNull(f);long start=System.nanoTime();sender.close();assertTrue(done.await(Math.max(0,TimeUnit.MILLISECONDS.toNanos(500)-(System.nanoTime()-start)),TimeUnit.NANOSECONDS));assertTrue(peer.eof.await(Math.max(0,TimeUnit.MILLISECONDS.toNanos(500)-(System.nanoTime()-start)),TimeUnit.NANOSECONDS));assertEquals(0,f.exited.getCount());assertEquals("UNKNOWN",result.get().state);assertEquals("SERVICE_STOPPED",result.get().reason);assertEquals(0,peer.dataCommands.get());assertNull(peer.failure);
        }
    }
    @Test void startTlsUsesTrustedSyntheticCertificateAndRealMime()throws Exception {
        NotificationSmtpPeer.TlsMaterial tls=NotificationSmtpPeer.tls(false);
        try(NotificationSmtpPeer peer=new NotificationSmtpPeer(NotificationSmtpPeer.Mode.TLS_SUCCESS,tls);ExportNotificationSender sender=new ExportNotificationSender()){
            ExportNotificationSettings c=new ExportNotificationSettings("true","127.0.0.1",String.valueOf(peer.port()),"sender@notification.invalid","admin@notification.invalid","","","STARTTLS");ExportNotificationSender.Result r=sender.send(record(),c,Instant.now(),tls.client.getSocketFactory());assertEquals("SENT",r.state);assertTrue(r.accepted);assertTrue(r.ownerExited);assertEquals(1,peer.tlsHandshakes.get());assertEquals(1,peer.messages.size());assertEquals(1,peer.accepted.get());assertEquals(0,peer.authCommands.get());assertTrue(peer.commands.indexOf("STARTTLS")>=0);assertNull(peer.failure);
        }
    }
    @Test void requiredTlsRejectsMissingCapabilityWrongSanAndUntrustedChainBeforeData()throws Exception {
        for(int scenario=0;scenario<3;scenario++){
            NotificationSmtpPeer.TlsMaterial tls=scenario==0?null:NotificationSmtpPeer.tls(scenario==1);
            try(NotificationSmtpPeer peer=new NotificationSmtpPeer(tls==null?NotificationSmtpPeer.Mode.SUCCESS:NotificationSmtpPeer.Mode.TLS_SUCCESS,tls);ExportNotificationSender sender=new ExportNotificationSender()){
                ExportNotificationSettings c=new ExportNotificationSettings("true","127.0.0.1",String.valueOf(peer.port()),"sender@notification.invalid","admin@notification.invalid","","","STARTTLS");ExportNotificationSender.Result r=sender.send(record(),c,Instant.now(),scenario==1?tls.client.getSocketFactory():null);assertEquals("FAILED",r.state);assertEquals("PRE_DATA_IO_FAILED",r.reason);assertTrue(r.ownerExited);assertEquals(0,peer.authCommands.get());assertEquals(0,peer.dataCommands.get());assertEquals(0,peer.messages.size());assertTrue(peer.eof.await(500,TimeUnit.MILLISECONDS));
            }
        }
    }
    @Test void interruptClosesRawSocketDuringSilentTlsHandshakeBeforeReadTimeout()throws Exception {
        NotificationSmtpPeer.TlsMaterial tls=NotificationSmtpPeer.tls(false);
        try(NotificationSmtpPeer peer=new NotificationSmtpPeer(NotificationSmtpPeer.Mode.TLS_HANDSHAKE_SILENT,tls);ExportNotificationSender sender=new ExportNotificationSender()){
            ExportNotificationSettings c=new ExportNotificationSettings("true","127.0.0.1",String.valueOf(peer.port()),"sender@notification.invalid","admin@notification.invalid","","","STARTTLS");AtomicReference<ExportNotificationSender.Result> result=new AtomicReference<>();AtomicBoolean flag=new AtomicBoolean();CountDownLatch done=new CountDownLatch(1);Thread caller=new Thread(()->{try{result.set(sender.send(record(),c,Instant.now(),tls.client.getSocketFactory()));flag.set(Thread.currentThread().isInterrupted());}finally{done.countDown();}});caller.start();assertTrue(peer.waiting.await(3,TimeUnit.SECONDS));ExportNotificationSender.Flight f=flight(sender);long start=System.nanoTime();caller.interrupt();assertTrue(done.await(500,TimeUnit.MILLISECONDS));assertTrue(peer.eof.await(Math.max(0,TimeUnit.MILLISECONDS.toNanos(500)-(System.nanoTime()-start)),TimeUnit.NANOSECONDS));assertEquals(0,f.exited.getCount());assertTrue(flag.get());assertEquals("CALLER_INTERRUPTED",result.get().reason);assertEquals("UNKNOWN",result.get().state);assertEquals(0,peer.authCommands.get());assertEquals(0,peer.dataCommands.get());assertNull(peer.failure);
        }
    }
    @Test void badConfigurationNeverConnectsAndAcceptedCloseCannotWaitForQuit()throws Exception {
        try(NotificationSmtpPeer peer=new NotificationSmtpPeer(NotificationSmtpPeer.Mode.SUCCESS);ExportNotificationSender sender=new ExportNotificationSender()){
            for(ExportNotificationSettings config:Arrays.asList(new ExportNotificationSettings("wrong","127.0.0.1","25","a@notification.invalid","b@notification.invalid","","","LOOPBACK_PLAIN"),new ExportNotificationSettings("true","localhost","25","a@notification.invalid","b@notification.invalid","","","LOOPBACK_PLAIN"),new ExportNotificationSettings("true","127.0.0.1","0","a@notification.invalid","b@notification.invalid","","","STARTTLS"),new ExportNotificationSettings("true","127.0.0.1",String.valueOf(peer.port()),"Name <a@notification.invalid>","b@notification.invalid","","","LOOPBACK_PLAIN"),new ExportNotificationSettings("true","127.0.0.1",String.valueOf(peer.port()),"a@notification.invalid","b@notification.invalid,c@notification.invalid","","","LOOPBACK_PLAIN"),new ExportNotificationSettings("true","127.0.0.1",String.valueOf(peer.port()),"a@notification.invalid","b@notification.invalid","synthetic","","STARTTLS"),new ExportNotificationSettings("true","127.0.0.1",String.valueOf(peer.port()),"a@notification.invalid","b@notification.invalid\r\nBcc: c@notification.invalid","","","LOOPBACK_PLAIN"))){assertEquals("INVALID_MAIL_CONFIG",sender.send(record(),config,Instant.now()).reason);assertEquals(0,peer.connections.get());}
            long start=System.nanoTime();ExportNotificationSender.Result result=sender.send(record(),settings(peer),Instant.now());assertEquals("SENT",result.state);assertTrue(result.accepted);assertTrue(result.ownerExited);assertTrue(System.nanoTime()-start<TimeUnit.SECONDS.toNanos(2));assertTrue(peer.eof.await(500,TimeUnit.MILLISECONDS));assertEquals(1,peer.accepted.get());assertFalse(peer.commands.contains("QUIT"),"raw先关闭，不等待QUIT回应");assertNull(peer.failure);
        }
    }

    @Test void reachableRealSocketOutputFailureAfterDataIsUnknownAndOwnerClosesIt()throws Exception {
        try(NotificationSmtpPeer peer=new NotificationSmtpPeer(NotificationSmtpPeer.Mode.SUCCESS);ExportNotificationSender sender=new ExportNotificationSender()){
            AtomicReference<ExportNotificationSender.Flight> observed=new AtomicReference<>();AtomicReference<java.net.Socket> raw=new AtomicReference<>();peer.onData=()->{try{ExportNotificationSender.Flight f=flight(sender);observed.set(f);raw.set(f.raw.get());f.raw.get().shutdownOutput();}catch(Exception e){throw new AssertionError(e);}};
            ExportNotificationSender.Result result=sender.send(record(),settings(peer),Instant.now());assertEquals("UNKNOWN",result.state);assertEquals("DATA_OUTCOME_UNKNOWN",result.reason);assertTrue(result.ownerExited);assertEquals(1,peer.dataCommands.get());assertEquals(0,peer.messages.size());assertEquals(0,peer.accepted.get());assertTrue(raw.get().isClosed(),"故障只shutdownOutput，完整close归生产owner");assertEquals(0,observed.get().exited.getCount());assertTrue(peer.eof.await(500,TimeUnit.MILLISECONDS));assertNull(peer.failure);
        }
    }
}
