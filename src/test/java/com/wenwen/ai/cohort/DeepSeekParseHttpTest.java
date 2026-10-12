package com.wenwen.ai.cohort;
import com.fasterxml.jackson.databind.JsonNode;
import com.wenwen.ai.scope.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
/** 当前官方协议经production identity/parse/apply/SQL的受控接收。 */
class DeepSeekParseHttpTest extends AiGatewayHttpFixture {
    final MockHttpSession session=typedSession();
    static MockHttpSession typedSession(){MockHttpSession session=new MockHttpSession();session.setAttribute(AuthenticatedDoctorSession.ATTRIBUTE,new AuthenticatedDoctorSession(new TrustedDoctor(101)));return session;}
    @Override void buildContext(boolean trusted){super.buildContext(false);}
    @Override JsonNode route(String path,String body) throws Exception {
        MvcResult response=http.perform(post("/api/ra/ai/"+path).session(session).contentType("application/json").content(body)).andReturn();
        assertEquals(200,response.getResponse().getStatus(),response.getResponse().getContentAsString());JsonNode envelope=json.readTree(response.getResponse().getContentAsByteArray());assertTrue(envelope.path("success").asBoolean());return envelope.path("data");
    }
    @Override MvcResult request(String body) throws Exception {return http.perform(post("/api/ra/ai/cohort").session(session).contentType("application/json").content(body)).andReturn();}
    String cacheOuter(String inner) throws Exception {return outer(inner).replace("\"total_tokens\":2","\"total_tokens\":2,\"prompt_cache_hit_tokens\":0,\"prompt_cache_miss_tokens\":1");}
    Set<String> keys(JsonNode node){Set<String> keys=new TreeSet<>();node.fieldNames().forEachRemaining(keys::add);return keys;}
    @Test void officialCacheUsageReachesParseApplyAndAudit() throws Exception {
        String inner="{\"filters\":{\"sex\":\"F\",\"age\":\"35-45\"},\"matched\":[{\"field\":\"sex\",\"value\":\"F\"},{\"field\":\"age\",\"value\":\"35-45\"}],\"unrecognized\":[],\"status\":\"OK\",\"notice\":\"已生成候选条件，尚未应用。\"}";
        AiGatewayLoopbackPeer.Exchange exchange=peer.json(cacheOuter(inner));
        JsonNode parsed=parse("女性35-45岁");
        assertEquals("AI",parsed.path("engine").asText());assertTrue(parsed.path("fallbackReason").isNull());
        assertEquals(json.readTree("{\"sex\":\"F\",\"age\":\"35-45\"}"),parsed.path("candidateFilters"));
        assertEquals("已生成候选条件，尚未应用。",parsed.path("notice").asText());
        assertTrue(exchange.received.await(1,TimeUnit.SECONDS));assertTrue(exchange.eof.await(1,TimeUnit.SECONDS));assertEquals(1,peer.requests.get());assertNull(peer.failure.get());
        assertEquals(new TreeSet<>(Arrays.asList("accept","accept-encoding","authorization","connection","content-length","content-type","host","user-agent")),exchange.headers.keySet());assertTrue(exchange.body.length<=16384);assertEquals(Integer.toString(exchange.body.length),exchange.headers.get("content-length"));assertEquals("POST",exchange.method);assertEquals("/chat/completions",exchange.path);
        assertEquals("application/json; charset=utf-8",exchange.headers.get("content-type"));assertEquals("application/json",exchange.headers.get("accept"));assertEquals("Bearer p05b-loopback-key",exchange.headers.get("authorization"));assertEquals("ra-ai-gateway/1",exchange.headers.get("user-agent"));assertEquals("gzip",exchange.headers.get("accept-encoding"));
        JsonNode wire=json.readTree(exchange.body);assertEquals(new TreeSet<>(Arrays.asList("model","messages","stream","temperature","max_tokens","response_format","thinking")),keys(wire));assertEquals("p05b-synthetic-model",wire.path("model").asText());assertFalse(wire.path("stream").asBoolean());assertEquals(0,wire.path("temperature").asInt());assertEquals(1024,wire.path("max_tokens").asInt());assertEquals(json.readTree("{\"type\":\"json_object\"}"),wire.path("response_format"));assertEquals(json.readTree("{\"type\":\"disabled\"}"),wire.path("thinking"));assertEquals(2,wire.path("messages").size());for(JsonNode message:wire.path("messages"))assertEquals(new TreeSet<>(Arrays.asList("role","content")),keys(message));assertEquals("system",wire.path("messages").get(0).path("role").asText());assertEquals("user",wire.path("messages").get(1).path("role").asText());assertEquals("女性35-45岁",wire.path("messages").get(1).path("content").asText());
        byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(wire.path("messages").get(0).path("content").asText().getBytes(java.nio.charset.StandardCharsets.UTF_8));StringBuilder hash=new StringBuilder();for(byte b:digest)hash.append(String.format("%02x",b));assertEquals("e73fb7fedb281e100d4ed6599e5c1030813eb511ee9f7f991098f4bff7100b6a",hash.toString());
        assertEquals(0,observed.selects);assertEquals(0,observed.inserts);auditExited();assertEquals(1,auditCount());Map<String,String> event=auditEvent(auditObserved.lastEventId);assertEquals("AI",event.get("engine"));assertNull(event.get("fallback_reason"));assertEquals("p05b-synthetic-model",event.get("model_version"));assertEquals("dev-parse-ai-prompt-v01",event.get("prompt_version"));assertEquals(parsed.path("meta").path("durationMs").asText(),event.get("duration_ms"));assertFalse(event.toString().contains("35-45岁"));assertFalse(event.toString().contains("p05b-loopback-key"));assertFalse(event.toString().contains("已生成"));
        JsonNode applied=apply(parsed,"{\"sex\":\"M\",\"tx\":\"TNFi\",\"act\":\"high\",\"at\":\"6m\",\"ids\":[\"1\",\"2\",\"7\"]}",null);assertTrue(applied.path("applied").asBoolean());assertTrue(applied.path("filters").path("tx").isNull());assertTrue(applied.path("filters").path("act").isNull());assertEquals("now",applied.path("filters").path("at").asText());assertEquals(json.readTree("[\"1\",\"2\",\"7\"]"),applied.path("filters").path("ids"));
        JsonNode cohort=success("{\"filters\":"+applied.path("filters")+"}");assertEquals(Collections.singletonList("1"),ids(cohort));assertEquals(1,cohort.path("n").asInt());assertEquals(6,cohort.path("studyTotal").asInt());assertEquals(3,cohort.path("submittedUniqueIdsN").asInt());assertEquals(2,cohort.path("effectiveIdsN").asInt());assertEquals(1,cohort.path("activity").path("evalN").asInt());assertEquals(1.0,cohort.path("stats").path("targetRate").asDouble());assertEquals(1,cohort.path("stats").path("evaluable").asInt());assertEquals(2,auditCount());assertEquals(1,peer.requests.get());
        networkExited();auditExited();
    }
    @Test void explicitSystemProxyUsesOnlySelectedPeerAndOneShotBody() throws Exception {
        java.net.ProxySelector original=java.net.ProxySelector.getDefault();
        try(AiGatewayLoopbackPeer proxy=new AiGatewayLoopbackPeer()){
            java.net.URI uri=java.net.URI.create(proxy.endpoint());
            java.net.ProxySelector.setDefault(new java.net.ProxySelector(){
                public List<java.net.Proxy> select(java.net.URI target){return Collections.singletonList(new java.net.Proxy(java.net.Proxy.Type.HTTP,new java.net.InetSocketAddress("127.0.0.1",uri.getPort())));}
                public void connectFailed(java.net.URI target,java.net.SocketAddress address,java.io.IOException failure){fail("synthetic proxy connect failed");}
            });
            configure("ra.ai.gateway.proxy-mode","system");
            AiGatewayLoopbackPeer.Exchange target=peer.json(outer(female())),selected=proxy.json(outer(female()));
            JsonNode parsed=parse("女性");
            assertEquals(0,peer.requests.get(),"system不能直连target");assertEquals(1,proxy.requests.get());
            assertEquals("AI",parsed.path("engine").asText());assertTrue(parsed.path("fallbackReason").isNull());
            assertTrue(selected.received.await(1,TimeUnit.SECONDS));assertTrue(selected.eof.await(1,TimeUnit.SECONDS));
            assertEquals("POST",selected.method);assertEquals(peer.endpoint(),selected.path);
            assertEquals(Integer.toString(selected.body.length),selected.headers.get("content-length"));
            assertEquals("女性",json.readTree(selected.body).path("messages").get(1).path("content").asText());
            assertEquals(2,json.readTree(selected.body).path("messages").size());assertNull(proxy.failure.get());assertNull(peer.failure.get());
            networkExited();auditExited();assertEquals(1,auditCount());peer.discardUnused(target);
            com.wenwen.ai.parse.AiParseGateway gateway=context.getBean(com.wenwen.ai.parse.AiParseGateway.class);gateway.close();assertTrue(networkExecutor(gateway).isTerminated());
        }finally{java.net.ProxySelector.setDefault(original);}
    }
    @Test void defaultDirectIgnoresSystemSelectorAndInvalidModeStaysLocal() throws Exception {
        java.net.ProxySelector original=java.net.ProxySelector.getDefault();
        try(AiGatewayLoopbackPeer proxy=new AiGatewayLoopbackPeer()){
            java.net.URI uri=java.net.URI.create(proxy.endpoint());
            java.net.ProxySelector.setDefault(new java.net.ProxySelector(){
                public List<java.net.Proxy> select(java.net.URI target){return Collections.singletonList(new java.net.Proxy(java.net.Proxy.Type.HTTP,new java.net.InetSocketAddress("127.0.0.1",uri.getPort())));}
                public void connectFailed(java.net.URI target,java.net.SocketAddress address,java.io.IOException failure){fail("unexpected proxy attempt");}
            });
            configure("ra.ai.gateway.enabled","true");
            AiGatewayLoopbackPeer.Exchange target=peer.json(outer(female())),unused=proxy.json(outer(female()));
            assertEquals("AI",parse("女性").path("engine").asText());assertEquals(1,peer.requests.get());assertEquals(0,proxy.requests.get());
            assertEquals("/chat/completions",target.path);assertTrue(target.eof.await(1,TimeUnit.SECONDS));networkExited();auditExited();proxy.discardUnused(unused);
            for(String mode:new String[]{"SYSTEM","","http://127.0.0.1:1"," direct"}){
                configure("ra.ai.gateway.proxy-mode",mode);int before=peer.requests.get();
                JsonNode invalid=parse("女性");assertEquals("AI_CONFIG_UNAVAILABLE",invalid.path("fallbackReason").asText());assertEquals("RULE",invalid.path("engine").asText());assertEquals(before,peer.requests.get());assertEquals(0,proxy.requests.get());networkExited();auditExited();
            }
            java.net.ProxySelector.setDefault(null);configure("ra.ai.gateway.proxy-mode","system");
            AiGatewayLoopbackPeer.Exchange direct=peer.json(outer(female()));assertEquals("AI",parse("女性").path("engine").asText());assertTrue(direct.eof.await(1,TimeUnit.SECONDS));networkExited();auditExited();
        }finally{java.net.ProxySelector.setDefault(original);}
    }
    @Test void cacheUsageRejectsInvalidTypesDuplicatesAndUnknownKeys() throws Exception {
        String valid=cacheOuter(female());
        List<String> invalid=new ArrayList<>();
        for(String field:new String[]{"prompt_cache_hit_tokens","prompt_cache_miss_tokens"}){
            String value="prompt_cache_hit_tokens".equals(field)?"0":"1";
            for(String bad:new String[]{"-1","1.5","\"1\"","null","true","10000000000000000"})invalid.add(valid.replace("\""+field+"\":"+value,"\""+field+"\":"+bad));
            invalid.add(valid.replace("\""+field+"\":"+value,"\""+field+"\":"+value+",\""+field+"\":"+value));
        }
        invalid.add(valid.replace("\"total_tokens\":2","\"total_tokens\":2,\"future_cache_tokens\":0"));
        for(String body:invalid){int before=peer.requests.get();AiGatewayLoopbackPeer.Exchange exchange=peer.json(body);JsonNode parsed=parse("女性");assertEquals("RULE",parsed.path("engine").asText());assertEquals("AI_INVALID_RESPONSE",parsed.path("fallbackReason").asText());assertEquals(before+1,peer.requests.get());assertTrue(exchange.eof.await(1,TimeUnit.SECONDS));networkExited();auditExited();}
        // Both keys are independently optional, while absent usage retains the old contract.
        for(String body:new String[]{outer(female()),valid.replace(",\"prompt_cache_hit_tokens\":0",""),valid.replace(",\"prompt_cache_miss_tokens\":1","")}){
            AiGatewayLoopbackPeer.Exchange exchange=peer.json(body);assertEquals("AI",parse("女性").path("engine").asText());assertTrue(exchange.eof.await(1,TimeUnit.SECONDS));networkExited();auditExited();
        }
        assertEquals(invalid.size()+3,peer.requests.get());assertEquals(peer.requests.get(),auditCount());assertNull(peer.failure.get());
    }
    @Test void closedPermissionGatesDoNotReadKeyOrInvokeSelector() {
        for(String[] flags:new String[][]{{"false","true"},{"true","false"},{"TRUE","true"},{"true","TRUE"}}){
            org.springframework.mock.env.MockEnvironment environment=new org.springframework.mock.env.MockEnvironment(){
                @Override public String getProperty(String key){if("ra.ai.gateway.api-key".equals(key)||"RA_AI_API_KEY".equals(key))fail("closed gate read key");return super.getProperty(key);}
            };
            environment.setProperty("ra.ai.gateway.enabled",flags[0]);environment.setProperty("ra.ai.gateway.outbound-approved",flags[1]);environment.setProperty("ra.ai.gateway.proxy-mode","system");
            com.wenwen.ai.parse.AiParseGateway gateway=new com.wenwen.ai.parse.AiParseGateway(new com.wenwen.ai.parse.AiGatewaySettings(environment));gateway.close();
        }
    }
    String female(){return "{\"filters\":{\"sex\":\"F\"},\"matched\":[{\"field\":\"sex\",\"value\":\"F\"}],\"unrecognized\":[],\"status\":\"OK\",\"notice\":\"已生成候选条件，尚未应用。\"}";}
}
