package com.wenwen.ai.cohort;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 当前 typed 身份与六人独立源沿真实 TCP/SQL 组合。 */
class CurrentAnalysisCompositionTest {
    static final String AI="/api/ra/ai/";
    static CurrentExportCompositionTest.Client start(WebLoopbackApiFixture f,String scenario)throws Exception {
        f.scenario=scenario;f.connect();f.schema();
        CurrentExportCompositionTest.Client c=new CurrentExportCompositionTest.Client(f.start("synthetic",false,Files.createTempDirectory("p07-current-").toFile()));
        assertEquals(204,c.post("/api/ra/test/session","{}").status);return c;
    }
    @Test void typedHomepageQuestionReachesCurrentCohort()throws Exception {
        try(WebLoopbackApiFixture f=new WebLoopbackApiFixture()) {
            CurrentExportCompositionTest.Client c=start(f,"p07-current");
            JsonNode overview=c.data(AI+"overview","{}");assertEquals(6,overview.path("studyTotal").asInt());
            assertEquals(.4,overview.path("targetRate").path("value").asDouble());assertEquals(5,overview.path("targetRate").path("meta").path("denominator").asInt());assertEquals(1,overview.path("targetRate").path("meta").path("unknownN").asInt());
            assertEquals(6,c.data(AI+"cohort","{}").path("n").asInt());
            JsonNode parsed=c.data(AI+"parse","{\"question\":\"女性\"}");assertEquals("OK",parsed.path("parseStatus").asText());
            JsonNode applied=c.data(AI+"applyParse","{\"currentFilters\":{},\"candidateFilters\":"+parsed.path("candidateFilters")+",\"parseStatus\":\"OK\",\"questionRef\":\""+parsed.path("questionRef").asText()+"\"}");
            assertTrue(applied.path("applied").asBoolean());JsonNode cohort=c.data(AI+"cohort","{\"filters\":"+applied.path("filters")+"}");assertEquals(3,cohort.path("n").asInt());assertEquals(.5,cohort.path("stats").path("targetRate").asDouble());assertEquals(0,f.observed.active.get());
        }
    }
    @Test void realPatientVisitAndSimilarEntryKeepSource()throws Exception {
        try(WebLoopbackApiFixture f=new WebLoopbackApiFixture()) {
            CurrentExportCompositionTest.Client c=start(f,"p07-current");
            JsonNode found=c.data(AI+"patientSearch","{\"keyword\":\"合成患者1\",\"page\":1,\"size\":10}");assertEquals("1",found.path("items").get(0).path("patientId").asText());
            JsonNode detail=c.data(AI+"patientDetail","{\"patientId\":\"1\"}");assertEquals("1",detail.path("patientId").asText());assertEquals(2,detail.path("visits").size());
            JsonNode visit=c.data(AI+"visitDetail","{\"patientId\":\"1\",\"visitId\":\"120\"}");assertEquals("120",visit.path("visitId").asText());assertEquals("2026-10-06",visit.path("visitDate").asText());assertEquals(7,visit.path("modules").size());
            JsonNode insight=c.data(AI+"patientInsight","{\"patientId\":\"1\"}");assertEquals(2,insight.path("visits").size());assertEquals("120",insight.path("visits").get(1).path("visitId").asText());assertEquals(2,insight.path("visits").get(1).path("metrics").path("das28Crp").path("value").asInt());assertEquals(6,insight.path("visits").get(1).path("metrics").path("crp").path("value").asInt());assertEquals("120",insight.path("visits").get(1).path("metrics").path("crp").path("sourceVisitId").asText());
            JsonNode entry=c.data(AI+"resolveEntry","{\"source\":\"SIMILAR\",\"indexPatientId\":\"1\"}");assertEquals("F",entry.path("filters").path("sex").asText());assertEquals("35-45",entry.path("filters").path("age").asText());assertEquals("1",entry.path("filters").path("sero").asText());
            JsonNode similar=c.data(AI+"cohort","{\"filters\":"+entry.path("filters")+"}");assertEquals(1,similar.path("n").asInt());assertEquals("1",similar.path("patients").path("items").get(0).path("patientId").asText());assertEquals(0,f.observed.active.get());
        }
    }

