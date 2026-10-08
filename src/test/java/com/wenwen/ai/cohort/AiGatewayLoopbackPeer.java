package com.wenwen.ai.cohort;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
/** Java8合成HTTP peer；完整请求、响应放行和对端EOF分别观测。 */
final class AiGatewayLoopbackPeer implements AutoCloseable {
    static final class Exchange {
        final byte[] response;
        final CountDownLatch received=new CountDownLatch(1),written=new CountDownLatch(1),eof=new CountDownLatch(1),attempted=new CountDownLatch(1),allow;
        final boolean held;
        volatile boolean writeSucceeded,closeAfterWrite;
        volatile String method,path;
        volatile Map<String,String> headers;
        volatile byte[] body;
        Exchange(byte[] response){this(response,false);}
        Exchange(byte[] response,boolean held){this.response=response;this.held=held;allow=new CountDownLatch(held?1:0);}
    }
    private final ServerSocket server;
    private final List<Exchange> plans=new CopyOnWriteArrayList<>();
    private final List<Thread> workers=new CopyOnWriteArrayList<>();
    private final List<Socket> sockets=new CopyOnWriteArrayList<>();
    private final BlockingQueue<Exchange> exchanges=new LinkedBlockingQueue<>();
    final AtomicInteger requests=new AtomicInteger();
    final AtomicReference<Throwable> failure=new AtomicReference<>();
    private final Thread accepting;
    private volatile boolean closed;
    AiGatewayLoopbackPeer() throws IOException {
        server=new ServerSocket();server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),0));
        accepting=new Thread(()->{while(!closed)try{Socket socket=server.accept();sockets.add(socket);Thread worker=new Thread(()->serve(socket),"p05b-peer");worker.setDaemon(true);workers.add(worker);worker.start();}catch(IOException e){if(!closed)failure.compareAndSet(null,e);}},"p05b-peer-accept");
        accepting.setDaemon(true);accepting.start();
    }
    String endpoint(){return "http://127.0.0.1:"+server.getLocalPort()+"/chat/completions";}
    Exchange reply(byte[] response){Exchange exchange=new Exchange(response);plans.add(exchange);exchanges.add(exchange);return exchange;}
    Exchange closeAfterWrite(byte[] response){Exchange exchange=reply(response);exchange.closeAfterWrite=true;return exchange;}
    Exchange holdJson(String body){byte[] bytes=body.getBytes(StandardCharsets.UTF_8);Exchange exchange=new Exchange(http(200,"Content-Type: application/json\r\nContent-Length: "+bytes.length+"\r\n",bytes),true);plans.add(exchange);exchanges.add(exchange);return exchange;}
    void discardUnused(Exchange exchange){if(exchange.received.getCount()!=1||!exchanges.remove(exchange))throw new AssertionError("synthetic response already consumed");}
    Exchange json(String body){byte[] bytes=body.getBytes(StandardCharsets.UTF_8);return reply(http(200,"Content-Type: application/json\r\nContent-Length: "+bytes.length+"\r\n",bytes));}
    static byte[] http(int status,String headers,byte[] body){ByteArrayOutputStream out=new ByteArrayOutputStream();try{out.write(("HTTP/1.1 "+status+" synthetic\r\n"+headers+"\r\n").getBytes(StandardCharsets.ISO_8859_1));out.write(body);}catch(IOException e){throw new AssertionError(e);}return out.toByteArray();}
    private void serve(Socket socket) {
        List<Exchange> served=new ArrayList<>();
        try {
            socket.setSoTimeout(12000);InputStream input=socket.getInputStream();
            while(true){
                ByteArrayOutputStream header=new ByteArrayOutputStream();int state=0;
                while(state<4){int b=input.read();if(b<0){for(Exchange old:served)old.eof.countDown();return;}header.write(b);if(header.size()>8192)throw new IOException("synthetic header limit");state=(state==0||state==2)?(b==13?state+1:0):(b==10?state+1:0);}
                String[] lines=new String(header.toByteArray(),StandardCharsets.ISO_8859_1).split("\r\n");Map<String,String> headers=new TreeMap<>();for(int i=1;i<lines.length;i++){int colon=lines[i].indexOf(':');if(colon>0)headers.put(lines[i].substring(0,colon).toLowerCase(Locale.ROOT),lines[i].substring(colon+1).trim());}
                int length=Integer.parseInt(headers.getOrDefault("content-length","0"));if(length<0||length>16384)throw new IOException("synthetic request limit");byte[] body=new byte[length];for(int offset=0;offset<length;){int n=input.read(body,offset,length-offset);if(n<0)throw new EOFException();offset+=n;}
                Exchange exchange=exchanges.poll(2,TimeUnit.SECONDS);if(exchange==null)throw new IOException("synthetic missing response");served.add(exchange);String[] start=lines[0].split(" ");exchange.method=start[0];exchange.path=start[1];exchange.headers=Collections.unmodifiableMap(headers);exchange.body=body;requests.incrementAndGet();exchange.received.countDown();
                if(exchange.held){Thread sender=new Thread(()->sendHeld(socket,exchange),"p05b-peer-late-sender");sender.setDaemon(true);workers.add(sender);sender.start();}
                else{socket.getOutputStream().write(exchange.response);socket.getOutputStream().flush();exchange.writeSucceeded=true;exchange.written.countDown();exchange.attempted.countDown();if(exchange.closeAfterWrite)return;}
            }
        }catch(SocketException|EOFException expected){for(Exchange old:served)old.eof.countDown();}
        catch(Exception e){if(!closed)failure.compareAndSet(null,e);}
        finally{try{socket.close();}catch(IOException ignored){}sockets.remove(socket);}
    }
    private void sendHeld(Socket socket,Exchange exchange){
        try{if(!exchange.allow.await(10,TimeUnit.SECONDS))throw new IOException("synthetic release timeout");socket.getOutputStream().write(exchange.response);socket.getOutputStream().flush();exchange.writeSucceeded=true;exchange.written.countDown();}
        catch(SocketException expected){}catch(Exception failure){if(!closed)this.failure.compareAndSet(null,failure);}
        finally{exchange.attempted.countDown();}
    }
    public void close() throws Exception {closed=true;for(Exchange exchange:plans)exchange.allow.countDown();server.close();for(Socket socket:sockets)socket.close();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);accepting.join(2000);for(Thread worker:workers){long left=deadline-System.nanoTime();if(left>0)worker.join(Math.max(1,TimeUnit.NANOSECONDS.toMillis(left)));}}
}
