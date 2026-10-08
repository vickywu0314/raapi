package com.wenwen.ai.cohort;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SimilarEntryHttpTest extends SimilarEntryHttpFixture {
    @Test void realSimilarEntryFeedsCompleteCohortAndRetainedPage() throws Exception {
        similarPopulation();JsonNode entry=resolve(101);
        assertEquals("F",entry.path("filters").path("sex").asText());
        assertEquals("35-45",entry.path("filters").path("age").asText());
        assertEquals("1",entry.path("filters").path("sero").asText());
        resolverReleased();assertEquals(0,runs());
        assertEquals("SIMILAR",entry.path("source").asText());assertEquals("READY",entry.path("status").asText());assertTrue(entry.path("canApply").asBoolean());
        assertEquals(0,entry.path("missingBasis").size());assertTrue(entry.path("reason").isNull());
        assertEquals(new java.util.HashSet<>(java.util.Arrays.asList("source","filters","missingBasis","status","canApply","reason","meta")),fields(entry));
        assertEquals(new java.util.HashSet<>(java.util.Arrays.asList("studyCode","at","act","ids","sex","age","sero","cm","tx","data")),fields(entry.path("filters")));
        assertEquals("RA",entry.path("filters").path("studyCode").asText());assertEquals("now",entry.path("filters").path("at").asText());
        for(String k:new String[]{"act","ids","cm","tx","data"})assertTrue(entry.path("filters").path(k).isNull(),k);
        assertEquals(new java.util.HashSet<>(java.util.Arrays.asList("asOf","readStartedAt","readCompletedAt","traceId","policyVersion")),fields(entry.path("meta")));
        assertEquals("2026-10-07",entry.path("meta").path("asOf").asText());assertEquals("2026-10-07T02:00:00Z",entry.path("meta").path("readStartedAt").asText());
        assertEquals("2026-10-07T02:00:00Z",entry.path("meta").path("readCompletedAt").asText());assertEquals("dev-similar-v01",entry.path("meta").path("policyVersion").asText());
        observed.reset();JsonNode full=success(json.writeValueAsString(com.wenwen.vo.AiCohortVo.object("filters",entry.path("filters"))));
        assertEquals(22,full.path("n").asInt());assertEquals(27,full.path("studyTotal").asInt());
        assertEquals(sequence(101,110),ids(full));assertEquals(1,runs());
        JsonNode stats=full.path("stats");assertEquals(1,stats.path("femaleRate").asDouble());assertEquals(40,stats.path("ageMedian").asDouble());
        assertEquals(1,stats.path("seroRate").asDouble());assertEquals(0,stats.path("targetRate").asDouble());assertEquals(22,stats.path("evaluable").asInt());
        assertEquals(22,full.path("metricMeta").path("targetRate").path("denominator").asInt());assertEquals(27,full.path("byTx").path("studyTargetMeta").path("denominator").asInt());
        assertEquals(0,full.path("byTx").path("studyTargetRate").asDouble());assertEquals(4,full.path("activity").path("current").size());
        assertEquals(new java.math.BigDecimal("0.8148148148148148"),stats.path("cohortRate").decimalValue());assertTrue(stats.path("durationMedian").isNull());
        assertEquals(22,full.path("metricMeta").path("durationMedian").path("unknownN").asInt());assertEquals(0,full.path("metricMeta").path("ageMedian").path("unknownN").asInt());
        assertEquals(0,full.path("byTx").path("groups").size());assertEquals(22,full.path("byTx").path("unknownTxN").asInt());assertEquals(0,full.path("byTx").path("noCurrentTxN").asInt());
        String[] activity={"remission","low","moderate","high"};for(int b=0;b<4;b++){JsonNode bucket=full.path("activity").path("current").get(b);assertEquals(activity[b],bucket.path("level").asText());assertEquals(b==2?22:0,bucket.path("count").asInt());assertEquals(b==2?1:0,bucket.path("value").asDouble());}
        assertEquals(3,full.path("lines").path("groups").size());
        assertEquals(22,full.path("lines").path("unknownLineN").asInt());for(JsonNode line:full.path("lines").path("groups"))assertEquals(0,line.path("count").asInt());
        assertEquals("INSUFFICIENT_SAMPLE",full.path("fm").path("status").asText());assertEquals(22,full.path("fm").path("unknownN").asInt());assertEquals(0,full.path("fm").path("rows").size());
        String id=full.path("analysisId").asText();assertEquals(22,json.readTree(stored(id)).path("rows").size());
        observed.reset();JsonNode last=page(id,full.path("patients").path("nextCursor").asText());assertEquals(sequence(111,122),ids(last));
        assertEquals(12,last.path("patients").path("returnedCount").asInt());assertTrue(last.path("patients").path("nextCursor").isNull());assertFalse(last.path("patients").path("hasMore").asBoolean());
        java.util.List<String> union=new java.util.ArrayList<>(ids(full));union.addAll(ids(last));assertEquals(sequence(101,122),union);assertEquals(22,new java.util.HashSet<>(union).size());
        assertEquals(0,observed.active.get());assertEquals(observed.borrowed,observed.returned);
    }
    @Test void ageWindowClipsBothSupportedEndpoints() throws Exception {
        similarPopulation();sql("UPDATE patient_basic_info SET card_no='110101202610070011' WHERE id=101");
        assertEquals("0-5",resolve(101).path("filters").path("age").asText());resolverReleased();
        sql("UPDATE patient_basic_info SET card_no='110101190610070011' WHERE id=101");observed.reset();
        assertEquals("115-120",resolve(101).path("filters").path("age").asText());resolverReleased();assertEquals(0,runs());
    }
    @Test void knownAgeBeyondQueryDomainCannotSilentlyBroaden() throws Exception {
        similarPopulation();sql("UPDATE patient_basic_info SET card_no='110101190010070011' WHERE id=101");
        JsonNode entry=resolve(101);assertEquals("UNSUPPORTED_BASIS",entry.path("status").asText());
        assertFalse(entry.path("canApply").asBoolean());assertTrue(entry.path("filters").isNull());
        assertEquals("AGE_OUT_OF_SUPPORTED_RANGE",entry.path("reason").asText());assertEquals(0,entry.path("missingBasis").size());
        resolverReleased();assertEquals(0,runs());
        sql("UPDATE patient_basic_info SET gender=0 WHERE id=101");sql("UPDATE patient_follow_up_history SET fzjc=NULL WHERE patient_basic_info_id=101");observed.reset();
        JsonNode incomplete=resolve(101);assertEquals("UNSUPPORTED_BASIS",incomplete.path("status").asText());assertFalse(incomplete.path("canApply").asBoolean());assertTrue(incomplete.path("filters").isNull());assertEquals(json.readTree("[\"sex\",\"sero\"]"),incomplete.path("missingBasis"));assertEquals("AGE_OUT_OF_SUPPORTED_RANGE",incomplete.path("reason").asText());resolverReleased();
    }
    @Test void missingAgeIsReportedWithoutEstimatingAndMaleIsPreserved() throws Exception {
        similarPopulation();sql("UPDATE patient_basic_info SET gender=1,card_no='invalid',age=55 WHERE id=101");
        JsonNode entry=resolve(101);assertEquals("M",entry.path("filters").path("sex").asText());assertTrue(entry.path("filters").path("age").isNull());
        assertEquals("1",entry.path("filters").path("sero").asText());assertEquals(json.readTree("[\"age\"]"),entry.path("missingBasis"));
        assertEquals("PARTIAL_BASIS",entry.path("status").asText());assertTrue(entry.path("canApply").asBoolean());assertTrue(entry.path("reason").isNull());resolverReleased();assertEquals(0,runs());
    }
    @Test void missingFactsHaveFixedOrderAndDoNotMakeUnknownSerologyPositive() throws Exception {
        similarPopulation();sql("UPDATE patient_basic_info SET gender=0,card_no='invalid' WHERE id=101");
        JsonNode partial=resolve(101);assertEquals(json.readTree("[\"sex\",\"age\"]"),partial.path("missingBasis"));assertTrue(partial.path("filters").path("sex").isNull());assertEquals("1",partial.path("filters").path("sero").asText());
        sql("UPDATE patient_basic_info SET card_no='110101198610070011' WHERE id=101");sql("UPDATE patient_follow_up_history SET fzjc=NULL WHERE patient_basic_info_id=101");observed.reset();
        JsonNode unknown=resolve(101);assertEquals(json.readTree("[\"sex\",\"sero\"]"),unknown.path("missingBasis"));assertEquals("35-45",unknown.path("filters").path("age").asText());assertTrue(unknown.path("filters").path("sero").isNull());assertEquals("PARTIAL_BASIS",unknown.path("status").asText());
        resolverReleased();assertEquals(0,runs());
    }
    @Test void noBasisIsNotAnImplicitWholeUniverseAnalysis() throws Exception {
        similarPopulation();sql("UPDATE patient_basic_info SET gender=0,card_no='invalid' WHERE id=101");sql("UPDATE patient_follow_up_history SET fzjc=NULL WHERE patient_basic_info_id=101");
        JsonNode unknown=resolve(101);assertEquals("NO_BASIS",unknown.path("status").asText());assertFalse(unknown.path("canApply").asBoolean());assertTrue(unknown.path("filters").isNull());assertTrue(unknown.path("reason").isNull());assertEquals(json.readTree("[\"sex\",\"age\",\"sero\"]"),unknown.path("missingBasis"));resolverReleased();assertEquals(0,runs());
        sql("UPDATE patient_follow_up_history SET fzjc='{\"lfsyz\":10}' WHERE patient_basic_info_id=101");observed.reset();
        JsonNode negative=resolve(101);assertEquals("NO_BASIS",negative.path("status").asText());assertFalse(negative.path("canApply").asBoolean());assertTrue(negative.path("filters").isNull());assertEquals(json.readTree("[\"sex\",\"age\"]"),negative.path("missingBasis"));resolverReleased();assertEquals(0,runs());
    }
    @Test void serologyUsesDatedHistoryAndNegativeIsNotMissing() throws Exception {
        similarPopulation();visit(9001,101,0,null,"2026-10-07",null,"{\"lfsyz\":10}");
        JsonNode historical=resolve(101);assertEquals("1",historical.path("filters").path("sero").asText());assertEquals("READY",historical.path("status").asText());resolverReleased();
        sql("UPDATE patient_follow_up_history SET fzjc=NULL WHERE patient_basic_info_id=101");
        visit(9002,101,0,null,"2026-10-08",null,"{\"lfsyz\":35}");visit(9003,101,0,null,null,null,"{\"kccpkt\":100}");observed.reset();
        JsonNode ignored=resolve(101);assertTrue(ignored.path("filters").path("sero").isNull());assertEquals(json.readTree("[\"sero\"]"),ignored.path("missingBasis"));assertEquals("PARTIAL_BASIS",ignored.path("status").asText());resolverReleased();
        sql("UPDATE patient_follow_up_history SET fzjc='{\"lfsyz\":10}' WHERE id=1010");observed.reset();
        JsonNode negative=resolve(101);assertTrue(negative.path("filters").path("sero").isNull());assertEquals(0,negative.path("missingBasis").size());assertEquals("READY",negative.path("status").asText());resolverReleased();assertEquals(0,runs());
    }
    @Test void requestMustBeExactSingleSimilarSourceWithCanonicalTextId() throws Exception {
        similarPopulation();
        for(String body:java.util.Arrays.asList("","null","[]","1","{}","{","{} {}",
            "{\"source\":\"SIMILAR\"}","{\"indexPatientId\":\"101\"}",
            "{\"source\":null,\"indexPatientId\":\"101\"}","{\"source\":\"\",\"indexPatientId\":\"101\"}",
            "{\"source\":\"similar\",\"indexPatientId\":\"101\"}","{\"source\":\"SAVED\",\"indexPatientId\":\"101\"}",
            "{\"source\":[\"SIMILAR\"],\"indexPatientId\":\"101\"}","{\"source\":1,\"indexPatientId\":\"101\"}",
            "{\"source\":\"SIMILAR\",\"source\":\"SIMILAR\",\"indexPatientId\":\"101\"}",
            "{\"source\":\"SIMILAR\",\"indexPatientId\":\"101\",\"indexPatientId\":\"101\"}",
            "{\"source\":\"SIMILAR\",\"indexPatientId\":\"101\"} {}")) {
            observed.reset();resolveError(body,400,"INVALID_REQUEST");assertEquals(0,observed.selects,body);assertEquals(0,observed.borrowed,body);
        }
        for(String value:java.util.Arrays.asList("null","101","0","\"\"","\"0\"","\"-1\"","\"01\"","\"+101\"","\"101 \"","\" 101\"","\"1.0\"","\"9223372036854775808\"","[]","{}")) {
            observed.reset();resolveError("{\"source\":\"SIMILAR\",\"indexPatientId\":"+value+"}",400,"INVALID_REQUEST");assertEquals(0,observed.selects,value);assertEquals(0,observed.borrowed,value);
        }
        for(String key:new String[]{"cohortId","compare","q","filters","ids","doctorId","extra"}) {
            observed.reset();resolveError("{\"source\":\"SIMILAR\",\"indexPatientId\":\"101\",\""+key+"\":null}",400,"INVALID_REQUEST");assertEquals(0,observed.selects,key);assertEquals(0,observed.borrowed,key);
        }
        assertEquals(0,runs());
    }
    @Test void bodyLimitCountsUtf8BytesAndReadsAtMost4097() throws Exception {
        similarPopulation();String valid="{\"source\":\"SIMILAR\",\"indexPatientId\":\"101\"}";
        String exact=valid+String.join("",java.util.Collections.nCopies(4096-valid.getBytes(java.nio.charset.StandardCharsets.UTF_8).length," "));
        assertEquals(200,resolveRequest(exact).getResponse().getStatus());assertEquals(4096,bodyRead.bytes);resolverReleased();
        observed.reset();resolveError(exact+" ",413,"LIMIT_EXCEEDED");assertEquals(4097,bodyRead.bytes);assertEquals(0,observed.borrowed);assertEquals(0,observed.selects);
        String unicode="{\"source\":\"SIMILAR\",\"indexPatientId\":\""+String.join("",java.util.Collections.nCopies(1500,"合"))+"\"}";
        assertTrue(unicode.length()<4096);observed.reset();resolveError(unicode,413,"LIMIT_EXCEEDED");assertEquals(4097,bodyRead.bytes);assertEquals(0,observed.borrowed);assertEquals(0,observed.selects);assertEquals(0,runs());
    }
    @Test void parserRejectsAlternateEncodingsAndMalformedUtf8BeforeSql() throws Exception {
        similarPopulation();String body="{\"source\":\"SIMILAR\",\"indexPatientId\":\"101\"}";
        for(String charset:new String[]{"UTF-16LE","UTF-16BE","UTF-32LE","UTF-32BE"}) {
            observed.reset();org.springframework.test.web.servlet.MvcResult r=http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/ra/ai/resolveEntry").contentType("application/json").content(body.getBytes(java.nio.charset.Charset.forName(charset)))).andReturn();
            assertEquals(400,r.getResponse().getStatus(),charset);JsonNode e=json.readTree(r.getResponse().getContentAsByteArray());assertEquals("INVALID_REQUEST",e.path("code").asText());assertTrue(e.path("data").isNull());assertEquals(0,observed.borrowed);assertEquals(0,observed.selects);
        }
        byte[] prefix="{\"source\":\"SIMILAR\",\"indexPatientId\":\"101\",\"".getBytes(java.nio.charset.StandardCharsets.UTF_8);byte[] malformed=java.util.Arrays.copyOf(prefix,prefix.length+2);malformed[prefix.length]=(byte)0xc3;malformed[prefix.length+1]=(byte)0x28;
        observed.reset();org.springframework.test.web.servlet.MvcResult r=http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/ra/ai/resolveEntry").contentType("application/json").content(malformed)).andReturn();assertEquals(400,r.getResponse().getStatus());assertEquals("INVALID_REQUEST",json.readTree(r.getResponse().getContentAsByteArray()).path("code").asText());assertEquals(0,observed.selects);assertEquals(0,observed.borrowed);assertEquals(0,runs());
    }
    @Test void trustedIdentityWinsOverBodyHeaderAndPrincipalName() throws Exception {
        similarPopulation();org.springframework.test.web.servlet.MvcResult ok=http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/ra/ai/resolveEntry").contentType("application/json").header("doctorId","202").header("X-Doctor-Id","202").principal(()->"202").content("{\"source\":\"SIMILAR\",\"indexPatientId\":\"101\"}")).andReturn();assertEquals(200,ok.getResponse().getStatus());resolverReleased();
        context.close();buildContext(false);observed.reset();bodyRead.onFirstRead=()->fail("匿名身份必须先于body");
        org.springframework.test.web.servlet.MvcResult denied=http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/ra/ai/resolveEntry").contentType("application/json").header("doctorId","101").header("X-Doctor-Id","101").principal(()->"101").content("{\"doctorId\":101}"+String.join("",java.util.Collections.nCopies(5000," ")))).andReturn();
        assertEquals(401,denied.getResponse().getStatus());JsonNode envelope=json.readTree(denied.getResponse().getContentAsByteArray());assertEquals("UNAUTHENTICATED",envelope.path("code").asText());assertTrue(envelope.path("data").isNull());assertEquals(0,bodyRead.bytes);assertEquals(0,observed.borrowed);assertEquals(0,observed.selects);assertEquals(0,observed.active.get());assertEquals(0,runs());
    }
    @Test void inaccessibleDeletedRevokedAndMissingIndexesShare404() throws Exception {
        similarPopulation();
        for(long id:new long[]{201,202,999,Long.MAX_VALUE}) { observed.reset();resolveError("{\"source\":\"SIMILAR\",\"indexPatientId\":\""+id+"\"}",404,"RESOURCE_NOT_FOUND");resolverReleased(); }
        sql("DELETE FROM patient_basic_info WHERE id=101");observed.reset();resolveError("{\"source\":\"SIMILAR\",\"indexPatientId\":\"101\"}",404,"RESOURCE_NOT_FOUND");resolverReleased();
        sql("DELETE FROM patient_relation_doctor WHERE doctor_id=101 AND patient_id=102");observed.reset();resolveError("{\"source\":\"SIMILAR\",\"indexPatientId\":\"102\"}",404,"RESOURCE_NOT_FOUND");resolverReleased();
        sql("DELETE FROM patient_relation_doctor WHERE doctor_id=101");observed.reset();resolveError("{\"source\":\"SIMILAR\",\"indexPatientId\":\"103\"}",404,"RESOURCE_NOT_FOUND");resolverReleased();assertEquals(0,runs());
    }
    @Test void requestStartFixesShanghaiBirthdayEvenWhenBodyCrossesMidnight() throws Exception {
        similarPopulation();sql("UPDATE patient_basic_info SET card_no='110101198610080011' WHERE id=101");clock.now=java.time.Instant.parse("2026-10-07T15:59:59Z");
        java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();clock.onInstant=()->{assertEquals(0,observed.active.get());calls.incrementAndGet();};
        bodyRead.onFirstRead=()->clock.now=java.time.Instant.parse("2026-10-07T16:00:01Z");
        JsonNode before=resolve(101);assertEquals("34-44",before.path("filters").path("age").asText());assertEquals("2026-10-07",before.path("meta").path("asOf").asText());assertEquals("2026-10-07T15:59:59Z",before.path("meta").path("readStartedAt").asText());assertEquals("2026-10-07T16:00:01Z",before.path("meta").path("readCompletedAt").asText());assertEquals(2,calls.get());resolverReleased();
        calls.set(0);observed.reset();JsonNode after=resolve(101);assertEquals("35-45",after.path("filters").path("age").asText());assertEquals("2026-10-08",after.path("meta").path("asOf").asText());assertEquals(2,calls.get());resolverReleased();assertEquals(0,runs());
    }
    @Test void explicitlyAddingRemissionProducesRealEmptyCohortAndStableStructure() throws Exception {
        similarPopulation();JsonNode entry=resolve(101);com.fasterxml.jackson.databind.node.ObjectNode filters=(com.fasterxml.jackson.databind.node.ObjectNode)entry.path("filters").deepCopy();filters.put("act","remission");
        observed.reset();JsonNode empty=success(json.writeValueAsString(com.wenwen.vo.AiCohortVo.object("filters",filters)));assertEquals(0,empty.path("n").asInt());assertEquals(27,empty.path("studyTotal").asInt());assertEquals(1,runs());assertEquals(0,json.readTree(stored(empty.path("analysisId").asText())).path("rows").size());
        for(String kind:new String[]{"current","base"}){assertEquals(4,empty.path("activity").path(kind).size());for(JsonNode bucket:empty.path("activity").path(kind)){assertEquals(0,bucket.path("count").asInt());assertEquals(0,bucket.path("value").asDouble());assertEquals("NO_DATA",bucket.path("status").asText());}}
        assertEquals(3,empty.path("lines").path("groups").size());for(JsonNode line:empty.path("lines").path("groups")){assertEquals(0,line.path("count").asInt());assertEquals(0,line.path("value").asDouble());assertEquals("NO_DATA",line.path("status").asText());}
        for(String key:new String[]{"ageMedian","durationMedian"}){assertTrue(empty.path("stats").path(key).isNull());assertEquals("NO_DATA",empty.path("metricMeta").path(key).path("status").asText());}
        for(String key:new String[]{"femaleRate","seroRate","targetRate","completeRate"}){assertEquals(0,empty.path("stats").path(key).asDouble());assertEquals("NO_DATA",empty.path("metricMeta").path(key).path("status").asText());}
        assertEquals(0,empty.path("patients").path("items").size());assertTrue(empty.path("patients").path("nextCursor").isNull());assertFalse(empty.path("patients").path("hasMore").asBoolean());assertEquals(0,empty.path("byTx").path("groups").size());assertEquals(0,empty.path("fm").path("rows").size());assertEquals(27,empty.path("byTx").path("studyTargetMeta").path("denominator").asInt());assertEquals(0,observed.active.get());assertEquals(observed.borrowed,observed.returned);
    }
    @Test void candidateFiltersGrantNoAuthorityAndCohortRereadsCurrentFacts() throws Exception {
        similarPopulation();JsonNode entry=resolve(101);String body=json.writeValueAsString(com.wenwen.vo.AiCohortVo.object("filters",entry.path("filters")));
        sql("UPDATE patient_basic_info SET gender=1,card_no='110101199610070011' WHERE id=101");observed.reset();JsonNode changed=success(body);assertEquals(21,changed.path("n").asInt());assertEquals(27,changed.path("studyTotal").asInt());assertFalse(ids(changed).contains("101"));
        observed.reset();JsonNode newEntry=resolve(101);assertEquals("M",newEntry.path("filters").path("sex").asText());assertEquals("25-35",newEntry.path("filters").path("age").asText());assertEquals("1",newEntry.path("filters").path("sero").asText());resolverReleased();
        sql("DELETE FROM patient_relation_doctor WHERE doctor_id=101 AND patient_id=101");observed.reset();JsonNode revoked=success(body);assertEquals(21,revoked.path("n").asInt());assertEquals(26,revoked.path("studyTotal").asInt());assertFalse(ids(revoked).contains("101"));
        observed.reset();resolveError("{\"source\":\"SIMILAR\",\"indexPatientId\":\"101\"}",404,"RESOURCE_NOT_FOUND");resolverReleased();assertEquals(2,runs());
        com.fasterxml.jackson.databind.node.ObjectNode conflict=(com.fasterxml.jackson.databind.node.ObjectNode)entry.path("filters").deepCopy();
        observed.reset();error(json.writeValueAsString(com.wenwen.vo.AiCohortVo.object("source","SIMILAR","indexPatientId","101","filters",conflict)),400,"INVALID_FILTER");assertEquals(0,observed.borrowed);assertEquals(0,observed.selects);
    }
    @Test void oldPageKeepsNumbersAcrossVisitsButRejectsUniverseRevocation() throws Exception {
        similarPopulation();JsonNode entry=resolve(101);String body=json.writeValueAsString(com.wenwen.vo.AiCohortVo.object("filters",entry.path("filters")));JsonNode first=success(body);String id=first.path("analysisId").asText(),cursor=first.path("patients").path("nextCursor").asText();
        JsonNode old=page(id,cursor);assertEquals(3,patientById(old,111).path("das28At").asDouble());
        visit(1111,111,0,"{\"result\":{\"crpScore\":9}}","2026-10-07",null,null);JsonNode retained=page(id,cursor);assertEquals(ids(old),ids(retained));assertEquals(3,patientById(retained,111).path("das28At").asDouble());
        JsonNode fresh=success(body);assertNotEquals(id,fresh.path("analysisId").asText());assertEquals("111",ids(fresh).get(0));assertEquals(9,patientById(fresh,111).path("das28At").asDouble());assertEquals(22,fresh.path("n").asInt());
        sql("DELETE FROM patient_follow_up_history WHERE id=1111");JsonNode deleted=success(body);assertEquals(3,patientById(page(deleted.path("analysisId").asText(),deleted.path("patients").path("nextCursor").asText()),111).path("das28At").asDouble());assertEquals(3,patientById(page(id,cursor),111).path("das28At").asDouble());
        sql("DELETE FROM patient_relation_doctor WHERE doctor_id=101 AND patient_id=127");observed.reset();pageError(id,cursor,409,"SCOPE_CHANGED");assertEquals(0,observed.active.get());assertEquals(observed.borrowed,observed.returned);assertEquals(0,observed.displayReads);
    }
    @Test void actualResolverFiveSelectViewCannotMixWriterClinicalFacts() throws Exception {
        similarPopulation();sql("UPDATE patient_follow_up_history SET fzjc='{\"lfsyz\":10}' WHERE patient_basic_info_id=101");
        java.util.concurrent.atomic.AtomicInteger commits=new java.util.concurrent.atomic.AtomicInteger();
        observed.afterFirstQuery=()->{
            try(java.sql.Connection writer=raw.getConnection();java.sql.Statement statement=writer.createStatement()) {
                assertFalse(observed.queryConnections.contains(System.identityHashCode(writer)));writer.setAutoCommit(false);
                statement.execute("UPDATE patient_basic_info SET gender=1,card_no='110101198610080011' WHERE id=101");
                statement.execute("UPDATE patient_follow_up_history SET fzjc='{\"lfsyz\":35}' WHERE id=1010");writer.commit();commits.incrementAndGet();
            } catch(java.sql.SQLException e) { throw new AssertionError("合成writer屏障失败",e); }
        };
        JsonNode current=resolve(101);assertEquals("F",current.path("filters").path("sex").asText());assertEquals("35-45",current.path("filters").path("age").asText());assertTrue(current.path("filters").path("sero").isNull(),"当前视图必须读取唯一旧RF10；混入新RF35会误加阳性");assertEquals(0,current.path("missingBasis").size());
        assertEquals(1,commits.get());assertEquals(1,new java.util.HashSet<>(observed.queryConnections).size());assertEquals(java.util.Collections.nCopies(5,java.sql.Connection.TRANSACTION_REPEATABLE_READ),observed.isolations);assertEquals(java.util.Collections.nCopies(5,false),observed.autoCommits);resolverReleased();
        observed.reset();JsonNode next=resolve(101);assertEquals("M",next.path("filters").path("sex").asText());assertEquals("34-44",next.path("filters").path("age").asText());assertEquals("1",next.path("filters").path("sero").asText());assertEquals(0,next.path("missingBasis").size());resolverReleased();assertEquals(0,runs());
    }
    @Test void resolverRealSqlFailureIs503AndReleasesBeforeCleanupOrRecovery() throws Exception {
        similarPopulation();String before=sourceDigest();assertEquals("READY",resolve(101).path("status").asText());observed.reset();
        java.util.concurrent.atomic.AtomicInteger first=new java.util.concurrent.atomic.AtomicInteger();observed.afterFirstQuery=()->{assertEquals(1,observed.active.get());first.incrementAndGet();};observed.failSecondSql=true;
        resolveError("{\"source\":\"SIMILAR\",\"indexPatientId\":\"101\"}",503,"SERVICE_UNAVAILABLE");assertEquals(1,first.get());assertTrue(observed.mysqlFailures>0);assertEquals("42S02",observed.lastSqlState);assertEquals(1146,observed.lastMysqlError);assertEquals(3,observed.selects);assertEquals(1,observed.borrowed);assertEquals(1,observed.returned);assertEquals(0,observed.active.get());assertEquals(0,runs());assertEquals(before,sourceDigest());
        observed.reset();assertEquals("READY",resolve(101).path("status").asText());resolverReleased();assertEquals(0,runs());
    }
    @Test void postSourceProgramFailureHasNoLiveOwnerOrPartialCandidate() throws Exception {
        similarPopulation();assertEquals("READY",resolve(101).path("status").asText());observed.reset();
        java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();clock.onInstant=()->{assertEquals(0,observed.active.get(),"Clock点位在身份后源前及owner退出后");if(calls.incrementAndGet()==2)throw new IllegalStateException("synthetic-private-entry-fault-CANARY");};
        org.springframework.test.web.servlet.MvcResult result=resolveRequest("{\"source\":\"SIMILAR\",\"indexPatientId\":\"101\"}");assertEquals(503,result.getResponse().getStatus());JsonNode envelope=json.readTree(result.getResponse().getContentAsByteArray());assertEquals("SERVICE_UNAVAILABLE",envelope.path("code").asText());assertTrue(envelope.path("data").isNull());assertFalse(result.getResponse().getContentAsString().contains("CANARY"));assertEquals(2,calls.get());resolverReleased();assertEquals(0,runs());
        clock.onInstant=null;observed.reset();assertEquals("READY",resolve(101).path("status").asText());resolverReleased();
    }
    @Test void candidatePayloadContainsNoIndexIdentityOrClinicalProvenance() throws Exception {
        similarPopulation();sql("UPDATE patient_follow_up_history SET bqpg='{\"result\":{\"crpScore\":3},\"private\":\"synthetic-entry-case-CANARY\"}' WHERE id=1010");String before=sourceDigest();
        JsonNode entry=resolve(101);String text=json.writeValueAsString(entry);
        for(String secret:new String[]{"synthetic-similar-name-CANARY","synthetic-entry-case-CANARY","110101198610070011","1986-10-07","indexPatientId","clinicalProvenance","provenance","cardNo","card_no","name","studyNo","bqpg","fzjc"})assertFalse(text.contains(secret),secret);
        resolverReleased();assertEquals(0,runs());assertEquals(0,observed.inserts);assertEquals(0,observed.deletes);assertEquals(before,sourceDigest());
    }
    @Test void resolverNeedsNoResultSigningKeyAndNeverCreatesAnalysis() throws Exception {
        similarPopulation();context.close();syntheticCursorKey=null;buildContext(true);observed.reset();
        assertEquals("READY",resolve(101).path("status").asText());resolverReleased();assertEquals(0,runs());assertEquals(0,observed.inserts);assertEquals(0,observed.deletes);
        observed.reset();error("{}",503,"SERVICE_UNAVAILABLE");assertEquals(0,observed.borrowed);assertEquals(0,observed.selects);assertEquals(0,runs());
    }
    private java.util.Set<String> fields(JsonNode node){java.util.Set<String> out=new java.util.HashSet<>();node.fieldNames().forEachRemaining(out::add);return out;}
    private java.util.List<String> sequence(int from,int to){java.util.List<String> out=new java.util.ArrayList<>();for(int n=from;n<=to;n++)out.add(Integer.toString(n));return out;}
}