    static final String FEMALE="{\"studyCode\":\"RA\",\"sex\":\"F\",\"age\":null,\"sero\":null,\"tx\":null,\"at\":\"now\",\"act\":null,\"cm\":null,\"data\":null,\"ids\":null}";
    @Test void dynamicDefinitionAndRetainedReferencesUseDifferentTimeCuts()throws Exception {
        try(WebLoopbackApiFixture f=new WebLoopbackApiFixture()) {
            CurrentExportCompositionTest.Client c=start(f,"p07-current");
            String saved=c.data("/api/ra/cohort/save","{\"name\":\"Current F\",\"filters\":"+FEMALE+",\"requestId\":\"00000000-0000-4000-8000-000000000701\"}").path("cohortId").asText();
            JsonNode original=c.data("/api/ra/cohort/open","{\"cohortId\":\""+saved+"\"}").path("analysis");assertEquals(3,original.path("n").asInt());byte[] originalPayload;try(java.sql.Connection connection=f.raw.getConnection();java.sql.PreparedStatement q=connection.prepareStatement("SELECT payload FROM ra_ai_analysis_run WHERE id=?")){q.setString(1,original.path("analysisId").asText());try(java.sql.ResultSet r=q.executeQuery()){assertTrue(r.next());originalPayload=r.getBytes(1);}}
            try(java.sql.Connection connection=f.raw.getConnection();java.sql.Statement q=connection.createStatement();java.sql.ResultSet r=q.executeQuery("SELECT filters_utf8 FROM ra_private_cohort_definition")){assertTrue(r.next());JsonNode filters=WebLoopbackApiFixture.JSON.readTree(r.getBytes(1));assertEquals(10,filters.size());assertEquals("F",filters.path("sex").asText());assertTrue(filters.path("ids").isNull());assertFalse(filters.has("patients"));}
            JsonNode before=c.data(AI+"compare","{\"a\":{\"source\":\"FILTERS\",\"filters\":{\"sex\":\"M\"}},\"b\":{\"source\":\"SAVED\",\"cohortId\":\""+saved+"\"},\"excludeOverlap\":false}");assertEquals(3,before.path("a").path("n").asInt());assertEquals(3,before.path("b").path("n").asInt());
            CurrentExportCompositionTest.sql(f,"UPDATE patient_basic_info SET gender=1 WHERE id=6");
            JsonNode next=c.data("/api/ra/cohort/open","{\"cohortId\":\""+saved+"\"}").path("analysis");assertEquals(2,next.path("n").asInt());assertNotEquals(original.path("analysisId"),next.path("analysisId"));
            String originalId=original.path("analysisId").asText();
            try(java.sql.Connection connection=f.raw.getConnection();java.sql.PreparedStatement q=connection.prepareStatement("SELECT payload,expires_at_ms FROM ra_ai_analysis_run WHERE id=?")){q.setString(1,originalId);try(java.sql.ResultSet r=q.executeQuery()){assertTrue(r.next());assertArrayEquals(originalPayload,r.getBytes(1));JsonNode retained=WebLoopbackApiFixture.JSON.readTree(r.getBytes(1));assertEquals(3,retained.path("n").asInt());assertEquals(3,retained.path("rows").size());assertEquals(java.util.Arrays.asList("3","1","6"),retained.path("rows").findValuesAsText("patientId"));assertEquals(original.path("meta").path("expiresAt").asText(),java.time.Instant.ofEpochMilli(r.getLong(2)).toString());}}
            CurrentExportCompositionTest.Reply old=c.post("/api/ra/export/xlsx",CurrentExportCompositionTest.request(originalId,"[\"crpCurrent\"]"));
            // p6无实测来源日期，原retained n3必须在字段门准确拒绝，不能重算为当前n2后成功。
            CurrentExportCompositionTest.blocked(old,"crpCurrent","MISSING_SOURCE_DATE",1);
            JsonNode compare=c.data(AI+"compare","{\"a\":{\"source\":\"FILTERS\",\"filters\":{\"sex\":\"M\"}},\"b\":{\"source\":\"SAVED\",\"cohortId\":\""+saved+"\"},\"excludeOverlap\":false}");assertEquals(4,compare.path("a").path("n").asInt());assertEquals(2,compare.path("b").path("n").asInt());assertEquals(0,compare.path("overlapN").asInt());assertEquals("SAVED",compare.path("b").path("source").asText());assertEquals(saved,compare.path("b").path("cohortId").asText());
            String ref=compare.path("compareResultId").asText();assertEquals(3,c.data(AI+"compareResult","{\"compareResultId\":\""+before.path("compareResultId").asText()+"\"}").path("a").path("n").asInt());
            CurrentExportCompositionTest inspector=new CurrentExportCompositionTest();JsonNode book=inspector.inspect(c.post("/api/ra/export/xlsx","{\"source\":\"COMPARE\",\"compareResultId\":\""+ref+"\",\"metrics\":[\"n\"]}"),"p07-current-compare");assertEquals(ref,book.path("metadata").path("referenceId").asText());CurrentExportCompositionTest.number(book,1,3,"4");CurrentExportCompositionTest.number(book,1,4,"2");
            String id=next.path("analysisId").asText();JsonNode cohortBook=inspector.inspect(c.post("/api/ra/export/xlsx",CurrentExportCompositionTest.request(id,"[\"das28Current\",\"crpCurrent\"]")),"p07-current-cohort");assertEquals(id,cohortBook.path("metadata").path("referenceId").asText());assertEquals("2",cohortBook.path("metadata").path("rowN").asText());assertEquals(0,f.observed.active.get());
        }
    }

