package com.wenwen.ai.cohort;
import com.fasterxml.jackson.databind.JsonNode;
import com.wenwen.ai.scope.*;
import org.junit.jupiter.api.*;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import java.util.*;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static com.wenwen.vo.AiCohortVo.object;
/** 显式选择的合成真实模型接收；默认*Test与CI不发现，不输出真实wire/key。 */
class DeepSeekParseReceptionIT extends ParseCohortHttpFixture {
    final MockHttpSession session=DeepSeekParseHttpTest.typedSession();
    static ch.qos.logback.classic.Level previousLevel;
    @BeforeAll static void requireSyntheticApproval(){
        assertEquals("true",System.getenv("RA_TEST_DEEPSEEK_SYNTHETIC"),"explicit synthetic reception approval required");
        assertEquals("deepseek-flash",System.getenv("DEEPSEEK_MODEL"),"specified server model required");
        assertNotNull(System.getenv("RA_AI_API_KEY"),"private server key required");
        ch.qos.logback.classic.Logger root=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        previousLevel=root.getLevel();root.setLevel(ch.qos.logback.classic.Level.WARN);
    }
    @AfterAll static void restoreLogging(){if(previousLevel!=null)((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(previousLevel);}
    @Override void buildContext(boolean trusted){super.buildContext(false);}
    @Override void beforeRefresh(){
        // Deliberately omit super: it registers PrimarySyntheticDictionary. No loopback gateway.
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("explicit-deepseek-synthetic",object("ra.ai.gateway.enabled","true","ra.ai.gateway.outbound-approved","true","ra.ai.gateway.synthetic-loopback","false","ra.ai.gateway.endpoint","https://api.deepseek.com/chat/completions","ra.ai.gateway.model","deepseek-flash","ra.ai.gateway.proxy-mode","system")));
    }
    JsonNode route(String path,String body) throws Exception {
        MvcResult response=http.perform(post("/api/ra/ai/"+path).session(session).contentType("application/json").content(body)).andReturn();
        assertEquals(200,response.getResponse().getStatus());JsonNode envelope=json.readTree(response.getResponse().getContentAsByteArray());assertTrue(envelope.path("success").asBoolean());return envelope.path("data");
    }
    @Override MvcResult request(String body) throws Exception {return http.perform(post("/api/ra/ai/cohort").session(session).contentType("application/json").content(body)).andReturn();}
    JsonNode oneAttempt(String question,String category) throws Exception {
        assertEquals(0,observed.selects);assertEquals(0,observed.scopeReads);assertEquals(0,observed.inserts);
        long started=System.nanoTime();String timestamp=java.time.Instant.now().toString();
        JsonNode parsed=route("parse",json.writeValueAsString(Collections.singletonMap("question",question)));
        System.out.println("P04a single_attempt=1 timestamp="+timestamp+" category="+category+" model=deepseek-flash engine="+parsed.path("engine").asText()+" fallback="+parsed.path("fallbackReason").asText()+" elapsed_ms="+java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started));
        networkExited();auditExited();assertEquals(0,observed.selects);assertEquals(0,observed.scopeReads);assertEquals(0,observed.inserts);
        assertEquals("AI",parsed.path("engine").asText());assertTrue(parsed.path("fallbackReason").isNull());assertEquals("deepseek-flash",parsed.path("meta").path("modelVersion").asText());
        assertEquals(1,auditCount());Map<String,String> event=auditEvent(auditObserved.lastEventId);assertEquals("AI",event.get("engine"));assertNull(event.get("fallback_reason"));assertEquals("deepseek-flash",event.get("model_version"));assertFalse(event.toString().contains(question));assertFalse(event.toString().contains(System.getenv("RA_AI_API_KEY")));
        return parsed;
    }
    JsonNode apply(JsonNode parsed,String current,Boolean confirm) throws Exception {return route("applyParse","{\"currentFilters\":"+current+",\"candidateFilters\":"+parsed.path("candidateFilters")+",\"parseStatus\":\""+parsed.path("parseStatus").asText()+"\",\"questionRef\":\""+parsed.path("questionRef").asText()+"\""+(confirm==null?"":",\"confirmPartial\":"+confirm)+"}");}
    @Test void realOkCandidateAppliesWithinTypedScope() throws Exception {
        String original=sourceDigest();JsonNode parsed=oneAttempt("女性35-45岁","OK_AGE_SEX");
        assertEquals("OK",parsed.path("parseStatus").asText());assertEquals(json.readTree("{\"sex\":\"F\",\"age\":\"35-45\"}"),parsed.path("candidateFilters"));
        JsonNode applied=apply(parsed,"{\"sex\":\"M\",\"tx\":\"TNFi\",\"act\":\"high\",\"at\":\"6m\",\"ids\":[\"1\",\"2\",\"7\"]}",null);
        assertTrue(applied.path("applied").asBoolean());assertTrue(applied.path("filters").path("tx").isNull());assertTrue(applied.path("filters").path("act").isNull());assertEquals("now",applied.path("filters").path("at").asText());assertEquals(json.readTree("[\"1\",\"2\",\"7\"]"),applied.path("filters").path("ids"));
        JsonNode cohort=success("{\"filters\":"+applied.path("filters")+"}");assertEquals(Collections.singletonList("1"),ids(cohort));assertEquals(1,cohort.path("n").asInt());assertEquals(6,cohort.path("studyTotal").asInt());assertEquals(3,cohort.path("submittedUniqueIdsN").asInt());assertEquals(2,cohort.path("effectiveIdsN").asInt());assertEquals(2,auditCount());assertEquals(original,sourceDigest());networkExited();auditExited();
    }
    @Test void realPartialCandidateRequiresExplicitApply() throws Exception {
        String original=sourceDigest();JsonNode parsed=oneAttempt("女性且吸烟","PARTIAL_SMOKING");
        assertEquals("PARTIAL",parsed.path("parseStatus").asText());assertEquals("REQUIRE_CONFIRMATION",parsed.path("applyMode").asText());assertEquals(json.readTree("{\"sex\":\"F\"}"),parsed.path("candidateFilters"));assertEquals(json.readTree("[{\"reason\":\"UNMAPPED_TEXT\",\"count\":2}]"),parsed.path("unrecognized"));
        String current="{\"sex\":\"M\",\"tx\":\"TNFi\",\"ids\":[\"2\"]}";
        JsonNode missing=apply(parsed,current,null),no=apply(parsed,current,false);assertFalse(missing.path("applied").asBoolean());assertFalse(no.path("applied").asBoolean());assertEquals(missing.path("filters"),no.path("filters"));assertEquals("M",no.path("filters").path("sex").asText());assertEquals("TNFi",no.path("filters").path("tx").asText());assertEquals(json.readTree("[\"2\"]"),no.path("filters").path("ids"));
        JsonNode yes=apply(parsed,current,true);assertTrue(yes.path("applied").asBoolean());assertEquals("F",yes.path("filters").path("sex").asText());assertTrue(yes.path("filters").path("tx").isNull());assertEquals(json.readTree("[\"2\"]"),yes.path("filters").path("ids"));
        JsonNode cohort=success("{\"filters\":"+yes.path("filters")+"}");assertEquals(0,cohort.path("n").asInt());assertEquals(Collections.emptyList(),ids(cohort));assertEquals(4,auditCount());assertEquals(original,sourceDigest());networkExited();auditExited();
    }
    long auditCount() throws Exception {try(Connection c=raw.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT COUNT(*) FROM ra_ai_parse_audit")){r.next();return r.getLong(1);}}
    Map<String,String> auditEvent(String id) throws Exception {try(Connection c=raw.getConnection();PreparedStatement s=c.prepareStatement("SELECT * FROM ra_ai_parse_audit WHERE event_id=?")){s.setString(1,id);try(ResultSet rows=s.executeQuery()){assertTrue(rows.next());Map<String,String> out=new TreeMap<>();for(int i=1;i<=rows.getMetaData().getColumnCount();i++)out.put(rows.getMetaData().getColumnName(i),rows.getString(i));assertFalse(rows.next());return out;}}}
    boolean auditExists(String id) throws Exception {try(Connection c=raw.getConnection();PreparedStatement s=c.prepareStatement("SELECT COUNT(*) FROM ra_ai_parse_audit WHERE event_id=?")){s.setString(1,id);try(ResultSet rows=s.executeQuery()){rows.next();return rows.getInt(1)!=0;}}}
    String sourceDigest() throws Exception {java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");for(String table:new String[]{"patient_basic_info","patient_relation_doctor","patient_follow_up_history","patient_comorbidity"})try(Connection c=raw.getConnection();Statement s=c.createStatement();ResultSet rows=s.executeQuery("SELECT * FROM "+table+" ORDER BY 1,2")){while(rows.next())for(int i=1;i<=rows.getMetaData().getColumnCount();i++){String value=rows.getString(i);digest.update((value==null?"<null>":value).getBytes(java.nio.charset.StandardCharsets.UTF_8));digest.update((byte)0);}}return Base64.getEncoder().encodeToString(digest.digest());}
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
