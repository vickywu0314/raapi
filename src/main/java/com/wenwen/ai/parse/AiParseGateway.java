package com.wenwen.ai.parse;

import com.wenwen.ai.scope.TrustedDoctor;
import okhttp3.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** 唯一caller决定与独立网络owner；没有审计callback、队列或共享连接池。 */
public final class AiParseGateway implements AutoCloseable {
    private static final AtomicInteger THREAD_IDS=new AtomicInteger();
    private final AiGatewaySettings settings;
    private final RuleParser rules=new RuleParser();
    private final Object admission=new Object();
    private final Set<Flight> active=new HashSet<>();
    private boolean closed;
    private final ThreadPoolExecutor executor=new ThreadPoolExecutor(0,2,30,TimeUnit.SECONDS,new SynchronousQueue<Runnable>(),r->{Thread t=new Thread(r,"ra-ai-gateway-"+THREAD_IDS.incrementAndGet());t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy()){
        @Override protected void afterExecute(Runnable task,Throwable failure){try{super.afterExecute(task,failure);}finally{if(task instanceof OwnedTask)((OwnedTask)task).flight.exited.countDown();}}
    };
    private final ConnectionPool pool=new ConnectionPool(0,1,TimeUnit.SECONDS);
    private final OkHttpClient client=new OkHttpClient.Builder().connectionPool(pool).retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).authenticator(Authenticator.NONE).proxyAuthenticator(Authenticator.NONE).proxy(java.net.Proxy.NO_PROXY).cookieJar(CookieJar.NO_COOKIES).protocols(Collections.singletonList(Protocol.HTTP_1_1)).connectTimeout(1000,TimeUnit.MILLISECONDS).readTimeout(4500,TimeUnit.MILLISECONDS).writeTimeout(1000,TimeUnit.MILLISECONDS).callTimeout(4500,TimeUnit.MILLISECONDS).build();

    public AiParseGateway(AiGatewaySettings settings){this.settings=settings;}

    private static final class Flight {
        final long networkDeadline,deadline;
        final AtomicReference<String> terminal=new AtomicReference<>();
        volatile Call call;
        boolean accepted;
        final CountDownLatch exited=new CountDownLatch(1);
        Flight(){long started=System.nanoTime();networkDeadline=started+TimeUnit.MILLISECONDS.toNanos(4500);deadline=started+TimeUnit.SECONDS.toNanos(5);}
        boolean networkExpired(){return System.nanoTime()>=networkDeadline;}
        void retire(String reason){terminal.compareAndSet(null,reason);if(!"NORMAL".equals(terminal.get())){Call current=call;if(current!=null)current.cancel();}}
    }
    private static final class NetworkOutcome {
        final AiGatewayProtocol.Proposal proposal;
        final String failure;
        private NetworkOutcome(AiGatewayProtocol.Proposal proposal,String failure){this.proposal=proposal;this.failure=failure;}
        static NetworkOutcome failure(String reason){return new NetworkOutcome(null,reason);}
    }

    private final class OwnedTask extends FutureTask<NetworkOutcome> {
        final Flight flight;
        OwnedTask(Flight flight){super(()->execute(flight));this.flight=flight;}
    }
    private static final class Cancelled extends IOException {Cancelled(){super("parse interrupted");}}

