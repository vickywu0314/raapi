package com.wenwen.ai.cohort;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static com.wenwen.vo.AiCohortVo.object;
abstract class AiGatewayHttpFixture extends ParseCohortHttpFixture {
    AiGatewayLoopbackPeer peer;
    final Map<String,Object> gatewayProperties=new LinkedHashMap<>();
    @Override void beforeRefresh(){super.beforeRefresh();try{if(peer==null)peer=new AiGatewayLoopbackPeer();}catch(Exception e){throw new IllegalStateException("synthetic peer unavailable");}
        Map<String,Object> properties=new LinkedHashMap<>(object("ra.ai.gateway.enabled","true","ra.ai.gateway.outbound-approved","true","ra.ai.gateway.synthetic-loopback","true","ra.ai.gateway.endpoint",peer.endpoint(),"ra.ai.gateway.model","p05b-synthetic-model","ra.ai.gateway.api-key","p05b-loopback-key"));properties.putAll(gatewayProperties);
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("p05b-synthetic",properties));
    }
    void configure(String key,String value){context.close();gatewayProperties.clear();gatewayProperties.put(key,value);buildContext(true);auditObserved=context.getBean(ParseAuditObservedDataSource.class);}
    @AfterEach void closePeer() throws Exception {if(context!=null)context.close();if(peer!=null)peer.close();}
    String outer(String inner) throws Exception {return json.writeValueAsString(object("id","p05b-synthetic-response-1","object","chat.completion","created",0,"model","p05b-synthetic-model","choices",Collections.singletonList(object("index",0,"finish_reason","stop","message",object("role","assistant","content",inner),"logprobs",null)),"usage",object("prompt_tokens",1,"completion_tokens",1,"total_tokens",2)));}
    JsonNode route(String path,String body) throws Exception {MvcResult response=http.perform(post("/api/ra/ai/"+path).contentType("application/json").content(body)).andReturn();assertEquals(200,response.getResponse().getStatus(),response.getResponse().getContentAsString());JsonNode envelope=json.readTree(response.getResponse().getContentAsByteArray());assertTrue(envelope.path("success").asBoolean());return envelope.path("data");}
    JsonNode parse(String question) throws Exception {return route("parse",json.writeValueAsString(Collections.singletonMap("question",question)));}
    JsonNode apply(JsonNode parsed,String current,Boolean confirm) throws Exception {return route("applyParse","{\"currentFilters\":"+current+",\"candidateFilters\":"+parsed.path("candidateFilters")+",\"parseStatus\":\""+parsed.path("parseStatus").asText()+"\",\"questionRef\":\""+parsed.path("questionRef").asText()+"\""+(confirm==null?"":",\"confirmPartial\":"+confirm)+"}");}
    long auditCount() throws Exception {try(Connection c=raw.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT COUNT(*) FROM ra_ai_parse_audit")){r.next();return r.getLong(1);}}
    Map<String,String> auditEvent(String id) throws Exception {try(Connection c=raw.getConnection();PreparedStatement s=c.prepareStatement("SELECT * FROM ra_ai_parse_audit WHERE event_id=?")){s.setString(1,id);try(ResultSet rows=s.executeQuery()){assertTrue(rows.next());Map<String,String> out=new TreeMap<>();for(int i=1;i<=rows.getMetaData().getColumnCount();i++)out.put(rows.getMetaData().getColumnName(i),rows.getString(i));assertFalse(rows.next());return out;}}}
    boolean auditExists(String id) throws Exception {try(Connection c=raw.getConnection();PreparedStatement s=c.prepareStatement("SELECT COUNT(*) FROM ra_ai_parse_audit WHERE event_id=?")){s.setString(1,id);try(ResultSet rows=s.executeQuery()){rows.next();return rows.getInt(1)!=0;}}}
    String sourceDigest() throws Exception {java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");for(String table:new String[]{"patient_basic_info","patient_relation_doctor","patient_follow_up_history","patient_comorbidity"})try(Connection c=raw.getConnection();Statement s=c.createStatement();ResultSet rows=s.executeQuery("SELECT * FROM "+table+" ORDER BY 1,2")){while(rows.next())for(int i=1;i<=rows.getMetaData().getColumnCount();i++){String value=rows.getString(i);digest.update((value==null?"<null>":value).getBytes(java.nio.charset.StandardCharsets.UTF_8));digest.update((byte)0);}}return Base64.getEncoder().encodeToString(digest.digest());}
    final class Caller {
        final java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.atomic.AtomicReference<Throwable> error=new java.util.concurrent.atomic.AtomicReference<>();
        final Thread thread;
        volatile MvcResult response;volatile boolean interrupted;volatile long started,finished;
        Caller(String question,boolean preInterrupted) throws Exception {
            String body=json.writeValueAsString(Collections.singletonMap("question",question));
            thread=new Thread(()->{started=System.nanoTime();try{if(preInterrupted)Thread.currentThread().interrupt();response=http.perform(post("/api/ra/ai/parse").contentType("application/json").content(body)).andReturn();}catch(Throwable failure){error.set(failure);}finally{interrupted=Thread.currentThread().isInterrupted();finished=System.nanoTime();done.countDown();}},"p05b-real-servlet-caller");thread.setDaemon(true);thread.start();
        }
        JsonNode data() throws Exception {assertNull(error.get());assertNotNull(response);return json.readTree(response.getResponse().getContentAsByteArray()).path("data");}
        long millis(){return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(finished-started);}
        void fallbackCleanup() throws Exception {if(thread.isAlive()){thread.interrupt();peer.close();thread.join(2000);}}
    }
    void networkExited() throws Exception {networkExited(context.getBean(com.wenwen.ai.parse.AiParseGateway.class));}
    void networkExited(com.wenwen.ai.parse.AiParseGateway gateway) throws Exception {
        java.lang.reflect.Field field=gateway.getClass().getDeclaredField("executor");field.setAccessible(true);java.util.concurrent.ThreadPoolExecutor executor=(java.util.concurrent.ThreadPoolExecutor)field.get(gateway);
        java.lang.reflect.Field clientField=gateway.getClass().getDeclaredField("client");clientField.setAccessible(true);okhttp3.OkHttpClient client=(okhttp3.OkHttpClient)clientField.get(gateway);
        long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(1);while((executor.getActiveCount()!=0||client.connectionPool().connectionCount()!=0)&&System.nanoTime()<end)Thread.yield();
        assertEquals(0,executor.getActiveCount(),"实际runnable必须退出");assertEquals(0,client.connectionPool().connectionCount(),"真实私有pool连接须退出");
        java.lang.reflect.Field activeField=gateway.getClass().getDeclaredField("active"),lockField=gateway.getClass().getDeclaredField("admission");activeField.setAccessible(true);lockField.setAccessible(true);synchronized(lockField.get(gateway)){assertTrue(((Set<?>)activeField.get(gateway)).isEmpty(),"本次真实Call登记已结清");}
    }
    java.util.concurrent.ThreadPoolExecutor networkExecutor(com.wenwen.ai.parse.AiParseGateway gateway) throws Exception {java.lang.reflect.Field field=gateway.getClass().getDeclaredField("executor");field.setAccessible(true);return (java.util.concurrent.ThreadPoolExecutor)field.get(gateway);}
    void auditExited(){assertEquals(0,auditObserved.active);assertEquals(auditObserved.borrowed,auditObserved.returned);assertEquals(0,auditObserved.statements);assertEquals(0,auditObserved.results);assertEquals(0,observed.active.get());assertEquals(observed.borrowed,observed.returned);}
}
