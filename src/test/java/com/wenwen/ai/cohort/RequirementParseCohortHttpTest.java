package com.wenwen.ai.cohort;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** P07独立七人输入；只经真实parse/apply/cohort消费。 */
class RequirementParseCohortHttpTest extends AiGatewayHttpFixture {
    private static final String QUESTION="TNFi 治疗 6 个月后仍处于中高疾病活动的患者";
    private static final String CONDITIONS="{\"tx\":\"TNFi\",\"at\":\"6m\",\"act\":\"mod-high\"}";
    private static final String MATCHED="[{\"field\":\"tx\",\"value\":\"TNFi\"},{\"field\":\"at\",\"value\":\"6m\"},{\"field\":\"act\",\"value\":\"mod-high\"}]";
    private static final String CURRENT="{\"sex\":\"M\",\"tx\":\"JAKi\",\"act\":\"target\",\"at\":\"now\",\"ids\":[\"2\",\"4\",\"7\"]}";
    void sevenPatients() throws Exception {
        clock.now=Instant.parse("2026-10-07T02:00:00Z");
        sql("DELETE FROM patient_follow_up_history");
        sql("DELETE FROM patient_relation_doctor");
        sql("INSERT INTO patient_relation_doctor (doctor_id,patient_id,research_type,miss) VALUES (101,1,0,0),(101,1,1,0),(101,2,0,0),(101,3,0,1),(101,4,0,0),(101,5,0,0),(101,6,0,0),(202,7,0,0)");
        int[] genders={2,1,1,2,1,2,2};
        String[] six={"2.00","3.20","3.50","4.20","2.70","4.00","4.00"};
        String[] now={"2.00","1.10","3.50","1.20","4.50","4.00","4.00"};
        for(int p=1;p<=7;p++) {
            sql("UPDATE patient_basic_info SET gender="+genders[p-1]+" WHERE id="+p);
            String name=p==3?"托法替布":"依那西普",start=p==6?null:"2026-04-06";
            rawVisit(p*100+10,p,"2026-04-05","4.50",null,null);
            rawVisit(p*100+11,p,"2026-04-06",null,null,drugs(drug(name,start,null)));
            rawVisit(p*100+20,p,"2026-10-06",six[p-1],null,drugs(drug(name,start,null)));
            rawVisit(p*100+21,p,"2026-10-07",now[p-1],null,null);
        }
        observed.reset();auditObserved.reset();
    }
    String modelConditions() {return "{\"filters\":"+CONDITIONS+",\"matched\":"+MATCHED+",\"unrecognized\":[],\"status\":\"OK\",\"notice\":\"已生成候选条件，尚未应用。\"}";}
    void checkApplied(JsonNode applied,String ids) throws Exception {
        assertTrue(applied.path("applied").asBoolean());JsonNode f=applied.path("filters");
        assertEquals("RA",f.path("studyCode").asText());assertEquals("TNFi",f.path("tx").asText());assertEquals("6m",f.path("at").asText());assertEquals("mod-high",f.path("act").asText());
        for(String field:new String[]{"sex","age","sero","cm","data"})assertTrue(f.path(field).isNull(),field);
        assertEquals(json.readTree(ids),f.path("ids"));
    }
    @Test void exactRequirementSentenceAppliesToRealSixMonthCohort() throws Exception {
        sevenPatients();
        AiGatewayLoopbackPeer.Exchange available=peer.json(outer(modelConditions()));
        JsonNode parsed=parse(QUESTION);
        assertEquals("OK",parsed.path("parseStatus").asText());
        assertEquals("REPLACE_EXCEPT_IDS",parsed.path("applyMode").asText());
        assertEquals(json.readTree(CONDITIONS),parsed.path("candidateFilters"));assertEquals(json.readTree(MATCHED),parsed.path("matched"));
        assertTrue(parsed.path("unrecognized").isEmpty());assertTrue(parsed.path("rejectedFields").isEmpty());
        assertEquals("RULE",parsed.path("engine").asText());assertEquals("AI_SAFETY_BLOCKED",parsed.path("fallbackReason").asText());
        assertTrue(parsed.path("meta").path("modelVersion").isNull());assertTrue(parsed.path("meta").path("promptVersion").isNull());
        assertEquals(0,peer.requests.get());assertEquals(1L,available.received.getCount());peer.discardUnused(available);
        Map<String,String> parseEvent=checkedAudit(parsed,"PARSE",101);String parseId=auditObserved.lastEventId;
        JsonNode applied=apply(parsed,CURRENT,null);checkApplied(applied,"[\"2\",\"4\",\"7\"]");checkedAudit(applied,"APPLY",101);assertEquals(parseEvent,auditEvent(parseId));
        assertEquals(0,observed.selects);assertEquals(0,observed.inserts);assertEquals(2,auditCount());auditExited();
        JsonNode cohort=success("{\"filters\":"+applied.path("filters")+"}");
        assertEquals(Arrays.asList("4","2"),ids(cohort));assertEquals(2,cohort.path("n").asInt());assertEquals(6,cohort.path("studyTotal").asInt());
        assertEquals(3,cohort.path("submittedUniqueIdsN").asInt());assertEquals(2,cohort.path("effectiveIdsN").asInt());
        assertEquals(2,cohort.path("activity").path("evalN").asInt());assertEquals(0.0,cohort.path("stats").path("targetRate").asDouble());
        int[] buckets={0,0,1,1};for(int i=0;i<4;i++)assertEquals(buckets[i],cohort.path("activity").path("current").get(i).path("count").asInt());
        double[] at={4.20,3.20},now={1.20,1.10};String[] visits={"420","220"};
        for(int i=0;i<2;i++){JsonNode item=cohort.path("patients").path("items").get(i);assertEquals(at[i],item.path("das28At").asDouble());assertEquals(now[i],item.path("das28Current").asDouble());assertEquals(visits[i],item.path("selection").path("evalVisitId").asText());assertEquals("2026-10-06",item.path("selection").path("target6mDate").asText());assertEquals("2026-10-06",item.path("selection").path("evalDate").asText());}
        assertEquals(0,peer.requests.get());assertNull(peer.failure.get());auditExited();
    }
    @Test void ruleVersionIsV02InResponseAndCommittedParseApplyEvents() throws Exception {
        String source=sourceDigest();JsonNode parsed=parse(QUESTION);
        assertEquals("dev-parse-rules-v02",parsed.path("meta").path("ruleVersion").asText());
        Map<String,String> prior=checkedAudit(parsed,"PARSE",101);
        String priorId=auditObserved.lastEventId;JsonNode applied=apply(parsed,CURRENT,null);
        checkedAudit(applied,"APPLY",101);assertEquals(prior,auditEvent(priorId));
        assertEquals(2,auditCount());assertEquals(source,sourceDigest());assertEquals(0,observed.selects);assertEquals(0,observed.inserts);auditExited();
    }
    Map<String,String> checkedAudit(JsonNode data,String action,long doctor) throws Exception {
        Map<String,String> event=auditEvent(auditObserved.lastEventId);
        assertEquals(Long.toString(doctor),event.get("actor_doctor_id"));assertEquals(action,event.get("action"));
        assertEquals(data.path("questionRef").asText(),event.get("question_ref"));assertEquals(data.path("parseStatus").asText(),event.get("parse_status"));
        assertEquals(data.path("meta").path("traceId").asText(),event.get("trace_id"));
        assertEquals("dev-parse-rules-v02",event.get("rule_version"));assertEquals("dev-parse-schema-v01",event.get("schema_version"));
        assertEquals("dev-parse-rules-v02",data.path("meta").path("ruleVersion").asText());assertEquals("dev-parse-schema-v01",data.path("meta").path("schemaVersion").asText());
        assertEquals(data.path("meta").path("durationMs").asText(),event.get("duration_ms"));
        JsonNode summary=json.readTree(event.get("condition_summary"));
        Set<String> ordinary=new HashSet<>(Arrays.asList("sex","age","sero","tx","at","act","cm","data"));
        for(String part:new String[]{"candidate","effective"})if(!summary.path(part).isNull())summary.path(part).fieldNames().forEachRemaining(key->assertTrue(ordinary.contains(key),key));
        assertFalse(event.toString().contains(QUESTION));assertFalse(event.toString().contains("合成患者"));assertFalse(event.get("condition_summary").contains("\"ids\""));
        if("PARSE".equals(action)){assertEquals(data.path("candidateFilters"),summary.path("candidate"));assertTrue(summary.path("effective").isNull());assertEquals(data.path("engine").asText(),event.get("engine"));assertEquals("0",event.get("applied"));}
        else{assertEquals("NONE",event.get("engine"));assertEquals(data.path("applied").asBoolean()?"1":"0",event.get("applied"));assertNull(event.get("model_version"));assertNull(event.get("prompt_version"));assertEquals(json.readTree(CONDITIONS),summary.path("candidate"));for(String key:ordinary)assertEquals(data.path("filters").path(key),summary.path("effective").path(key),key);}
        return event;
    }
    @Test void appliedCandidatePreservesNullEmptyAndIntersectedIdsAndTrustedDoctor() throws Exception {
        sevenPatients();JsonNode parsed=parse(QUESTION);
        String[] values={"null","[]","[\"2\",\"7\"]"};
        List<List<String>> expected=Arrays.asList(Arrays.asList("4","2"),Collections.emptyList(),Collections.singletonList("2"));
        for(int i=0;i<values.length;i++){
            observed.reset();JsonNode applied=apply(parsed,"{\"ids\":"+values[i]+"}",null);checkApplied(applied,values[i]);
            assertEquals(0,observed.selects);assertEquals(0,observed.inserts);
            JsonNode cohort=success("{\"filters\":"+applied.path("filters")+"}");assertEquals(expected.get(i),ids(cohort));assertEquals(expected.get(i).size(),cohort.path("n").asInt());assertEquals(6,cohort.path("studyTotal").asInt());
            if(i==0){assertTrue(cohort.path("submittedUniqueIdsN").isNull());assertTrue(cohort.path("effectiveIdsN").isNull());}else{assertEquals(i==1?0:2,cohort.path("submittedUniqueIdsN").asInt());assertEquals(i==1?0:1,cohort.path("effectiveIdsN").asInt());}
        }
        context.getBean(FakePrincipalProvider.class).doctor=202;observed.reset();JsonNode other=apply(parsed,CURRENT,null);checkApplied(other,"[\"2\",\"4\",\"7\"]");checkedAudit(other,"APPLY",202);
        assertEquals(0,observed.selects);assertEquals(0,observed.inserts);JsonNode cohort=success("{\"filters\":"+other.path("filters")+"}");
        assertEquals(Collections.singletonList("7"),ids(cohort));assertEquals(1,cohort.path("n").asInt());assertEquals(1,cohort.path("studyTotal").asInt());assertEquals(3,cohort.path("submittedUniqueIdsN").asInt());assertEquals(1,cohort.path("effectiveIdsN").asInt());
        assertEquals(5,auditCount());assertEquals(0,peer.requests.get());auditExited();
    }

