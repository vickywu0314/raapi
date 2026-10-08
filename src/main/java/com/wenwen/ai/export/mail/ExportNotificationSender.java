package com.wenwen.ai.export.mail;

import com.wenwen.ai.export.ExportApplicationRecord;
import javax.mail.*;
import javax.mail.internet.MimeMessage;
import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** 单一真实socket owner；caller仅消费afterExecute之后的结果，不向worker授予DB权利。 */
public final class ExportNotificationSender implements AutoCloseable {
 private final AtomicReference<Flight> active=new AtomicReference<>();private final AtomicBoolean closed=new AtomicBoolean();
 private final ThreadPoolExecutor executor=new ThreadPoolExecutor(0,1,30,TimeUnit.SECONDS,new SynchronousQueue<Runnable>(),r->{Thread t=new Thread(r,"ra-export-notification");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy()){
  @Override protected void afterExecute(Runnable runnable,Throwable error){try{super.afterExecute(runnable,error);}finally{if(runnable instanceof OwnedTask){Flight f=((OwnedTask)runnable).flight;active.compareAndSet(f,null);f.exited.countDown();}}}
 };
 public static final class Result {public final String state,reason;public final Boolean accepted;public final boolean ownerExited;private Result(String state,String reason,Boolean accepted,boolean exited){this.state=state;this.reason=reason;this.accepted=accepted;ownerExited=exited;}public static Result failed(String reason){return new Result("FAILED",reason,false,true);}}
 static final class Flight {
  final long networkDeadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(9500),callerDeadline=networkDeadline+TimeUnit.MILLISECONDS.toNanos(500);
  final AtomicReference<String> terminal=new AtomicReference<>();final AtomicReference<Socket> raw=new AtomicReference<>();final CountDownLatch exited=new CountDownLatch(1);volatile boolean dataEntered;volatile Thread worker;
  void check()throws MessagingException {if(terminal.get()!=null)throw new MessagingException("notification retired");if(left(networkDeadline)==0){retire("DEADLINE");throw new MessagingException("notification deadline");}}
  void retire(String cause){terminal.compareAndSet(null,cause);closeRaw();Thread t=worker;if(t!=null)t.interrupt();}
  void closeRaw(){Socket socket=raw.get();if(socket!=null)try{socket.close();}catch(IOException ignored){}}
 }
 private final class OwnedTask extends FutureTask<Void>{final Flight flight;OwnedTask(Flight f,ExportApplicationRecord r,ExportNotificationSettings c,Instant at,SSLSocketFactory tls){super(()->{execute(f,r,c,at,tls);return null;});flight=f;}}
 public Result send(ExportApplicationRecord r,ExportNotificationSettings c,Instant at){return send(r,c,at,null);}
 /** TLS factory仅由装配提供；生产调用固定null使用JVM默认链。 */
 public Result send(ExportApplicationRecord r,ExportNotificationSettings c,Instant at,SSLSocketFactory tls){String config=c.failure();if(config!=null)return Result.failed(config);if(closed.get())return Result.failed("SERVICE_STOPPED");Flight f=new Flight();if(!active.compareAndSet(null,f))return Result.failed("NOTIFICATION_BUSY");OwnedTask task=new OwnedTask(f,r,c,at,tls);if(closed.get()){active.compareAndSet(f,null);return Result.failed("SERVICE_STOPPED");}try{executor.execute(task);}catch(RejectedExecutionException busy){active.compareAndSet(f,null);return Result.failed(closed.get()?"SERVICE_STOPPED":"NOTIFICATION_BUSY");}
  boolean interrupted=false;try{if(!f.exited.await(left(f.networkDeadline),TimeUnit.NANOSECONDS))f.retire("DEADLINE");}catch(InterruptedException e){interrupted=true;f.retire("CALLER_INTERRUPTED");}
  boolean exited=f.exited.getCount()==0;
  if(!exited){long until=Math.min(f.callerDeadline,System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(500));while(left(until)>0)try{exited=f.exited.await(left(until),TimeUnit.NANOSECONDS);break;}catch(InterruptedException e){interrupted=true;f.retire("CALLER_INTERRUPTED");}}
  if(interrupted)Thread.currentThread().interrupt();String cause=f.terminal.get();Boolean accepted="ACCEPTED".equals(cause)?Boolean.TRUE:Arrays.asList("PRE_DATA_REJECTED","PRE_DATA_IO_FAILED").contains(cause)?Boolean.FALSE:null;if(!exited)return new Result("UNKNOWN",cause==null?"IN_PROGRESS_OR_UNCERTAIN":cause,accepted,false);
  if("ACCEPTED".equals(cause))return new Result("SENT",null,true,true);if(Arrays.asList("PRE_DATA_REJECTED","PRE_DATA_IO_FAILED").contains(cause))return Result.failed(cause);return new Result("UNKNOWN",cause==null?"IN_PROGRESS_OR_UNCERTAIN":cause,null,true);
 }
 private void execute(Flight f,ExportApplicationRecord r,ExportNotificationSettings c,Instant at,SSLSocketFactory tls){TrackedSmtpTransport transport=null;f.worker=Thread.currentThread();try{f.check();Properties props=new Properties();props.put("mail.smtp.socketFactory",new OwnedSocketFactory(f));props.setProperty("mail.smtp.socketFactory.fallback","false");props.setProperty("mail.smtp.connectiontimeout","9500");props.setProperty("mail.smtp.timeout","9500");props.setProperty("mail.smtp.quitwait","false");props.setProperty("mail.smtp.localhost","ra-notification");props.setProperty("mail.smtp.sendpartial","false");props.setProperty("mail.smtp.reportsuccess","false");props.setProperty("mail.smtp.allow8bitmime","false");props.setProperty("mail.debug.auth","false");if("STARTTLS".equals(c.mode)){props.setProperty("mail.smtp.starttls.enable","true");props.setProperty("mail.smtp.starttls.required","true");props.setProperty("mail.smtp.ssl.checkserveridentity","true");if(tls!=null)props.put("mail.smtp.ssl.socketFactory",tls);}props.setProperty("mail.smtp.auth",String.valueOf(!c.username.isEmpty()));Session session=Session.getInstance(props);session.setDebug(false);MimeMessage message=ExportMailMessage.build(session,r,c,at);transport=new TrackedSmtpTransport(session,f);f.check();transport.connect(c.host,Integer.parseInt(c.port),c.username.isEmpty()?null:c.username,c.password.isEmpty()?null:c.password);f.check();transport.sendMessage(message,message.getAllRecipients());}catch(IOException|MessagingException|RuntimeException e){f.terminal.compareAndSet(null,f.dataEntered?"DATA_OUTCOME_UNKNOWN":rejected(e)?"PRE_DATA_REJECTED":"PRE_DATA_IO_FAILED");}finally{f.closeRaw();if(transport!=null)try{transport.close();}catch(MessagingException|RuntimeException ignored){}}}
 private static boolean rejected(Throwable e){for(Throwable x=e;x!=null;x=x.getCause()){if(x instanceof com.sun.mail.smtp.SMTPAddressFailedException||x instanceof com.sun.mail.smtp.SMTPSendFailedException)return true;if(x instanceof MessagingException&&((MessagingException)x).getNextException()!=null&&((MessagingException)x).getNextException()!=x&&rejected(((MessagingException)x).getNextException()))return true;}return false;}
 private static long left(long until){return Math.max(0,until-System.nanoTime());}
 private static final class OwnedSocketFactory extends SocketFactory {
  private final Flight flight;OwnedSocketFactory(Flight flight){this.flight=flight;}
  public Socket createSocket()throws IOException {Socket socket=new Socket(){
   private void valid()throws IOException {try{flight.check();}catch(MessagingException e){throw new IOException("notification retired",e);}}
   @Override public void connect(SocketAddress endpoint,int timeout)throws IOException{valid();super.connect(endpoint,Math.max(1,(int)Math.min(timeout==0?9500:timeout,TimeUnit.NANOSECONDS.toMillis(left(flight.networkDeadline)))));valid();}
   @Override public InputStream getInputStream()throws IOException{valid();final InputStream in=super.getInputStream();return new FilterInputStream(in){private void before()throws IOException{valid();setSoTimeout(Math.max(1,(int)TimeUnit.NANOSECONDS.toMillis(left(flight.networkDeadline))));}public int read()throws IOException{before();return in.read();}public int read(byte[] b,int off,int len)throws IOException{before();return in.read(b,off,len);}};}
   @Override public OutputStream getOutputStream()throws IOException {valid();final OutputStream out=super.getOutputStream();return new FilterOutputStream(out){public void write(int b)throws IOException{valid();out.write(b);}public void write(byte[] b,int off,int len)throws IOException{valid();out.write(b,off,len);}};}
  };socket.setSoLinger(false,0);if(!flight.raw.compareAndSet(null,socket)){socket.close();throw new IOException("notification socket already owned");}try{flight.check();return socket;}catch(MessagingException e){socket.close();throw new IOException("notification retired",e);}}
  public Socket createSocket(String h,int p)throws IOException{throw new IOException("only owned unconnected socket supported");}public Socket createSocket(String h,int p,InetAddress a,int q)throws IOException{throw new IOException("only owned unconnected socket supported");}public Socket createSocket(InetAddress h,int p)throws IOException{throw new IOException("only owned unconnected socket supported");}public Socket createSocket(InetAddress h,int p,InetAddress a,int q)throws IOException{throw new IOException("only owned unconnected socket supported");}
 }
 public void close(){if(!closed.compareAndSet(false,true))return;Flight f=active.get();if(f!=null)f.retire("SERVICE_STOPPED");executor.shutdown();if(f==null)return;long until=Math.min(f.callerDeadline,System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(500));boolean interrupted=Thread.interrupted();try{while(left(until)>0)try{f.exited.await(left(until),TimeUnit.NANOSECONDS);break;}catch(InterruptedException e){interrupted=true;}}finally{if(interrupted)Thread.currentThread().interrupt();}}
}
