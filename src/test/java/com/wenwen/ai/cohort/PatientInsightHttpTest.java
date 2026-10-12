package com.wenwen.ai.cohort;

import com.fasterxml.jackson.databind.JsonNode;
import com.wenwen.ai.scope.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class PatientInsightHttpTest extends DescriptiveHttpFixture {
    final Map<String,Object> properties=new HashMap<>();
    PatientInsightHttpTest(){properties.put("ra.ai.fm-evidence.json",ConfiguredFmHttpTest.configuration("synthetic-fm-v1",0));properties.put("ra.ai.drug-dictionary.json",ConfiguredFmHttpTest.drugConfiguration());}
    @Configuration
    @ComponentScan(basePackages={"com.wenwen.controller.ra","com.wenwen.ai.insight"},useDefaultFilters=false,
        includeFilters=@ComponentScan.Filter(type=FilterType.REGEX,pattern="com\\.wenwen\\.(controller\\.ra\\.PatientInsightController|ai\\.insight\\.PatientInsightService)"))
    static class InsightDiscovery {}
    @Override void buildContext(boolean ignored){super.buildContext(false);}
    @Override void beforeRefresh(){context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("insight-synthetic",properties));context.register(InsightDiscovery.class);}
    MockHttpSession session(){MockHttpSession s=new MockHttpSession();s.setAttribute(AuthenticatedDoctorSession.ATTRIBUTE,new AuthenticatedDoctorSession(new TrustedDoctor(101)));return s;}
    @Override MvcResult request(String body)throws Exception{return http.perform(post("/api/ra/ai/patientInsight").session(session()).contentType("application/json").content(body)).andReturn();}
    @Test void returnsActualHistoryThroughTrustedHttp()throws Exception{
        sixPatients();sql("DELETE FROM patient_follow_up_history WHERE patient_basic_info_id=1");
        rawVisit(1001,1,"2026-01-01","4.11","{\"cfydb\":20}",null);observed.reset();
        assertTrue(context.getBean(PrincipalProvider.class) instanceof SessionPrincipalProvider);
        JsonNode d=success("{\"patientId\":\"1\"}");assertEquals("1",d.path("patient").path("patientId").asText());
        assertEquals("合成患者1",d.path("patient").path("name").asText());
        assertEquals(1,d.path("visits").size());assertEquals("1001",d.path("visits").get(0).path("visitId").asText());
        assertEquals(0,new java.math.BigDecimal("4.11").compareTo(d.path("visits").get(0).path("metrics").path("das28Crp").path("value").decimalValue()));
        sourceReleased();assertEquals(0,observed.inserts);
    }
    @Test void projectsOnlyRecordedMetricsAndClinicalProvenance()throws Exception{
        sixPatients();sql("DELETE FROM patient_follow_up_history WHERE patient_basic_info_id=1");
        visit(1001,1,0,"{\"result\":{\"crpScore\":4.11,\"ytgjs\":4,\"zzgjs\":2},\"ztScoreByPatient\":70,\"hqaScore\":1.4,\"tjScore\":99}","2026-01-01",null,"{\"cfydb\":20,\"lfsyz\":35}");observed.reset();
        JsonNode d=success("{\"patientId\":\"1\"}");JsonNode p=d.path("patient"),v=d.path("visits").get(0),m=v.path("metrics");
        assertEquals(4,d.size());assertEquals(10,p.size());assertEquals("RA-P03-00001",p.path("studyNo").asText());assertEquals("F",p.path("sex").asText());assertEquals(40,p.path("age").asInt());assertEquals(1,p.path("diseaseDurationYears").asInt());assertEquals("TRUE",p.path("sero").asText());assertEquals("TRUE",p.path("fm").asText());assertEquals("UNKNOWN",p.path("as").asText());
        assertEquals("patient_basic_info.card_no",p.path("clinicalProvenance").path("age").path("sourceField").asText());assertEquals("CONFIGURED_FM_EVIDENCE",p.path("clinicalProvenance").path("fm").path("source").asText());
        assertEquals("OBSERVED",v.path("temporalStatus").asText());assertEquals("2026-01-01",v.path("observedAt").asText());assertEquals(7,m.size());
        String[] keys={"das28Crp","tjc","sjc","gh","haq","crp"},values={"4.11","4","2","70","1.4","20"},fields={"bqpg.result.crpScore","bqpg.result.ytgjs","bqpg.result.zzgjs","bqpg.ztScoreByPatient","bqpg.hqaScore","fzjc.cfydb"};
        for(int n=0;n<keys.length;n++){JsonNode metric=m.path(keys[n]);assertEquals(7,metric.size());assertEquals(0,new java.math.BigDecimal(values[n]).compareTo(metric.path("value").decimalValue()));assertEquals("1001",metric.path("sourceVisitId").asText());assertEquals(fields[n],metric.path("sourceField").asText());assertEquals("2026-01-01",metric.path("observedAt").asText());assertTrue(metric.path("missingReason").isNull());}
        assertTrue(m.path("das28Crp").path("unit").isNull());assertEquals("mg/L",m.path("crp").path("unit").asText());assertTrue(m.path("pain").path("value").isNull());assertEquals("UNVERIFIED_SCALE",m.path("pain").path("missingReason").asText());assertTrue(m.path("pain").path("quality").toString().contains("SCALE_UNVERIFIED"));assertFalse(d.toString().contains("110101"));assertFalse(d.toString().contains("tjScore\":99"));
        JsonNode meta=d.path("meta"),versions=meta.path("policyVersions");assertEquals("RA",meta.path("studyCode").asText());assertEquals("2026-10-07",meta.path("asOfDate").asText());assertEquals(8,versions.size());assertEquals("dev-insight-v01",versions.path("insight").asText());assertEquals("synthetic-drug-v1",versions.path("drugDictionary").asText());assertEquals("synthetic-fm-v1",versions.path("fmSource").asText());sourceReleased();
    }

    @Test void retainsActualDatesMissingMetricsAndNumericOrder()throws Exception{
        sixPatients();sql("DELETE FROM patient_follow_up_history WHERE patient_basic_info_id=1");
        rawVisit(1003,1,"2026-09-01","2.31","{\"cfydb\":6}",null);rawVisit(1002,1,"2026-04-01","2.70","{\"cfydb\":10}",null);rawVisit(1001,1,"2026-01-01","4.11","{\"cfydb\":20}",null);
        rawVisit(9223372036854775807L,1,"2026-04-01",null,"{\"cfydb\":8}",null);rawVisit(9007199254740993L,1,"2026-04-01",null,null,null);rawVisit(9007199254740992L,1,"2026-04-01",null,null,null);
        rawVisit(1004,1,"2026-10-08","2",null,null);rawVisit(1005,1,null,null,"{\"cfydb\":3}",null);observed.reset();
        JsonNode visits=success("{\"patientId\":\"1\"}").path("visits");
        String[] ids={"1001","1002","9007199254740992","9007199254740993","9223372036854775807","1003","1004","1005"};assertEquals(ids.length,visits.size());for(int n=0;n<ids.length;n++)assertEquals(ids[n],visits.get(n).path("visitId").asText());
        assertEquals("FUTURE",visits.get(6).path("temporalStatus").asText());assertEquals("UNDATED",visits.get(7).path("temporalStatus").asText());assertTrue(visits.get(7).path("observedAt").isNull());
        JsonNode missing=visits.get(4).path("metrics");assertTrue(missing.path("das28Crp").path("value").isNull());assertEquals("MISSING_OR_INVALID_CRP",missing.path("das28Crp").path("missingReason").asText());assertEquals(8,missing.path("crp").path("value").intValue());assertEquals("MISSING_VALUE",missing.path("tjc").path("missingReason").asText());sourceReleased();
        sql("DELETE FROM patient_follow_up_history WHERE patient_basic_info_id=1");observed.reset();assertEquals(0,success("{\"patientId\":\"1\"}").path("visits").size());sourceReleased();
    }

    @Test void sharesTimelineWithHistoryAndDictionaryNames()throws Exception{
        sixPatients();sql("DELETE FROM patient_follow_up_history WHERE patient_basic_info_id=1");
        rawVisit(1001,1,"2026-01-01","4.11","{\"cfydb\":20}",drugs(drug("甲氨蝶呤","2025-12-01",null)));
        rawVisit(1002,1,"2026-04-01","2.70","{\"cfydb\":10}",drugs(drug("synthetic-brand-A","2026-04-01",null)));
        rawVisit(1003,1,"2026-09-01","2.31","{\"cfydb\":6}",null);observed.reset();
        JsonNode tx=success("{\"patientId\":\"1\"}").path("treatment"),current=tx.path("current"),history=tx.path("history");
        assertEquals(3,tx.size());assertEquals(2,history.size());assertEquals("2026-03-31",history.get(0).path("endDate").asText());assertEquals("csDMARD",history.get(0).path("category").asText());
        for(JsonNode episode:history){assertEquals(11,episode.size());assertFalse(episode.has("schemeDurationMonths"));}
        assertEquals("ACTIVE",current.path("state").asText());assertEquals("TNFi",current.path("category").asText());assertEquals(2,current.path("line").intValue());assertEquals(6,current.path("schemeDurationMonths").intValue());assertEquals("EXPLICIT",current.path("startConfidence").asText());assertEquals(json.readTree("[\"synthetic:g2\"]"),current.path("genericDrugIds"));assertTrue(current.path("provenance").path("quality").toString().contains("CARRIED_FORWARD"));
        assertEquals(2,tx.path("drugNames").size());assertEquals("依那西普",tx.path("drugNames").get(1).path("name").asText());sourceReleased();
        sql("DELETE FROM patient_follow_up_history WHERE patient_basic_info_id=1");rawVisit(1004,1,"2026-04-01",null,null,drugs(drug("synthetic-brand-A",null,null)));observed.reset();JsonNode estimated=success("{\"patientId\":\"1\"}").path("treatment").path("current");assertEquals("ESTIMATED_FIRST_OBSERVED",estimated.path("startConfidence").asText());assertEquals("2026-04-01",estimated.path("estimatedStartDate").asText());sourceReleased();
        sql("DELETE FROM patient_follow_up_history WHERE patient_basic_info_id=1");rawVisit(1005,1,"2026-04-01",null,null,drugs(drug("unrecognized-private",null,null)));observed.reset();JsonNode unknown=success("{\"patientId\":\"1\"}").path("treatment");assertEquals("UNKNOWN",unknown.path("current").path("state").asText());assertEquals(0,unknown.path("drugNames").size());sourceReleased();
        sql("DELETE FROM patient_follow_up_history WHERE patient_basic_info_id=1");rawVisit(1006,1,"2026-04-01",null,null,drugs(drug("synthetic-brand-A","2026-04-01",null),drug("托法替布","2026-04-01",null)));observed.reset();JsonNode conflict=success("{\"patientId\":\"1\"}").path("treatment").path("current");assertEquals("CONFLICT",conflict.path("state").asText());assertTrue(conflict.path("category").isNull());sourceReleased();
    }
    void zeroSql(){assertEquals(0,observed.borrowed);assertEquals(0,observed.selects);assertEquals(0,observed.active.get());assertEquals(0,observed.inserts);}
    @Test void typedIdentityStrictBodyAndMembershipProtectTheRead()throws Exception{
        sixPatients();observed.reset();bodyRead.bytes=0;
        MvcResult anonymous=http.perform(post("/api/ra/ai/patientInsight").principal(()->"101").header("X-Doctor-Id","101").content("{")).andReturn();assertEquals(401,anonymous.getResponse().getStatus());assertEquals(0,bodyRead.bytes);zeroSql();
        MockHttpSession wrong=session();wrong.setAttribute(AuthenticatedDoctorSession.ATTRIBUTE,"101");assertEquals(401,http.perform(post("/api/ra/ai/patientInsight").session(wrong).content("{")).andReturn().getResponse().getStatus());zeroSql();
        for(String body:Arrays.asList("","null","[]","{","{}","{\"patientId\":1}","{\"patientId\":\"01\"}","{\"patientId\":\"9223372036854775808\"}","{\"patientId\":\"1\",\"doctorId\":\"101\"}","{\"patientId\":\"1\",\"patientId\":\"1\"}","{\"patientId\":\"1\"} {}")){error(body,400,"INVALID_REQUEST");zeroSql();}
        assertEquals(400,http.perform(post("/api/ra/ai/patientInsight").session(session()).content(new byte[]{(byte)0xc3,(byte)0x28})).andReturn().getResponse().getStatus());zeroSql();
        String input="{\"patientId\":\"1\"}";String exact=input+String.join("",Collections.nCopies(4096-input.length()," "));assertEquals(4096,exact.length());success(exact);sourceReleased();observed.reset();bodyRead.bytes=0;error(exact+" ",413,"LIMIT_EXCEEDED");assertEquals(4097,bodyRead.bytes);zeroSql();
        sql("INSERT INTO patient_relation_doctor (doctor_id,patient_id,research_type,miss) VALUES (101,7,6,0)");
        for(String patient:Arrays.asList("7","999")){observed.reset();error("{\"patientId\":\""+patient+"\"}",404,"NOT_FOUND");sourceReleased();}
        context.close();properties.put("ra.ai.api.enabled","false");buildContext(false);bodyRead.bytes=0;error("{",503,"SERVICE_UNAVAILABLE");assertEquals(0,bodyRead.bytes);zeroSql();
    }
    @Test void preservesMaximumAndAdjacentPatientLongsWithoutBatchLeak()throws Exception{
        sixPatients();for(String value:Arrays.asList("9007199254740992","9007199254740993","9223372036854775807")){sql("INSERT INTO patient_basic_info(id,name) VALUES ("+value+",'synthetic-long-"+value+"')");sql("INSERT INTO patient_relation_doctor(doctor_id,patient_id,research_type,miss) VALUES(101,"+value+",0,0)");}
        rawVisit(9223372036854775807L,9223372036854775807L,"2026-01-01","2.31",null,null);
        for(String value:Arrays.asList("9007199254740992","9007199254740993","9223372036854775807")){observed.reset();JsonNode d=success("{\"patientId\":\""+value+"\"}");assertTrue(d.path("patient").path("patientId").isTextual());assertEquals(value,d.path("patient").path("patientId").asText());assertEquals("synthetic-long-"+value,d.path("patient").path("name").asText());assertFalse(d.toString().contains("合成患者"));assertFalse(d.toString().contains("OTHER-SECRET"));sourceReleased();}
    }
    @Test void rrSnapshotVersionsRevocationAndFailuresReleaseOneOwner()throws Exception{
        sixPatients();observed.failInsertSql=true;java.util.concurrent.atomic.AtomicInteger barrier=new java.util.concurrent.atomic.AtomicInteger();
        observed.afterFirstQuery=()->{assertEquals(1,observed.selects);assertEquals(1,observed.active.get());barrier.incrementAndGet();try{sql("UPDATE patient_basic_info SET name='合成改名' WHERE id=1");sql("UPDATE patient_follow_up_history SET bqpg='{\"result\":{\"crpScore\":4.11}}' WHERE patient_basic_info_id=1");}catch(Exception e){throw new RuntimeException(e);}properties.put("ra.ai.drug-dictionary.json",ConfiguredFmHttpTest.drugConfiguration().replace("synthetic-drug-v1","synthetic-drug-v2"));properties.put("ra.ai.fm-evidence.json",ConfiguredFmHttpTest.configuration("synthetic-fm-v2",0));};
        JsonNode old=success("{\"patientId\":\"1\"}");assertEquals(1,barrier.get());assertEquals("合成患者1",old.path("patient").path("name").asText());assertEquals("synthetic-drug-v1",old.path("meta").path("policyVersions").path("drugDictionary").asText());assertEquals("synthetic-fm-v1",old.path("meta").path("policyVersions").path("fmSource").asText());assertEquals(2,old.path("visits").get(1).path("metrics").path("das28Crp").path("value").intValue());sourceReleased();assertEquals(1,new HashSet<>(observed.queryConnections).size());assertEquals(Collections.nCopies(5,java.sql.Connection.TRANSACTION_REPEATABLE_READ),observed.isolations);assertEquals(Collections.nCopies(5,false),observed.autoCommits);assertEquals(0,observed.inserts);assertEquals(0,observed.writeCommits);
        observed.reset();JsonNode next=success("{\"patientId\":\"1\"}");assertEquals("合成改名",next.path("patient").path("name").asText());assertEquals("synthetic-drug-v2",next.path("meta").path("policyVersions").path("drugDictionary").asText());assertEquals("synthetic-fm-v2",next.path("meta").path("policyVersions").path("fmSource").asText());sourceReleased();
        observed.reset();observed.failSecondSql=true;error("{\"patientId\":\"1\"}",503,"SERVICE_UNAVAILABLE");assertTrue(observed.mysqlFailures>0);assertEquals(1,observed.borrowed);assertEquals(1,observed.returned);assertEquals(0,observed.active.get());assertEquals(0,observed.inserts);
        sql("DELETE FROM patient_relation_doctor WHERE doctor_id=101 AND patient_id=1");observed.reset();error("{\"patientId\":\"1\"}",404,"NOT_FOUND");sourceReleased();
        try(java.sql.Connection c=raw.getConnection();java.sql.Statement st=c.createStatement();java.sql.ResultSet rows=st.executeQuery("SELECT COUNT(*) FROM ra_ai_analysis_run")){assertTrue(rows.next());assertEquals(0,rows.getInt(1));}
    }
    @Test void sourceConfigurationErrorsAndFmThreeStatesRemainExplicit()throws Exception{
        sixPatients();for(String state:Arrays.asList("TRUE","FALSE","UNKNOWN")){properties.put("ra.ai.fm-evidence.json",ConfiguredFmHttpTest.configuration("synthetic-fm-v1",0).replace("\"state\":\"TRUE\"","\"state\":\""+state+"\""));observed.reset();assertEquals(state,success("{\"patientId\":\"1\"}").path("patient").path("fm").asText());sourceReleased();}
        for(String key:Arrays.asList("ra.ai.fm-evidence.json","ra.ai.drug-dictionary.json")){Object valid=properties.get(key);properties.put(key,"invalid-private-canary");observed.reset();error("{\"patientId\":\"1\"}",503,"SERVICE_UNAVAILABLE");zeroSql();assertFalse(request("{\"patientId\":\"1\"}").getResponse().getContentAsString().contains("canary"));properties.put(key,valid);}
        properties.remove("ra.ai.fm-evidence.json");observed.reset();JsonNode legacy=success("{\"patientId\":\"4\"}");assertEquals("UNKNOWN",legacy.path("patient").path("fm").asText());assertFalse(legacy.path("meta").path("policyVersions").has("fmSource"));sourceReleased();
    }

    @Test void threeLiteralRowsRetainIndependentMeasuredValues()throws Exception{
        sixPatients();sql("DELETE FROM patient_follow_up_history WHERE patient_basic_info_id=1");
        String[] dates={"2026-01-01","2026-04-01","2026-09-01"},scores={"4.11","2.70","2.31"},crps={"20","10","6"},tjcs={"4","3","2"},sjcs={"2","2","1"},ghs={"70","50","30"},haqs={"1.4","1.2","1.0"};
        for(int n=0;n<3;n++)visit(1001+n,1,0,"{\"result\":{\"crpScore\":"+scores[n]+",\"ytgjs\":"+tjcs[n]+",\"zzgjs\":"+sjcs[n]+"},\"ztScoreByPatient\":"+ghs[n]+",\"hqaScore\":"+haqs[n]+",\"tjScore\":99}",dates[n],null,"{\"cfydb\":"+crps[n]+"}");observed.reset();JsonNode data=success("{\"patientId\":\"1\"}");assertEquals(3,data.path("visits").size());
        for(int n=0;n<3;n++){JsonNode v=data.path("visits").get(n);assertEquals(dates[n],v.path("observedAt").asText());String[] keys={"das28Crp","crp","tjc","sjc","gh","haq"},values={scores[n],crps[n],tjcs[n],sjcs[n],ghs[n],haqs[n]};for(int k=0;k<keys.length;k++)assertEquals(0,new java.math.BigDecimal(values[k]).compareTo(v.path("metrics").path(keys[k]).path("value").decimalValue()));assertTrue(v.path("metrics").path("pain").path("value").isNull());assertFalse(v.path("metrics").path("pain").has("raw"));}sourceReleased();
    }

}
