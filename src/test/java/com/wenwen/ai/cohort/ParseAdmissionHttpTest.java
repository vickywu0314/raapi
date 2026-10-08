package com.wenwen.ai.cohort;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
/** 真实parse/apply、审计与有成功响应能力的合成HTTP peer。 */
class ParseAdmissionHttpTest extends AiGatewayHttpFixture {
    static final String GLOBAL="ra.ai.api.enabled",FILE="ra.export.xlsx.enabled";
    static final String APPLY="{\"currentFilters\":{},\"candidateFilters\":{\"sex\":\"F\",\"age\":\"35-45\"},\"parseStatus\":\"OK\",\"questionRef\":\"00000000-0000-4000-8000-000000000001\"}";
    void rebuild(boolean trusted){
        context.close();
        buildContext(trusted);
        auditObserved=context.getBean(ParseAuditObservedDataSource.class);
        observed.reset();
        auditObserved.reset();
    }
    AiGatewayLoopbackPeer.Exchange answer()throws Exception{
        return peer.json(outer("{\"filters\":{\"sex\":\"F\",\"age\":\"35-45\"},\"matched\":[{\"field\":\"sex\",\"value\":\"F\"},{\"field\":\"age\",\"value\":\"35-45\"}],\"unrecognized\":[],\"status\":\"OK\",\"notice\":\"已生成候选条件，尚未应用。\"}"));
    }
    @Test void globalClosureRejectsParseAndApplyWithoutConsumingSuccessfulPeer()throws Exception {
        gatewayProperties.put(GLOBAL,"false");
        rebuild(true);
        AiGatewayLoopbackPeer.Exchange ready=answer();
        for(String route:new String[]{"parse","applyParse"})for(String body:new String[]{route.equals("parse")?"{\"question\":\"女性35-45岁\"}":APPLY,"{"}){
            observed.reset();
            auditObserved.reset();
            MvcResult r=http.perform(post("/api/ra/ai/"+route).contentType("application/json").content(body)).andReturn();
            System.out.println("P07c parse admission HTTP="+r.getResponse().getStatus()+" peerRequests="+peer.requests.get()+" audits="+auditCount());
            assertEquals(503,r.getResponse().getStatus());
            failure(r,503,"SERVICE_UNAVAILABLE","队列功能暂不可用");
            zero();
            assertEquals(0,peer.requests.get());
            assertEquals(0,auditCount());
            assertEquals(1,ready.received.getCount());
            networkExited();
        }
        peer.discardUnused(ready);
        assertNull(peer.failure.get());
    }
    @Test void globalInvalidStringsAndAnonymousIdentityRejectBothParseRoutesBeforeIo()throws Exception {
        AiGatewayLoopbackPeer.Exchange ready=answer();
        for(String value:new String[]{"false","","TRUE","False"," true ","1","yes"}){
            gatewayProperties.put(GLOBAL,value);
            gatewayProperties.put(FILE,"false");
            rebuild(true);
            assertEquals(value,context.getEnvironment().getProperty(GLOBAL));
            for(String route:new String[]{"parse","applyParse"})for(String body:new String[]{route.equals("parse")?"{\"question\":\"女性35-45岁\"}":APPLY,"{"}){
                MvcResult r=http.perform(post("/api/ra/ai/"+route).contentType("application/json").content(body)).andReturn();
                failure(r,503,"SERVICE_UNAVAILABLE","队列功能暂不可用");
                zero();
            }
        }
        for(String global:new String[]{"false","true"}){
            gatewayProperties.put(GLOBAL,global);
            rebuild(false);
            for(String route:new String[]{"parse","applyParse"})for(String body:new String[]{route.equals("parse")?"{\"question\":\"女性35-45岁\"}":APPLY,"{"}){
                MvcResult r=http.perform(post("/api/ra/ai/"+route).contentType("application/json").content(body)).andReturn();
                failure(r,401,"UNAUTHENTICATED","请先登录");
                zero();
            }
        }
        assertEquals(0,peer.requests.get());
        assertEquals(1,ready.received.getCount());
        assertEquals(0,auditCount());
        networkExited();
        peer.discardUnused(ready);
        assertNull(peer.failure.get());
    }
    @Test void defaultTrueAndEveryFileClosedValueKeepOneAiAttemptAndExplicitApply()throws Exception {
        String[] fileValues={null,"true","false","","TRUE","False"," true ","1","yes"};
        int expected=0;
        for(String value:fileValues){
            if(value==null){
                gatewayProperties.remove(GLOBAL);
                gatewayProperties.remove(FILE);
            }
            else {
                gatewayProperties.put(GLOBAL,"true");
                gatewayProperties.put(FILE,value);
            }
            rebuild(true);
            AiGatewayLoopbackPeer.Exchange exchange=answer();
            JsonNode parsed=parse("女性35-45岁");
            assertEquals("AI",parsed.path("engine").asText());
            assertTrue(parsed.path("fallbackReason").isNull());
            assertEquals(json.readTree("{\"sex\":\"F\",\"age\":\"35-45\"}"),parsed.path("candidateFilters"));
            assertEquals("OK",parsed.path("parseStatus").asText());
            assertTrue(exchange.received.await(1,TimeUnit.SECONDS));
            assertTrue(exchange.eof.await(1,TimeUnit.SECONDS));
            assertEquals(++expected,peer.requests.get());
            networkExited();
            auditExited();
            assertEquals(expected*2-1,auditCount());
            Map<String,String> event=auditEvent(auditObserved.lastEventId);
            assertEquals("AI",event.get("engine"));
            assertEquals("dev-parse-rules-v02",event.get("rule_version"));
            assertEquals(parsed.path("questionRef").asText(),event.get("question_ref"));
            JsonNode applied=apply(parsed,"{\"ids\":[\"1\",\"2\",\"7\"],\"sex\":\"M\",\"tx\":\"TNFi\"}",null);
            assertTrue(applied.path("applied").asBoolean());
            assertEquals("F",applied.path("filters").path("sex").asText());
            assertEquals("35-45",applied.path("filters").path("age").asText());
            assertEquals(json.readTree("[\"1\",\"2\",\"7\"]"),applied.path("filters").path("ids"));
            assertTrue(applied.path("filters").path("tx").isNull());
            assertEquals(Collections.singletonList("1"),ids(success("{\"filters\":"+applied.path("filters")+"}")));
            assertEquals(expected*2,auditCount());
            assertEquals("APPLY",auditEvent(auditObserved.lastEventId).get("action"));
            assertEquals(expected,peer.requests.get());
            auditExited();
        }
        assertNull(peer.failure.get());
    }
    @Test void originalGatewayDisabledStillParsesAppliesAndAuditsWithoutPeerRequest()throws Exception {
        gatewayProperties.put(GLOBAL,"true");
        gatewayProperties.put(FILE,"false");
        gatewayProperties.put("ra.ai.gateway.enabled","false");
        rebuild(true);
        AiGatewayLoopbackPeer.Exchange ready=answer();
        JsonNode p=parse("女性35-45岁");
        assertEquals("RULE",p.path("engine").asText());
        assertEquals("AI_NOT_ENABLED",p.path("fallbackReason").asText());
        assertEquals("OK",p.path("parseStatus").asText());
        assertEquals(json.readTree("{\"sex\":\"F\",\"age\":\"35-45\"}"),p.path("candidateFilters"));
        assertEquals(1,auditCount());
        assertEquals("RULE",auditEvent(auditObserved.lastEventId).get("engine"));
        JsonNode applied=apply(p,"{\"ids\":[\"1\",\"2\",\"7\"]}",null);
        assertTrue(applied.path("applied").asBoolean());
        assertEquals(Collections.singletonList("1"),ids(success("{\"filters\":"+applied.path("filters")+"}")));
        assertEquals(2,auditCount());
        assertEquals("APPLY",auditEvent(auditObserved.lastEventId).get("action"));
        assertEquals(0,peer.requests.get());
        assertEquals(1,ready.received.getCount());
        networkExited();
        auditExited();
        peer.discardUnused(ready);
        assertNull(peer.failure.get());
    }
    void failure(MvcResult r,int status,String code,String message)throws Exception{
        assertEquals(status,r.getResponse().getStatus());
        JsonNode e=json.readTree(r.getResponse().getContentAsByteArray());
        assertFalse(e.path("success").asBoolean());
        assertEquals(code,e.path("code").asText());
        assertEquals(message,e.path("message").asText());
        assertTrue(e.path("data").isNull());
        assertNotNull(r.getResponse().getHeader("X-Trace-Id"));
    }
    void zero(){
        assertEquals(0,bodyRead.bytes);
        assertEquals(0,auditObserved.borrowed);
        assertEquals(0,observed.borrowed);
        auditExited();
    }
}
