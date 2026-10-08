package com.wenwen.ai.cohort;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class RuleParseCohortHttpTest extends ParseCohortHttpFixture {
    @Test void ruleCandidateExplicitlyAppliesToExistingCohort() throws Exception {
        MvcResult response=http.perform(post("/api/ra/ai/parse").contentType("application/json").content("{\"question\":\"女性35-45岁\"}")).andReturn();
        assertEquals(200,response.getResponse().getStatus(),response.getResponse().getContentAsString());
        JsonNode parsed=json.readTree(response.getResponse().getContentAsByteArray()).path("data");
        assertEquals("OK",parsed.path("parseStatus").asText());
        assertEquals(json.readTree("{\"sex\":\"F\",\"age\":\"35-45\"}"),parsed.path("candidateFilters"));
        String input="{\"currentFilters\":{\"sex\":\"M\",\"tx\":\"TNFi\",\"act\":\"high\",\"at\":\"6m\",\"ids\":[\"1\",\"2\",\"7\"]},\"candidateFilters\":"+parsed.path("candidateFilters")+",\"parseStatus\":\"OK\",\"questionRef\":\""+parsed.path("questionRef").asText()+"\"}";
        MvcResult appliedResponse=http.perform(post("/api/ra/ai/applyParse").contentType("application/json").content(input)).andReturn();
        assertEquals(200,appliedResponse.getResponse().getStatus());
        JsonNode applied=json.readTree(appliedResponse.getResponse().getContentAsByteArray()).path("data");
        assertTrue(applied.path("applied").asBoolean());
        JsonNode filters=applied.path("filters");
        assertTrue(filters.path("tx").isNull());assertTrue(filters.path("act").isNull());assertEquals("now",filters.path("at").asText());
        assertEquals(json.readTree("[\"1\",\"2\",\"7\"]"),filters.path("ids"));
        assertEquals(0,observed.selects);assertEquals(0,observed.inserts);
        JsonNode cohort=success("{\"filters\":"+filters+"}");
        assertEquals(1,cohort.path("n").asInt());assertEquals(6,cohort.path("studyTotal").asInt());
        assertEquals(java.util.Collections.singletonList("1"),ids(cohort));
        assertEquals(3,cohort.path("submittedUniqueIdsN").asInt());assertEquals(2,cohort.path("effectiveIdsN").asInt());
        assertEquals(2.0,cohort.path("patients").path("items").get(0).path("das28At").asDouble());
        assertEquals(1,cohort.path("activity").path("evalN").asInt());
        assertEquals(1,cohort.path("stats").path("evaluable").asInt());assertEquals(1.0,cohort.path("stats").path("targetRate").asDouble());
        int[] counts={1,0,0,0};for(int i=0;i<4;i++)assertEquals(counts[i],cohort.path("activity").path("current").get(i).path("count").asInt());
        assertEquals(0,observed.active.get());assertEquals(observed.borrowed,observed.returned);
    }
    JsonNode route(String path,String body) throws Exception {
        MvcResult response=http.perform(post("/api/ra/ai/"+path).contentType("application/json").content(body)).andReturn();
        assertEquals(200,response.getResponse().getStatus(),response.getResponse().getContentAsString());
        JsonNode envelope=json.readTree(response.getResponse().getContentAsByteArray());assertTrue(envelope.path("success").asBoolean());
        assertEquals(response.getResponse().getHeader("X-Trace-Id"),envelope.path("data").path("meta").path("traceId").asText());return envelope.path("data");
    }
    JsonNode parse(String question) throws Exception {return route("parse",json.writeValueAsString(java.util.Collections.singletonMap("question",question)));}
    JsonNode apply(JsonNode parsed,String current,Boolean confirm) throws Exception {
        String body="{\"currentFilters\":"+current+",\"candidateFilters\":"+parsed.path("candidateFilters")+",\"parseStatus\":\""+parsed.path("parseStatus").asText()+"\",\"questionRef\":\""+parsed.path("questionRef").asText()+"\""+(confirm==null?"":",\"confirmPartial\":"+confirm)+"}";
        return route("applyParse",body);
    }
    @Test void partialRequiresConfirmationAndPreservesCurrent() throws Exception {
        JsonNode parsed=parse("女性且吸烟");assertEquals("PARTIAL",parsed.path("parseStatus").asText());
        assertEquals("REQUIRE_CONFIRMATION",parsed.path("applyMode").asText());
        String current="{\"sex\":\"M\",\"tx\":\"TNFi\",\"ids\":[\"2\"]}";
        JsonNode no=apply(parsed,current,null),explicitNo=apply(parsed,current,false);
        assertFalse(no.path("applied").asBoolean());assertEquals("M",no.path("filters").path("sex").asText());assertEquals("TNFi",no.path("filters").path("tx").asText());assertEquals(no.path("filters"),explicitNo.path("filters"));
        assertEquals(0,observed.selects);assertEquals(0,observed.inserts);
        JsonNode yes=apply(parsed,current,true);assertTrue(yes.path("applied").asBoolean());assertEquals("F",yes.path("filters").path("sex").asText());assertTrue(yes.path("filters").path("tx").isNull());
        assertEquals(json.readTree("[\"2\"]"),yes.path("filters").path("ids"));assertEquals(0,success("{\"filters\":"+yes.path("filters")+"}").path("n").asInt());
    }

    java.util.Set<String> keys(JsonNode node){java.util.Set<String> result=new java.util.TreeSet<>();node.fieldNames().forEachRemaining(result::add);return result;}
    java.util.Set<String> keySet(String... names){return new java.util.TreeSet<>(java.util.Arrays.asList(names));}
    @Test void publicResultIsSafeFixedShapeWithMonotonicPreAuditDuration() throws Exception {
        clock.now=java.time.Instant.parse("2001-01-01T00:00:00Z");bodyRead.onFirstRead=()->clock.now=clock.now.minusSeconds(1000);
        JsonNode p=parse("女性且吸烟");
        assertEquals(keySet("questionRef","candidateFilters","parseStatus","applyMode","engine","matched","unrecognized","rejectedFields","warnings","notice","fallbackReason","meta"),keys(p));
        assertEquals("RULE",p.path("engine").asText());assertEquals("AI_NOT_ENABLED",p.path("fallbackReason").asText());
        assertEquals(json.readTree("[{\"field\":\"sex\",\"value\":\"F\"}]"),p.path("matched"));assertEquals(json.readTree("[{\"reason\":\"UNMAPPED_TEXT\",\"count\":2}]"),p.path("unrecognized"));
        assertEquals(json.readTree("[\"AI_NOT_ENABLED\",\"PARTIAL_REQUIRES_CONFIRMATION\"]"),p.path("warnings"));assertEquals(json.readTree("[]"),p.path("rejectedFields"));
        assertEquals("AI未启用，已使用规则解析。部分内容无法转换，需明确确认后才应用可识别条件。可用问法：女性45至65岁。",p.path("notice").asText());
        assertEquals(java.util.UUID.fromString(p.path("questionRef").asText()).toString(),p.path("questionRef").asText());
        assertEquals(keySet("traceId","ruleVersion","schemaVersion","modelVersion","promptVersion","durationMs"),keys(p.path("meta")));
        assertTrue(p.path("meta").path("durationMs").isIntegralNumber());assertTrue(p.path("meta").path("durationMs").asLong()>=0);assertTrue(p.path("meta").path("modelVersion").isNull());assertTrue(p.path("meta").path("promptVersion").isNull());
        JsonNode a=apply(p,"{}",false);assertEquals(keySet("questionRef","parseStatus","applyMode","applied","filters","meta"),keys(a));
    }

    @Test void explicitIdsNullEmptyAndNonemptyRemainDistinct() throws Exception {
        JsonNode p=parse("女性35-45岁");String[] values={"null","[]","[\"7\",\"1\",\"1\",\"2\"]"};String[] expected={"null","[]","[\"1\",\"2\",\"7\"]"};int[] ns={1,0,1};
        for(int i=0;i<values.length;i++){JsonNode a=apply(p,"{\"ids\":"+values[i]+"}",null);assertEquals(json.readTree(expected[i]),a.path("filters").path("ids"));assertEquals(ns[i],success("{\"filters\":"+a.path("filters")+"}").path("n").asInt());}
        context.getBean(FakePrincipalProvider.class).doctor=202;
        JsonNode a=apply(p,"{\"ids\":[\"1\",\"2\",\"7\"]}",null);JsonNode c=success("{\"filters\":"+a.path("filters")+"}");assertEquals(0,c.path("n").asInt());assertEquals(1,c.path("studyTotal").asInt());assertEquals(1,c.path("effectiveIdsN").asInt());assertTrue(ids(c).isEmpty());
    }

    @Test void emptyAndUnknownNeverClearCurrentEvenWhenConfirmed() throws Exception {
        String[] questions={"   ","吸烟"};String[] statuses={"EMPTY","UNRECOGNIZED"};String[] notices={"问题为空，保留当前条件。","未识别出可用条件，请改写，例如：女性45至65岁。"};
        JsonNode expected=json.readTree("{\"studyCode\":\"RA\",\"at\":\"6m\",\"act\":null,\"ids\":[\"2\"],\"sex\":\"M\",\"age\":null,\"sero\":null,\"cm\":null,\"tx\":\"TNFi\",\"data\":null}");
        for(int i=0;i<questions.length;i++){JsonNode p=parse(questions[i]);assertEquals(statuses[i],p.path("parseStatus").asText());assertTrue(p.path("candidateFilters").isNull());assertEquals("KEEP_CURRENT",p.path("applyMode").asText());assertEquals("AI未启用，已使用规则解析。"+notices[i],p.path("notice").asText());JsonNode a=apply(p,"{\"sex\":\"M\",\"tx\":\"TNFi\",\"at\":\"6m\",\"ids\":[\"2\"]}",true);assertFalse(a.path("applied").asBoolean());assertEquals(expected,a.path("filters"));}
        assertEquals(0,observed.selects);assertEquals(0,observed.inserts);
    }

    @Test void unsupportedOrAndNegationKeepCurrent() throws Exception {
        String[] questions={"女性或男性","TNF或JAK","非女性","不服用TNF","RF阴性","高活动或未达标","非缓解","不是未达标"};
        for(String q:questions){JsonNode p=parse(q);assertEquals("UNSUPPORTED",p.path("parseStatus").asText(),q);assertTrue(p.path("candidateFilters").isNull(),q);assertEquals("KEEP_CURRENT",p.path("applyMode").asText());assertTrue(p.path("warnings").toString().contains(q.contains("或")?"UNSUPPORTED_OR":"UNSUPPORTED_NEGATION"));assertTrue(p.path("unrecognized").isEmpty());JsonNode a=apply(p,"{\"sex\":\"M\",\"ids\":[\"2\"]}",true);assertFalse(a.path("applied").asBoolean());assertEquals("M",a.path("filters").path("sex").asText());assertEquals(json.readTree("[\"2\"]"),a.path("filters").path("ids"));}
    }

    @Test void targetedTreatmentSubsetConsumesExactNarrowCohort() throws Exception {
        for(String question:new String[]{"TNF生物制剂","生物制剂且TNF"}){JsonNode p=parse(question);assertEquals("OK",p.path("parseStatus").asText());assertEquals("TNFi",p.path("candidateFilters").path("tx").asText());JsonNode a=apply(p,"{}",null);JsonNode c=success("{\"filters\":"+a.path("filters")+"}");assertEquals(java.util.Collections.singletonList("2"),ids(c));assertEquals(1,c.path("n").asInt());}
        JsonNode p=parse("生物制剂");JsonNode a=apply(p,"{}",null);assertEquals(new java.util.HashSet<>(java.util.Arrays.asList("2","3","4","5")),new java.util.HashSet<>(ids(success("{\"filters\":"+a.path("filters")+"}"))));
        JsonNode conflict=parse("TNF生物制剂且JAK");assertEquals("UNSUPPORTED",conflict.path("parseStatus").asText());assertTrue(conflict.path("candidateFilters").isNull());assertTrue(conflict.path("warnings").toString().contains("CONFLICTING_CONDITIONS"));assertTrue(conflict.path("matched").isEmpty());
    }

    @Test void longestActivityWordConsumesExactNarrowAndBroadCohorts() throws Exception {
        String[] questions={"仍有高活动","仍有高疾病活动","高活动","中高活动","中高疾病活动","仍有中高活动","仍有活动","仍有疾病活动","仍未达标","仍活动","仍疾病活动","仍高活动","仍高疾病活动","仍中高活动","仍中高疾病活动"};
        for(int i=0;i<questions.length;i++){String value=i<3||i==11||i==12?"high":"mod-high";JsonNode p=parse(questions[i]);assertEquals("OK",p.path("parseStatus").asText(),questions[i]);assertEquals(value,p.path("candidateFilters").path("act").asText());JsonNode a=apply(p,"{}",null);java.util.Set<String> expected="high".equals(value)?new java.util.HashSet<>(java.util.Collections.singletonList("5")):new java.util.HashSet<>(java.util.Arrays.asList("3","4","5"));assertEquals(expected,new java.util.HashSet<>(ids(success("{\"filters\":"+a.path("filters")+"}"))),questions[i]);}
    }

    @Test void independentActivitySubsetRetainsNarrowValueInEitherOrder() throws Exception {
        String[] questions={"高活动且未达标","未达标且高活动","中高活动且高活动","缓解且已达标","已达标且缓解","缓解","低疾病活动","已达标","达到目标"};
        for(int i=0;i<questions.length;i++){String value=i<3?"high":i<6?"remission":"target";JsonNode p=parse(questions[i]);assertEquals("OK",p.path("parseStatus").asText(),questions[i]);assertEquals(value,p.path("candidateFilters").path("act").asText());JsonNode a=apply(p,"{}",null);java.util.Set<String> expected=new java.util.HashSet<>(java.util.Arrays.asList("high".equals(value)?new String[]{"5"}:"remission".equals(value)?new String[]{"1"}:new String[]{"1","2"}));assertEquals(expected,new java.util.HashSet<>(ids(success("{\"filters\":"+a.path("filters")+"}"))),questions[i]);}
        for(String q:new String[]{"已达标且未达标","缓解且高活动","缓解且未达标"}){JsonNode p=parse(q);assertEquals("UNSUPPORTED",p.path("parseStatus").asText(),q);assertTrue(p.path("candidateFilters").isNull());assertTrue(p.path("warnings").toString().contains("CONFLICTING_CONDITIONS"));assertTrue(p.path("matched").isEmpty());}
    }

    @Test void completeOrdinaryConditionsConsumeOnlyApprovedNeutralWords() throws Exception {
        JsonNode p=parse("请帮我筛选当前治疗TNF、半年仍有高活动的女性年龄45至65岁患者并且纤维肌痛和RF阳性资料不完整");
        // “阳性资料”没有获准作中性语法，先用准确词表组合。
        assertEquals("PARTIAL",p.path("parseStatus").asText());
        p=parse("请帮我筛选当前治疗TNF、半年仍有高活动的女性年龄45至65岁患者并且纤维肌痛和RF且不完整");
        assertEquals("OK",p.path("parseStatus").asText());
        assertEquals(json.readTree("{\"tx\":\"TNFi\",\"at\":\"6m\",\"act\":\"high\",\"sex\":\"F\",\"age\":\"45-65\",\"cm\":\"FM\",\"sero\":\"1\",\"data\":\"missing\"}"),p.path("candidateFilters"));
        assertEquals(json.readTree("[{\"field\":\"tx\",\"value\":\"TNFi\"},{\"field\":\"at\",\"value\":\"6m\"},{\"field\":\"act\",\"value\":\"high\"},{\"field\":\"sex\",\"value\":\"F\"},{\"field\":\"age\",\"value\":\"45-65\"},{\"field\":\"cm\",\"value\":\"FM\"},{\"field\":\"sero\",\"value\":\"1\"},{\"field\":\"data\",\"value\":\"missing\"}]"),p.path("matched"));
        for(String question:new String[]{"女性仍有明显活动","仍有明显活动","缓解达标"}){p=parse(question);assertEquals(question.startsWith("仍")?"UNRECOGNIZED":"PARTIAL",p.path("parseStatus").asText());assertEquals(question.equals("缓解达标")?2:6,p.path("unrecognized").get(0).path("count").asInt());if(question.startsWith("女性")){assertEquals(json.readTree("{\"sex\":\"F\"}"),p.path("candidateFilters"));}else if(question.startsWith("缓解")){assertEquals(json.readTree("{\"act\":\"remission\"}"),p.path("candidateFilters"));}else assertTrue(p.path("candidateFilters").isNull());}
    }

    void routeError(String path,String body,int status,String code) throws Exception {routeError(path,body.getBytes(java.nio.charset.StandardCharsets.UTF_8),status,code);}
    void routeError(String path,byte[] body,int status,String code) throws Exception {
        MvcResult r=http.perform(post("/api/ra/ai/"+path).contentType("application/json").content(body)).andReturn();assertEquals(status,r.getResponse().getStatus(),r.getResponse().getContentAsString());JsonNode e=json.readTree(r.getResponse().getContentAsByteArray());assertEquals(code,e.path("code").asText());assertFalse(e.path("success").asBoolean());assertTrue(e.path("data").isNull());assertNotNull(r.getResponse().getHeader("X-Trace-Id"));
    }
    long auditCount() throws Exception {try(java.sql.Connection c=raw.getConnection();java.sql.Statement s=c.createStatement();java.sql.ResultSet r=s.executeQuery("SELECT COUNT(*) FROM ra_ai_parse_audit")){r.next();return r.getLong(1);}}
    @Test void parseStrictRootRejectsMalformedInputBeforeBorrowing() throws Exception {
        String[] bad={"{\"question\":\"女性\",\"doctorId\":\"101\"}","{\"question\":\"女性\",\"filters\":{}}","{\"question\":\"女性\",\"question\":\"男性\"}","{\"question\":\"女性\"} {}","[]","null","{}","{\"question\":null}","{\"question\":1}","{\"question\":false}","{\"question\":{}}","{\"question\":[]}","{","{\"question\":\"女性\",\"model\":\"x\"}","{\"question\":\"女性\",\"ids\":[]}","{\"question\":\"女性\",\"url\":\"https://canary.invalid\"}","{\"question\":\"女性\",\"requestId\":\"x\"}"};
        for(String b:bad){observed.reset();routeError("parse",b,400,"INVALID_REQUEST");assertEquals(0,observed.borrowed);assertEquals(0,auditCount());}
    }

    final String applyBase="{\"currentFilters\":{},\"candidateFilters\":{\"sex\":\"F\"},\"parseStatus\":\"OK\",\"questionRef\":\"00000000-0000-0000-0000-000000000001\"}";
    @Test void applyStrictRootAndStatusContradictionsDoNotBroaden() throws Exception {
        String[] bad={applyBase.replace("\"OK\"","\"MADE_UP\""),applyBase.replace("\"currentFilters\":{},",""),applyBase.replace("\"currentFilters\":{}","\"currentFilters\":null"),applyBase.replace("\"currentFilters\":{}","\"currentFilters\":[]"),applyBase.replace("\"candidateFilters\":{\"sex\":\"F\"}","\"candidateFilters\":null"),applyBase.replace("\"candidateFilters\":{\"sex\":\"F\"}","\"candidateFilters\":{}"),applyBase.replace("\"candidateFilters\":{\"sex\":\"F\"}","\"candidateFilters\":{\"at\":\"\"}"),applyBase.replace("\"OK\"","\"EMPTY\""),applyBase.replace("00000000-0000-0000-0000-000000000001","0-0-0-0-1"),applyBase.replace("00000000-0000-0000-0000-000000000001","AAAAAAAA-0000-0000-0000-000000000001"),applyBase.replace("\"questionRef\":\"00000000-0000-0000-0000-000000000001\"","\"questionRef\":1"),applyBase.substring(0,applyBase.length()-1)+",\"confirmPartial\":null}",applyBase+" {}",applyBase.replace("\"parseStatus\":\"OK\"","\"parseStatus\":\"OK\",\"parseStatus\":\"OK\""),applyBase.replace("\"questionRef\"","\"question\":\"CANARY\",\"questionRef\"")};
        for(String b:bad){observed.reset();routeError("applyParse",b,400,"INVALID_REQUEST");assertEquals(0,observed.borrowed);assertEquals(0,auditCount());}
        for(String status:new String[]{"PARTIAL","UNRECOGNIZED","UNSUPPORTED"}){String b=applyBase.replace("\"OK\"","\""+status+"\"");if("PARTIAL".equals(status))b=b.replace("{\"sex\":\"F\"}","null");routeError("applyParse",b,400,"INVALID_REQUEST");}
    }

    @Test void candidateWhitelistValidatesBeforeUnconfirmedPartial() throws Exception {
        String[] bad={"{\"ids\":[\"1\"]}","{\"studyCode\":\"RA\"}","{\"doctorId\":\"101\"}","{\"sql\":\"select 1\"}","{\"url\":\"https://canary.invalid\"}","{\"CANARY_UNKNOWN_KEY\":\"x\"}","{\"sex\":1}","{\"sex\":\"x\"}","{\"age\":\"121-\"}","{\"age\":\"65-45\"}","{\"sero\":1}","{\"tx\":\"bogus\"}","{\"at\":\"baseline\"}","{\"act\":\"bogus\"}","{\"cm\":\"both\"}","{\"data\":\"all\"}"};
        for(String candidate:bad){String body=applyBase.replace("{\"sex\":\"F\"}",candidate).replace("\"OK\"","\"PARTIAL\"");observed.reset();routeError("applyParse",body,400,"INVALID_FILTER");assertEquals(0,observed.borrowed);assertEquals(0,auditCount());}
        String[] badCurrent={"{\"ids\":[1]}","{\"ids\":[\"01\"]}","{\"ids\":[\"9223372036854775808\"]}","{\"sex\":\"X\"}","{\"doctorId\":\"101\"}"};
        for(String current:badCurrent){routeError("applyParse",applyBase.replace("\"currentFilters\":{}","\"currentFilters\":"+current),400,"INVALID_FILTER");assertEquals(0,auditCount());}
    }

    @Test void unicodeCodePointsStrictUtf8AndAllSurrogatesAreValidated() throws Exception {
        String supplementary=new String(Character.toChars(0x1F600));StringBuilder q=new StringBuilder();for(int i=0;i<200;i++)q.append(supplementary);
        JsonNode p=parse(q.toString());assertEquals("UNRECOGNIZED",p.path("parseStatus").asText());assertEquals(200,p.path("unrecognized").get(0).path("count").asInt());long count=auditCount();
        observed.reset();routeError("parse",json.writeValueAsString(java.util.Collections.singletonMap("question",q+supplementary)),400,"INVALID_REQUEST");assertEquals(0,observed.borrowed);assertEquals(count,auditCount());
        for(String literal:new String[]{"\\uD800","\\uDC00"}){
            routeError("parse","{\"question\":\""+literal+"\"}",400,"INVALID_REQUEST");
            routeError("parse","{\""+literal+"\":\"female\",\"question\":\"女性\"}",400,"INVALID_REQUEST");
            routeError("applyParse",applyBase.replace("\"F\"","\""+literal+"\""),400,"INVALID_REQUEST");
            routeError("applyParse",applyBase.replace("\"currentFilters\":{}","\"currentFilters\":{\"ids\":[\""+literal+"\"]}"),400,"INVALID_REQUEST");
            routeError("applyParse",applyBase.replace("\"OK\"","\""+literal+"\""),400,"INVALID_REQUEST");
            routeError("applyParse",applyBase.replace("00000000-0000-0000-0000-000000000001",literal),400,"INVALID_REQUEST");
            routeError("applyParse",applyBase.replace("\"sex\"","\""+literal+"\""),400,"INVALID_REQUEST");
        }
        for(byte[] invalid:new byte[][]{{(byte)0xc3,0x28},{(byte)0xed,(byte)0xa0,(byte)0x80},{(byte)0xff},{0,123,0,125}}){routeError("parse",invalid,400,"INVALID_REQUEST");routeError("applyParse",invalid,400,"INVALID_REQUEST");}
        assertEquals(0,observed.borrowed);assertEquals(count,auditCount());
        p=route("parse","{\"question\":\"\\uD83D\\uDE00\"}");assertEquals(1,p.path("unrecognized").get(0).path("count").asInt());
    }
    String padded(String body,int size){StringBuilder out=new StringBuilder(body);while(out.length()<size)out.append(' ');return out.toString();}
    @Test void boundedBodiesReadAtMostOneExtraByteAndRejectBeforeSql() throws Exception {
        route("parse",padded("{\"question\":\"女性\"}",4096-4)); // 女性 UTF8 比 char 多4字节
        assertEquals(4096,bodyRead.bytes);long count=auditCount();observed.reset();
        routeError("parse",padded("{\"question\":\"女性\"}",4097-4),413,"LIMIT_EXCEEDED");assertEquals(4097,bodyRead.bytes);assertEquals(0,observed.borrowed);assertEquals(count,auditCount());
        routeError("parse",new byte[10000],413,"LIMIT_EXCEEDED");assertEquals(4097,bodyRead.bytes);
        route("applyParse",padded(applyBase,128*1024));assertEquals(128*1024,bodyRead.bytes);count=auditCount();observed.reset();
        routeError("applyParse",padded(applyBase,128*1024+1),413,"LIMIT_EXCEEDED");assertEquals(128*1024+1,bodyRead.bytes);assertEquals(0,observed.borrowed);assertEquals(count,auditCount());
        StringBuilder ids=new StringBuilder("[");for(int i=0;i<5001;i++){if(i>0)ids.append(',');ids.append("\"1\"");}ids.append(']');
        routeError("applyParse",applyBase.replace("\"currentFilters\":{}","\"currentFilters\":{\"ids\":"+ids+"}"),413,"LIMIT_EXCEEDED");assertEquals(count,auditCount());assertEquals(0,observed.borrowed);
    }
    @Test void principalFirstRejectsSpoofedIdentityWithoutReadingBody() throws Exception {
        context.close();buildContext(false);
        for(String path:new String[]{"parse","applyParse"}){
            MvcResult r=http.perform(post("/api/ra/ai/"+path).header("doctorId","101").header("X-Doctor-Id","101").principal(()->"101").contentType("application/json").content(new byte[200000])).andReturn();
            assertEquals(401,r.getResponse().getStatus());assertEquals("UNAUTHENTICATED",json.readTree(r.getResponse().getContentAsByteArray()).path("code").asText());assertEquals(0,bodyRead.bytes);assertEquals(0,observed.borrowed);assertEquals(0,auditCount());
        }
    }
    void auditExited(){assertEquals(0,auditObserved.active);assertEquals(auditObserved.borrowed,auditObserved.returned);assertEquals(0,auditObserved.statements);assertEquals(0,auditObserved.results);assertEquals(0,observed.active.get());assertEquals(observed.borrowed,observed.returned);}
    java.util.Map<String,String> auditEvent(String id) throws Exception {
        try(java.sql.Connection c=raw.getConnection();java.sql.PreparedStatement s=c.prepareStatement("SELECT * FROM ra_ai_parse_audit WHERE event_id=?")){s.setString(1,id);try(java.sql.ResultSet rows=s.executeQuery()){assertTrue(rows.next());java.util.Map<String,String> out=new java.util.TreeMap<>();for(int i=1;i<=rows.getMetaData().getColumnCount();i++)out.put(rows.getMetaData().getColumnName(i),rows.getString(i));assertFalse(rows.next());return out;}}
    }
    @Test void everyValidOperationCommitsExactlyOneSafeEventBeforeReturning() throws Exception {
        String source=sourceDigest();long count=0;
        for(String q:new String[]{"女性35-45岁","女性且吸烟","吸烟","女性或男性","   "}){
            auditObserved.reset();observed.reset();JsonNode p=parse(q);auditExited();assertEquals(1,auditObserved.inserts);assertEquals(1,auditObserved.commits);assertEquals(0,auditObserved.rollbacks);assertEquals(java.util.Collections.singletonList(false),auditObserved.autoCommits);assertEquals(++count,auditCount());
            java.util.Map<String,String> event=auditEvent(auditObserved.lastEventId);assertEquals("101",event.get("actor_doctor_id"));assertEquals("PARSE",event.get("action"));assertEquals(p.path("questionRef").asText(),event.get("question_ref"));assertEquals(p.path("meta").path("traceId").asText(),event.get("trace_id"));assertEquals(p.path("parseStatus").asText(),event.get("parse_status"));assertEquals("RULE",event.get("engine"));assertEquals("0",event.get("applied"));assertEquals("AI_NOT_ENABLED",event.get("fallback_reason"));assertEquals(p.path("meta").path("durationMs").asText(),event.get("duration_ms"));assertEquals(Long.toString(clock.millis()),event.get("created_at_ms"));assertEquals("dev-parse-rules-v01",event.get("rule_version"));assertEquals("dev-parse-schema-v01",event.get("schema_version"));assertNull(event.get("model_version"));assertNull(event.get("prompt_version"));
            assertEquals(java.util.UUID.fromString(event.get("event_id")).toString(),event.get("event_id"));JsonNode summary=json.readTree(event.get("condition_summary"));assertEquals(keySet("candidate","effective"),keys(summary));assertEquals(p.path("candidateFilters"),summary.path("candidate"));assertTrue(summary.path("effective").isNull());assertTrue(event.get("condition_summary").getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=1024);assertEquals(0,observed.selects);assertEquals(0,observed.inserts);
            java.util.Map<String,String> prior=new java.util.TreeMap<>(event);String priorId=event.get("event_id");auditObserved.reset();observed.reset();JsonNode a=apply(p,"{\"sex\":\"M\",\"ids\":[\"7\",\"2\"]}",false);auditExited();assertEquals(++count,auditCount());assertEquals(1,auditObserved.commits);assertEquals(prior,auditEvent(priorId));event=auditEvent(auditObserved.lastEventId);assertEquals("APPLY",event.get("action"));assertEquals("NONE",event.get("engine"));assertEquals(a.path("applied").asBoolean()?"1":"0",event.get("applied"));assertNull(event.get("fallback_reason"));assertEquals(a.path("meta").path("durationMs").asText(),event.get("duration_ms"));summary=json.readTree(event.get("condition_summary"));assertEquals(p.path("candidateFilters"),summary.path("candidate"));assertFalse(summary.path("effective").has("ids"));assertFalse(summary.path("effective").has("studyCode"));assertEquals(keySet("sex","age","sero","tx","at","act","cm","data"),keys(summary.path("effective")));assertEquals(0,observed.selects);assertEquals(0,observed.inserts);
        }
        assertEquals(source,sourceDigest());
    }
    String sourceDigest() throws Exception {
        java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");
        for(String table:new String[]{"patient_basic_info","patient_relation_doctor","patient_follow_up_history","patient_comorbidity"})try(java.sql.Connection c=raw.getConnection();java.sql.Statement s=c.createStatement();java.sql.ResultSet rows=s.executeQuery("SELECT * FROM "+table+" ORDER BY 1,2")){while(rows.next())for(int i=1;i<=rows.getMetaData().getColumnCount();i++){String value=rows.getString(i);digest.update((value==null?"<null>":value).getBytes(java.nio.charset.StandardCharsets.UTF_8));digest.update((byte)0);}}
        return java.util.Base64.getEncoder().encodeToString(digest.digest());
    }

    boolean auditExists(String id) throws Exception {
        try(java.sql.Connection c=raw.getConnection();java.sql.PreparedStatement s=c.prepareStatement("SELECT COUNT(*) FROM ra_ai_parse_audit WHERE event_id=?")){s.setString(1,id);try(java.sql.ResultSet rows=s.executeQuery()){rows.next();return rows.getInt(1)!=0;}}
    }
    @Test void sqlFailureBeforeInsertIsClosedWithoutPublishingCandidate() throws Exception {
        JsonNode p=parse("女性");String oldId=auditObserved.lastEventId;java.util.Map<String,String> old=auditEvent(oldId);String source=sourceDigest();
        auditObserved.reset();observed.reset();auditObserved.failInsertSql=true;
        routeError("parse","{\"question\":\"女性\"}",503,"SERVICE_UNAVAILABLE");
        auditExited();assertEquals(1,auditObserved.attempts);assertEquals(0,auditObserved.inserts);assertEquals(0,auditObserved.commits);assertEquals(1,auditObserved.rollbacks);assertEquals(1,auditCount());assertEquals(old,auditEvent(oldId));assertEquals(source,sourceDigest());assertEquals(0,observed.scopeReads);
        auditObserved.failInsertSql=false;auditObserved.reset();observed.reset();assertEquals("OK",parse("女性").path("parseStatus").asText());auditExited();assertEquals(2,auditCount());
    }
    @Test void postInsertPreCommitFailureRollsBackSameEventAndExitsBeforeHttpReturns() throws Exception {
        JsonNode p=parse("女性35-45岁");String oldId=auditObserved.lastEventId;java.util.Map<String,String> old=auditEvent(oldId);String source=sourceDigest();
        auditObserved.reset();observed.reset();final int[] cuts={0};
        auditObserved.afterInsert=()->{cuts[0]++;assertEquals(1,auditObserved.ownerSawInserted);try{assertFalse(auditExists(auditObserved.lastEventId),"同event尚未commit，独立视图必须不可见");}catch(Exception e){throw new AssertionError(e);}throw new IllegalStateException("synthetic post-insert failure");};
        String body=applyBase.replace("{\"sex\":\"F\"}",p.path("candidateFilters").toString()).replace("00000000-0000-0000-0000-000000000001",p.path("questionRef").asText());
        try{
            routeError("applyParse",body,503,"SERVICE_UNAVAILABLE");
            assertEquals(1,cuts[0]);assertEquals(1,auditObserved.inserts);assertEquals(1,auditObserved.ownerSawInserted);assertNotNull(auditObserved.lastEventId);
            assertFalse(auditExists(auditObserved.lastEventId),"HTTP已返回后/任何清理前，精确新event必须回滚");
            assertEquals(0,auditObserved.commits);assertEquals(1,auditObserved.rollbacks);assertEquals(java.util.Collections.singletonList(false),auditObserved.autoCommits);auditExited();
            assertEquals(1,auditCount());assertEquals(old,auditEvent(oldId));assertEquals(source,sourceDigest());assertEquals(0,observed.scopeReads);assertEquals(0,observed.inserts);
        }finally{auditObserved.afterInsert=null;}
        auditObserved.reset();observed.reset();route("applyParse",body);auditExited();assertEquals(2,auditCount());
    }
    @Test void privacyCanaryIsAbsentFromEveryPersistedColumnResponseAndLog() throws Exception {
        String question="女性 合成姓名CANARY_P05A 电话13800000000 身份证110101198610070011 https://canary.invalid/p05a";
        ch.qos.logback.classic.Logger logger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> capture=new ch.qos.logback.core.read.ListAppender<>();capture.start();logger.addAppender(capture);
        try{
            String source=sourceDigest();JsonNode p=parse(question);assertEquals("PARTIAL",p.path("parseStatus").asText());assertEquals(json.readTree("{\"sex\":\"F\"}"),p.path("candidateFilters"));auditExited();
            String firstId=auditObserved.lastEventId;java.util.Map<String,String> first=auditEvent(firstId);
            assertFalse(first.get("condition_summary").contains("CANARY_P05A"),"独立raw读取同event的持久condition_summary泄漏canary");
            JsonNode a=apply(p,"{\"sex\":\"M\",\"ids\":[\"1\",\"7\"]}",false);auditExited();assertFalse(a.path("applied").asBoolean());
            routeError("parse",json.writeValueAsString(com.wenwen.vo.AiCohortVo.object("question",question,"CANARY_REJECTED_KEY",question)),400,"INVALID_REQUEST");assertEquals(2,auditCount());
            auditObserved.failInsertSql=true;routeError("parse",json.writeValueAsString(java.util.Collections.singletonMap("question",question)),503,"SERVICE_UNAVAILABLE");auditExited();assertEquals(2,auditCount());assertEquals(first,auditEvent(firstId));
            StringBuilder all=new StringBuilder(p.toString()).append(a).append(first);
            try(java.sql.Connection c=raw.getConnection();java.sql.Statement s=c.createStatement();java.sql.ResultSet rows=s.executeQuery("SELECT * FROM ra_ai_parse_audit")){
                while(rows.next())for(int i=1;i<=rows.getMetaData().getColumnCount();i++){String value=rows.getString(i);all.append(value);if("condition_summary".equals(rows.getMetaData().getColumnName(i))){JsonNode summary=json.readTree(value);assertEquals(keySet("candidate","effective"),keys(summary));for(String part:new String[]{"candidate","effective"})if(!summary.path(part).isNull())assertTrue(keySet("sex","age","sero","tx","at","act","cm","data").containsAll(keys(summary.path(part))));assertFalse(value.contains("\"ids\""));assertFalse(value.contains("\"studyCode\""));}}
            }
            for(ch.qos.logback.classic.spi.ILoggingEvent event:capture.list){all.append(event.getFormattedMessage());if(event.getThrowableProxy()!=null)all.append(ch.qos.logback.classic.spi.ThrowableProxyUtil.asString(event.getThrowableProxy()));}
            for(String secret:new String[]{"CANARY_P05A","CANARY_REJECTED_KEY","13800000000","110101198610070011","https://canary.invalid/p05a",question})assertFalse(all.toString().contains(secret),"持久内容/响应/日志泄漏合成canary："+secret);
            assertEquals(source,sourceDigest());assertEquals(0,observed.scopeReads);assertEquals(0,observed.inserts);for(String sql:auditObserved.sqls)assertFalse(sql.toLowerCase(java.util.Locale.ROOT).contains("patient_"));
        }finally{auditObserved.failInsertSql=false;logger.detachAppender(capture);capture.stop();}
    }
    @Test void actualHttpWriteFailureAfterCommitKeepsCompleteAuditEvent() throws Exception {
        final int[] cuts={0};final String[] committed={null};
        org.springframework.http.converter.json.MappingJackson2HttpMessageConverter failing=new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(){
            @Override protected void writeInternal(Object value,java.lang.reflect.Type type,org.springframework.http.HttpOutputMessage output)throws java.io.IOException {
                cuts[0]++;committed[0]=auditObserved.lastEventId;assertEquals(1,auditObserved.commits);auditExited();
                try{assertTrue(auditExists(committed[0]));assertEquals("PARSE",auditEvent(committed[0]).get("action"));}catch(Exception e){throw new AssertionError(e);}
                throw new java.io.IOException("synthetic HTTP output failure");
            }
        };
        http=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(context.getBean(com.wenwen.controller.ra.AiParseController.class)).setMessageConverters(failing).addFilters(bodyRead).build();
        assertThrows(Exception.class,()->http.perform(post("/api/ra/ai/parse").contentType("application/json").content("{\"question\":\"女性\"}")).andReturn());
        assertEquals(1,cuts[0]);assertNotNull(committed[0]);assertTrue(auditExists(committed[0]));assertEquals(1,auditCount());assertEquals(0,auditObserved.rollbacks);auditExited();
        http=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context).addFilters(bodyRead).build();
        parse("女性");assertEquals(2,auditCount());assertNotEquals(committed[0],auditObserved.lastEventId);auditExited();
    }
    @Test void localParseAndApplyDoNotRequireAnalysisKeyOrModelConfiguration() throws Exception {
        context.close();syntheticCursorKey=null;buildContext(true);auditObserved=context.getBean(ParseAuditObservedDataSource.class);
        JsonNode p=parse("女性35-45岁"),a=apply(p,"{\"ids\":[]}",null);
        assertEquals("RULE",p.path("engine").asText());assertEquals("AI_NOT_ENABLED",p.path("fallbackReason").asText());assertTrue(a.path("applied").asBoolean());assertEquals(json.readTree("[]"),a.path("filters").path("ids"));assertEquals(2,auditCount());assertEquals(0,observed.selects);assertEquals(0,observed.inserts);auditExited();
    }
    @Test void bareFemaleWithMaleTextCannotBeConfirmedIntoFemaleCohort() throws Exception {
        for(String question:new String[]{"男女","女男"}){
            JsonNode p=parse(question),a=apply(p,"{\"sex\":\"M\"}",true);
            String applyEventId=auditObserved.lastEventId;
            JsonNode cohort=success("{\"filters\":"+a.path("filters")+"}");
            String actual="question="+question+", parse="+p+", apply="+a+", cohortN="+cohort.path("n")+", cohortIds="+ids(cohort);
            assertFalse(p.path("candidateFilters").has("sex"),"单字“女”在含男时不得生成F候选；"+actual);
            assertEquals("UNRECOGNIZED",p.path("parseStatus").asText());assertTrue(p.path("candidateFilters").isNull());
            assertEquals("KEEP_CURRENT",p.path("applyMode").asText());assertEquals(2,p.path("unrecognized").get(0).path("count").asInt());
            assertFalse(a.path("applied").asBoolean());assertEquals("M",a.path("filters").path("sex").asText());assertTrue(a.path("filters").path("ids").isNull());assertEquals("now",a.path("filters").path("at").asText());
            assertEquals(new java.util.HashSet<>(java.util.Arrays.asList("2","4","5")),new java.util.HashSet<>(ids(cohort)));assertEquals(3,cohort.path("n").asInt());assertEquals(6,cohort.path("studyTotal").asInt());
            java.util.Map<String,String> event=auditEvent(applyEventId);assertEquals("APPLY",event.get("action"));assertEquals("UNRECOGNIZED",event.get("parse_status"));assertEquals("0",event.get("applied"));JsonNode summary=json.readTree(event.get("condition_summary"));assertTrue(summary.path("candidate").isNull());assertEquals("M",summary.path("effective").path("sex").asText());assertFalse(summary.path("effective").has("ids"));auditExited();
        }
        assertEquals(4,auditCount());
    }
}