    public AiGatewayResult parse(String question,ParseDecision baseline,TrustedDoctor doctor) throws IOException {
        if(Thread.currentThread().isInterrupted())throw new Cancelled();
        if(settings.failure()!=null)return AiGatewayResult.failure(settings.failure(),null);
        if("EMPTY".equals(baseline.getStatus())||"UNSUPPORTED".equals(baseline.getStatus()))return AiGatewayResult.failure("AI_NOT_APPLICABLE",null);
        if(!rules.safeForOutbound(question))return AiGatewayResult.failure("AI_SAFETY_BLOCKED",null);
        synchronized(admission){if(closed)return AiGatewayResult.failure("AI_BUSY",null);}
        Flight flight=new Flight();Future<NetworkOutcome> future=null;
        try{
            if(Thread.currentThread().isInterrupted())return interrupted(flight,null);
            if(flight.networkExpired())return timeout(flight,null);
            final byte[] bytes=AiGatewayProtocol.request(settings.model,question);
            if(bytes.length>16384)return AiGatewayResult.failure("AI_INVALID_RESPONSE",null);
            if(Thread.currentThread().isInterrupted())return interrupted(flight,null);
            if(flight.networkExpired())return timeout(flight,null);
            RequestBody body=new RequestBody(){
                private final AtomicBoolean written=new AtomicBoolean();
                public MediaType contentType(){return MediaType.get("application/json; charset=utf-8");}
                public long contentLength(){return bytes.length;}
                public boolean isOneShot(){return true;}
                public void writeTo(okio.BufferedSink sink) throws IOException {
                    if(flight.networkExpired())flight.retire("TIMEOUT");
                    if(flight.terminal.get()!=null||!written.compareAndSet(false,true))throw new IOException("request retired");
                    sink.write(bytes);
                }
            };
            Request request=new Request.Builder().url(settings.endpoint).header("Accept","application/json").header("Authorization","Bearer "+settings.key).header("User-Agent","ra-ai-gateway/1").post(body).build();
            flight.call=client.newCall(request);flight.call.timeout().deadlineNanoTime(flight.networkDeadline);
            if(Thread.currentThread().isInterrupted())return interrupted(flight,null);
            synchronized(admission){
                if(closed){flight.retire("CLOSED");return AiGatewayResult.failure("AI_BUSY",null);}
                if(Thread.currentThread().isInterrupted())return interrupted(flight,null);
                if(flight.networkExpired()||flight.terminal.get()!=null)return timeout(flight,null);
                active.add(flight);OwnedTask task=new OwnedTask(flight);future=task;
                try{executor.execute(task);flight.accepted=true;}
                catch(RejectedExecutionException busy){active.remove(flight);flight.retire("CLOSED");return AiGatewayResult.failure("AI_BUSY",null);}
            }
        }catch(Cancelled cancelled){throw cancelled;}
        catch(IOException|RuntimeException unavailable){flight.retire("CLOSED");return AiGatewayResult.failure("AI_CONFIG_UNAVAILABLE",null);}
        try{
            NetworkOutcome outcome=get(future,flight.networkDeadline);
            if(!flight.exited.await(remaining(flight.networkDeadline),TimeUnit.NANOSECONDS))return timeout(flight,future);
            if(Thread.currentThread().isInterrupted())return interrupted(flight,future);
            if(flight.networkExpired()||"TIMEOUT".equals(outcome.failure))return timeout(flight,future);
            AiGatewayResult result=outcome.failure==null?AiGatewayProtocol.validateCandidate(outcome.proposal,question,baseline,doctor,settings.model):AiGatewayResult.failure(outcome.failure,settings.model);
            return finish(flight,future,result);
        }catch(TimeoutException timeout){return timeout(flight,future);}
        catch(InterruptedException interrupted){return interrupted(flight,future);}
        catch(ExecutionException unexpected){return failedOwner(flight,future);}
        catch(Cancelled cancelled){throw cancelled;}
        catch(IOException|RuntimeException invalid){return finish(flight,future,AiGatewayResult.failure("AI_INVALID_RESPONSE",settings.model));}
    }
    private AiGatewayResult failedOwner(Flight flight,Future<NetworkOutcome> future) throws IOException {
        try{if(!flight.exited.await(remaining(flight.networkDeadline),TimeUnit.NANOSECONDS))return timeout(flight,future);}
        catch(InterruptedException interrupted){return interrupted(flight,future);}
        return finish(flight,future,AiGatewayResult.failure("AI_IO_ERROR",settings.model));
    }
    private static long remaining(long deadline){return Math.max(0,deadline-System.nanoTime());}
    private static NetworkOutcome get(Future<NetworkOutcome> future,long deadline) throws InterruptedException,ExecutionException,TimeoutException {long left=remaining(deadline);if(left==0)throw new TimeoutException();return future.get(left,TimeUnit.NANOSECONDS);}
    private AiGatewayResult finish(Flight flight,Future<NetworkOutcome> future,AiGatewayResult result) throws IOException {
        synchronized(admission){
            if(Thread.currentThread().isInterrupted())flight.terminal.compareAndSet(null,"INTERRUPTED");
            else if(System.nanoTime()>=flight.deadline)flight.terminal.compareAndSet(null,"TIMEOUT");
            else if(closed)flight.terminal.compareAndSet(null,"CLOSED");
            else flight.terminal.compareAndSet(null,"NORMAL");
        }
        String cause=flight.terminal.get();
        if("NORMAL".equals(cause))return result;
        if(Thread.currentThread().isInterrupted()||"INTERRUPTED".equals(cause))return interrupted(flight,future);
        if("TIMEOUT".equals(cause))return timeout(flight,future);
        flight.retire("CLOSED");if(drain(flight,future,false))throw new Cancelled();
        return AiGatewayResult.failure(flight.accepted?"AI_IO_ERROR":"AI_BUSY",flight.accepted?settings.model:null);
    }
    /** 可暂时清interrupt以有界等同一owner；最后总是恢复已观察标志。 */
    private boolean drain(Flight flight,Future<NetworkOutcome> future,boolean observed){
        boolean interrupted=Thread.interrupted()||observed,completed=false;
        try{
            if(future!=null)while(remaining(flight.deadline)>0){
                try{if(!completed){get(future,flight.deadline);completed=true;}flight.exited.await(remaining(flight.deadline),TimeUnit.NANOSECONDS);break;}
                catch(InterruptedException again){interrupted=true;}
                catch(ExecutionException failure){completed=true;}
                catch(TimeoutException exhausted){break;}
            }
        }finally{if(interrupted)Thread.currentThread().interrupt();}
        return interrupted;
    }
    private AiGatewayResult interrupted(Flight flight,Future<NetworkOutcome> future) throws IOException {
        flight.terminal.compareAndSet(null,"INTERRUPTED");
        if(flight.call!=null)flight.call.cancel(); // 真实caller主动取消保护切点
        drain(flight,future,true);throw new Cancelled();
    }
    private NetworkOutcome execute(Flight flight){
        try{
            if(flight.networkExpired())flight.retire("TIMEOUT");
            if(flight.terminal.get()!=null)return NetworkOutcome.failure("AI_TIMEOUT");
            try(Response response=flight.call.execute()){
                if(response.code()!=200){flight.call.cancel();return NetworkOutcome.failure("AI_HTTP_ERROR");}
                try{
                    MediaType type=response.body()==null?null:response.body().contentType();String rawType=response.header("Content-Type",""),encoding=response.header("Content-Encoding");
                    if(type==null||!"application".equals(type.type())||!"json".equals(type.subtype())||type.charset()!=null&&!StandardCharsets.UTF_8.equals(type.charset())||type.charset()==null&&rawType.toLowerCase(Locale.ROOT).contains("charset")||encoding!=null&&!"identity".equalsIgnoreCase(encoding)||response.body().contentLength()>16384)throw new AiGatewayProtocol.Invalid();
                    return new NetworkOutcome(AiGatewayProtocol.decode(response.body().byteStream()),null);
                }catch(IOException|RuntimeException invalid){flight.call.cancel();throw invalid;}
            }
        }catch(AiGatewayProtocol.Invalid invalid){return NetworkOutcome.failure("AI_INVALID_RESPONSE");}
        catch(InterruptedIOException timeout){return NetworkOutcome.failure("AI_TIMEOUT");}
        catch(IOException io){return NetworkOutcome.failure("AI_IO_ERROR");}
        catch(RuntimeException invalid){return NetworkOutcome.failure("AI_INVALID_RESPONSE");}
        finally{synchronized(admission){active.remove(flight);}}
    }
    private AiGatewayResult timeout(Flight flight,Future<NetworkOutcome> future) throws IOException {
        flight.retire("TIMEOUT");if(drain(flight,future,false))throw new Cancelled();
        String reason="CLOSED".equals(flight.terminal.get())&&flight.accepted?"AI_IO_ERROR":"AI_TIMEOUT";
        return AiGatewayResult.failure(reason,flight.accepted?settings.model:null);
    }
    public void close(){
        List<Flight> calls; synchronized(admission){closed=true;calls=new ArrayList<>(active);}
        for(Flight flight:calls)flight.retire("CLOSED");
        executor.shutdown();pool.evictAll();
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(1);boolean interrupted=Thread.interrupted();
        try{while(!executor.isTerminated()&&remaining(deadline)>0)try{executor.awaitTermination(remaining(deadline),TimeUnit.NANOSECONDS);}catch(InterruptedException again){interrupted=true;}}
        finally{if(interrupted)Thread.currentThread().interrupt();}
        // native DNS仍未返回时保持active/真实容量及未terminated状态，绝不clear或强杀。
    }
}
