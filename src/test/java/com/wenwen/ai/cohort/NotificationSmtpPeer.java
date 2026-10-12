package com.wenwen.ai.cohort;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** 真实字面loopback协议仪器；静默模式不靠fixture提前释放。 */
public final class NotificationSmtpPeer implements AutoCloseable {
    public enum Mode {SUCCESS,RCPT_REJECT,DATA_SILENT,ACK_DROP,GREETING_SILENT,TLS_SUCCESS,TLS_HANDSHAKE_SILENT}
    public final AtomicInteger tlsHandshakes=new AtomicInteger();
    public final List<String> tlsProtocols=Collections.synchronizedList(new ArrayList<>());
    public final AtomicInteger connections=new AtomicInteger(),dataCommands=new AtomicInteger(),accepted=new AtomicInteger(),authCommands=new AtomicInteger();
    public final CountDownLatch waiting=new CountDownLatch(1),eof=new CountDownLatch(1);
    public final List<byte[]> messages=Collections.synchronizedList(new ArrayList<>());
    public final List<String> commands=Collections.synchronizedList(new ArrayList<>());
    public volatile Mode mode;public volatile Throwable failure;public Runnable onConnect,onData;
    private final TlsMaterial tls;private final ServerSocket server;private final Thread acceptor;private final List<Socket> sockets=Collections.synchronizedList(new ArrayList<>());private final List<Thread> workers=Collections.synchronizedList(new ArrayList<>());private volatile boolean closed;
    public NotificationSmtpPeer(Mode mode)throws IOException {this(mode,null);}
    public NotificationSmtpPeer(Mode mode,TlsMaterial tls)throws IOException {this.tls=tls;this.mode=mode;server=new ServerSocket(0,8,InetAddress.getByName("127.0.0.1"));acceptor=new Thread(()->{while(!closed)try{Socket socket=server.accept();sockets.add(socket);connections.incrementAndGet();Thread t=new Thread(()->serve(socket),"synthetic-smtp-peer");workers.add(t);t.setDaemon(true);t.start();}catch(IOException e){if(!closed)failure=e;}},"synthetic-smtp-accept");acceptor.setDaemon(true);acceptor.start();}
    public int port(){return server.getLocalPort();}
    private void serve(Socket socket){try{if(onConnect!=null)onConnect.run();Mode current=mode;InputStream in=socket.getInputStream();OutputStream out=socket.getOutputStream();if(current==Mode.GREETING_SILENT){waiting.countDown();untilEof(in);return;}line(out,"220 synthetic notification peer");String command;while((command=readLine(in))!=null){commands.add(command);if(command.startsWith("EHLO")){if(tls!=null&&!(socket instanceof javax.net.ssl.SSLSocket)){line(out,"250-synthetic");line(out,"250 STARTTLS");}else line(out,"250 synthetic");}else if(command.equals("STARTTLS")){line(out,"220 begin TLS");if(current==Mode.TLS_HANDSHAKE_SILENT){waiting.countDown();untilEof(in);return;}javax.net.ssl.SSLSocket secure=(javax.net.ssl.SSLSocket)tls.server.getSocketFactory().createSocket(socket,"127.0.0.1",port(),true);secure.setUseClientMode(false);secure.startHandshake();tlsProtocols.add(secure.getSession().getProtocol());tlsHandshakes.incrementAndGet();socket=secure;in=secure.getInputStream();out=secure.getOutputStream();}else if(command.startsWith("HELO"))line(out,"250 synthetic");else if(command.startsWith("MAIL FROM"))line(out,"250 sender ok");else if(command.startsWith("RCPT TO"))line(out,current==Mode.RCPT_REJECT?"450 synthetic recipient unavailable":"250 recipient ok");else if(command.equals("RSET"))line(out,"250 reset");else if(command.startsWith("AUTH")){authCommands.incrementAndGet();line(out,"535 no test auth");}else if(command.equals("DATA")){dataCommands.incrementAndGet();if(onData!=null)onData.run();if(current==Mode.DATA_SILENT){waiting.countDown();untilEof(in);return;}line(out,"354 end with dot");ByteArrayOutputStream bytes=new ByteArrayOutputStream();String body;while((body=readLine(in))!=null&&!body.equals(".")){if(body.startsWith(".."))body=body.substring(1);bytes.write(body.getBytes(StandardCharsets.ISO_8859_1));bytes.write('\r');bytes.write('\n');}if(body==null){eof.countDown();return;}messages.add(bytes.toByteArray());if(current==Mode.ACK_DROP){waiting.countDown();untilEof(in);return;}accepted.incrementAndGet();line(out,"250 message accepted");}else if(command.equals("QUIT")){waiting.countDown();untilEof(in);return;}else line(out,"500 unsupported");}eof.countDown();}catch(Throwable e){if(!closed)failure=e;if(tls!=null)eof.countDown();}finally{try{socket.close();}catch(IOException ignored){}}}
    private void untilEof(InputStream in)throws IOException{while(in.read()!=-1){}eof.countDown();}
    private static void line(OutputStream out,String line)throws IOException{out.write((line+"\r\n").getBytes(StandardCharsets.US_ASCII));out.flush();}
    private static String readLine(InputStream in)throws IOException {ByteArrayOutputStream out=new ByteArrayOutputStream();int b;while((b=in.read())!=-1){if(b=='\n')return new String(out.toByteArray(),StandardCharsets.ISO_8859_1).replaceFirst("\\r$","");out.write(b);if(out.size()>20000)throw new IOException("synthetic line limit");}return out.size()==0?null:new String(out.toByteArray(),StandardCharsets.ISO_8859_1);}
    public void close()throws Exception{closed=true;server.close();synchronized(sockets){for(Socket socket:sockets)socket.close();}acceptor.join(1000);synchronized(workers){for(Thread t:workers)t.join(1000);}}

