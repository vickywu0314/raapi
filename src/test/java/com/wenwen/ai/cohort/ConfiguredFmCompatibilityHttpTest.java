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
    @Test void changedFmVersionExpiresCohortQueryAndKeepsIndependentComparison()throws Exception {
        fixedPatients();JsonNode cohort=success("{}"),compared=compareSuccess(filtersBody(true));String id=cohort.path("analysisId").asText(),compareId=compared.path("compareResultId").asText();
        summary("COHORT",id,"synthetic-fm-v1");summary("COMPARE",compareId,"synthetic-fm-v1");
        analysisProperties.put("ra.ai.fm-evidence.json","{\"version\":\"synthetic-fm-v2\",\"records\":[]}");context.close();buildContext(true);
        routeError("/api/ra/ai/cohortPatients",json.writeValueAsBytes(com.wenwen.vo.AiCohortVo.object("analysisId",id,"cursor",cohort.path("patients").path("nextCursor").asText())),410,"ANALYSIS_EXPIRED");
        sameRetainedComparison(compared,readComparison(compareId));summary("COMPARE",compareId,"synthetic-fm-v1");assertEquals(0,count("ra_ai_analysis_run"));assertEquals(1,count("ra_ai_analysis_query_session"));assertEquals(1,count("ra_ai_compare_run"));exited();
    }
}
