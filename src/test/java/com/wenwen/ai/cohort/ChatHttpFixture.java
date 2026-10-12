package com.wenwen.ai.cohort;

import com.fasterxml.jackson.databind.JsonNode;
import com.wenwen.ai.scope.*;
import java.io.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static com.wenwen.vo.AiCohortVo.object;

/** 首轮只注册已存在组件；真实typed session、独立SQL和服务器peer。 */
abstract class ChatHttpFixture extends DescriptiveHttpFixture {
    AiGatewayLoopbackPeer peer;
    MockHttpSession session;
    ChatObservedDataSource auditObserved;
    boolean realModel;
    final Map<String,Object> chatProperties=new LinkedHashMap<>();
    @Override void buildContext(boolean ignored) { super.buildContext(false); session=new MockHttpSession();session.setAttribute(AuthenticatedDoctorSession.ATTRIBUTE,new AuthenticatedDoctorSession(new TrustedDoctor(101)));auditObserved=context.getBean(ChatObservedDataSource.class); }
    @Override void beforeRefresh() {
        try {
            if(!realModel&&peer==null)peer=new AiGatewayLoopbackPeer();
            Map<String,Object> p=new LinkedHashMap<>(object("ra.ai.chat.enabled","true","ra.ai.chat.aggregate-outbound-approved","true"));
            if(!realModel)p.putAll(object("ra.ai.chat.synthetic-loopback","true","ra.ai.chat.endpoint",peer.endpoint(),"ra.ai.chat.model","p05a-synthetic-model","ra.ai.chat.api-key","p05a-loopback-key"));
            p.putAll(chatProperties);context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("synthetic-chat",p));
            try {context.register(Class.forName("com.wenwen.config.AiChatConfiguration"),AuditWiring.class);} catch(ClassNotFoundException baseline) { }
        } catch(Exception e) {throw new IllegalStateException("合成chat装配失败",e);}
    }
    @org.springframework.context.annotation.Configuration static class AuditWiring {
        @org.springframework.context.annotation.Bean static org.springframework.beans.factory.config.BeanFactoryPostProcessor auditPrimary(){return f->f.getBeanDefinition("observedDataSource").setPrimary(false);}
        @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary public ChatObservedDataSource chatObservedDataSource(ObservedDataSource observed){return new ChatObservedDataSource(observed);}

        @org.springframework.context.annotation.Bean public com.wenwen.mapper.AiChatAuditMapper chatAuditMapper(org.apache.ibatis.session.SqlSessionFactory factory)throws Exception {
            org.apache.ibatis.session.Configuration c=factory.getConfiguration();if(!c.hasStatement("com.wenwen.mapper.AiChatAuditMapper.insert"))try(InputStream in=ChatHttpFixture.class.getResourceAsStream("/mybatis/AiChatAuditMapper.xml")){new org.apache.ibatis.builder.xml.XMLMapperBuilder(in,c,"mybatis/AiChatAuditMapper.xml",c.getSqlFragments()).parse();}
            return new org.mybatis.spring.SqlSessionTemplate(factory).getMapper(com.wenwen.mapper.AiChatAuditMapper.class);
        }
    }
    @BeforeEach void setupChat() throws Exception {
        sixPatients();
        try(InputStream in=getClass().getResourceAsStream("/sql/20261012_ai_chat_audit.sql")) {
            if(in!=null){sql(new Scanner(in,"UTF-8").useDelimiter("\\A").next().trim());ownedTables.add("ra_ai_chat_audit");}
        }
    }
    void patientRows()throws Exception {
        sql("DELETE FROM patient_follow_up_history WHERE patient_basic_info_id=1");
        visit(1001,1,0,"{\"result\":{\"crpScore\":4.11,\"ytgjs\":4,\"zzgjs\":2},\"ztScoreByPatient\":70,\"hqaScore\":1.4,\"tjScore\":99}","2026-01-01",null,"{\"cfydb\":20}");
        sql("UPDATE patient_follow_up_history SET zlfa='"+drugs(drug("甲氨蝶呤","2025-12-01",null))+"' WHERE id=1001");
        rawVisit(1002,1,"2026-04-01","2.70","{\"cfydb\":10}",drugs(drug("synthetic-brand-A","2026-04-01",null)));
        rawVisit(1003,1,"2026-09-01","2.31","{\"cfydb\":6}",null);observed.reset();
    }
    @Override MvcResult request(String body) throws Exception {return http.perform(post("/api/ra/ai/chat").session(session).contentType("application/json").content(body)).andReturn();}
    String body(String kind,Map<String,Object> filters,String question) throws Exception {return json.writeValueAsString(object("scope",filters==null?object("kind",kind):object("kind",kind,"filters",filters),"question",question));}
    String all() throws Exception {return body("ALL",null,"当前范围的达标率是多少？");}
    Map<String,Object> filters() {return new LinkedHashMap<>(object("studyCode","RA","sex",null,"age",null,"sero",null,"tx",null,"at","now","act",null,"cm",null,"data",null,"ids",null));}
    String outer(String inner) throws Exception {return json.writeValueAsString(object("id","synthetic-response","object","chat.completion","created",0,"model","p05a-synthetic-model","choices",Collections.singletonList(object("index",0,"finish_reason","stop","message",object("role","assistant","content",inner),"logprobs",null)),"usage",object("prompt_tokens",1,"completion_tokens",1,"total_tokens",2)));}
    AiGatewayLoopbackPeer.Exchange answer(String... keys) throws Exception {return peer.json(outer(json.writeValueAsString(object("status","ANSWER","factKeys",Arrays.asList(keys),"reason",null))));}
    JsonNode fact(JsonNode data,String key) {for(JsonNode f:data.path("facts"))if(key.equals(f.path("key").asText()))return f;fail("未选中fact "+key);return null;}
    long count(String table) throws Exception {try(Connection c=raw.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT COUNT(*) FROM "+table)){r.next();return r.getLong(1);}}
    Map<String,String> audit(String ref) throws Exception {try(Connection c=raw.getConnection();PreparedStatement s=c.prepareStatement("SELECT * FROM ra_ai_chat_audit WHERE event_ref=?")){s.setString(1,ref);try(ResultSet r=s.executeQuery()){assertTrue(r.next());Map<String,String> out=new TreeMap<>();for(int i=1;i<=r.getMetaData().getColumnCount();i++)out.put(r.getMetaData().getColumnName(i),r.getString(i));assertFalse(r.next());return out;}}}
    String digest() throws Exception {java.security.MessageDigest hash=java.security.MessageDigest.getInstance("SHA-256");for(String table:new String[]{"patient_basic_info","patient_relation_doctor","patient_follow_up_history","patient_comorbidity"})try(Connection c=raw.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT * FROM "+table+" ORDER BY 1,2")){while(r.next())for(int i=1;i<=r.getMetaData().getColumnCount();i++){String value=r.getString(i);hash.update((value==null?"<null>":value).getBytes(java.nio.charset.StandardCharsets.UTF_8));hash.update((byte)0);}}return Base64.getEncoder().encodeToString(hash.digest());}
    final class Caller {
        final java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);final Thread thread;volatile MvcResult response;volatile Throwable error;volatile boolean interrupted;volatile long started,finished;
        Caller(String body,boolean preInterrupted){thread=new Thread(()->{started=System.nanoTime();try{if(preInterrupted)Thread.currentThread().interrupt();response=request(body);}catch(Throwable e){error=e;}finally{interrupted=Thread.currentThread().isInterrupted();finished=System.nanoTime();done.countDown();}},"p05a-servlet-caller");thread.setDaemon(true);thread.start();}
        JsonNode data()throws Exception{assertTrue(done.await(6,java.util.concurrent.TimeUnit.SECONDS));assertNull(error);assertNotNull(response);return json.readTree(response.getResponse().getContentAsByteArray()).path("data");}
        void fallbackCleanup()throws Exception{if(thread.isAlive()){thread.interrupt();peer.close();thread.join(2000);}}
    }
    void networkExited()throws Exception {
        Object gateway=context.getBean(com.wenwen.ai.chat.ChatGateway.class);java.lang.reflect.Field e=gateway.getClass().getDeclaredField("executor"),c=gateway.getClass().getDeclaredField("client"),a=gateway.getClass().getDeclaredField("active"),l=gateway.getClass().getDeclaredField("admission");for(java.lang.reflect.Field f:Arrays.asList(e,c,a,l))f.setAccessible(true);
        java.util.concurrent.ThreadPoolExecutor executor=(java.util.concurrent.ThreadPoolExecutor)e.get(gateway);okhttp3.OkHttpClient client=(okhttp3.OkHttpClient)c.get(gateway);long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(1);while((executor.getActiveCount()!=0||client.connectionPool().connectionCount()!=0)&&System.nanoTime()<end)Thread.yield();assertEquals(0,executor.getActiveCount(),"实际runnable退出");assertEquals(0,client.connectionPool().connectionCount(),"私有pool归零");synchronized(l.get(gateway)){assertTrue(((Set<?>)a.get(gateway)).isEmpty(),"真实Call登记结清");}
    }
    void auditExited(){assertEquals(0,auditObserved.active.get());assertEquals(auditObserved.borrowed.get(),auditObserved.returned.get());assertEquals(0,auditObserved.statements.get());assertEquals(0,auditObserved.results.get());sourceExited();}
    static final class ChatObservedDataSource extends org.springframework.jdbc.datasource.AbstractDataSource {
        final javax.sql.DataSource delegate;final java.util.concurrent.atomic.AtomicInteger active=new java.util.concurrent.atomic.AtomicInteger(),borrowed=new java.util.concurrent.atomic.AtomicInteger(),returned=new java.util.concurrent.atomic.AtomicInteger(),statements=new java.util.concurrent.atomic.AtomicInteger(),results=new java.util.concurrent.atomic.AtomicInteger();
        volatile Runnable afterInsert;volatile boolean failInsert;volatile String insertedRef;volatile int ownerSawInserted,inserts,commits,rollbacks;
        ChatObservedDataSource(javax.sql.DataSource d){delegate=d;}
        public Connection getConnection()throws SQLException{return observe(delegate.getConnection());}public Connection getConnection(String u,String p)throws SQLException{return observe(delegate.getConnection(u,p));}
        Connection observe(Connection real){active.incrementAndGet();borrowed.incrementAndGet();return (Connection)java.lang.reflect.Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},new java.lang.reflect.InvocationHandler(){boolean closed,touched;public Object invoke(Object proxy,java.lang.reflect.Method m,Object[] args)throws Throwable{try{
            if("close".equals(m.getName())){if(!closed){real.close();closed=true;active.decrementAndGet();returned.incrementAndGet();}return null;}
            if(touched&&"commit".equals(m.getName()))commits++;if(touched&&"rollback".equals(m.getName()))rollbacks++;
            if("prepareStatement".equals(m.getName())){boolean audit=((String)args[0]).contains("INSERT INTO ra_ai_chat_audit");touched|=audit;Object[] actual=args.clone();if(audit&&failInsert)actual[0]=((String)actual[0]).replace("ra_ai_chat_audit","missing_chat_audit");PreparedStatement st=(PreparedStatement)m.invoke(real,actual);statements.incrementAndGet();return java.lang.reflect.Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),new Class<?>[]{PreparedStatement.class},new java.lang.reflect.InvocationHandler(){boolean closed;Object resultProxy;public Object invoke(Object p,java.lang.reflect.Method method,Object[] a)throws Throwable{try{
                if("close".equals(method.getName())){if(!closed){st.close();closed=true;statements.decrementAndGet();}return null;}
                if(audit&&"setString".equals(method.getName())&&Integer.valueOf(1).equals(a[0]))insertedRef=(String)a[1];Object value=method.invoke(st,a);
                if(audit&&("execute".equals(method.getName())||"executeUpdate".equals(method.getName()))){inserts++;if(afterInsert!=null){try(PreparedStatement read=real.prepareStatement("SELECT COUNT(*) FROM ra_ai_chat_audit WHERE event_ref=?")){read.setString(1,insertedRef);try(ResultSet r=read.executeQuery()){r.next();ownerSawInserted=r.getInt(1);}}Runnable fault=afterInsert;afterInsert=null;fault.run();}}
                if(value instanceof ResultSet){if(resultProxy==null){ResultSet rs=(ResultSet)value;results.incrementAndGet();resultProxy=java.lang.reflect.Proxy.newProxyInstance(ResultSet.class.getClassLoader(),new Class<?>[]{ResultSet.class},new java.lang.reflect.InvocationHandler(){boolean closed;public Object invoke(Object p,java.lang.reflect.Method rm,Object[] ra)throws Throwable{try{if("close".equals(rm.getName())){if(!closed){rs.close();closed=true;results.decrementAndGet();}return null;}return rm.invoke(rs,ra);}catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}}});}return resultProxy;}return value;
            }catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}}});}return m.invoke(real,args);
        }catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}}});}
    }
    void sourceExited() {assertEquals(0,observed.active.get());assertEquals(observed.borrowed,observed.returned);}
    @AfterEach void closePeer() throws Exception {if(context!=null)context.close();if(peer!=null)peer.close();}
}
