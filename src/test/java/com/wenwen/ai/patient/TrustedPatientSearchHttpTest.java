package com.wenwen.ai.patient;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
class TrustedPatientSearchHttpTest extends TrustedPatientHttpFixture {
    private org.springframework.test.web.servlet.MvcResult search(String keyword,int page,int size)throws Exception{
        return http.perform(post("/api/ra/ai/patientSearch").session(session(101)).contentType("application/json").content("{\"keyword\":"+json.writeValueAsString(keyword)+",\"page\":"+page+",\"size\":"+size+"}")).andReturn();
    }
    @Test void findsExactLargeIdThroughTrustedSearch()throws Exception{
        seed();org.springframework.test.web.servlet.MvcResult r=search("9007199254740993",1,20);assertEquals(200,r.getResponse().getStatus());
        JsonNode d=json.readTree(r.getResponse().getContentAsByteArray()).path("data");assertEquals(1,d.path("total").intValue());assertEquals(1,d.path("page").intValue());assertEquals(20,d.path("size").intValue());assertEquals(1,d.path("items").size());
        JsonNode item=d.path("items").get(0);assertEquals(3,item.size());assertEquals("9007199254740993",item.path("patientId").textValue());assertEquals("synthetic-patient-odd",item.path("name").textValue());assertEquals("RA-ODD",item.path("studyNo").textValue());assertNotNull(r.getResponse().getHeader("X-Trace-Id"));
    }
    private JsonNode data(String keyword,int page,int size)throws Exception{org.springframework.test.web.servlet.MvcResult r=search(keyword,page,size);assertEquals(200,r.getResponse().getStatus());return json.readTree(r.getResponse().getContentAsByteArray()).path("data");}
    private void ids(JsonNode d,String... ids){assertEquals(ids.length,d.path("items").size());for(int i=0;i<ids.length;i++){assertEquals(ids[i],d.path("items").get(i).path("patientId").textValue());assertEquals(3,d.path("items").get(i).size());}}
    @Test void stablePagesDeduplicateAndKeepExactLongOrder()throws Exception{
        seed();JsonNode one=data("  ",1,2);ids(one,"9223372036854775807","9007199254740993");assertEquals(3,one.path("total").intValue());
        ids(data("",2,2),"9007199254740992");JsonNode beyond=data("",3,2);ids(beyond);assertEquals(3,beyond.path("total").intValue());assertEquals(3,beyond.path("page").intValue());
        ids(data("9007199254740992",1,20),"9007199254740992");ids(data("RA-EVEN",1,20),"9007199254740992");ids(data("9223372036854775807",1,20),"9223372036854775807");
        sql("UPDATE patient_follow_up_history SET follow_up_date='2026-10-01' WHERE id=9007199254741001");ids(data("",1,20),"9223372036854775807","9007199254740993","9007199254740992");ids(data("",2147483647,200));
    }
    @Test void literalKeywordsAndCurrentScopeAreReadFromSql()throws Exception{
        seed();String[] names={"synthetic%literal","synthetic_literal","synthetic\\literal!"};String[] expected={"9007199254740992","9007199254740993","9223372036854775807"};
        try(java.sql.Connection c=raw.getConnection();java.sql.PreparedStatement p=c.prepareStatement("UPDATE patient_basic_info SET name=? WHERE id=?")){for(int i=0;i<names.length;i++){p.setString(1,names[i]);p.setLong(2,Long.parseLong(expected[i]));assertEquals(1,p.executeUpdate());}}
        String[] keywords={"%","_","\\","!"};for(int i=0;i<keywords.length;i++){JsonNode result=data(keywords[i],1,20);assertEquals(1,result.path("items").size(),"literal keyword index="+i);ids(result,expected[Math.min(i,2)]);}
        // 多个候选字符及200码点边界仍按原文完整匹配，不引入新的keyword长度限制。
        String allCandidates=java.util.stream.IntStream.rangeClosed(33,232).collect(StringBuilder::new,StringBuilder::appendCodePoint,StringBuilder::append).toString();
        ids(data(allCandidates,1,20)); // 超过合成name列容量，合法完整关键词应正常返回空而不是截断。
        String mixed="!\"#$%&'()*+,-./";
        try(java.sql.Connection c=raw.getConnection();java.sql.PreparedStatement p=c.prepareStatement("UPDATE patient_basic_info SET name=? WHERE id=?")){p.setString(1,mixed);p.setLong(2,Long.parseLong(expected[2]));assertEquals(1,p.executeUpdate());}
        ids(data(mixed,1,20),expected[2]);
        try(java.sql.Connection c=raw.getConnection();java.sql.PreparedStatement p=c.prepareStatement("UPDATE patient_basic_info SET name=? WHERE id=?")){p.setString(1,names[2]);p.setLong(2,Long.parseLong(expected[2]));assertEquals(1,p.executeUpdate());}
        for(String keyword:new String[]{"8001","8002","synthetic-other-doctor","synthetic-non-ra"}){JsonNode d=data(keyword,1,20);ids(d);assertEquals(0,d.path("total").intValue());}
        sql("DELETE FROM patient_relation_doctor WHERE doctor_id=101 AND patient_id=9007199254740993");ids(data("9007199254740993",1,20));
        sql("UPDATE patient_basic_info SET study_no=NULL WHERE id=9223372036854775807");JsonNode item=data("9223372036854775807",1,20).path("items").get(0);assertEquals("synthetic\\literal!",item.path("name").textValue());assertTrue(item.path("studyNo").isNull());assertEquals(3,item.size());
        sql("RENAME TABLE patient_follow_up_history TO synthetic_search_offline");try{org.springframework.test.web.servlet.MvcResult r=search("",1,20);assertEquals(503,r.getResponse().getStatus());JsonNode e=json.readTree(r.getResponse().getContentAsByteArray());assertEquals("SERVICE_UNAVAILABLE",e.path("code").textValue());assertTrue(e.path("data").isNull());assertFalse(e.toString().contains("synthetic_search"));}finally{sql("RENAME TABLE synthetic_search_offline TO patient_follow_up_history");}
    }
    private void error(byte[] body,int status,String code)throws Exception{org.springframework.test.web.servlet.MvcResult r=http.perform(post("/api/ra/ai/patientSearch").session(session(101)).contentType("application/json").content(body)).andReturn();assertEquals(status,r.getResponse().getStatus());assertEquals(code,json.readTree(r.getResponse().getContentAsByteArray()).path("code").textValue());assertNotNull(r.getResponse().getHeader("X-Trace-Id"));assertEquals(0,observed.borrowed);}
    @Test void strictBoundedSearchInputRejectsBeforeSql()throws Exception{
        String[] bad={"", "{}", "[]", "null", "{", "{} {}", "{\"keyword\":\"\",\"page\":1,\"size\":20,\"doctorId\":101}","{\"keyword\":\"x\",\"keyword\":\"y\",\"page\":1,\"size\":20}","{\"keyword\":null,\"page\":1,\"size\":20}"};
        for(String b:bad)error(b.getBytes("UTF-8"),400,"INVALID_REQUEST");
        for(String field:new String[]{"page","size"})for(String value:new String[]{"0","-1","1.0","true","\"1\"","2147483648","null"}){String b="{\"keyword\":\"\",\"page\":1,\"size\":20}";b=b.replace(field.equals("page")?"\"page\":1":"\"size\":20","\""+field+"\":"+value);error(b.getBytes("UTF-8"),400,"INVALID_REQUEST");}
        error("{\"keyword\":\"\",\"page\":1,\"size\":201}".getBytes("UTF-8"),400,"INVALID_REQUEST");
        error(new byte[]{(byte)0xc3,(byte)0x28},400,"INVALID_REQUEST");
        for(String k:new String[]{json.writeValueAsString(String.join("",java.util.Collections.nCopies(201,"😀"))),"\"\\ud800\"","\"\\udc00\""})error(("{\"keyword\":"+k+",\"page\":1,\"size\":20}").getBytes("UTF-8"),400,"INVALID_REQUEST");
        error(String.join("",java.util.Collections.nCopies(5000," ")).getBytes("UTF-8"),413,"LIMIT_EXCEEDED");assertEquals(4097,this.body.bytes);
        seed();assertEquals(200,search(String.join("",java.util.Collections.nCopies(200,"😀")),1,20).getResponse().getStatus());
        String valid="{\"keyword\":\"\",\"page\":1,\"size\":20}";valid+=String.join("",java.util.Collections.nCopies(4096-valid.getBytes("UTF-8").length," "));assertEquals(200,http.perform(post("/api/ra/ai/patientSearch").session(session(101)).contentType("application/json").content(valid)).andReturn().getResponse().getStatus());assertEquals(4096,this.body.bytes);
    }
    @Test void identityAndGateRejectBeforeBodyAndSql()throws Exception{
        org.springframework.test.web.servlet.MvcResult r=http.perform(post("/api/ra/ai/patientSearch").content("{").principal(()->"101").header("X-Doctor-Id","101")).andReturn();assertEquals(401,r.getResponse().getStatus());assertEquals(0,body.bytes);assertEquals(0,observed.borrowed);
        org.springframework.mock.web.MockHttpSession wrong=session(101);wrong.setAttribute(com.wenwen.ai.scope.AuthenticatedDoctorSession.ATTRIBUTE,"101");r=http.perform(post("/api/ra/ai/patientSearch").session(wrong).content("{")).andReturn();assertEquals(401,r.getResponse().getStatus());assertEquals(0,body.bytes);assertEquals(0,observed.borrowed);
        context.close();apiEnabled=false;setup();r=http.perform(post("/api/ra/ai/patientSearch").session(session(101)).content("{")).andReturn();assertEquals(503,r.getResponse().getStatus());assertEquals(0,body.bytes);assertEquals(0,observed.borrowed);
        r=http.perform(post("/api/ra/ai/patientSearch").content("{")).andReturn();assertEquals(401,r.getResponse().getStatus());assertEquals(0,body.bytes);assertEquals(0,observed.borrowed);
    }
}
