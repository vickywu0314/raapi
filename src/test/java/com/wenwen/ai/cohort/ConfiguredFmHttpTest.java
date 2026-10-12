package com.wenwen.ai.cohort;

import com.fasterxml.jackson.databind.JsonNode;
import com.wenwen.ai.scope.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class ConfiguredFmHttpTest extends DescriptiveHttpFixture {
    final Map<String,Object> properties=new HashMap<>();
    ConfiguredFmHttpTest(){properties.put("ra.ai.fm-evidence.json",configuration("synthetic-fm-v1",0));}
    static String configuration(String version,int omitted){
        List<String> rows=new ArrayList<>();
        for(int i=1;i<=6;i++)if(i!=omitted)rows.add("{\"patientId\":\""+i+"\",\"state\":\""+(i<=3?"TRUE":"FALSE")+"\",\"evidenceId\":\"synthetic:fm"+i+"\",\"observedAt\":\"2026-10-06\"}");
        return "{\"version\":\""+version+"\",\"records\":["+String.join(",",rows)+"]}";
    }
    @Override void buildContext(boolean ignored){super.buildContext(false);}
    @Override void beforeRefresh(){context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("synthetic-fm",properties));context.register(com.wenwen.controller.ra.AiOverviewController.class,com.wenwen.ai.overview.AiOverviewService.class);}
    MockHttpSession session(){MockHttpSession s=new MockHttpSession();s.setAttribute(AuthenticatedDoctorSession.ATTRIBUTE,new AuthenticatedDoctorSession(new TrustedDoctor(101)));return s;}
    @Override MvcResult request(String body)throws Exception{return http.perform(post("/api/ra/ai/cohort").session(session()).contentType("application/json").content(body)).andReturn();}
    @Test void explicitFalseSourceReachesRealStatistics()throws Exception{
        sixPatients();assertTrue(context.getBean(PrincipalProvider.class) instanceof SessionPrincipalProvider);
        JsonNode d=success("{}");assertEquals(3,d.path("fm").path("nOther").asInt());
        assertEquals(6,d.path("n").asInt());assertEquals(3,d.path("fm").path("nFM").asInt());assertEquals(0,d.path("fm").path("unknownN").asInt());
        assertEquals(6,d.path("fm").path("rows").size());
        assertEquals("synthetic-fm-v1",d.path("meta").path("policyVersions").path("fmSource").asText());
        JsonNode rows=d.path("fm").path("rows"),das=rows.get(0);
        decimal(das,"meanFM","2.47");decimal(das,"meanOther","4.105");decimal(das,"difference","-1.635");
        assertEquals(3,das.path("nFMValid").asInt());assertEquals(2,das.path("nOtherValid").asInt());
        assertEquals("2.5",das.path("displayFM").asText());assertEquals("4.1",das.path("displayOther").asText());assertEquals("-1.6",das.path("displayDifference").asText());assertTrue(das.path("highlight").asBoolean());
        for(int i:new int[]{1,2,3,5}){decimal(rows.get(i),"meanFM",i==1?"2":i==2?"1":i==3?"6":"1.2");decimal(rows.get(i),"meanOther",i==1?"2":i==2?"1":i==3?"6":"1.2");}
        JsonNode pain=rows.get(4);assertEquals(0,pain.path("nFMValid").asInt());assertEquals(0,pain.path("nOtherValid").asInt());assertEquals("NO_DATA",pain.path("status").asText());assertEquals("MISSING_METRIC",pain.path("reason").asText());
        for(JsonNode r:rows){assertEquals("VALID_N_LT_5",r.path("test").path("reason").asText());assertTrue(r.path("test").path("pValue").isNull());}
        for(int i=1;i<=6;i++){JsonNode p=patientById(d,i),fm=p.path("clinicalProvenance").path("fm");assertEquals(i<=3?"TRUE":"FALSE",p.path("clinical").path("fm").asText());assertEquals("CONFIGURED_FM_EVIDENCE",fm.path("source").asText());assertEquals(json.readTree("[\"SOURCE_UNVERIFIED\"]"),fm.path("quality"));assertTrue(fm.path("missingReason").isNull());assertEquals("synthetic:fm"+i,fm.path("observations").get(0).path("evidenceId").asText());assertEquals(p.path("clinical").path("fm"),fm.path("observations").get(0).path("fmState"));assertFalse(fm.toString().contains("patientId"));assertTrue(p.path("evaluation").path("pain").path("quality").toString().contains("SCALE_UNVERIFIED")||i==6);}
        released();
    }
    @Test void missingFutureUnknownAndAbsentConfigurationsNeverInventFalse()throws Exception{
        sixPatients();
        for(int omitted:new int[]{6,3}){properties.put("ra.ai.fm-evidence.json",configuration("synthetic-fm-v1",omitted));observed.reset();JsonNode d=success("{}");assertEquals(omitted==6?3:2,d.path("fm").path("nFM").asInt());assertEquals(omitted==6?2:3,d.path("fm").path("nOther").asInt());assertEquals(1,d.path("fm").path("unknownN").asInt());assertEquals("GROUP_SIZE_LT_3",d.path("fm").path("reason").asText());assertEquals(0,d.path("fm").path("rows").size());assertEquals("NO_EFFECTIVE_FM_EVIDENCE",patientById(d,omitted).path("clinicalProvenance").path("fm").path("missingReason").asText());released();}
        properties.remove("ra.ai.fm-evidence.json");observed.reset();JsonNode legacy=success("{}");assertEquals(3,legacy.path("fm").path("nFM").asInt());assertEquals(0,legacy.path("fm").path("nOther").asInt());assertEquals(3,legacy.path("fm").path("unknownN").asInt());assertFalse(legacy.path("meta").path("policyVersions").has("fmSource"));released();
        properties.put("ra.ai.fm-evidence.json","{\"version\":\"empty-v1\",\"records\":[]}");observed.reset();JsonNode empty=success("{}");assertEquals(0,empty.path("fm").path("nFM").asInt());assertEquals(0,empty.path("fm").path("nOther").asInt());assertEquals(6,empty.path("fm").path("unknownN").asInt());released();
        properties.put("ra.ai.fm-evidence.json",configuration("synthetic-fm-v1",0).replace("\"patientId\":\"4\",\"state\":\"FALSE\"","\"patientId\":\"4\",\"state\":\"UNKNOWN\"").replace("synthetic:fm5\",\"observedAt\":\"2026-10-06","synthetic:fm5\",\"observedAt\":\"2026-10-08"));observed.reset();JsonNode unknown=success("{}");assertEquals(2,unknown.path("fm").path("unknownN").asInt());assertEquals("EXPLICIT_UNKNOWN",patientById(unknown,4).path("clinicalProvenance").path("fm").path("missingReason").asText());assertEquals(1,patientById(unknown,4).path("clinicalProvenance").path("fm").path("observations").size());assertEquals(0,patientById(unknown,5).path("clinicalProvenance").path("fm").path("observations").size());released();
        sql("INSERT INTO patient_relation_doctor (doctor_id,patient_id,research_type,miss) VALUES (101,7,6,0)");
        properties.put("ra.ai.fm-evidence.json",configuration("synthetic-fm-v1",0).replace("}]}","},{\"patientId\":\"7\",\"state\":\"TRUE\",\"evidenceId\":\"other:7\",\"observedAt\":\"2026-10-06\"},{\"patientId\":\"9223372036854775806\",\"state\":\"TRUE\",\"evidenceId\":\"other:max\",\"observedAt\":\"2026-10-06\"}]}"));observed.reset();assertEquals(6,success("{}").path("n").asInt());released();
        observed.reset();assertEquals(0,success("{\"filters\":{\"cm\":\"none\"}}").path("n").asInt());released();
    }
    @Test void overviewSharesConfiguredSourceAndReadOnlySnapshot()throws Exception{
        sixPatients();observed.failInsertSql=true;
        MvcResult r=http.perform(post("/api/ra/ai/overview").session(session()).contentType("application/json").content("{}")).andReturn();assertEquals(200,r.getResponse().getStatus());JsonNode d=json.readTree(r.getResponse().getContentAsByteArray()).path("data");
        assertEquals("synthetic-fm-v1",d.path("meta").path("policyVersions").path("fmSource").asText());assertEquals(6,d.path("studyTotal").asInt());assertEquals(3,d.path("fmN").asInt());
        sourceReleased();assertEquals(0,observed.inserts);assertEquals(0,observed.writeCommits);assertEquals(1,new HashSet<>(observed.queryConnections).size());assertEquals(Collections.nCopies(5,java.sql.Connection.TRANSACTION_REPEATABLE_READ),observed.isolations);assertEquals(Collections.nCopies(5,false),observed.autoCommits);
        try(java.sql.Connection c=raw.getConnection();java.sql.Statement st=c.createStatement();java.sql.ResultSet rows=st.executeQuery("SELECT COUNT(*) FROM ra_ai_analysis_run")){assertTrue(rows.next());assertEquals(0,rows.getInt(1));}
    }
    @Test void capturedVersionCannotDriftAtRealFirstSqlBarrier()throws Exception{
        sixPatients();java.util.concurrent.atomic.AtomicInteger reached=new java.util.concurrent.atomic.AtomicInteger();
        observed.afterFirstQuery=()->{assertEquals(1,observed.selects);assertEquals(1,observed.active.get());reached.incrementAndGet();properties.put("ra.ai.fm-evidence.json",configuration("synthetic-fm-v2",0).replace("FALSE","TRUE"));System.out.println("P03b real first SELECT active=1; FM V1 -> V2");};
        JsonNode first=success("{}");assertEquals(1,reached.get());assertNull(observed.afterFirstQuery);assertEquals("synthetic-fm-v1",first.path("meta").path("policyVersions").path("fmSource").asText());assertEquals(3,first.path("fm").path("nOther").asInt());for(int i=4;i<=6;i++)assertEquals("FALSE",patientById(first,i).path("clinical").path("fm").asText());released();
        observed.reset();JsonNode next=success("{}");assertEquals("synthetic-fm-v2",next.path("meta").path("policyVersions").path("fmSource").asText());assertEquals(6,next.path("fm").path("nFM").asInt());assertEquals(0,next.path("fm").path("nOther").asInt());released();
    }
    void zeroSql(){assertEquals(0,observed.selects);assertEquals(0,observed.borrowed);assertEquals(0,observed.active.get());assertEquals(0,observed.inserts);assertEquals(0,observed.writeCommits);}
    @Test void invalidSourceRejectsBeforeSqlAndRecoversAfterIdentityAndGates()throws Exception{
        sixPatients();
        for(String bad:Arrays.asList("","private-fm-canary",configuration("v1",0).replace("FALSE","invalid"),configuration("v1",0)+" {}")){properties.put("ra.ai.fm-evidence.json",bad);observed.reset();MvcResult r=request("{}");assertEquals(503,r.getResponse().getStatus());JsonNode e=json.readTree(r.getResponse().getContentAsByteArray());assertEquals("SERVICE_UNAVAILABLE",e.path("code").asText());assertFalse(e.path("success").asBoolean());assertTrue(e.path("data").isNull());assertFalse(r.getResponse().getContentAsString().contains("canary"));zeroSql();}
        bodyRead.bytes=0;MvcResult anonymous=http.perform(post("/api/ra/ai/cohort").principal(()->"101").header("X-Doctor-Id","101").content("{")).andReturn();assertEquals(401,anonymous.getResponse().getStatus());assertEquals(0,bodyRead.bytes);zeroSql();
        properties.put("ra.ai.fm-evidence.json",configuration("v1",0));observed.reset();assertEquals(3,success("{}").path("fm").path("nOther").asInt());released();
        context.close();properties.put("ra.ai.fm-evidence.json","");properties.put("ra.ai.api.enabled","false");buildContext(false);bodyRead.bytes=0;error("{",503,"SERVICE_UNAVAILABLE");assertEquals(0,bodyRead.bytes);zeroSql();
    }
    static String drugConfiguration(){
        String[] names={"甲氨蝶呤","依那西普","托法替布","托珠单抗","阿巴西普","阿达木单抗"},categories={"csDMARD","TNFi","JAKi","IL-6i","Abatacept","TNFi"};List<String> rows=new ArrayList<>();
        for(int i=0;i<6;i++)rows.add("{\"id\":\"synthetic:g"+(i+1)+"\",\"name\":\""+names[i]+"\",\"category\":\""+categories[i]+"\",\"aliases\":"+(i==1?"[\"synthetic-brand-A\"]":"[]")+"}");return "{\"version\":\"synthetic-drug-v1\",\"drugs\":["+String.join(",",rows)+"]}";
    }
    @Test void configuredDrugAndFmSourcesComposeWithoutPrimaryReplacement()throws Exception{
        sixPatients();properties.put("ra.ai.drug-dictionary.json",drugConfiguration());assertEquals(Collections.singleton("cohortDrugDictionary"),context.getBeansOfType(com.wenwen.ai.treatment.DrugDictionary.class).keySet());assertTrue(context.getBean(com.wenwen.ai.treatment.DrugDictionary.class) instanceof com.wenwen.ai.treatment.ConfiguredDrugDictionary);
        try(java.sql.Connection c=raw.getConnection();java.sql.PreparedStatement st=c.prepareStatement("UPDATE patient_follow_up_history SET zlfa=? WHERE patient_basic_info_id=2")){st.setString(1,drugs(drug("synthetic-brand-A","2026-04-06",null)));st.executeUpdate();}
        observed.reset();JsonNode d=success("{}");assertEquals(3,d.path("fm").path("nOther").asInt());assertEquals("synthetic-drug-v1",d.path("meta").path("policyVersions").path("drugDictionary").asText());assertEquals("synthetic-fm-v1",d.path("meta").path("policyVersions").path("fmSource").asText());assertEquals("TNFi",treatment(d,2).path("category").asText());assertEquals(json.readTree("[\"synthetic:g2\"]"),treatment(d,2).path("genericDrugIds"));released();
        observed.reset();JsonNode only=success("{\"filters\":{\"tx\":\"TNFi\"}}");assertEquals(Collections.singletonList("2"),ids(only));released();
        try(java.sql.Connection c=raw.getConnection();java.sql.PreparedStatement st=c.prepareStatement("UPDATE patient_follow_up_history SET zlfa=? WHERE patient_basic_info_id=2")){st.setString(1,drugs(drug("synthetic-brand-A","2026-04-06",null),drug("依那西普","2026-04-06",null)));st.executeUpdate();}
        observed.reset();JsonNode both=success("{}");assertEquals(json.readTree("[\"synthetic:g2\"]"),treatment(both,2).path("genericDrugIds"));assertEquals(1,treatment(both,2).path("targetedDrugHistoryN").asInt());released();
    }
    void decimal(JsonNode n,String key,String expected){assertTrue(n.path(key).isNumber());assertEquals(0,new java.math.BigDecimal(expected).compareTo(n.path(key).decimalValue()),key);}
}
