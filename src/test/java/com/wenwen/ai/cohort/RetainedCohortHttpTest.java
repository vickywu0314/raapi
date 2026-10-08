package com.wenwen.ai.cohort;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RetainedCohortHttpTest extends RetainedCohortHttpFixture {
    @Test void createsActualFullStored37AndSortedFirstTen() throws Exception {
        population37();JsonNode data=success("{}");
        String id=data.path("analysisId").asText();
        assertTrue(id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"),"必须发布实际持久结果的 analysisId");
        JsonNode payload=json.readTree(stored(id));assertEquals(37,payload.path("n").asInt());assertEquals(37,payload.path("rows").size());
        List<String> full=Arrays.asList("120","110","130","105","122","108","124","131","133","102","119","137","107","128","104","125","115","132","106","126","114","134","109","123","113","129","112","135","116","136","101","103","111","117","118","121","127");
        assertEquals(full,rowIds(payload.path("rows")));assertEquals(full.subList(0,10),ids(data));
        assertEquals(data.path("stats"),payload.path("stats"));assertEquals(data.path("activity"),payload.path("activity"));assertEquals(data.path("byTx"),payload.path("byTx"));
        assertEquals(37,data.path("patients").path("total").asInt());assertEquals(10,data.path("patients").path("returnedCount").asInt());assertTrue(data.path("patients").path("hasMore").asBoolean());
        assertEquals("LIVE_SOURCE_V04",data.path("meta").path("patientProjection").asText());assertEquals("P03_RESULT",data.path("meta").path("completion").asText());
        assertEquals("RA",payload.path("filters").path("studyCode").asText());assertEquals("now",payload.path("filters").path("at").asText());
        assertEquals(2,observed.borrowed);assertEquals(2,observed.returned);assertEquals(0,observed.active.get());
    }
    @Test void sameSignedRunReturnsTwentyThenSevenWithoutGaps() throws Exception {
        population37();JsonNode first=success("{}");String id=first.path("analysisId").asText(),cursor=first.path("patients").path("nextCursor").asText();
        assertFalse(cursor.isEmpty(),"首10后需要签名offset10游标");assertTrue(cursor.contains("."));
        String[] parts=cursor.split("\\.");byte[] payload=Base64.getUrlDecoder().decode(parts[0]);
        javax.crypto.Mac mac=javax.crypto.Mac.getInstance("HmacSHA256");mac.init(new javax.crypto.spec.SecretKeySpec(Base64.getDecoder().decode(SYNTHETIC_KEY),"HmacSHA256"));assertArrayEquals(mac.doFinal(payload),Base64.getUrlDecoder().decode(parts[1]));
        assertEquals(10,json.readTree(payload).path("nextOffset").asInt());
        observed.reset();JsonNode middle=page(id,cursor);assertEquals(Arrays.asList("119","137","107","128","104","125","115","132","106","126","114","134","109","123","113","129","112","135","116","136"),ids(middle));
        assertEquals(20,middle.path("patients").path("returnedCount").asInt());assertEquals(37,middle.path("patients").path("total").asInt());assertTrue(middle.path("patients").path("hasMore").asBoolean());
        String next=middle.path("patients").path("nextCursor").asText();assertEquals(30,json.readTree(Base64.getUrlDecoder().decode(next.split("\\.")[0])).path("nextOffset").asInt());
        assertEquals(2,observed.borrowed);assertEquals(2,observed.returned);assertEquals(3,observed.selects);assertEquals(0,observed.active.get());
        JsonNode last=page(id,next);assertEquals(Arrays.asList("101","103","111","117","118","121","127"),ids(last));assertEquals(7,last.path("patients").path("returnedCount").asInt());assertFalse(last.path("patients").path("hasMore").asBoolean());assertTrue(last.path("patients").path("nextCursor").isNull());
        Set<String> union=new HashSet<>(ids(first));union.addAll(ids(middle));union.addAll(ids(last));assertEquals(37,union.size());
        assertEquals("RETAINED_NUMERIC_V1",middle.path("meta").path("patientProjection").asText());assertEquals(first.path("meta").path("traceId"),middle.path("meta").path("analysisTraceId"));assertNotEquals(middle.path("meta").path("traceId"),middle.path("meta").path("analysisTraceId"));
        assertEquals(new HashSet<>(Arrays.asList("analysisId","patients","meta")),fields(middle));
    }
    @Test void tied55AndPartitionBoundariesPublishActualOffsets() throws Exception {
        for(int count:new int[]{0,1,9,10,11,30,31,37,55}){
            tiedPopulation(count);JsonNode first=success("{}");String id=first.path("analysisId").asText();assertEquals(count,json.readTree(stored(id)).path("rows").size());
            List<String> actual=new ArrayList<>(ids(first));int offset=10;JsonNode current=first;
            while(current.path("patients").path("hasMore").asBoolean()){
                String cursor=current.path("patients").path("nextCursor").asText();assertEquals(offset,json.readTree(Base64.getUrlDecoder().decode(cursor.split("\\.")[0])).path("nextOffset").asInt());
                current=page(id,cursor);assertEquals(Math.min(20,count-offset),current.path("patients").path("returnedCount").asInt());actual.addAll(ids(current));offset+=20;
            }
            List<String> expected=new ArrayList<>();for(int i=0;i<count;i++)expected.add(Integer.toString(201+i));assertEquals(expected,actual);assertTrue(current.path("patients").path("nextCursor").isNull());
        }
    }
    @Test void retainedAllowlistPreservesExactNumbersButOmitsSourceCanaries() throws Exception {
        population37();String precise="2.2949999999999999999",crp="123.456789012345678901234567890123456789";
        sql("UPDATE patient_basic_info SET gender=2,card_no='110101198610070011',confirm_date='2017-10-07'");
        sql("UPDATE patient_follow_up_history SET bqpg='{\"result\":{\"crpScore\":\""+precise+"\",\"ytgjs\":4,\"zzgjs\":1},\"ztScoreByPatient\":50,\"hqaScore\":1.25,\"tjScore\":\"synthetic-arbitrary-pain-CANARY\",\"secret\":\"synthetic-case-json-CANARY\"}',fzjc='{\"cfydb\":"+crp+",\"lfsyz\":35}' WHERE id=1190");
        sql("UPDATE patient_follow_up_history SET bqpg='{\"result\":{\"crpScore\":4.2},\"tjScore\":\"7.125\"}' WHERE id=1200");
        JsonNode first=success("{}");assertEquals("7.125",first.path("patients").path("items").get(0).path("evaluation").path("pain").path("raw").asText());
        byte[] bytes=stored(first.path("analysisId").asText());String text=new String(bytes,java.nio.charset.StandardCharsets.UTF_8);
        for(String forbidden:new String[]{"synthetic-retained-name","synthetic-study","110101198610070011","synthetic-arbitrary-pain-CANARY","synthetic-case-json-CANARY","\"name\"","\"studyNo\""})assertFalse(text.contains(forbidden),forbidden);
        com.wenwen.ai.result.AnalysisRun run=context.getBean(com.wenwen.ai.result.AnalysisResultStore.class).find(first.path("analysisId").asText());
        Map<String,Object> retained=new com.wenwen.ai.result.AnalysisResultCodec().decode(run);
        Map<String,Object> selected=null;for(Object r:(List<?>)retained.get("rows"))if("119".equals(((Map<?,?>)r).get("patientId")))selected=(Map<String,Object>)r;
        assertNotNull(selected);assertEquals(precise,((Map<?,?>)selected.get("scoreProvenance")).get("raw"));assertEquals(new java.math.BigDecimal("2.29"),selected.get("das28At"));
        assertEquals(new java.math.BigDecimal(crp),((Map<?,?>)((Map<?,?>)selected.get("evaluation")).get("crp")).get("value"));
        JsonNode middle=page(first.path("analysisId").asText(),first.path("patients").path("nextCursor").asText());
        JsonNode item=null;for(JsonNode r:middle.path("patients").path("items"))if("119".equals(r.path("patientId").asText()))item=r;
        assertNotNull(item);assertTrue(item.path("evaluation").path("pain").path("raw").isNull());assertEquals("OMITTED",item.path("evaluation").path("pain").path("rawRetention").asText());assertEquals("UNVERIFIED_SCALE",item.path("evaluation").path("pain").path("missingReason").asText());
        assertEquals("synthetic-retained-name-119",item.path("name").asText());assertEquals(40,item.path("clinical").path("age").asInt());assertEquals(9,item.path("clinical").path("diseaseDurationYears").asInt());
        byte[] callerBytes=run.getPayload();callerBytes[0]=0;assertNotEquals(0,run.getPayload()[0]);run.setPayload(bytes);bytes[0]=0;assertNotEquals(0,run.getPayload()[0]);
    }
    @Test void replayUsesCurrentDisplayButKeepsRetainedClinicalScoresAndStatistics() throws Exception {
        population37();sql("UPDATE patient_basic_info SET gender=2,card_no='110101198610070011',confirm_date='2017-10-07'");
        JsonNode first=success("{}");String id=first.path("analysisId").asText(),cursor=first.path("patients").path("nextCursor").asText();JsonNode before=page(id,cursor);
        sql("UPDATE patient_basic_info SET name='synthetic-current-name',study_no='synthetic-current-study',card_no='110101199610070011',confirm_date='2020-10-07' WHERE id=119");
        sql("UPDATE patient_follow_up_history SET bqpg='{\"result\":{\"crpScore\":9}}' WHERE id=1190");
        JsonNode after=page(id,cursor);assertEquals(ids(before),ids(after));
        for(int i=0;i<20;i++){
            JsonNode old=before.path("patients").path("items").get(i),current=after.path("patients").path("items").get(i);
            for(String field:new String[]{"das28At","das28Base","das28Current","deltaDas28","clinical","selection","scoreProvenance","baselineProvenance","evaluation","treatment","qc"})assertEquals(old.path(field),current.path(field),field);
        }
        JsonNode current=after.path("patients").path("items").get(0);assertEquals("119",current.path("patientId").asText());assertEquals("synthetic-current-name",current.path("name").asText());assertEquals("synthetic-current-study",current.path("studyNo").asText());assertEquals(3.7,current.path("das28At").asDouble());assertEquals(40,current.path("clinical").path("age").asInt());assertEquals(9,current.path("clinical").path("diseaseDurationYears").asInt());
        JsonNode fresh=success("{}");assertNotEquals(id,fresh.path("analysisId").asText());assertEquals("119",ids(fresh).get(0));assertEquals(9,fresh.path("patients").path("items").get(0).path("das28At").asInt());assertEquals(30,fresh.path("patients").path("items").get(0).path("clinical").path("age").asInt());
        assertEquals(before.path("meta").path("policyVersions"),after.path("meta").path("policyVersions"));assertEquals(first.path("stats"),json.readTree(stored(id)).path("stats"));
    }
    @Test void finalUtf8BytesCapRejectsBeforeStoreAndDoesNotInvalidateOldRun() throws Exception {
        population37();JsonNode old=success("{}");String id=old.path("analysisId").asText(),cursor=old.path("patients").path("nextCursor").asText();byte[] payload=stored(id);assertTrue(payload.length>new String(payload,java.nio.charset.StandardCharsets.UTF_8).length());
        setting("maxBytes",Integer.toString(payload.length-1));observed.reset();error("{}",413,"RESULT_TOO_LARGE");assertEquals(1,runs());assertEquals(1,observed.borrowed);assertEquals(1,observed.returned);assertEquals(0,observed.active.get());
        assertEquals(20,ids(page(id,cursor)).size());setting("maxBytes",Integer.toString(payload.length));assertNotEquals(id,success("{}").path("analysisId").asText());assertEquals(2,runs());
    }
    @Test void invalidDeploymentConfigFailsBeforeSourceSqlWithoutBlockingBeanStartup() throws Exception {
        String[][] cases={{"keyBase64",""},{"keyBase64","%%%"},{"keyBase64","YQ=="},{"ttlSeconds","0"},{"ttlSeconds","-1"},{"ttlSeconds","invalid"},{"ttlSeconds","9223372036854775807"},{"ttlSeconds","9223372036854775"},{"maxBytes","0"},{"maxBytes","16777216"},{"maxBytes","invalid"}};
        for(String[] c:cases){setting("keyBase64",SYNTHETIC_KEY);setting("ttlSeconds","900");setting("maxBytes","8388608");setting(c[0],c[1]);observed.reset();error("{}",503,"SERVICE_UNAVAILABLE");assertEquals(0,observed.selects,c[0]+"="+c[1]);assertEquals(0,observed.borrowed);assertEquals(0,observed.active.get());assertEquals(0,runs());}
        setting("keyBase64",SYNTHETIC_KEY);setting("ttlSeconds","900");setting("maxBytes","8388608");assertEquals(5,success("{}").path("n").asInt());
    }
    @Test void signatureOwnerExpiryAndMissingRowFollowFixedReadPrecedence() throws Exception {
        population37();JsonNode first=success("{}");String id=first.path("analysisId").asText(),cursor=first.path("patients").path("nextCursor").asText();long expires=java.time.Instant.parse(first.path("meta").path("expiresAt").asText()).toEpochMilli();
        context.getBean(FakePrincipalProvider.class).doctor=202;observed.reset();pageError(id,cursor,404,"RESOURCE_NOT_FOUND");assertEquals(0,observed.borrowed);assertEquals(0,observed.selects);
        clock.now=java.time.Instant.ofEpochMilli(expires);pageError(id,cursor,404,"RESOURCE_NOT_FOUND");assertEquals(0,observed.borrowed);
        context.getBean(FakePrincipalProvider.class).doctor=101;observed.reset();pageError(id,cursor,410,"ANALYSIS_EXPIRED");assertEquals(0,observed.borrowed);
        clock.now=java.time.Instant.ofEpochMilli(expires-1);assertEquals(20,ids(page(id,cursor)).size());clock.now=java.time.Instant.ofEpochMilli(expires);observed.reset();pageError(id,cursor,410,"ANALYSIS_EXPIRED");assertEquals(0,observed.borrowed);
        sql("DELETE FROM ra_ai_analysis_run WHERE id='"+id+"'");pageError(id,cursor,410,"ANALYSIS_EXPIRED");assertEquals(0,observed.borrowed);
        clock.now=java.time.Instant.ofEpochMilli(expires-1);observed.reset();pageError(id,cursor,404,"RESOURCE_NOT_FOUND");assertEquals(1,observed.selects);assertEquals(1,observed.borrowed);assertEquals(1,observed.returned);assertEquals(0,observed.active.get());
        JsonNode fresh=success("{}");String freshId=fresh.path("analysisId").asText(),freshCursor=fresh.path("patients").path("nextCursor").asText();
        sql("UPDATE ra_ai_analysis_run SET owner_doctor_id=202 WHERE id='"+freshId+"'");observed.reset();pageError(freshId,freshCursor,404,"RESOURCE_NOT_FOUND");assertEquals(1,observed.selects);
        sql("UPDATE ra_ai_analysis_run SET owner_doctor_id=101,expires_at_ms=expires_at_ms+1 WHERE id='"+freshId+"'");observed.reset();pageError(freshId,freshCursor,503,"SERVICE_UNAVAILABLE");assertEquals(1,observed.selects);
        sql("UPDATE ra_ai_analysis_run SET expires_at_ms="+(expires-1)+" WHERE id='"+freshId+"'");observed.reset();pageError(freshId,freshCursor,410,"ANALYSIS_EXPIRED");assertEquals(1,observed.selects);
        String invalidOffset=context.getBean(com.wenwen.ai.result.AnalysisCursor.class).issue(id,101,50,expires);observed.reset();pageError(id,invalidOffset,404,"RESOURCE_NOT_FOUND");assertEquals(1,observed.selects);
    }
    @Test void pageBodyAndSignedOwnerCannotBypassTrustedIdentityOrReadBounds() throws Exception {
        population37();JsonNode a=success("{}");String id=a.path("analysisId").asText(),cursor=a.path("patients").path("nextCursor").asText();
        sql("INSERT INTO patient_relation_doctor(doctor_id,patient_id,research_type) SELECT 202,id,0 FROM patient_basic_info");context.getBean(FakePrincipalProvider.class).doctor=202;JsonNode b=success("{}");String bid=b.path("analysisId").asText(),bcursor=b.path("patients").path("nextCursor").asText();
        observed.reset();pageError(id,cursor,404,"RESOURCE_NOT_FOUND");assertEquals(0,observed.borrowed);context.getBean(FakePrincipalProvider.class).doctor=101;pageError(bid,bcursor,404,"RESOURCE_NOT_FOUND");assertEquals(0,observed.borrowed);
        for(String body:Arrays.asList("", "[]", "null", "{}", "{", "{} {}", "{\"analysisId\":\""+id+"\",\"cursor\":\"x\",\"extra\":null}", "{\"analysisId\":\""+id+"\",\"analysisId\":\""+id+"\",\"cursor\":\"x\"}", "{\"analysisId\":0,\"cursor\":\"x\"}","{\"analysisId\":\"\",\"cursor\":\"x\"}","{\"analysisId\":\""+id+"\",\"cursor\":null}"))pageBodyError(body,400,"INVALID_REQUEST");
        pageError(id,"x",400,"CURSOR_INVALID");pageError(id,cursor+"=",400,"CURSOR_INVALID");pageError(bid,cursor,400,"CURSOR_INVALID");pageError(id,String.join("",Collections.nCopies(2049,"a")),400,"CURSOR_INVALID");assertEquals(0,observed.borrowed);
        pageBodyError("{}"+String.join("",Collections.nCopies(4095," ")),413,"LIMIT_EXCEEDED");assertEquals(4097,bodyRead.bytes);assertEquals(0,observed.borrowed);
        String unicode="{\"analysisId\":\""+id+"\",\"cursor\":\""+String.join("",Collections.nCopies(1500,"合"))+"\"}";assertTrue(unicode.length()<4096);pageBodyError(unicode,413,"LIMIT_EXCEEDED");assertEquals(4097,bodyRead.bytes);assertEquals(0,observed.borrowed);
        String valid="{\"analysisId\":\""+id+"\",\"cursor\":\""+cursor+"\"}";assertEquals(200,pageRequest(valid+String.join("",Collections.nCopies(4096-valid.getBytes(java.nio.charset.StandardCharsets.UTF_8).length," "))).getResponse().getStatus());
        String offset=context.getBean(com.wenwen.ai.result.AnalysisCursor.class).issue(id,101,50,java.time.Instant.parse(a.path("meta").path("expiresAt").asText()).toEpochMilli());observed.reset();pageError(id,offset,400,"CURSOR_INVALID");assertEquals(1,observed.selects);assertEquals(1,observed.borrowed);assertEquals(0,observed.active.get());
        context.close();buildContext(false);observed.reset();org.springframework.test.web.servlet.MvcResult denied=pageRequest(valid);assertEquals(401,denied.getResponse().getStatus());assertEquals(0,bodyRead.bytes);assertEquals(0,observed.borrowed);
    }
    @Test void everyCurrentUniverseChangeInvalidatesEvenMembersOutsideFilteredCohort() throws Exception {
        String[] changes={"DELETE FROM patient_relation_doctor WHERE doctor_id=101 AND patient_id=101", "DELETE FROM patient_relation_doctor WHERE doctor_id=101 AND patient_id=137", "UPDATE patient_relation_doctor SET doctor_id=202 WHERE patient_id=137", "UPDATE patient_relation_doctor SET research_type=6 WHERE patient_id=137", "DELETE FROM patient_basic_info WHERE id=101", "DELETE FROM patient_basic_info WHERE id=137", "INSERT INTO patient_basic_info(id,name) VALUES(138,'synthetic-new-U');INSERT INTO patient_relation_doctor(doctor_id,patient_id,research_type) VALUES(101,138,0)"};
        List<String> submitted=new ArrayList<>();for(int id=101;id<=117;id++)submitted.add("\""+id+"\"");String body="{\"filters\":{\"ids\":["+String.join(",",submitted)+"]}}";
        for(String change:changes){population37();JsonNode first=success(body);String id=first.path("analysisId").asText(),cursor=first.path("patients").path("nextCursor").asText();assertEquals(17,first.path("n").asInt());assertEquals(37,first.path("studyTotal").asInt());
            StringBuilder canonical=new StringBuilder("101\nRA\n");for(int n=101;n<=137;n++)canonical.append(n).append('\n');byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));StringBuilder expected=new StringBuilder();for(byte x:digest)expected.append(String.format(java.util.Locale.ROOT,"%02x",x&255));assertEquals(expected.toString(),context.getBean(com.wenwen.ai.result.AnalysisResultStore.class).find(id).getScopeFingerprint());
            for(String statement:change.split(";"))sql(statement);observed.reset();pageError(id,cursor,409,"SCOPE_CHANGED");assertEquals(2,observed.selects,"范围失配不得读取display");assertEquals(2,observed.borrowed);assertEquals(2,observed.returned);assertEquals(0,observed.active.get());
        }
        population37();JsonNode stable=success(body);sql("INSERT INTO patient_relation_doctor(doctor_id,patient_id,research_type,miss) VALUES(101,137,1,1)");sql("UPDATE patient_relation_doctor SET miss=1 WHERE doctor_id=101");assertEquals(7,ids(page(stable.path("analysisId").asText(),stable.path("patients").path("nextCursor").asText())).size());
    }
    @Test void pageUniverseAndDisplayShareOneShortRepeatableReadOwner() throws Exception {
        population37();JsonNode first=success("{}");String id=first.path("analysisId").asText(),cursor=first.path("patients").path("nextCursor").asText();observed.reset();java.util.concurrent.atomic.AtomicInteger commits=new java.util.concurrent.atomic.AtomicInteger();
        observed.afterScopeQuery=()->{assertEquals(1,observed.scopeReads);assertEquals(1,observed.active.get());try(java.sql.Connection writer=raw.getConnection();java.sql.Statement s=writer.createStatement()){assertFalse(observed.queryConnections.contains(System.identityHashCode(writer)));writer.setAutoCommit(false);s.execute("UPDATE patient_basic_info SET name='synthetic-new-view',study_no='synthetic-new-study' WHERE id=119");s.execute("DELETE FROM patient_relation_doctor WHERE doctor_id=101 AND patient_id=119");writer.commit();commits.incrementAndGet();}catch(java.sql.SQLException e){throw new AssertionError(e);}};
        org.springframework.test.web.servlet.MvcResult reached=pageRequest("{\"analysisId\":\""+id+"\",\"cursor\":\""+cursor+"\"}");assertEquals(1,commits.get(),"独立writer已在完整U读取后实际提交");assertEquals(1,observed.scopeReads);assertEquals(1,observed.displayReads);assertEquals(200,reached.getResponse().getStatus(),reached.getResponse().getContentAsString());JsonNode current=json.readTree(reached.getResponse().getContentAsByteArray()).path("data");JsonNode item=current.path("patients").path("items").get(0);assertEquals("119",item.path("patientId").asText());assertEquals("synthetic-retained-name-119",item.path("name").asText());assertEquals("synthetic-study-119",item.path("studyNo").asText());
        assertEquals(3,observed.selects);assertEquals(observed.queryConnections.get(1),observed.queryConnections.get(2));assertNotEquals(observed.queryConnections.get(0),observed.queryConnections.get(1));assertEquals(Arrays.asList(false,false,false),observed.autoCommits);assertEquals(Collections.nCopies(3,java.sql.Connection.TRANSACTION_REPEATABLE_READ),observed.isolations);assertEquals(2,observed.borrowed);assertEquals(2,observed.returned);assertEquals(0,observed.active.get());
        observed.reset();pageError(id,cursor,409,"SCOPE_CHANGED");assertEquals(0,observed.displayReads);assertEquals(2,observed.borrowed);assertEquals(2,observed.returned);assertEquals(0,observed.active.get());
    }
    @Test void actualMysqlInsertErrorRollsBackCleanupAndPreservesValidOldResult() throws Exception {
        population37();JsonNode old=success("{}");String id=old.path("analysisId").asText(),cursor=old.path("patients").path("nextCursor").asText();byte[] payload=stored(id);expiredCopies(id,2);String before=sourceDigest();
        observed.reset();observed.failInsertSql=true;error("{}",503,"SERVICE_UNAVAILABLE");assertTrue(observed.mysqlFailures>0);assertEquals("42S02",observed.lastSqlState);assertEquals(1146,observed.lastMysqlError);assertEquals(1,observed.deletes);assertEquals(0,observed.inserts);assertEquals(1,observed.writeRollbacks);assertEquals(0,observed.writeCommits);
        assertEquals(3,runs());assertArrayEquals(payload,stored(id));assertEquals(before,sourceDigest());assertEquals(2,observed.borrowed);assertEquals(2,observed.returned);assertEquals(0,observed.active.get());assertEquals(Arrays.asList(false,false),observed.writeAutoCommits);assertEquals(1,new HashSet<>(observed.writeConnections).size());
        observed.reset();assertEquals(20,ids(page(id,cursor)).size());JsonNode next=success("{}");assertNotEquals(id,next.path("analysisId").asText());assertEquals(2,runs());assertEquals(before,sourceDigest());
    }
    @Test void actualPostInsertFaultRollsBackNewRowAndThisCleanupBeforeReturn() throws Exception {
        population37();JsonNode old=success("{}");String id=old.path("analysisId").asText(),cursor=old.path("patients").path("nextCursor").asText();byte[] payload=stored(id);expiredCopies(id,2);String before=sourceDigest();java.util.concurrent.atomic.AtomicInteger cut=new java.util.concurrent.atomic.AtomicInteger();
        observed.reset();observed.afterInsert=()->{cut.incrementAndGet();assertEquals(1,observed.inserts);assertEquals(1,observed.ownerSawInserted,"同一个实际INSERT已在owner内可读");throw new IllegalStateException("synthetic-post-insert-private-fault");};
        org.springframework.test.web.servlet.MvcResult response=request("{}");assertEquals(503,response.getResponse().getStatus());JsonNode envelope=json.readTree(response.getResponse().getContentAsByteArray());assertEquals("SERVICE_UNAVAILABLE",envelope.path("code").asText());assertTrue(envelope.path("data").isNull());assertFalse(response.getResponse().getContentAsString().contains("synthetic-post-insert-private-fault"));
        assertEquals(1,cut.get());assertEquals(1,observed.inserts);assertNotNull(observed.lastInsertedId);
        assertFalse(runExists(observed.lastInsertedId),"失败INSERT不得留下可继续消费的新run，观察先于任何cleanup/retry");assertEquals(3,runs(),"清理的两条到期行必须一并回滚");assertArrayEquals(payload,stored(id));assertEquals(before,sourceDigest());
        assertEquals(1,observed.writeRollbacks);assertEquals(0,observed.writeCommits);assertEquals(Arrays.asList(false,false),observed.writeAutoCommits);assertEquals(2,observed.borrowed);assertEquals(2,observed.returned);assertEquals(0,observed.active.get());
        observed.reset();assertEquals(20,ids(page(id,cursor)).size());assertNotEquals(id,success("{}").path("analysisId").asText());assertEquals(2,runs());assertEquals(before,sourceDigest());
    }
    @Test void cleanupDeletesAtMostThousandExpiredRowsAndNeverValidRuns() throws Exception {
        population37();JsonNode old=success("{}");String id=old.path("analysisId").asText();byte[] payload=stored(id);expiredCopies(id,1001);
        sql("INSERT INTO ra_ai_analysis_run SELECT 'ffffffff-ffff-ffff-ffff-ffffffffffff',303,scope_fingerprint,sort_key,payload_version,payload,payload_sha256,created_at_ms,expires_at_ms FROM ra_ai_analysis_run WHERE id='"+id+"'");assertEquals(1003,runs());
        observed.reset();success("{}");assertEquals(4,runs());assertEquals(1,observed.deletes);assertEquals(1,observed.inserts);assertEquals(1,observed.writeCommits);assertArrayEquals(payload,stored(id));assertArrayEquals(payload,stored("ffffffff-ffff-ffff-ffff-ffffffffffff"));
        try(java.sql.Connection c=raw.getConnection();java.sql.Statement s=c.createStatement();java.sql.ResultSet r=s.executeQuery("SELECT COUNT(*) FROM ra_ai_analysis_run WHERE expires_at_ms<="+clock.now.toEpochMilli())){assertTrue(r.next());assertEquals(1,r.getInt(1));}
        success("{}");assertEquals(4,runs());try(java.sql.Connection c=raw.getConnection();java.sql.Statement s=c.createStatement();java.sql.ResultSet r=s.executeQuery("SELECT COUNT(*) FROM ra_ai_analysis_run WHERE expires_at_ms<="+clock.now.toEpochMilli())){assertTrue(r.next());assertEquals(0,r.getInt(1));}
        assertEquals(20,ids(page(id,old.path("patients").path("nextCursor").asText())).size());
    }
    @Test void actualStoreAndDisplaySelectFailuresReleaseTheirReachedOwnersAndRecover() throws Exception {
        population37();JsonNode first=success("{}");String id=first.path("analysisId").asText(),cursor=first.path("patients").path("nextCursor").asText();byte[] original=stored(id);
        observed.reset();observed.failRunReadSql=true;pageError(id,cursor,503,"SERVICE_UNAVAILABLE");assertTrue(observed.mysqlFailures>0);assertEquals("42S02",observed.lastSqlState);assertEquals(1146,observed.lastMysqlError);assertEquals(1,observed.selects);assertEquals(1,observed.borrowed);assertEquals(1,observed.returned);assertEquals(0,observed.active.get());assertEquals(0,observed.scopeReads);assertEquals(0,observed.displayReads);assertArrayEquals(original,stored(id));
        observed.reset();assertEquals(20,ids(page(id,cursor)).size());
        observed.reset();observed.failDisplaySql=true;pageError(id,cursor,503,"SERVICE_UNAVAILABLE");assertTrue(observed.mysqlFailures>0);assertEquals("42S02",observed.lastSqlState);assertEquals(1146,observed.lastMysqlError);assertEquals(3,observed.selects);assertEquals(2,observed.borrowed);assertEquals(2,observed.returned);assertEquals(0,observed.active.get());assertEquals(1,observed.scopeReads);assertEquals(1,observed.displayReads);assertEquals(Arrays.asList(false,false,false),observed.autoCommits);assertArrayEquals(original,stored(id));
        observed.reset();assertEquals(20,ids(page(id,cursor)).size());assertEquals(1,runs());
    }
    @Test void corruptPayloadTypesCountsIdsOrderingVersionAndHashFailClosed() throws Exception {
        population37();JsonNode first=success("{}");String id=first.path("analysisId").asText(),cursor=first.path("patients").path("nextCursor").asText();byte[] original=stored(id);
        List<java.util.function.Consumer<com.fasterxml.jackson.databind.node.ObjectNode>> mutations=Arrays.asList(
            p->p.put("n","37"),p->p.put("n",36),p->p.put("rows","not-an-array"),p->((com.fasterxml.jackson.databind.node.ObjectNode)p.path("rows").get(0)).put("patientId","0120"),p->((com.fasterxml.jackson.databind.node.ObjectNode)p.path("rows").get(0)).put("das28At","4.2"),
            p->((com.fasterxml.jackson.databind.node.ObjectNode)p.path("rows").get(0).path("clinical")).set("age",json.createObjectNode().put("name","synthetic-hidden-canary")),
            p->((com.fasterxml.jackson.databind.node.ObjectNode)p.path("rows").get(0)).put("name","synthetic-hidden-canary"),
            p->{com.fasterxml.jackson.databind.node.ArrayNode a=(com.fasterxml.jackson.databind.node.ArrayNode)p.path("rows");JsonNode r=a.get(0);a.set(0,a.get(1));a.set(1,r);},
            p->{com.fasterxml.jackson.databind.node.ArrayNode a=(com.fasterxml.jackson.databind.node.ArrayNode)p.path("rows");a.set(1,a.get(0));},
            p->((com.fasterxml.jackson.databind.node.ObjectNode)p.path("meta")).put("expiresAt",7),p->((com.fasterxml.jackson.databind.node.ObjectNode)p.path("rows").get(0).path("evaluation").path("crp")).put("value","NaN"));
        for(java.util.function.Consumer<com.fasterxml.jackson.databind.node.ObjectNode> mutation:mutations){com.fasterxml.jackson.databind.node.ObjectNode bad=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(original);mutation.accept(bad);replacePayload(id,json.writeValueAsBytes(bad),true);observed.reset();pageError(id,cursor,503,"SERVICE_UNAVAILABLE");assertEquals(1,observed.selects);assertEquals(1,observed.borrowed);assertEquals(1,observed.returned);assertEquals(0,observed.active.get());assertEquals(0,observed.scopeReads);}
        replacePayload(id,original,false);observed.reset();pageError(id,cursor,503,"SERVICE_UNAVAILABLE");assertEquals(1,observed.selects);
        replacePayload(id,original,true);for(String sql:Arrays.asList("UPDATE ra_ai_analysis_run SET payload_version=0 WHERE id='"+id+"'","UPDATE ra_ai_analysis_run SET payload_version=1,sort_key='BAD' WHERE id='"+id+"'")){sql(sql);observed.reset();pageError(id,cursor,503,"SERVICE_UNAVAILABLE");assertEquals(1,observed.selects);}
        sql("UPDATE ra_ai_analysis_run SET sort_key='DAS28_AT_DESC_NULL_LAST_ID_ASC' WHERE id='"+id+"'");assertEquals(20,ids(page(id,cursor)).size());
    }
    @Test void expiryUsesRequestEntryInstantEvenWhenBodyReadCrossesBoundary() throws Exception {
        population37();JsonNode first=success("{}");String id=first.path("analysisId").asText(),cursor=first.path("patients").path("nextCursor").asText();long expiry=java.time.Instant.parse(first.path("meta").path("expiresAt").asText()).toEpochMilli();
        clock.now=java.time.Instant.ofEpochMilli(expiry-1);bodyRead.onFirstRead=()->clock.now=java.time.Instant.ofEpochMilli(expiry);
        assertEquals(20,ids(page(id,cursor)).size());observed.reset();pageError(id,cursor,410,"ANALYSIS_EXPIRED");assertEquals(0,observed.borrowed);
    }
    @Test void newStoreCodecAndHttpContextReadSameDurableRunAndEmptyFilterSemantics() throws Exception {
        population37();JsonNode first=success("{}");String id=first.path("analysisId").asText(),cursor=first.path("patients").path("nextCursor").asText();byte[] bytes=stored(id);Object oldStore=context.getBean(com.wenwen.ai.result.AnalysisResultStore.class);context.close();buildContext(true);assertNotSame(oldStore,context.getBean(com.wenwen.ai.result.AnalysisResultStore.class));
        JsonNode middle=page(id,cursor);assertEquals(Arrays.asList("119","137","107","128","104","125","115","132","106","126","114","134","109","123","113","129","112","135","116","136"),ids(middle));assertArrayEquals(bytes,stored(id));
        com.wenwen.ai.result.AnalysisRun run=context.getBean(com.wenwen.ai.result.AnalysisResultStore.class).find(id);assertEquals(37,((List<?>)new com.wenwen.ai.result.AnalysisResultCodec().decode(run).get("rows")).size());
        JsonNode empty=success("{\"filters\":{\"ids\":[]}}");assertEquals(0,empty.path("n").asInt());assertEquals(37,empty.path("studyTotal").asInt());assertEquals(0,empty.path("submittedUniqueIdsN").asInt());assertEquals(0,empty.path("effectiveIdsN").asInt());assertTrue(json.readTree(stored(empty.path("analysisId").asText())).path("filters").path("ids").isArray());assertEquals(0,empty.path("patients").path("returnedCount").asInt());assertTrue(empty.path("patients").path("nextCursor").isNull());
        JsonNode normalized=success("{\"filters\":{\"ids\":[\"137\",\"101\",\"101\"]}}");assertEquals(2,normalized.path("submittedUniqueIdsN").asInt());assertEquals(json.readTree("[\"101\",\"137\"]"),json.readTree(stored(normalized.path("analysisId").asText())).path("filters").path("ids"));
        tiedPopulation(0);JsonNode noUniverse=success("{}");assertEquals(0,noUniverse.path("n").asInt());assertEquals(0,noUniverse.path("studyTotal").asInt());assertTrue(noUniverse.path("submittedUniqueIdsN").isNull());assertTrue(json.readTree(stored(noUniverse.path("analysisId").asText())).path("filters").path("ids").isNull());
        assertEquals(com.wenwen.ai.result.AnalysisValues.sha256("101\nRA\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)),context.getBean(com.wenwen.ai.result.AnalysisResultStore.class).find(noUniverse.path("analysisId").asText()).getScopeFingerprint());
    }
    @Test void retainedTreatmentAndDictionaryPolicyStayFixedAndUnsafeDrugTextIsOmitted() throws Exception {
        population37();treatmentSource(1190,drugs(drug("阿达木单抗","2026-01-07",null)));treatmentSource(1070,drugs(drug("阿达木单抗","synthetic-invalid-date-CANARY",null)));treatmentSource(1370,drugs(drug("synthetic-unknown-drug-CANARY",null,null)));
        JsonNode first=success("{}");String id=first.path("analysisId").asText(),cursor=first.path("patients").path("nextCursor").asText();String text=new String(stored(id),java.nio.charset.StandardCharsets.UTF_8);assertFalse(text.contains("synthetic-invalid-date-CANARY"));assertFalse(text.contains("synthetic-unknown-drug-CANARY"));
        JsonNode old=page(id,cursor),tx=treatment(old,119);assertEquals("TNFi",tx.path("category").asText());assertEquals(9,tx.path("schemeDurationMonths").asInt());assertEquals(2,tx.path("line").asInt());assertEquals("dev-drug-v04",tx.path("provenance").path("dictionaryVersion").asText());
        JsonNode invalid=treatment(old,107);assertTrue(invalid.path("provenance").path("drugFacts").get(0).path("startTime").isNull());assertTrue(invalid.path("provenance").path("quality").toString().contains("INVALID_TREATMENT_DATE"));
        dictionary().view=new com.wenwen.ai.treatment.DrugDictionary.Snapshot("synthetic-policy-v2",Collections.emptyMap());
        JsonNode replay=page(id,cursor);assertEquals(tx,treatment(replay,119));assertEquals(old.path("meta").path("policyVersions"),replay.path("meta").path("policyVersions"));
        JsonNode fresh=success("{}");assertNotEquals(id,fresh.path("analysisId").asText());assertEquals("synthetic-policy-v2",fresh.path("meta").path("policyVersions").path("drugDictionary").asText());assertEquals("TNFi",treatment(page(id,cursor),119).path("category").asText());
    }
    @Test void actualHttpWriteFailureAfterCommitLeavesOnlyCompleteTtlOrphan() throws Exception {
        population37();JsonNode old=success("{}");String oldId=old.path("analysisId").asText();observed.reset();java.util.concurrent.atomic.AtomicReference<String> committed=new java.util.concurrent.atomic.AtomicReference<>();java.util.concurrent.atomic.AtomicInteger cut=new java.util.concurrent.atomic.AtomicInteger();
        org.springframework.http.converter.json.MappingJackson2HttpMessageConverter failing=new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(){
            @Override protected void writeInternal(Object object,java.lang.reflect.Type type,org.springframework.http.HttpOutputMessage output)throws java.io.IOException {
                cut.incrementAndGet();com.wenwen.vo.AiCohortVo value=(com.wenwen.vo.AiCohortVo)((com.wenwen.vo.DataResult<?>)object).getData();committed.set(value.getAnalysisId());
                assertEquals(1,observed.writeCommits);assertEquals(0,observed.active.get());try{assertTrue(runExists(value.getAnalysisId()));assertEquals(37,json.readTree(stored(value.getAnalysisId())).path("rows").size());}catch(Exception e){throw new AssertionError(e);}
                throw new java.io.IOException("synthetic-http-output-fault");
            }
        };
        http=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(context.getBean(com.wenwen.controller.ra.AiCohortController.class)).setMessageConverters(failing).addFilters(bodyRead).build();
        assertThrows(Exception.class,()->request("{}"));assertEquals(1,cut.get());assertNotNull(committed.get());assertNotEquals(oldId,committed.get());assertEquals(2,runs());assertEquals(1,observed.inserts);assertEquals(1,observed.writeCommits);assertEquals(0,observed.writeRollbacks);assertEquals(2,observed.borrowed);assertEquals(2,observed.returned);assertEquals(0,observed.active.get());
        http=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context).addFilters(bodyRead).build();
        com.wenwen.ai.result.AnalysisRun orphan=context.getBean(com.wenwen.ai.result.AnalysisResultStore.class).find(committed.get());assertEquals(37,((List<?>)new com.wenwen.ai.result.AnalysisResultCodec().decode(orphan).get("rows")).size());
        clock.now=java.time.Instant.ofEpochMilli(orphan.getExpiresAtMs());success("{}");assertEquals(1,runs());assertFalse(runExists(committed.get()));assertFalse(runExists(oldId));
    }
    @Test void actualDefaultMissingKeyAllowsContextStartupButRefusesAnalysisWithoutSql() throws Exception {
        context.close();syntheticCursorKey=null;buildContext(true);assertTrue(context.isActive());error("{}",503,"SERVICE_UNAVAILABLE");assertEquals(0,observed.borrowed);assertEquals(0,observed.selects);assertEquals(0,runs());
        context.close();syntheticCursorKey="YQ==";buildContext(true);error("{}",503,"SERVICE_UNAVAILABLE");assertEquals(0,observed.borrowed);assertEquals(0,runs());
        context.close();syntheticCursorKey=SYNTHETIC_KEY;buildContext(true);assertEquals(5,success("{}").path("n").asInt());
    }
    @Test void missingRequiredMetadataAndQualityArraysAreCorruptionRatherThanBusinessNulls() throws Exception {
        population37();JsonNode first=success("{}");String id=first.path("analysisId").asText(),cursor=first.path("patients").path("nextCursor").asText();byte[] original=stored(id);
        List<java.util.function.Consumer<com.fasterxml.jackson.databind.node.ObjectNode>> mutations=Arrays.asList(
            p->((com.fasterxml.jackson.databind.node.ObjectNode)p.path("meta")).putNull("traceId"),p->((com.fasterxml.jackson.databind.node.ObjectNode)p.path("meta")).putNull("asOfDate"),p->((com.fasterxml.jackson.databind.node.ObjectNode)p.path("meta").path("policyVersions")).putNull("statistics"),
            p->((com.fasterxml.jackson.databind.node.ObjectNode)p.path("rows").get(0).path("scoreProvenance")).putNull("quality"),p->((com.fasterxml.jackson.databind.node.ObjectNode)p.path("rows").get(0).path("qc")).putNull("missingCodes"),
            p->((com.fasterxml.jackson.databind.node.ObjectNode)p.path("filters")).put("age","synthetic-arbitrary-filter-canary"));
        for(java.util.function.Consumer<com.fasterxml.jackson.databind.node.ObjectNode> mutation:mutations){com.fasterxml.jackson.databind.node.ObjectNode bad=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(original);mutation.accept(bad);replacePayload(id,json.writeValueAsBytes(bad),true);observed.reset();pageError(id,cursor,503,"SERVICE_UNAVAILABLE");assertEquals(1,observed.selects);assertEquals(0,observed.scopeReads);assertEquals(1,observed.borrowed);assertEquals(1,observed.returned);assertEquals(0,observed.active.get());}
        replacePayload(id,original,true);assertEquals(20,ids(page(id,cursor)).size());
    }
    @Test void encodingRejectsUnsafePolicyTextBeforeInsertAndSourceRemainsReadOnly() throws Exception {
        population37();String before=sourceDigest();dictionary().view=new com.wenwen.ai.treatment.DrugDictionary.Snapshot("synthetic private policy CANARY",Collections.emptyMap());observed.reset();org.springframework.test.web.servlet.MvcResult r=request("{}");assertEquals(503,r.getResponse().getStatus());JsonNode envelope=json.readTree(r.getResponse().getContentAsByteArray());assertEquals("SERVICE_UNAVAILABLE",envelope.path("code").asText());assertTrue(envelope.path("data").isNull());assertFalse(r.getResponse().getContentAsString().contains("CANARY"));assertEquals(0,runs());assertEquals(0,observed.inserts);assertEquals(0,observed.deletes);assertEquals(5,observed.selects);assertEquals(1,observed.borrowed);assertEquals(1,observed.returned);assertEquals(0,observed.active.get());assertEquals(before,sourceDigest());
        dictionary().view=new com.wenwen.ai.treatment.DevelopmentDrugDictionary().snapshot();assertEquals(37,success("{}").path("n").asInt());assertEquals(1,runs());
    }
    @Test void actualScopeSelectFailureNeverReadsDisplayAndReleasesBeforeRecovery() throws Exception {
        population37();JsonNode first=success("{}");String id=first.path("analysisId").asText(),cursor=first.path("patients").path("nextCursor").asText();observed.reset();observed.failClinicalTable="SELECT p.id FROM patient_basic_info";
        pageError(id,cursor,503,"SERVICE_UNAVAILABLE");assertTrue(observed.mysqlFailures>0);assertEquals(1146,observed.lastMysqlError);assertEquals("42S02",observed.lastSqlState);assertEquals(2,observed.selects);assertEquals(0,observed.displayReads);assertEquals(2,observed.borrowed);assertEquals(2,observed.returned);assertEquals(0,observed.active.get());
        observed.reset();assertEquals(20,ids(page(id,cursor)).size());assertEquals(1,runs());
    }
    @Test void nullDisplayColumnsAreAllowedButIncompleteBatchCoverageIsFailure() throws Exception {
        population37();JsonNode first=success("{}");String id=first.path("analysisId").asText(),cursor=first.path("patients").path("nextCursor").asText();
        sql("ALTER TABLE patient_basic_info MODIFY name VARCHAR(100) NULL");sql("UPDATE patient_basic_info SET name=NULL,study_no=NULL WHERE id=119");JsonNode item=page(id,cursor).path("patients").path("items").get(0);assertEquals("119",item.path("patientId").asText());assertTrue(item.path("name").isNull());assertTrue(item.path("studyNo").isNull());assertEquals(3.7,item.path("das28At").asDouble());
        observed.reset();observed.omitDisplayRow=true;pageError(id,cursor,503,"SERVICE_UNAVAILABLE");assertEquals(0,observed.mysqlFailures,"实际SQL正常读19行，覆盖失配是程序失败");assertEquals(3,observed.selects);assertEquals(1,observed.displayReads);assertEquals(2,observed.borrowed);assertEquals(2,observed.returned);assertEquals(0,observed.active.get());
        observed.reset();assertEquals(20,ids(page(id,cursor)).size());
    }
    @Test void fixedOmissionMarkerAndRequiredCountsAndClinicalSourcesCannotBecomeNull() throws Exception {
        population37();JsonNode first=success("{}");String id=first.path("analysisId").asText(),cursor=first.path("patients").path("nextCursor").asText();byte[] original=stored(id);
        List<java.util.function.Consumer<com.fasterxml.jackson.databind.node.ObjectNode>> mutations=Arrays.asList(
            p->((com.fasterxml.jackson.databind.node.ObjectNode)p.path("rows").get(0).path("evaluation").path("pain")).putNull("rawRetention"),
            p->((com.fasterxml.jackson.databind.node.ObjectNode)p.path("stats")).putNull("evaluable"),p->((com.fasterxml.jackson.databind.node.ObjectNode)p.path("byTx")).putNull("unknownTxN"),
            p->((com.fasterxml.jackson.databind.node.ObjectNode)p.path("rows").get(0).path("clinical")).putNull("sero"),p->((com.fasterxml.jackson.databind.node.ObjectNode)p.path("rows").get(0).path("clinicalProvenance").path("age")).putNull("source"));
        for(java.util.function.Consumer<com.fasterxml.jackson.databind.node.ObjectNode> mutation:mutations){com.fasterxml.jackson.databind.node.ObjectNode bad=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(original);mutation.accept(bad);replacePayload(id,json.writeValueAsBytes(bad),true);observed.reset();pageError(id,cursor,503,"SERVICE_UNAVAILABLE");assertEquals(1,observed.selects);assertEquals(0,observed.scopeReads);assertEquals(1,observed.borrowed);assertEquals(1,observed.returned);assertEquals(0,observed.active.get());}
        replacePayload(id,original,true);assertEquals(20,ids(page(id,cursor)).size());
    }
    private Set<String> fields(JsonNode node){Set<String> out=new HashSet<>();node.fieldNames().forEachRemaining(out::add);return out;}
}
