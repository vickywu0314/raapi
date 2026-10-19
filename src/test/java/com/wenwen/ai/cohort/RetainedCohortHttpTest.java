package com.wenwen.ai.cohort;
import com.fasterxml.jackson.databind.JsonNode;
import com.wenwen.ai.result.*;
import com.wenwen.ai.analysis.CohortResultComputer;
import com.wenwen.ai.scope.TrustedDoctor;
import com.wenwen.ai.source.CohortSourceAdapter;
import java.util.*;
import java.sql.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** 新查询会话契约替代旧整blob快照契约；临床数值和安全边界保持独立期待。 */
class RetainedCohortHttpTest extends RetainedCohortHttpFixture {
    @Test void storesOnlyCanonicalQueryAndReturnsFirstTenThenTwentyThenSeven()throws Exception {
        population37();JsonNode first=success("{}");String id=first.path("analysisId").asText();
        assertEquals(37,first.path("n").asInt());assertEquals(Arrays.asList("120","110","130","105","122","108","124","131","133","102"),ids(first));
        JsonNode query=json.readTree(stored(id));assertEquals(10,query.size());assertEquals("RA",query.path("studyCode").asText());assertTrue(query.path("ids").isNull());assertFalse(query.has("rows"));assertTrue(stored(id).length<1024);
        AnalysisRun session=context.getBean(AnalysisResultStore.class).find(id);assertNull(session.getPayload());assertEquals(2,session.getPayloadVersion());assertEquals("2026-10-07",session.getAsOfDate());
        JsonNode middle=page(id,first.path("patients").path("nextCursor").asText());
        assertEquals(Arrays.asList("119","137","107","128","104","125","115","132","106","126","114","134","109","123","113","129","112","135","116","136"),ids(middle));
        JsonNode last=page(id,middle.path("patients").path("nextCursor").asText());assertEquals(Arrays.asList("101","103","111","117","118","121","127"),ids(last));assertFalse(last.path("patients").path("hasMore").asBoolean());
        assertEquals(first.path("meta").path("asOfDate"),middle.path("meta").path("asOfDate"));assertEquals(first.path("meta").path("policyVersions"),middle.path("meta").path("policyVersions"));assertEquals("LIVE_SOURCE_V04",middle.path("meta").path("patientProjection").asText());assertEquals(0,observed.active.get());
        try(Connection c=raw.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT COUNT(*) FROM ra_ai_analysis_run")){assertTrue(r.next());assertEquals(0,r.getInt(1));}
    }
    @Test void tiesNullsAndPartitionEdgesHaveDeterministicIdOrder()throws Exception {
        for(int count:new int[]{0,1,9,10,11,30,31,55}) {
            tiedPopulation(count);if(count>2)sql("DELETE FROM patient_follow_up_history WHERE patient_basic_info_id IN("+(201+count-2)+","+(201+count-1)+")");
            JsonNode current=success("{}");String id=current.path("analysisId").asText();List<String> actual=new ArrayList<>(ids(current));int offset=10;
            while(current.path("patients").path("hasMore").asBoolean()) {String cursor=current.path("patients").path("nextCursor").asText();JsonNode signed=json.readTree(Base64.getUrlDecoder().decode(cursor.split("\\.")[0]));assertEquals(offset,signed.path("nextOffset").asInt());assertEquals(101,signed.path("doctorId").asInt());assertEquals(AnalysisValues.SORT,signed.path("sort").asText());current=page(id,cursor);actual.addAll(ids(current));offset+=20;}
            List<String> expected=new ArrayList<>();for(int i=0;i<count;i++)expected.add(Integer.toString(201+i));assertEquals(expected,actual);
        }
    }
    @Test void sourceChangesAreLiveWhileAsOfAndExpiryStayFixed()throws Exception {
        tiedPopulation(37);JsonNode first=success("{}");String id=first.path("analysisId").asText(),cursor=first.path("patients").path("nextCursor").asText();
        sql("UPDATE patient_follow_up_history SET bqpg='{\"result\":{\"crpScore\":2.705}}' WHERE patient_basic_info_id=211");
        JsonNode changed=page(id,cursor);assertEquals("212",ids(changed).get(0));assertEquals("211",ids(page(id,changed.path("patients").path("nextCursor").asText())).get(6));
        clock.now=clock.now.plusSeconds(120);JsonNode replay=page(id,cursor);assertEquals(first.path("meta").path("asOfDate"),replay.path("meta").path("asOfDate"));assertEquals(first.path("meta").path("expiresAt"),replay.path("meta").path("expiresAt"));
        CohortResultComputer.Computed live=context.getBean(AnalysisResultStore.class).export(101,id,clock.instant(),1000);Map<?,?> row=live.rows.stream().filter(r->"211".equals(r.get("patientId"))).findFirst().get();assertEquals(new java.math.BigDecimal("2.71"),row.get("das28At"));
    }
    @Test void signatureOwnerExpiryAndScopeChangesFailClosed()throws Exception {
        population37();JsonNode first=success("{}");String id=first.path("analysisId").asText(),cursor=first.path("patients").path("nextCursor").asText();
        context.getBean(FakePrincipalProvider.class).doctor=202;observed.reset();pageError(id,cursor,404,"RESOURCE_NOT_FOUND");assertEquals(0,observed.selects);context.getBean(FakePrincipalProvider.class).doctor=101;
        String altered=cursor.substring(0,cursor.length()-2)+(cursor.endsWith("aa")?"bb":"aa");observed.reset();pageError(id,altered,400,"CURSOR_INVALID");assertEquals(0,observed.selects);
        sql("DELETE FROM patient_relation_doctor WHERE doctor_id=101 AND patient_id=101");pageError(id,cursor,409,"SCOPE_CHANGED");
        clock.now=java.time.Instant.parse(first.path("meta").path("expiresAt").asText());observed.reset();pageError(id,cursor,410,"ANALYSIS_EXPIRED");assertEquals(0,observed.borrowed);
    }
    @Test void missingLegacyAndUnknownReferencesUseExplicitReanalysis()throws Exception {
        population37();JsonNode first=success("{}");String id=first.path("analysisId").asText(),cursor=first.path("patients").path("nextCursor").asText();sql("DELETE FROM ra_ai_analysis_query_session WHERE id='"+id+"'");pageError(id,cursor,410,"ANALYSIS_EXPIRED");
        String missing=UUID.randomUUID().toString();String signed=context.getBean(AnalysisCursor.class).issue(missing,101,10,clock.now.plusSeconds(900).toEpochMilli());pageError(missing,signed,410,"ANALYSIS_EXPIRED");
    }
    @Test void metadataIsStrictAndVersionChangesRequireReanalysis()throws Exception {
        population37();JsonNode first=success("{}");String id=first.path("analysisId").asText(),cursor=first.path("patients").path("nextCursor").asText();byte[] original=stored(id);
        for(String bad:new String[]{"{\"at\":\"now\",\"at\":\"6m\"}","{","{}","null",new String(original,"UTF-8")+" {}"}) {try(Connection c=raw.getConnection();PreparedStatement s=c.prepareStatement("UPDATE ra_ai_analysis_query_session SET query_json=? WHERE id=?")){s.setString(1,bad);s.setString(2,id);s.executeUpdate();}observed.reset();pageError(id,cursor,503,"SERVICE_UNAVAILABLE");assertEquals(1,observed.selects);}
        try(Connection c=raw.getConnection();PreparedStatement s=c.prepareStatement("UPDATE ra_ai_analysis_query_session SET query_json=? WHERE id=?")){s.setString(1,new String(original,"UTF-8"));s.setString(2,id);s.executeUpdate();}
        dictionary().view=new com.wenwen.ai.treatment.DrugDictionary.Snapshot("synthetic-next-v2",Collections.emptyMap());pageError(id,cursor,410,"ANALYSIS_EXPIRED");
        JsonNode next=success("{}");assertEquals("synthetic-next-v2",next.path("meta").path("policyVersions").path("drugDictionary").asText());
    }
    @Test void emptyAndFiveThousandLargeIdsKeepQuerySemanticsWithoutTextCap()throws Exception {
        population37();JsonNode empty=success("{\"filters\":{\"ids\":[]}}");assertEquals(0,empty.path("n").asInt());assertTrue(json.readTree(stored(empty.path("analysisId").asText())).path("ids").isArray());
        List<String> ids=new ArrayList<>();for(int i=0;i<5000;i++)ids.add(Long.toString(Long.MAX_VALUE-i));String body=json.writeValueAsString(com.wenwen.vo.AiCohortVo.object("filters",com.wenwen.vo.AiCohortVo.object("ids",ids)));assertTrue(body.getBytes("UTF-8").length<128*1024);
        JsonNode large=success(body);assertEquals(0,large.path("n").asInt());assertEquals(5000,large.path("submittedUniqueIdsN").asInt());assertTrue(stored(large.path("analysisId").asText()).length>65535);
    }
    @Test void sourceProvenanceIsLazyImmutableAndPreservesStrictWholeJsonRejection()throws Exception {
        population37();sql("UPDATE patient_follow_up_history SET fzjc='{\"lfsyz\":30,\"lfsyz\":1,\"kccpkt\":100}' WHERE patient_basic_info_id=119");
        com.wenwen.ai.source.SourceBatch batch=context.getBean(CohortSourceAdapter.class).read(101,java.time.LocalDate.of(2026,10,7));java.lang.reflect.Field field=com.wenwen.ai.clinical.PatientClinical.class.getDeclaredField("provenance");field.setAccessible(true);
        for(com.wenwen.ai.clinical.PatientClinical p:batch.getClinical().values())assertNull(field.get(p));
        com.wenwen.ai.clinical.PatientClinical selected=batch.getClinical().get(119L);assertEquals("UNKNOWN",selected.getSero());Map<String,Object> provenance=selected.getProvenance();assertTrue(((List<?>)((Map<?,?>)provenance.get("sero")).get("observations")).isEmpty());assertSame(provenance,selected.getProvenance());assertThrows(UnsupportedOperationException.class,()->provenance.put("bad",1));assertNull(field.get(batch.getClinical().get(120L)));
    }
    @Test void oldBlobCapCannotBlockAnalyzeAndPageEvenWhenInvalid()throws Exception {
        population37();for(String limit:new String[]{"1","0","invalid","16777216"}){setting("maxBytes",limit);JsonNode first=success("{}");assertEquals(20,ids(page(first.path("analysisId").asText(),first.path("patients").path("nextCursor").asText())).size());}
    }
    @Test void fullEquivalentLegacyProjectionAboveEightMiBCanAnalyzeAndPageWithoutBlobWrites()throws Exception {
        population37();observed.rejectLegacyRunAccess=true; // 合成功能容量用例，无性能计时或基准断言
        // 220000 dated association facts on an unreturned null-score patient: the old complete projection exceeded 8MiB.
        try(Connection c=raw.getConnection();PreparedStatement s=c.prepareStatement("INSERT INTO patient_comorbidity(id,patient_id,disease_code,since_year) VALUES(?,127,'FM','2020')")){c.setAutoCommit(false);for(int i=0;i<220000;i++){s.setLong(1,1000000L+i);s.addBatch();if(i%1000==999)s.executeBatch();}c.commit();}
        JsonNode first=success("{}");assertEquals(37,first.path("n").asInt());String id=first.path("analysisId").asText();JsonNode middle=page(id,first.path("patients").path("nextCursor").asText());assertEquals(20,ids(middle).size());assertCompleteLargeLastPage(id,middle.path("patients").path("nextCursor").asText());
        CohortResultComputer.Computed all=context.getBean(AnalysisResultStore.class).export(101,id,clock.instant(),1000);Map<String,Object> large=all.rows.stream().filter(row->"127".equals(row.get("patientId"))).findFirst().get();assertEquals(220000,((List<?>)((Map<?,?>)((Map<?,?>)large.get("clinicalProvenance")).get("fm")).get("observations")).size());byte[] equivalent=new AnalysisResultCodec().encode(all.result,all.rows,all.query);System.out.println("合成旧完整投影bytes="+equivalent.length);assertTrue(equivalent.length>8388608,"真实旧完整安全投影字节必须超过旧8MiB");assertEquals(0,observed.legacyRunReads);assertEquals(0,observed.legacyRunWrites);assertTrue(stored(id).length<1024);assertEquals(1,runs());assertEquals(0,observed.active.get());
    }
    @Test void sourceAndSessionWriteFailuresNeverPublishPartialSession()throws Exception {
        population37();observed.failInsertSql=true;error("{}",503,"SERVICE_UNAVAILABLE");assertEquals(0,runs());assertEquals(0,observed.active.get());observed.reset();observed.failSecondSql=true;error("{}",503,"SERVICE_UNAVAILABLE");assertEquals(0,runs());assertEquals(2,observed.selects);assertEquals(0,observed.active.get());
    }
    @Test void contextRebuildReusesQueryButRecomputesCurrentFacts()throws Exception {
        population37();JsonNode first=success("{}");String id=first.path("analysisId").asText(),cursor=first.path("patients").path("nextCursor").asText();byte[] query=stored(id);context.close();buildContext(true);assertEquals(20,ids(page(id,cursor)).size());assertArrayEquals(query,stored(id));assertNull(context.getBean(AnalysisResultStore.class).find(id).getPayload());
    }
    @Test void sourceShrinksBeyondSignedOffsetReturnsEmptyTerminalPage()throws Exception {
        tiedPopulation(37);sql("UPDATE patient_basic_info SET gender=2");JsonNode first=success("{\"filters\":{\"sex\":\"F\"}}");String id=first.path("analysisId").asText();JsonNode middle=page(id,first.path("patients").path("nextCursor").asText());String cursor=middle.path("patients").path("nextCursor").asText();
        sql("UPDATE patient_basic_info SET gender=1 WHERE id>212");JsonNode last=page(id,cursor);assertEquals(12,last.path("patients").path("total").asInt());assertEquals(0,last.path("patients").path("returnedCount").asInt());assertFalse(last.path("patients").path("hasMore").asBoolean());assertTrue(last.path("patients").path("nextCursor").isNull());
    }
    @Test void serologyThresholdConfigurationDriftCannotReuseOldRuleIdentity()throws Exception {
        tiedPopulation(37);sql("UPDATE patient_follow_up_history SET fzjc='{\"lfsyz\":30}'");JsonNode first=success("{\"filters\":{\"sero\":\"1\"}}");assertEquals(37,first.path("n").asInt());
        org.springframework.test.util.ReflectionTestUtils.setField(context.getBean(CohortSourceAdapter.class),"rfUln",40d);
        pageError(first.path("analysisId").asText(),first.path("patients").path("nextCursor").asText(),410,"ANALYSIS_EXPIRED");assertEquals(0,success("{\"filters\":{\"sero\":\"1\"}}").path("n").asInt());
    }
    @Test void currentPageKeepsSafeOmissionOfPainAndInvalidTreatmentText()throws Exception {
        population37();sql("UPDATE patient_follow_up_history SET bqpg='{\"result\":{\"crpScore\":3.7},\"tjScore\":\"private-pain-CANARY\"}' WHERE id=1190");JsonNode first=success("{}");JsonNode selected=patientById(page(first.path("analysisId").asText(),first.path("patients").path("nextCursor").asText()),119);
        assertEquals(3.7,selected.path("das28At").asDouble());assertTrue(selected.path("evaluation").path("pain").path("raw").isNull());assertEquals("OMITTED",selected.path("evaluation").path("pain").path("rawRetention").asText());assertFalse(selected.toString().contains("private-pain-CANARY"));assertEquals("synthetic-retained-name-119",selected.path("name").asText());
    }
    @Test void pageSkipsUnconsumedStatisticsButKeepsFullSelectionSortAndMetadata()throws Exception {
        population37();JsonNode initial=success("{}");AnalysisResultStore store=context.getBean(AnalysisResultStore.class);AnalysisRun session=store.find(initial.path("analysisId").asText());
        CohortResultComputer.Computed page=store.compute(new TrustedDoctor(101),new AnalysisQueryCodec().decode(session),UUID.randomUUID().toString(),clock.instant(),session,10,20,0);
        assertEquals(0,page.aggregationPasses);assertTrue(page.result.getStats().isEmpty());assertTrue(page.result.getByTx().isEmpty());assertTrue(page.result.getFm().isEmpty());assertEquals(37,page.result.getN());assertEquals(20,page.rows.size());assertEquals("119",page.rows.get(0).get("patientId"));assertEquals("136",page.rows.get(19).get("patientId"));assertEquals(initial.path("meta").path("asOfDate").asText(),page.result.getMeta().get("asOfDate"));assertEquals(initial.path("meta").path("expiresAt").asText(),page.result.getMeta().get("expiresAt"));
        CohortResultComputer.Computed exported=store.export(101,session.getId(),clock.instant(),1000);assertEquals(1,exported.aggregationPasses);assertFalse(exported.result.getStats().isEmpty());assertFalse(exported.result.getByTx().isEmpty());assertEquals(37,exported.rows.size());
    }
    @Test void invalidSigningAndTtlSettingsStillFailBeforeAnySourceOrSessionWrite()throws Exception {
        for(String[] invalid:new String[][]{{"keyBase64",""},{"keyBase64","%%%"},{"keyBase64","YQ=="},{"ttlSeconds","0"},{"ttlSeconds","-1"},{"ttlSeconds","invalid"},{"ttlSeconds","9223372036854775807"}}){setting("keyBase64",SYNTHETIC_KEY);setting("ttlSeconds","900");setting(invalid[0],invalid[1]);observed.reset();error("{}",503,"SERVICE_UNAVAILABLE");assertEquals(0,observed.selects);assertEquals(0,observed.inserts);assertEquals(0,runs());}
    }
    @Test void actualPostInsertFailureRollsBackOnlyNewSessionAndOldSessionStaysUsable()throws Exception {
        population37();JsonNode existing=success("{}");String id=existing.path("analysisId").asText();byte[] query=stored(id);observed.reset();java.util.concurrent.atomic.AtomicInteger reached=new java.util.concurrent.atomic.AtomicInteger();
        observed.afterInsert=()->{reached.incrementAndGet();assertEquals(1,observed.ownerSawInserted);try{assertEquals(1,runs(),"独立连接不可见尚未提交的新session");}catch(Exception e){throw new AssertionError(e);}throw new IllegalStateException("synthetic-after-real-session-insert");};
        error("{}",503,"SERVICE_UNAVAILABLE");assertEquals(1,reached.get());assertEquals(1,observed.inserts);assertEquals(1,observed.writeRollbacks);assertEquals(0,observed.writeCommits);assertEquals(1,runs());assertArrayEquals(query,stored(id));assertEquals(20,ids(page(id,existing.path("patients").path("nextCursor").asText())).size());assertEquals(0,observed.active.get());
    }
    private void assertCompleteLargeLastPage(String id,String cursor)throws Exception {
        // page读取完整HTTP body并解析成JSON后再计数，不使用摘要/输出长度替代完整事实。
        JsonNode last=page(id,cursor);assertEquals(Arrays.asList("101","103","111","117","118","121","127"),ids(last));assertEquals(7,last.path("patients").path("returnedCount").asInt());assertFalse(last.path("patients").path("hasMore").asBoolean());assertTrue(last.path("patients").path("nextCursor").isNull());assertEquals(220000,patientById(last,127).path("clinicalProvenance").path("fm").path("observations").size());
    }
}
