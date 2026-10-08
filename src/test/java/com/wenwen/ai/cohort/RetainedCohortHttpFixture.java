package com.wenwen.ai.cohort;
import java.sql.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

abstract class RetainedCohortHttpFixture extends TreatmentHttpFixture {
    static final String SYNTHETIC_KEY="MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    com.fasterxml.jackson.databind.JsonNode page(String id,String cursor) throws Exception {
        org.springframework.test.web.servlet.MvcResult r=pageRequest("{\"analysisId\":\""+id+"\",\"cursor\":\""+cursor+"\"}");
        assertEquals(200,r.getResponse().getStatus(),r.getResponse().getContentAsString());return json.readTree(r.getResponse().getContentAsByteArray()).path("data");
    }
    org.springframework.test.web.servlet.MvcResult pageRequest(String body) throws Exception {return http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/ra/ai/cohortPatients").contentType("application/json").content(body)).andReturn();}
    final String[] scores={null,"3.7",null,"3.5","3.9","3.3","3.6","3.8","3.1","4.0",null,"2.9","3.0","3.2","3.4","2.8",null,null,"3.7","4.2",null,"3.9","3.1","3.8","3.5","3.3",null,"3.6","3.0","4.0","3.8","3.4","3.8","3.2","2.9","2.8","3.7"};
    void population37() throws Exception {
        sql("DELETE FROM patient_follow_up_history");sql("DELETE FROM patient_relation_doctor");sql("DELETE FROM patient_basic_info");
        for(int i=0;i<37;i++) {
            int id=101+i;
            sql("INSERT INTO patient_basic_info(id,name,study_no) VALUES("+id+",'synthetic-retained-name-"+id+"','synthetic-study-"+id+"')");
            sql("INSERT INTO patient_relation_doctor(doctor_id,patient_id,research_type) VALUES(101,"+id+",0)");
            if(scores[i]!=null) visit(id*10,id,0,"{\"result\":{\"crpScore\":"+scores[i]+"}}","2026-10-07",null,null);
        }
    }
    void tiedPopulation(int count) throws Exception {
        sql("DELETE FROM patient_follow_up_history");sql("DELETE FROM patient_relation_doctor");sql("DELETE FROM patient_basic_info");
        for(int i=0;i<count;i++){int id=201+i;sql("INSERT INTO patient_basic_info(id,name) VALUES("+id+",'synthetic-tied')");sql("INSERT INTO patient_relation_doctor(doctor_id,patient_id,research_type) VALUES(101,"+id+",0)");visit(id*10,id,0,"{\"result\":{\"crpScore\":3}}","2026-10-07",null,null);}
    }
    void setting(String name,String value){org.springframework.test.util.ReflectionTestUtils.setField(context.getBean(com.wenwen.ai.result.AnalysisSettings.class),name,value);}
    int runs() throws Exception {try(Connection c=raw.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT COUNT(*) FROM ra_ai_analysis_run")){assertTrue(r.next());return r.getInt(1);}}
    void pageError(String id,String cursor,int status,String code) throws Exception {pageBodyError("{\"analysisId\":\""+id+"\",\"cursor\":\""+cursor+"\"}",status,code);}
    void pageBodyError(String body,int status,String code) throws Exception {org.springframework.test.web.servlet.MvcResult r=pageRequest(body);assertEquals(status,r.getResponse().getStatus(),r.getResponse().getContentAsString());JsonNode n=json.readTree(r.getResponse().getContentAsByteArray());assertEquals(code,n.path("code").asText());assertTrue(n.path("data").isNull());assertFalse(n.path("success").asBoolean());assertNotNull(r.getResponse().getHeader("X-Trace-Id"));}
    void expiredCopies(String source,int count) throws Exception {
        try(Connection c=raw.getConnection();PreparedStatement s=c.prepareStatement("INSERT INTO ra_ai_analysis_run SELECT ?,202,scope_fingerprint,sort_key,payload_version,payload,payload_sha256,created_at_ms,? FROM ra_ai_analysis_run WHERE id=?")) {
            for(int i=1;i<=count;i++){s.setString(1,String.format(java.util.Locale.ROOT,"00000000-0000-0000-0000-%012d",i));s.setLong(2,clock.now.toEpochMilli()-1);s.setString(3,source);s.addBatch();}s.executeBatch();
        }
    }
    String sourceDigest() throws Exception {
        StringBuilder b=new StringBuilder();try(Connection c=raw.getConnection();Statement s=c.createStatement()){
            for(String table:new String[]{"patient_basic_info","patient_relation_doctor","patient_follow_up_history","patient_comorbidity"})try(ResultSet r=s.executeQuery("SELECT * FROM "+table+" ORDER BY id")){while(r.next())for(int i=1;i<=r.getMetaData().getColumnCount();i++)b.append(String.valueOf(r.getObject(i))).append('\n');}
        }
        return com.wenwen.ai.result.AnalysisValues.sha256(b.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    boolean runExists(String id) throws Exception {try(Connection c=raw.getConnection();PreparedStatement s=c.prepareStatement("SELECT COUNT(*) FROM ra_ai_analysis_run WHERE id=?")){s.setString(1,id);try(ResultSet r=s.executeQuery()){assertTrue(r.next());return r.getInt(1)>0;}}}
    void replacePayload(String id,byte[] bytes,boolean correctHash)throws Exception {
        try(Connection c=raw.getConnection();PreparedStatement s=c.prepareStatement("UPDATE ra_ai_analysis_run SET payload=?,payload_sha256=? WHERE id=?")){s.setBytes(1,bytes);s.setString(2,correctHash?com.wenwen.ai.result.AnalysisValues.sha256(bytes):String.join("",Collections.nCopies(64,"0")));s.setString(3,id);assertEquals(1,s.executeUpdate());}
    }
    void treatmentSource(long visit,String value)throws Exception {try(Connection c=raw.getConnection();PreparedStatement s=c.prepareStatement("UPDATE patient_follow_up_history SET zlfa=? WHERE id=?")){s.setString(1,value);s.setLong(2,visit);assertEquals(1,s.executeUpdate());}}
    byte[] stored(String id) throws Exception {
        try(Connection c=raw.getConnection();PreparedStatement s=c.prepareStatement("SELECT payload FROM ra_ai_analysis_run WHERE id=?")) {
            s.setString(1,id);try(ResultSet r=s.executeQuery()){assertTrue(r.next());byte[] b=r.getBytes(1);assertFalse(r.next());return b;}
        }
    }
    List<String> rowIds(JsonNode rows) { List<String> result=new ArrayList<>();rows.forEach(r->result.add(r.path("patientId").asText()));return result; }
}