    static JsonNode fact(JsonNode d,String key){for(JsonNode f:d.path("facts"))if(key.equals(f.path("key").asText()))return f.path("data");throw new AssertionError("missing fact "+key);}
    static String chat(String scope){return "{\"scope\":"+scope+",\"question\":\"独立达标情况\",\"previousQuestions\":[]}";}
    @Test void threeChatScopesUseCurrentFactsWithoutIdentifiersOutbound()throws Exception {
        try(WebLoopbackApiFixture f=new WebLoopbackApiFixture()) {
            CurrentExportCompositionTest.Client c=start(f,"p07-current");
            JsonNode all=c.data(AI+"chat",chat("{\"kind\":\"ALL\"}"));assertEquals(6,all.path("scope").path("n").asInt());assertEquals(.4,fact(all,"TARGET_RATE").path("value").asDouble());
            JsonNode female=c.data(AI+"chat",chat("{\"kind\":\"COHORT\",\"filters\":"+FEMALE+"}"));assertEquals(3,female.path("scope").path("n").asInt());assertEquals(.5,fact(female,"TARGET_RATE").path("value").asDouble());assertEquals(WebLoopbackApiFixture.JSON.readTree(FEMALE),female.path("scope").path("filters"));
            JsonNode empty=c.data(AI+"chat",chat("{\"kind\":\"COHORT\",\"filters\":"+FEMALE.replace("\"ids\":null","\"ids\":[]")+"}"));assertEquals(0,empty.path("scope").path("n").asInt());assertEquals("NO_DATA",fact(empty,"TARGET_RATE").path("meta").path("status").asText());
            f.planChat("CHAT_VISIT");JsonNode visit=c.data(AI+"chat",chat("{\"kind\":\"PATIENT\",\"patientId\":\"1\",\"visitId\":\"120\"}"));JsonNode measured=fact(visit,"VISIT_MEASUREMENTS");assertEquals("120",measured.path("visitId").asText());assertEquals(2,measured.path("metrics").path("das28Crp").path("value").asInt());assertEquals(6,measured.path("metrics").path("crp").path("value").asInt());
            f.planChat("CHAT_COURSE");JsonNode whole=c.data(AI+"chat",chat("{\"kind\":\"PATIENT\",\"patientId\":\"1\",\"visitId\":null}"));assertEquals(2,fact(whole,"PATIENT_COURSE").path("visits").size());
            java.util.List<AiGatewayLoopbackPeer.Exchange> exchanges=new java.util.ArrayList<>(f.chatHistory);exchanges.addAll(f.chatPlans);for(AiGatewayLoopbackPeer.Exchange e:exchanges)if(e.body!=null){JsonNode wire=WebLoopbackApiFixture.JSON.readTree(e.body);String payload=wire.path("messages").get(1).path("content").asText();for(String secret:new String[]{"patientId","visitId","sourceVisitId","doctorId","studyNo","cardNo","phone","合成患者","RA-P03"})assertFalse(payload.contains(secret),secret);}
            assertEquals(5,f.chatPeer.requests.get());assertEquals(5,CurrentExportCompositionTest.count(f,"ra_ai_chat_audit"));assertEquals(0,f.observed.active.get());
        }
    }