    @Test void smokingPartialKeepsEveryCurrentConditionUntilExplicitConfirmation() throws Exception {
        sevenPatients();JsonNode parsed=parse(QUESTION+"且吸烟");assertEquals("PARTIAL",parsed.path("parseStatus").asText());assertEquals("REQUIRE_CONFIRMATION",parsed.path("applyMode").asText());assertEquals(json.readTree(CONDITIONS),parsed.path("candidateFilters"));assertEquals(json.readTree(MATCHED),parsed.path("matched"));assertEquals(json.readTree("[{\"reason\":\"UNMAPPED_TEXT\",\"count\":2}]"),parsed.path("unrecognized"));
        JsonNode expected=json.readTree("{\"studyCode\":\"RA\",\"sex\":\"M\",\"age\":null,\"sero\":null,\"cm\":null,\"data\":null,\"tx\":\"JAKi\",\"act\":\"target\",\"at\":\"now\",\"ids\":[\"2\",\"4\",\"7\"]}");
        for(Boolean confirm:new Boolean[]{null,false}){JsonNode held=apply(parsed,CURRENT,confirm);assertFalse(held.path("applied").asBoolean());assertEquals(expected,held.path("filters"));checkedAudit(held,"APPLY",101);}
        JsonNode applied=apply(parsed,CURRENT,true);checkApplied(applied,"[\"2\",\"4\",\"7\"]");checkedAudit(applied,"APPLY",101);assertEquals(0,observed.selects);assertEquals(0,observed.inserts);
        JsonNode cohort=success("{\"filters\":"+applied.path("filters")+"}");assertEquals(Arrays.asList("4","2"),ids(cohort));assertEquals(2,cohort.path("n").asInt());assertEquals(6,cohort.path("studyTotal").asInt());assertEquals(4,auditCount());assertEquals(0,peer.requests.get());auditExited();
    }

