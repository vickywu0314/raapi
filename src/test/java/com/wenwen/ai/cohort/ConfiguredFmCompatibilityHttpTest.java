package com.wenwen.ai.cohort;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConfiguredFmCompatibilityHttpTest extends ExportApplicationHttpFixture {
    ConfiguredFmCompatibilityHttpTest(){analysisProperties.put("ra.ai.fm-evidence.json",ConfiguredFmHttpTest.configuration("synthetic-fm-v1",0));}
    @Test void configuredComparisonCarriesCapturedVersion()throws Exception{
        fixedPatients();JsonNode c=compareSuccess(filtersBody(true));
        assertEquals("synthetic-fm-v1",c.path("meta").path("policyVersions").path("fmSource").asText());
        assertEquals(14,c.path("studyTotal").asInt());assertEquals(6,c.path("a").path("n").asInt());assertEquals(6,c.path("b").path("n").asInt());
        exited();
    }
    String exportJson(String source,String id){return "{\"source\":\""+source+"\",\""+("COHORT".equals(source)?"analysisId":"compareResultId")+"\":\""+id+"\",\""+("COHORT".equals(source)?"fields":"metrics")+"\":[\"age\"]}";}
    void summary(String source,String id,String version)throws Exception{
        String export=exportJson(source,id);com.wenwen.ai.export.AiExportQuery query=com.wenwen.ai.export.AiExportQuery.read(new java.io.ByteArrayInputStream(export.getBytes("UTF-8")));
        com.wenwen.ai.export.ExportContentReader.Content content=context.getBean(com.wenwen.ai.export.ExportContentReader.class).read(101,query,clock.instant());
        com.wenwen.ai.export.ExportFieldPolicy.Window window=new com.wenwen.ai.export.ExportFieldPolicy.Window(clock.instant());List<Map<String,Object>> blocked=context.getBean(com.wenwen.ai.export.ExportFieldPolicy.class).blocked(query,content.payload,window);assertFalse(blocked.isEmpty());
        com.wenwen.ai.export.ExportApplicationQuery application=com.wenwen.ai.export.ExportApplicationQuery.read(new java.io.ByteArrayInputStream(("{\"requestId\":\""+UUID.randomUUID()+"\",\"export\":"+export+",\"startDate\":\"2026-01-01\",\"endDate\":\"2026-10-07\",\"purposeCode\":\"RESEARCH\",\"reason\":\"synthetic-only\"}").getBytes("UTF-8")));
        String encoded=com.wenwen.ai.export.ExportApplicationSummary.build(application,content,window,blocked);JsonNode decoded=com.wenwen.ai.export.ExportApplicationSummary.read(encoded);if(version==null)assertFalse(decoded.path("sourcePolicyVersions").has("fmSource"));else assertEquals(version,decoded.path("sourcePolicyVersions").path("fmSource").asText());assertFalse(encoded.contains("synthetic-only"));assertEquals(source,decoded.path("source").asText());
    }
    JsonNode page(JsonNode created)throws Exception{return routeSuccess("/api/ra/ai/cohortPatients",json.writeValueAsString(com.wenwen.vo.AiCohortVo.object("analysisId",created.path("analysisId").asText(),"cursor",created.path("patients").path("nextCursor").asText())));}
    @Test void oldAndNewRetainedConsumersSurviveChangedConfigurationAndReopen()throws Exception{
        fixedPatients();analysisProperties.remove("ra.ai.fm-evidence.json");JsonNode oldC=success("{}"),oldB=compareSuccess(filtersBody(true));
        analysisProperties.put("ra.ai.fm-evidence.json",ConfiguredFmHttpTest.configuration("synthetic-fm-v1",0));JsonNode newC=success("{}"),newB=compareSuccess(filtersBody(true));
        String oldCid=oldC.path("analysisId").asText(),newCid=newC.path("analysisId").asText(),oldBid=oldB.path("compareResultId").asText(),newBid=newB.path("compareResultId").asText();
        assertEquals(14,newC.path("n").asInt());assertEquals(3,newC.path("fm").path("nOther").asInt());assertEquals(8,newC.path("fm").path("unknownN").asInt());
        byte[] oc=analysisPayload(oldCid),nc=analysisPayload(newCid),ob=payload(oldBid),nb=payload(newBid);
        summary("COHORT",oldCid,null);summary("COMPARE",oldBid,null);summary("COHORT",newCid,"synthetic-fm-v1");summary("COMPARE",newBid,"synthetic-fm-v1");
        analysisProperties.put("ra.ai.fm-evidence.json","{\"version\":\"synthetic-fm-v2\",\"records\":[]}");context.close();buildContext(true);
        sameRetainedComparison(oldB,readComparison(oldBid));sameRetainedComparison(newB,readComparison(newBid));
        assertFalse(page(oldC).path("meta").path("policyVersions").has("fmSource"));assertEquals("synthetic-fm-v1",page(newC).path("meta").path("policyVersions").path("fmSource").asText());
        summary("COHORT",oldCid,null);summary("COHORT",newCid,"synthetic-fm-v1");summary("COMPARE",oldBid,null);summary("COMPARE",newBid,"synthetic-fm-v1");
        assertArrayEquals(oc,analysisPayload(oldCid));assertArrayEquals(nc,analysisPayload(newCid));assertArrayEquals(ob,payload(oldBid));assertArrayEquals(nb,payload(newBid));assertEquals(2,count("ra_ai_analysis_run"));assertEquals(2,count("ra_ai_compare_run"));assertEquals(0,count("export_application"));assertEquals(0,count("ra_ai_export_request"));exited();
    }

    @Test void persistedCorrectHashContradictionCannotPublishOrRepairAndOldRunSurvivesReopen()throws Exception{
        fixedPatients();analysisProperties.remove("ra.ai.fm-evidence.json");JsonNode old=success("{}");String oldId=old.path("analysisId").asText();byte[] oldBytes=analysisPayload(oldId);
        analysisProperties.put("ra.ai.fm-evidence.json",ConfiguredFmHttpTest.configuration("synthetic-fm-v1",0));JsonNode good=success("{}");String id=good.path("analysisId").asText();
        com.fasterxml.jackson.databind.node.ObjectNode payload=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(analysisPayload(id));com.fasterxml.jackson.databind.node.ObjectNode target=null;
        for(JsonNode r:payload.path("rows"))if("4".equals(r.path("patientId").asText()))target=(com.fasterxml.jackson.databind.node.ObjectNode)r;
        assertNotNull(target);assertEquals("FALSE",target.path("clinical").path("fm").asText());assertEquals("FALSE",target.path("clinicalProvenance").path("fm").path("observations").get(0).path("fmState").asText());
        ((com.fasterxml.jackson.databind.node.ObjectNode)target.path("clinicalProvenance").path("fm").path("observations").get(0)).put("fmState","TRUE");byte[] corrupt=json.writeValueAsBytes(payload);String hash=com.wenwen.ai.result.AnalysisValues.sha256(corrupt);
        try(java.sql.Connection c=raw.getConnection();java.sql.PreparedStatement st=c.prepareStatement("UPDATE ra_ai_analysis_run SET payload=?,payload_sha256=? WHERE id=?")){st.setBytes(1,corrupt);st.setString(2,hash);st.setString(3,id);assertEquals(1,st.executeUpdate());}
        assertEquals(hash,context.getBean(com.wenwen.ai.result.AnalysisResultStore.class).find(id).getPayloadSha256());
        for(int reopen=0;reopen<2;reopen++){
            if(reopen==1){context.close();analysisProperties.put("ra.ai.fm-evidence.json","{\"version\":\"new-v2\",\"records\":[]}");buildContext(true);}
            resetOwners();routeError("/api/ra/ai/cohortPatients",json.writeValueAsBytes(com.wenwen.vo.AiCohortVo.object("analysisId",id,"cursor",good.path("patients").path("nextCursor").asText())),503,"SERVICE_UNAVAILABLE");assertEquals(1,observed.selects,"only retained AnalysisRun SELECT");assertEquals(0,observed.scopeReads);assertEquals(0,observed.displayReads);assertEquals(0,observed.inserts);assertEquals(0,observed.writeCommits);exited();
            assertEquals(4,page(old).path("patients").path("items").size());summary("COHORT",oldId,null);assertArrayEquals(oldBytes,analysisPayload(oldId));assertArrayEquals(corrupt,analysisPayload(id));assertEquals(2,count("ra_ai_analysis_run"));
        }
        analysisProperties.put("ra.ai.fm-evidence.json","bad-private-source");resetOwners();error("{}",503,"SERVICE_UNAVAILABLE");assertEquals(0,observed.selects);assertEquals(0,observed.inserts);assertEquals(2,count("ra_ai_analysis_run"));assertArrayEquals(oldBytes,analysisPayload(oldId));exited();
    }

}