    static void relogin(CurrentExportCompositionTest.Client c)throws Exception{c.cookie=null;assertEquals(204,c.post("/api/ra/test/session","{}").status);}
    static void denied(CurrentExportCompositionTest.Reply r,int status,String code)throws Exception{assertEquals(status,r.status,new String(r.bytes,"UTF-8"));assertEquals(code,WebLoopbackApiFixture.JSON.readTree(r.bytes).path("code").asText());}
    @Test void currentTwentyThreeRoutesCloseBeforeBodySqlAndPeersAndReopenKeepsRows()throws Exception {
        try(WebLoopbackApiFixture f=new WebLoopbackApiFixture()) {
            CurrentExportCompositionTest.Client c=start(f,"p07-current");String saved=c.data("/api/ra/cohort/save","{\"name\":\"Preserved\",\"filters\":"+FEMALE+",\"requestId\":\"00000000-0000-4000-8000-000000000711\"}").path("cohortId").asText();
            String id=c.data(AI+"cohort","{\"filters\":{\"ids\":[\"1\",\"2\",\"3\",\"4\",\"5\"]}}").path("analysisId").asText();
            String compared=c.data(AI+"compare","{\"a\":{\"source\":\"FILTERS\",\"filters\":{\"sex\":\"M\"}},\"b\":{\"source\":\"SAVED\",\"cohortId\":\""+saved+"\"},\"excludeOverlap\":false}").path("compareResultId").asText();
            assertEquals("SENT",c.data("/api/ra/export/applyAnalysis",CurrentExportCompositionTest.application(id)).path("notification").path("mailStatus").asText());
            java.util.Map<String,Object> before=f.compositionRows();f.rebuildComposition(false,true);assertEquals(before,f.compositionRows());
            // 会话由实际容器处理；明确不带Cookie测匿名401，再获取新typed Cookie验证全局门。
            int borrowed=f.observed.borrowed,writes=f.compositionWrites.get(),chat=f.chatPeer.requests.get(),smtp=f.applicationPeer.connections.get();
            c.cookie=null;for(String path:WebLoopbackApiFixture.CURRENT_PATHS){denied(c.post(path,"{"),401,"UNAUTHENTICATED");assertEquals(0,f.currentBodyBytes,path);}assertEquals(borrowed,f.observed.borrowed);
            relogin(c);assertEquals(23,WebLoopbackApiFixture.CURRENT_PATHS.size());
            denied(c.post(AI+"cohort","{}"),503,"SERVICE_UNAVAILABLE");assertEquals(0,f.currentBodyBytes);assertEquals(borrowed,f.observed.borrowed);assertEquals(writes,f.compositionWrites.get());
            for(String path:WebLoopbackApiFixture.CURRENT_PATHS){denied(c.post(path,"{"),503,"SERVICE_UNAVAILABLE");assertEquals(0,f.currentBodyBytes,path);}assertEquals(borrowed,f.observed.borrowed);assertEquals(writes,f.compositionWrites.get());assertEquals(chat,f.chatPeer.requests.get());assertEquals(smtp,f.applicationPeer.connections.get());assertEquals(before,f.compositionRows());
            f.rebuildComposition(true,false);relogin(c);assertEquals(before,f.compositionRows());denied(c.post("/api/ra/export/xlsx",CurrentExportCompositionTest.request(id,"[\"das28Current\"]")),503,"SERVICE_UNAVAILABLE");assertEquals(0,f.currentBodyBytes);
            for(String path:WebLoopbackApiFixture.CURRENT_PATHS)if(!path.equals("/api/ra/export/xlsx")){assertEquals(400,c.post(path,"{").status,path);assertEquals(1,f.currentBodyBytes,path);}assertEquals(before,f.compositionRows());
            f.rebuildComposition(true,true);relogin(c);assertEquals(before,f.compositionRows());assertEquals(3,c.data(AI+"compareResult","{\"compareResultId\":\""+compared+"\"}").path("a").path("n").asInt());assertEquals(200,c.post("/api/ra/export/xlsx",CurrentExportCompositionTest.request(id,"[\"das28Current\"]")).status);assertEquals(3,c.data("/api/ra/cohort/open","{\"cohortId\":\""+saved+"\"}").path("analysis").path("n").asInt());f.assertCurrentExportOwners();
        }
    }