    public static final class TlsMaterial {
        public final javax.net.ssl.SSLContext server,client;public final java.nio.file.Path keystore;
        TlsMaterial(javax.net.ssl.SSLContext server,javax.net.ssl.SSLContext client,java.nio.file.Path path){this.server=server;this.client=client;keystore=path;}
    }
    /** JDK8官方keytool、仅合成PKCS12及本SSLContext，不触系统cacerts。 */
    public static synchronized TlsMaterial tls(boolean wrongSan)throws Exception {
        java.nio.file.Path directory=java.nio.file.Paths.get("../.tmp/ai-cohort-p06b/tls").toAbsolutePath().normalize();java.nio.file.Files.createDirectories(directory);java.nio.file.Path path=directory.resolve(wrongSan?"wrong-san.p12":"localhost.p12");String password="p06b-synthetic-only";
        if(!java.nio.file.Files.exists(path)){
            java.util.List<String> command=java.util.Arrays.asList(System.getenv("JAVA_HOME")+"/bin/keytool","-genkeypair","-alias","synthetic","-keyalg","RSA","-keysize","2048","-sigalg","SHA256withRSA","-dname","CN=localhost","-ext",wrongSan?"SAN=dns:wrong.notification.invalid":"SAN=dns:localhost,ip:127.0.0.1","-validity","3650","-storetype","PKCS12","-keystore",path.toString(),"-storepass",password,"-keypass",password,"-noprompt");
            java.nio.file.Files.write(directory.resolve(wrongSan?"wrong-san-command.txt":"localhost-command.txt"),Collections.singletonList(String.join(" ",command)),StandardCharsets.UTF_8);
            java.nio.file.Files.write(directory.resolve(wrongSan?"wrong-san-start.utc":"localhost-start.utc"),Collections.singletonList(java.time.Instant.now().toString()),StandardCharsets.US_ASCII);Process process=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(directory.resolve(wrongSan?"wrong-san-keytool.log":"localhost-keytool.log").toFile()).start();if(!process.waitFor(30,TimeUnit.SECONDS)){process.destroyForcibly();throw new IOException("synthetic keytool timeout");}java.nio.file.Files.write(directory.resolve(wrongSan?"wrong-san-end.utc":"localhost-end.utc"),Collections.singletonList(java.time.Instant.now().toString()),StandardCharsets.US_ASCII);int exit=process.exitValue();java.nio.file.Files.write(directory.resolve(wrongSan?"wrong-san-exit.txt":"localhost-exit.txt"),Collections.singletonList(String.valueOf(exit)),StandardCharsets.US_ASCII);if(exit!=0)throw new IOException("synthetic keytool instrument exit="+exit);
        }
        java.security.KeyStore keys=java.security.KeyStore.getInstance("PKCS12");try(InputStream in=java.nio.file.Files.newInputStream(path)){keys.load(in,password.toCharArray());}javax.net.ssl.KeyManagerFactory km=javax.net.ssl.KeyManagerFactory.getInstance(javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm());km.init(keys,password.toCharArray());javax.net.ssl.SSLContext server=javax.net.ssl.SSLContext.getInstance("TLS");server.init(km.getKeyManagers(),null,null);
        java.security.KeyStore trust=java.security.KeyStore.getInstance(java.security.KeyStore.getDefaultType());trust.load(null,null);trust.setCertificateEntry("synthetic-ca",keys.getCertificate("synthetic"));javax.net.ssl.TrustManagerFactory tm=javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());tm.init(trust);javax.net.ssl.SSLContext client=javax.net.ssl.SSLContext.getInstance("TLS");client.init(null,tm.getTrustManagers(),null);return new TlsMaterial(server,client,path);
    }
}
