package com.wenwen.ai.cohort;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.*;
import java.util.*;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.junit.jupiter.api.Assertions.*;

/** P03d 手写主表，仅以真实合成生日/检测记录产生临床事实。 */
abstract class SimilarEntryHttpFixture extends RetainedCohortHttpFixture {
    void similarPopulation() throws Exception {
        sql("DELETE FROM patient_comorbidity");sql("DELETE FROM patient_follow_up_history");
        sql("DELETE FROM patient_relation_doctor");sql("DELETE FROM patient_basic_info");
        for(int id=101;id<=127;id++) {
            int age=id==102?35:id==103?45:id==123?34:id==124?46:40;
            String birth=(2026-age)+"1007";
            sql("INSERT INTO patient_basic_info(id,name,study_no,gender,card_no) VALUES("+id+",'synthetic-similar-name-CANARY','synthetic-similar-study',"+(id==125?1:2)+",'110101"+birth+"0011')");
            sql("INSERT INTO patient_relation_doctor(doctor_id,patient_id,research_type) VALUES(101,"+id+",0)");
            visit(id*10,id,0,"{\"result\":{\"crpScore\":3.00}}","2026-10-07",null,id==127?null:"{\"lfsyz\":"+(id==126?10:35)+"}");
        }
        sql("INSERT INTO patient_basic_info(id,name) VALUES(201,'synthetic-other'),(202,'synthetic-non-ra')");
        sql("INSERT INTO patient_relation_doctor(doctor_id,patient_id,research_type) VALUES(202,201,0),(101,202,6)");
        observed.reset();
    }
    MvcResult resolveRequest(String body) throws Exception {
        return http.perform(post("/api/ra/ai/resolveEntry").contentType("application/json").content(body)).andReturn();
    }
    JsonNode resolve(long id) throws Exception {
        MvcResult r=resolveRequest("{\"source\":\"SIMILAR\",\"indexPatientId\":\""+id+"\"}");
        assertEquals(200,r.getResponse().getStatus(),r.getResponse().getContentAsString());
        JsonNode envelope=json.readTree(r.getResponse().getContentAsByteArray());
        assertTrue(envelope.path("success").asBoolean());assertEquals("200",envelope.path("code").asText());
        assertEquals(r.getResponse().getHeader("X-Trace-Id"),envelope.path("data").path("meta").path("traceId").asText());
        return envelope.path("data");
    }
    void resolveError(String body,int status,String code) throws Exception {
        MvcResult r=resolveRequest(body);assertEquals(status,r.getResponse().getStatus(),r.getResponse().getContentAsString());
        JsonNode envelope=json.readTree(r.getResponse().getContentAsByteArray());assertFalse(envelope.path("success").asBoolean());assertEquals(code,envelope.path("code").asText());assertTrue(envelope.path("data").isNull());assertNotNull(r.getResponse().getHeader("X-Trace-Id"));
        assertEquals(observed.borrowed,observed.returned);assertEquals(0,observed.active.get());
    }
    void resolverReleased() {
        assertEquals(5,observed.selects);assertEquals(1,observed.borrowed);assertEquals(1,observed.returned);assertEquals(0,observed.active.get());
    }
}