    @Test void aggregateAndClinicalPermissionsAreIndependentAfterRealRebuild()throws Exception {
        try(WebLoopbackApiFixture f=new WebLoopbackApiFixture()) {
            CurrentExportCompositionTest.Client c=start(f,"p07-current");f.currentAggregateApproved=false;f.rebuildComposition(true,true);relogin(c);int requests=f.chatPeer.requests.get(),borrowed=f.observed.borrowed;
            denied(c.post(AI+"chat",chat("{\"kind\":\"ALL\"}")),403,"CHAT_OUTBOUND_NOT_APPROVED");denied(c.post(AI+"chat",chat("{\"kind\":\"COHORT\",\"filters\":"+FEMALE+"}")),403,"CHAT_OUTBOUND_NOT_APPROVED");assertEquals(requests,f.chatPeer.requests.get());assertEquals(borrowed,f.observed.borrowed);
            f.planChat("CHAT_VISIT");assertEquals("PATIENT",c.data(AI+"chat",chat("{\"kind\":\"PATIENT\",\"patientId\":\"1\",\"visitId\":\"120\"}")).path("scope").path("kind").asText());
            f.currentAggregateApproved=true;f.currentClinicalApproved=false;f.rebuildComposition(true,true);relogin(c);requests=f.chatPeer.requests.get();borrowed=f.observed.borrowed;
            denied(c.post(AI+"chat",chat("{\"kind\":\"PATIENT\",\"patientId\":\"1\"}")),403,"CHAT_OUTBOUND_NOT_APPROVED");assertEquals(requests,f.chatPeer.requests.get());assertEquals(borrowed,f.observed.borrowed);f.planChat("CHAT_AGGREGATE");assertEquals(6,c.data(AI+"chat",chat("{\"kind\":\"ALL\"}")).path("scope").path("n").asInt());assertEquals(0,f.observed.active.get());
        }
    }
    @Test void shanghaiDayExpiryAndRevocationCannotExtendOriginalReferences()throws Exception {
        try(WebLoopbackApiFixture f=new WebLoopbackApiFixture()) {
            f.scenarioClock.now.set(WebLoopbackApiFixture.COMPOSITION_DAY_BEFORE);CurrentExportCompositionTest.Client c=start(f,"p07-current");
            JsonNode old=c.data(AI+"cohort","{\"filters\":{\"sex\":\"F\"}}");assertEquals(3,old.path("n").asInt());assertEquals("2026-10-31",old.path("meta").path("asOfDate").asText());String oldId=old.path("analysisId").asText();
            JsonNode compare=c.data(AI+"compare","{\"a\":{\"source\":\"FILTERS\",\"filters\":{\"sex\":\"M\"}},\"b\":{\"source\":\"FILTERS\",\"filters\":{\"sex\":\"F\"}},\"excludeOverlap\":false}");String compared=compare.path("compareResultId").asText();String expires=old.path("meta").path("expiresAt").asText();
            f.scenarioClock.now.set(WebLoopbackApiFixture.COMPOSITION_DAY_AFTER);JsonNode next=c.data(AI+"cohort","{}");assertEquals("2026-11-01",next.path("meta").path("asOfDate").asText());assertNotEquals(old.path("analysisId"),next.path("analysisId"));assertEquals(compare.path("meta").path("expiresAt"),c.data(AI+"compareResult","{\"compareResultId\":\""+compared+"\"}").path("meta").path("expiresAt"));
            java.util.Map<String,Object> snapshot=f.compositionRows();f.rebuildComposition(true,true);relogin(c);assertEquals(snapshot,f.compositionRows());
            f.scenarioClock.now.set(WebLoopbackApiFixture.COMPOSITION_DAY_BEFORE.plusMillis(900000));assertEquals(expires,f.scenarioClock.instant().toString());denied(c.post("/api/ra/export/xlsx",CurrentExportCompositionTest.request(oldId,"[\"crpCurrent\"]")),410,"ANALYSIS_EXPIRED");denied(c.post(AI+"compareResult","{\"compareResultId\":\""+compared+"\"}"),410,"COMPARE_EXPIRED");denied(c.post("/api/ra/export/xlsx","{\"source\":\"COMPARE\",\"compareResultId\":\""+compared+"\",\"metrics\":[\"n\"]}"),410,"COMPARE_EXPIRED");
        }
        try(WebLoopbackApiFixture f=new WebLoopbackApiFixture()) {
            CurrentExportCompositionTest.Client c=start(f,"p07-current");String id=c.data(AI+"cohort","{}").path("analysisId").asText();String compared=c.data(AI+"compare","{\"a\":{\"source\":\"FILTERS\",\"filters\":{\"sex\":\"M\"}},\"b\":{\"source\":\"FILTERS\",\"filters\":{\"sex\":\"F\"}},\"excludeOverlap\":false}").path("compareResultId").asText();CurrentExportCompositionTest.sql(f,"DELETE FROM patient_relation_doctor WHERE doctor_id=101 AND patient_id=1");
            denied(c.post("/api/ra/export/xlsx",CurrentExportCompositionTest.request(id,"[\"das28Current\"]")),409,"SCOPE_CHANGED");denied(c.post(AI+"compareResult","{\"compareResultId\":\""+compared+"\"}"),409,"SCOPE_CHANGED");assertEquals(5,c.data(AI+"cohort","{}").path("n").asInt());assertEquals(0,f.observed.active.get());
        }
    }