    @Test void localOnlyWordsNeverReachAvailablePeerAndOriginalSafeInputsStillUseAi() throws Exception {
        String[] questions={"TNFi","6个月后","仍处于中高疾病活动",QUESTION,"TNFi治疗6个月后仍处于中高疾病活动的患者","tnfi治疗6个月后仍处于中高疾病活动的患者"};
        String[] candidates={"{\"tx\":\"TNFi\"}","{\"at\":\"6m\"}","{\"act\":\"mod-high\"}",CONDITIONS,CONDITIONS,CONDITIONS};
        String[] matches={"[{\"field\":\"tx\",\"value\":\"TNFi\"}]","[{\"field\":\"at\",\"value\":\"6m\"}]","[{\"field\":\"act\",\"value\":\"mod-high\"}]",MATCHED,MATCHED,MATCHED};
        long count=0;
        for(int i=0;i<questions.length;i++){
            String inner="{\"filters\":"+candidates[i]+",\"matched\":"+matches[i]+",\"unrecognized\":[],\"status\":\"OK\",\"notice\":\"已生成候选条件，尚未应用。\"}";
            AiGatewayLoopbackPeer.Exchange available=peer.json(outer(inner));JsonNode parsed=parse(questions[i]);
            System.out.println("P07a local-only index="+i+" engine="+parsed.path("engine").asText()+" peer_requests="+peer.requests.get()+" received_remaining="+available.received.getCount());
            assertEquals(0,peer.requests.get(),"本地新词不能升级原文出站许可");assertEquals(1L,available.received.getCount());
            assertEquals("RULE",parsed.path("engine").asText());assertEquals("AI_SAFETY_BLOCKED",parsed.path("fallbackReason").asText());assertEquals("OK",parsed.path("parseStatus").asText());
            assertEquals(json.readTree(candidates[i]),parsed.path("candidateFilters"));assertEquals(json.readTree(matches[i]),parsed.path("matched"));assertTrue(parsed.path("meta").path("modelVersion").isNull());assertTrue(parsed.path("meta").path("promptVersion").isNull());
            Map<String,String> event=checkedAudit(parsed,"PARSE",101);assertEquals("AI_SAFETY_BLOCKED",event.get("fallback_reason"));assertNull(event.get("model_version"));assertNull(event.get("prompt_version"));assertEquals(++count,auditCount());peer.discardUnused(available);networkExited();auditExited();
        }
        String[] oldQuestions={"TNF治疗6个月","女性35-45岁"};
        String[] oldCandidates={"{\"tx\":\"TNFi\",\"at\":\"6m\"}","{\"sex\":\"F\",\"age\":\"35-45\"}"};
        String[] oldMatched={"[{\"field\":\"tx\",\"value\":\"TNFi\"},{\"field\":\"at\",\"value\":\"6m\"}]","[{\"field\":\"sex\",\"value\":\"F\"},{\"field\":\"age\",\"value\":\"35-45\"}]"};
        for(int i=0;i<oldQuestions.length;i++){
            AiGatewayLoopbackPeer.Exchange reachable=peer.json(outer("{\"filters\":"+oldCandidates[i]+",\"matched\":"+oldMatched[i]+",\"unrecognized\":[],\"status\":\"OK\",\"notice\":\"已生成候选条件，尚未应用。\"}"));
            JsonNode parsed=parse(oldQuestions[i]);assertEquals("AI",parsed.path("engine").asText());assertTrue(parsed.path("fallbackReason").isNull());assertEquals("OK",parsed.path("parseStatus").asText());assertEquals(json.readTree(oldCandidates[i]),parsed.path("candidateFilters"));
            assertTrue(reachable.received.await(1,java.util.concurrent.TimeUnit.SECONDS));assertTrue(reachable.eof.await(1,java.util.concurrent.TimeUnit.SECONDS));assertEquals(i+1,peer.requests.get());assertEquals(oldQuestions[i],json.readTree(reachable.body).path("messages").get(1).path("content").asText());
            Map<String,String> event=checkedAudit(parsed,"PARSE",101);assertEquals("AI",event.get("engine"));assertEquals("p05b-synthetic-model",event.get("model_version"));assertEquals("dev-parse-ai-prompt-v01",event.get("prompt_version"));assertEquals("p05b-synthetic-model",parsed.path("meta").path("modelVersion").asText());assertEquals("dev-parse-ai-prompt-v01",parsed.path("meta").path("promptVersion").asText());assertEquals(++count,auditCount());networkExited();auditExited();
        }
        assertEquals(0,observed.selects);assertEquals(0,observed.inserts);assertNull(peer.failure.get());
    }

    @Test void historicalV01EventsRemainUnchangedWhenV02EventsAppend() throws Exception {
        parse(QUESTION);String oldId=auditObserved.lastEventId;
        try(Connection c=raw.getConnection();PreparedStatement update=c.prepareStatement("UPDATE ra_ai_parse_audit SET rule_version='dev-parse-rules-v01' WHERE event_id=?")){update.setString(1,oldId);assertEquals(1,update.executeUpdate());}
        Map<String,String> old=auditEvent(oldId);assertEquals("dev-parse-rules-v01",old.get("rule_version"));String source=sourceDigest();
        JsonNode parsed=parse(QUESTION);checkedAudit(parsed,"PARSE",101);assertEquals(old,auditEvent(oldId));
        JsonNode applied=apply(parsed,CURRENT,null);checkedAudit(applied,"APPLY",101);assertEquals(old,auditEvent(oldId));
        assertEquals(3,auditCount());assertEquals(source,sourceDigest());assertEquals(0,observed.selects);assertEquals(0,observed.inserts);assertEquals(0,peer.requests.get());auditExited();
    }

}