    @Test void partialQuestionPreservesFullCurrentFiltersUntilExplicitConfirmation()throws Exception {
        try(WebLoopbackApiFixture f=new WebLoopbackApiFixture()) {
            CurrentExportCompositionTest.Client c=start(f,"p07-current");String current="{\"sex\":\"M\",\"tx\":\"TNFi\",\"ids\":[\"2\"]}";JsonNode parsed=c.data(AI+"parse","{\"question\":\"女性且吸烟\"}");assertEquals("PARTIAL",parsed.path("parseStatus").asText());assertEquals("REQUIRE_CONFIRMATION",parsed.path("applyMode").asText());
            String command="{\"currentFilters\":"+current+",\"candidateFilters\":"+parsed.path("candidateFilters")+",\"parseStatus\":\"PARTIAL\",\"questionRef\":\""+parsed.path("questionRef").asText()+"\"}";
            JsonNode no=c.data(AI+"applyParse",command);assertFalse(no.path("applied").asBoolean());assertEquals("M",no.path("filters").path("sex").asText());assertEquals("TNFi",no.path("filters").path("tx").asText());assertEquals(WebLoopbackApiFixture.JSON.readTree("[\"2\"]"),no.path("filters").path("ids"));assertEquals(10,no.path("filters").size());
            JsonNode yes=c.data(AI+"applyParse",command.substring(0,command.length()-1)+",\"confirmPartial\":true}");assertTrue(yes.path("applied").asBoolean());assertEquals("F",yes.path("filters").path("sex").asText());assertTrue(yes.path("filters").path("tx").isNull());assertEquals(no.path("filters").path("ids"),yes.path("filters").path("ids"));assertEquals(0,c.data(AI+"cohort","{\"filters\":"+yes.path("filters")+"}").path("n").asInt());assertEquals(0,f.observed.active.get());
        }
    }

}
