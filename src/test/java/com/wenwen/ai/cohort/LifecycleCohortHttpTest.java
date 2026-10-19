package com.wenwen.ai.cohort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.wenwen.vo.AiCohortVo;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import static org.junit.jupiter.api.Assertions.*;
/** 同一合成输入跨实时计算、保留引用、动态定义与实际文件的组合观察。 */
class LifecycleCohortHttpTest extends AiExportHttpFixture {
    static final String[] LEVELS={"remission","low","moderate","high"};
    static final int[][] COUNTS={{22,5,10,0},{22,4,10,1},{23,4,10,0},{22,5,10,0},{22,4,10,0}};
    static final int[][] A_COUNTS={{0,4,2,0},{0,3,2,1},{1,3,2,0},{0,4,2,0},{0,3,2,0}};
    static final String[][] QUARTILES={{"2.525","2.65","2.775",".25"},{"2.525","2.70","2.875",".35"},{"2.425","2.55","2.75",".325"},{"2.525","2.65","2.775",".25"},{"2.5","2.6","2.8",".3"}};
    static final String[] SCORE={"2.70","4.80","0.05","2.70",null}, DIFFERENCE={"1.20","1.25","1.10","1.20","1.15"}, DISPLAY_P={"0.002","0.002","0.065","0.002","0.004"};
    static final double[] P={1.0/462,1.0/462,5.0/77,1.0/462,1.0/231};
    static final int[] TARGET={4,3,4,4,3};
    @Test void committedVisitChangesSeparateFreshAndRetainedConsumers() throws Exception {
        population();
        Roots roots=roots("now",null);
        String initialSource=sourceDigest();
        assertCohort(roots.analysis,0,37,37);
        assertComparison(roots.comparison,0,37);
        assertComparison(compareSuccess(compareBody(null,"now")),0,37);
        retained(roots,0);
        assertEquals(initialSource,sourceDigest());
        for(int state=1;state<=4;state++) {
            if(state==1) committed("INSERT INTO patient_follow_up_history(id,patient_basic_info_id,research_type,follow_up_date,bqpg,fzjc) VALUES(1111,111,0,'2026-10-07','{\"result\":{\"crpScore\":4.80}}','{\"cfydb\":99}')",1);
            if(state==2) {
                committed("UPDATE patient_follow_up_history SET bqpg='{\"result\":{\"crpScore\":0.05}}' WHERE id=1111",1);
                committed("UPDATE patient_basic_info SET name='lifecycle-current-name-CANARY',study_no='lifecycle-current-study-CANARY' WHERE id=111",1);
            }
            if(state==3) committed("DELETE FROM patient_follow_up_history WHERE id=1111",1);
            if(state==4) committed("DELETE FROM patient_follow_up_history WHERE id=1110",1);
            String changed=sourceDigest();
            JsonNode fresh=success("{}"),opened=open(roots.definition),comparison=compareSuccess(compareBody(roots.aDefinition,"now"));
            assertNotEquals(roots.analysis.path("analysisId"),fresh.path("analysisId"));
            assertNotEquals(fresh.path("analysisId"),opened.path("analysisId"));
            assertNotEquals(roots.comparison.path("compareResultId"),comparison.path("compareResultId"));
            assertCohort(fresh,state,37,37);
            assertCohort(opened,state,37,37);
            assertComparison(comparison,state,37);
            retained(roots,state);
            cohortFile(fresh,state,37);
            cohortFile(opened,state,37);
            comparisonFile(comparison,state);
            assertEquals(changed,sourceDigest());
            unchanged(roots);
            exited();
            System.out.println("P07b S"+state+" fresh/open n37 eval"+(state==4?36:37)+"; live query current; comparison S0; compare valid"+(state==4?5:6)+"/6 P="+P[state]+"; XLSX old37/7, fresh "+(state==4?"NEED_APPLY/7":"37/7"));
        }
    }
    @Test void completeUniverseChangesInvalidateAllRetainedConsumers() throws Exception {
        for(String change:new String[]{"add","delete","revoke"}) {
            population();
            Roots r=roots("now",filters(101,136,"now"));
            byte[] oldA=r.analysisBytes,oldK=r.comparisonBytes;
            if("add".equals(change)) {
                try(Connection writer=raw.getConnection();Statement st=writer.createStatement()){
                    writer.setAutoCommit(false);
                    assertEquals(1,st.executeUpdate("INSERT INTO patient_basic_info(id,name,gender,card_no) VALUES(138,'lifecycle-new-CANARY',1,'110101198610070011')"));
                    assertEquals(1,st.executeUpdate("INSERT INTO patient_relation_doctor(doctor_id,patient_id,research_type) VALUES(101,138,0)"));
                    assertEquals(1,st.executeUpdate("INSERT INTO patient_follow_up_history(id,patient_basic_info_id,research_type,follow_up_date,bqpg) VALUES(1380,138,0,'2026-10-07','{\"result\":{\"crpScore\":4.0}}')"));
                    writer.commit();
                }
                assertEquals(1,rawCount("SELECT COUNT(*) FROM patient_basic_info p JOIN patient_relation_doctor r ON r.patient_id=p.id JOIN patient_follow_up_history v ON v.patient_basic_info_id=p.id WHERE p.id=138 AND r.doctor_id=101 AND v.id=1380"));
            }
            else if("delete".equals(change))committed("DELETE FROM patient_basic_info WHERE id=137",1);
            else committed("DELETE FROM patient_relation_doctor WHERE doctor_id=101 AND patient_id=137",1);
            int total="add".equals(change)?38:36;
            String source=sourceDigest();
            long auditBefore=count("ra_ai_export_audit");
            int writes=context.getBean(ObservedWriter.class).completed;
            pageError(r.analysis,409,"SCOPE_CHANGED");
            readError(r.comparison.path("compareResultId").asText(),409,"SCOPE_CHANGED");
            fileError(xlsx(fileBody(r.analysis,false)),409,"SCOPE_CHANGED");
            fileError(xlsx(fileBody(r.comparison,true)),409,"SCOPE_CHANGED");
            assertEquals(auditBefore,count("ra_ai_export_audit"));
            assertEquals(writes,context.getBean(ObservedWriter.class).completed);
            assertArrayEquals(oldA,analysisPayload(r.analysis.path("analysisId").asText()));
            assertArrayEquals(oldK,payload(r.comparison.path("compareResultId").asText()));
            JsonNode fresh=success(json.writeValueAsString(AiCohortVo.object("filters",filters(101,136,"now")))),dynamic=open(r.definition),comparison=compareSuccess(compareBody(r.aDefinition,"now"));
            assertCohort(fresh,0,36,total);
            assertComparison(comparison,0,total);
            cohortFile(fresh,0,36);
            comparisonFile(comparison,0);
            assertEquals(total,dynamic.path("n").asInt());
            assertEquals(total,dynamic.path("studyTotal").asInt());
            assertEquals(total,dynamic.path("activity").path("evalN").asInt());
            List<JsonNode> rows=allRows(dynamic);
            List<String> expected=new ArrayList<>();
            if("add".equals(change))expected.add("138");
            expected.addAll(range(101,"add".equals(change)?137:136));
            assertEquals(expected,rowIds(rows));
            if("add".equals(change)){
                decimal(rows.get(0).path("das28At"),"4.0");
                assertEquals("1380",rows.get(0).path("selection").path("evalVisitId").asText());
            }
            else {
                assertEquals(1,rawCount("SELECT COUNT(*) FROM patient_follow_up_history WHERE patient_basic_info_id=137"));
                assertEquals("delete".equals(change)?0:1,rawCount("SELECT COUNT(*) FROM patient_basic_info WHERE id=137"));
            }
            fileWithRows(dynamic,rows);
            assertEquals(source,sourceDigest());
            unchanged(r);
            exited();
            System.out.println("P07b U "+change+"="+total+" retained page/compare/both-files SCOPE_CHANGED zero audit/writer; fresh C36/open"+total+"/A6/B6");
        }
    }
    List<String> rowIds(List<JsonNode> rows){
        List<String> out=new ArrayList<>();
        rows.forEach(row->out.add(row.path("patientId").asText()));
        return out;
    }
    void pageError(JsonNode analysis,int status,String code)throws Exception {
        routeError("/api/ra/ai/cohortPatients",json.writeValueAsBytes(AiCohortVo.object("analysisId",analysis.path("analysisId").asText(),"cursor",analysis.path("patients").path("nextCursor").asText())),status,code);
        exited();
    }
    void fileWithRows(JsonNode resource,List<JsonNode> rows)throws Exception {
        MvcResult response=xlsx(fileBody(resource,false));
        assertEquals(200,response.getResponse().getStatus(),response.getResponse().getContentAsString());
        byte[] bytes=response.getResponse().getContentAsByteArray();
        try(XSSFWorkbook book=new XSSFWorkbook(new ByteArrayInputStream(bytes))){
            Sheet sheet=book.getSheet("cohort");
            assertEquals(rows.size(),sheet.getLastRowNum());
            for(int i=0;i<rows.size();i++){
                int id=Integer.parseInt(rows.get(i).path("patientId").asText());
                numericCell(sheet.getRow(i+1).getCell(0),id==138?"4.0":BigDecimal.valueOf(138-id,1).toPlainString());
            }
            assertEquals(Integer.toString(rows.size()),metadata(book,"rowN"));
            noCanary(book);
        }
        audit(response,"READY","WRITTEN",rows.size(),bytes.length);
    }
    @Test void shanghaiMidnightChangesFreshSixMonthSelectionAndAgeOnly() throws Exception {
        population();
        committed("UPDATE patient_follow_up_history SET zlfa='"+medication("阿达木单抗","2026-04-08",null)+"'",37);
        committed("UPDATE patient_basic_info SET card_no='110101198610080011' WHERE id=111",1);
        committed("INSERT INTO patient_follow_up_history(id,patient_basic_info_id,research_type,follow_up_date,bqpg,fzjc) VALUES(1111,111,0,'2026-10-08','{\"result\":{\"crpScore\":4.80}}','{\"cfydb\":99}')",1);
        clock.now=Instant.parse("2026-10-07T15:59:59Z");
        Roots r=roots("6m",null);
        assertCohort(r.analysis,0,37,37);
        assertComparison(r.comparison,0,37);
        JsonNode oldPatient=find(allRows(r.analysis),111);
        assertEquals(39,oldPatient.path("clinical").path("age").asInt());
        assertEquals("2026-10-08",oldPatient.path("selection").path("target6mDate").asText());
        assertEquals("2026-10-07",oldPatient.path("selection").path("evalDate").asText());
        clock.now=Instant.parse("2026-10-07T16:00:01Z");
        String source=sourceDigest();
        JsonNode fresh=success("{\"filters\":{\"at\":\"6m\"}}"),opened=open(r.definition),comparison=compareSuccess(compareBody(r.aDefinition,"6m"));
        for(JsonNode resource:Arrays.asList(fresh,opened)){
            assertCohort(resource,1,37,37);
            JsonNode patient=find(allRows(resource),111);
            assertEquals(40,patient.path("clinical").path("age").asInt());
            assertEquals("2026-10-08",patient.path("selection").path("target6mDate").asText());
            assertEquals("2026-10-08",patient.path("selection").path("evalDate").asText());
            assertEquals("6m",resource.path("meta").path("at").asText());
            assertEquals("2026-10-08",resource.path("meta").path("asOfDate").asText());
            cohortFile(resource,1,37);
        }
        assertComparison(comparison,1,37);
        JsonNode coverage=comparison.path("meta").path("sourceDateCoverage").path("a").path("das28At");
        assertEquals("2026-10-07",coverage.path("minDate").asText());
        assertEquals("2026-10-08",coverage.path("maxDate").asText());
        assertEquals(6,coverage.path("datedN").asInt());
        comparisonFile(comparison,1);
        JsonNode oldPage=page(r.analysis,r.analysis.path("patients").path("nextCursor").asText());
        assertEquals(39,patientById(oldPage,111).path("clinical").path("age").asInt());
        assertEquals("2026-10-07",oldPage.path("meta").path("asOfDate").asText());
        retained(r,0);
        assertEquals(source,sourceDigest());
        unchanged(r);
        exited();
        System.out.println("P07b Shanghai midnight6m new visit1111/4.80/age40 vs old1110/2.70/age39; same target10/8 and TTL live");
    }
    @Test void monthStartRechecksSourceWindowBeforeLiveTtlExpires() throws Exception {
        population();
        committed("UPDATE patient_follow_up_history SET follow_up_date='2026-04-01'",37);
        clock.now=Instant.parse("2026-10-31T15:59:59Z");
        Roots r=roots("now",null);
        assertCohort(r.analysis,0,37,37);
        assertComparison(r.comparison,0,37);
        cohortFile(r.analysis,0,37);
        comparisonFile(r.comparison,0);
        clock.now=Instant.parse("2026-10-31T16:00:01Z");
        assertTrue(clock.now.isBefore(Instant.parse(r.analysis.path("meta").path("expiresAt").asText())));
        String source=sourceDigest();
        int written=context.getBean(ObservedWriter.class).completed;
        List<JsonNode> old=allRows(r.analysis);
        assertEquals(range(101,137),rowIds(old));
        decimal(find(old,111).path("das28At"),"2.70");
        sameRetainedComparison(r.comparison,readComparison(r.comparison.path("compareResultId").asText()));
        windowBlocked(r.analysis,false);
        windowBlocked(r.comparison,true);
        JsonNode fresh=success("{}"),opened=open(r.definition),comparison=compareSuccess(compareBody(r.aDefinition,"now"));
        for(JsonNode a:Arrays.asList(fresh,opened)){
            assertCohort(a,0,37,37);
            assertEquals("2026-11-01",a.path("meta").path("asOfDate").asText());
            assertEquals("2026-04-01",find(allRows(a),111).path("scoreProvenance").path("observedAt").asText());
            windowBlocked(a,false);
        }
        assertComparison(comparison,0,37);
        assertEquals("2026-11-01",comparison.path("meta").path("asOfDate").asText());
        windowBlocked(comparison,true);
        assertEquals(written,context.getBean(ObservedWriter.class).completed);
        unchanged(r);
        assertEquals(source,sourceDigest());
        exited();
        System.out.println("P07b month-start old page/compare200 with live TTL; old/new/open XLSX NEED_APPLY freeStart5/1 source4/1, zero writer");
    }
    void windowBlocked(JsonNode resource,boolean comparison)throws Exception {
        MvcResult response=xlsx(fileBody(resource,comparison));
        JsonNode d=fileError(response,409,"NEED_APPLY");
        assertEquals("2026-05-01",d.path("freeStart").asText());
        assertEquals("2026-11-01",d.path("today").asText());
        assertEquals(comparison?6:1,d.path("blocked").size());
        int index=0;
        for(String field:comparison?new String[]{"das28At","target","activity"}:new String[]{"das28At"})for(String side:comparison?new String[]{"A","B"}:new String[]{null}){
            JsonNode b=d.path("blocked").get(index++);
            assertEquals(field,b.path("field").asText());
            if(side==null)assertTrue(b.path("side").isNull());
            else assertEquals(side,b.path("side").asText());
            assertEquals("SOURCE_BEFORE_WINDOW",b.path("reason").asText());
            assertEquals(comparison?6:37,b.path("affectedN").asInt());
        }
        audit(response,"NEED_APPLY","NOT_STARTED",null,null);
    }
    @Test void exactExpiryRejectsOldReferencesBeforeNewRunsCleanThem() throws Exception {
        population();
        Roots r=roots("now",null);
        Instant expiry=Instant.parse(r.analysis.path("meta").path("expiresAt").asText());
        assertEquals(expiry,Instant.parse(r.comparison.path("meta").path("expiresAt").asText()));
        clock.now=expiry.minusMillis(1);
        String source=sourceDigest();
        retained(r,0);
        long auditBefore=count("ra_ai_export_audit");
        int writes=context.getBean(ObservedWriter.class).completed;
        clock.now=expiry;
        pageError(r.analysis,410,"ANALYSIS_EXPIRED");
        readError(r.comparison.path("compareResultId").asText(),410,"COMPARE_EXPIRED");
        fileError(xlsx(fileBody(r.analysis,false)),410,"ANALYSIS_EXPIRED");
        fileError(xlsx(fileBody(r.comparison,true)),410,"COMPARE_EXPIRED");
        assertEquals(auditBefore,count("ra_ai_export_audit"));
        assertEquals(writes,context.getBean(ObservedWriter.class).completed);
        unchanged(r);
        assertEquals(1,count("ra_ai_analysis_query_session"));
        assertEquals(1,count("ra_ai_compare_run"));
        JsonNode fresh=success("{}"),opened=open(r.definition),comparison=compareSuccess(compareBody(r.aDefinition,"now"));
        for(JsonNode resource:Arrays.asList(fresh,opened)){
            assertNotEquals(r.analysis.path("analysisId"),resource.path("analysisId"));
            assertCohort(resource,0,37,37);
            assertEquals(expiry.plusSeconds(900).toString(),resource.path("meta").path("expiresAt").asText());
            cohortFile(resource,0,37);
        }
        assertNotEquals(r.comparison.path("compareResultId"),comparison.path("compareResultId"));
        assertEquals(expiry.plusSeconds(900).toString(),comparison.path("meta").path("expiresAt").asText());
        assertComparison(comparison,0,37);
        comparisonFile(comparison,0);
        assertArrayEquals(r.definitionBytes,bytes("ra_private_cohort_definition","filters_utf8",r.definition));
        assertArrayEquals(r.aBytes,bytes("ra_private_cohort_definition","filters_utf8",r.aDefinition));
        assertEquals(2,count("ra_private_cohort_definition"));
        assertEquals(source,sourceDigest());
        exited();
        System.out.println("P07b expiry-1ms200; exact expiry page/compare/both-files410 zero audit/writer; then dynamic definitions survive/new ids+900s");
    }
    @Test void allFreshConsumersUseOneRepeatableReadAcrossCommittedSourceCut() throws Exception {
        for(String consumer:new String[]{"cohort","open","compare"}) {
            population();
            Roots r=roots("now",null);
            resetOwners();
            int[] cut={0,0,0};
            comparisonObserved.afterPatients=(owner,unused)->{
                cut[0]++;
                assertFalse(owner.getAutoCommit());
                assertEquals(Connection.TRANSACTION_REPEATABLE_READ,owner.getTransactionIsolation());
                int ownerId=queryCount(owner,"SELECT CONNECTION_ID()");
                try(Connection writer=raw.getConnection();Statement sql=writer.createStatement()) {
                    cut[1]=queryCount(writer,"SELECT CONNECTION_ID()");
                    assertNotEquals(ownerId,cut[1]);
                    writer.setAutoCommit(false);
                    assertEquals(1,sql.executeUpdate("UPDATE patient_basic_info SET gender=2 WHERE id=111"));
                    assertEquals(1,sql.executeUpdate("UPDATE patient_follow_up_history SET bqpg='{\"result\":{\"crpScore\":4.80}}' WHERE id=1110"));
                    writer.commit();
                    cut[2]=queryCount(writer,"SELECT gender FROM patient_basic_info WHERE id=111");
                }
                assertEquals(2,cut[2]);
                System.out.println("P07b after actual patients SELECT owner="+ownerId+" raw="+cut[1]+" committed gender2/DAS4.80 visit1110");
            }
            ;
            JsonNode first="compare".equals(consumer)?compareSuccess(compareBody(r.aDefinition,"now")):"open".equals(consumer)?open(r.definition):success("{}");
            assertEquals(1,cut[0]);
            assertEquals(2,cut[2]);
            sourceView();
            exited();
            if("compare".equals(consumer)){
                assertComparison(first,0,37);
                assertEquals(0,row(first,"female").path("a").path("numerator").asInt());
                assertEquals(6,row(first,"female").path("a").path("denominator").asInt());
            }
            else {
                assertEquals(37,first.path("n").asInt());assertEquals(range(101,110),ids(first));
                assertEquals(0,first.path("metricMeta").path("femaleRate").path("numerator").asInt());
                for(int i=0;i<4;i++)assertEquals(COUNTS[0][i],first.path("activity").path("current").get(i).path("count").asInt());
            }
            String changed=sourceDigest();
            JsonNode next="compare".equals(consumer)?compareSuccess(compareBody(r.aDefinition,"now")):"open".equals(consumer)?open(r.definition):success("{}");
            if("compare".equals(consumer)){
                assertComparison(next,1,37);
                assertEquals(1,row(next,"female").path("a").path("numerator").asInt());
                assertEquals(6,row(next,"female").path("a").path("denominator").asInt());
                comparisonFile(next,1);
            }
            else {
                assertCohort(next,1,37,37,"1110");
                assertEquals("F",find(allRows(next),111).path("clinical").path("sex").asText());
                cohortFile(next,1,37);
            }
            retained(r,1,"1110");
            assertEquals(changed,sourceDigest());
            unchanged(r);
            exited();
            System.out.println("P07b RR "+consumer+" current full old M/2.70, next full new F/4.80; old query live current; comparison fixed S0");
        }
    }
    void sourceView() {
        Set<Integer> identities=new HashSet<>();
        int n=0;
        for(int i=0;i<comparisonObserved.statements.size();i++){
            String sql=comparisonObserved.statements.get(i);
            if(sql.startsWith("SELECT")&&sql.contains("patient_")&&!sql.startsWith("SELECT p.id,p.name,p.study_no")){
                n++;
                identities.add(comparisonObserved.identities.get(i));
                assertFalse(comparisonObserved.autoCommits.get(i));
                assertEquals(Connection.TRANSACTION_REPEATABLE_READ,comparisonObserved.isolations.get(i).intValue());
            }
        }
        assertEquals(4,n);
        assertEquals(1,identities.size());
    }
    @Test void realClinicalSqlFailuresNeverFallbackAndScopeFailuresWithholdFiles() throws Exception {
        population();
        Roots r=roots("now",null);
        String source=sourceDigest();
        long analyses=count("ra_ai_analysis_query_session"),comparisons=count("ra_ai_compare_run");
        resetOwners();
        observed.failSecondSql=true;
        for(String consumer:new String[]{"cohort","open","compare"}){
            int failures=observed.mysqlFailures;
            if("cohort".equals(consumer))error("{}",503,"SERVICE_UNAVAILABLE");
            else if("open".equals(consumer))routeError("/api/ra/cohort/open",json.writeValueAsBytes(AiCohortVo.object("cohortId",r.definition)),503,"SERVICE_UNAVAILABLE");
            else compareError(compareBody(r.aDefinition,"now"),503,"SERVICE_UNAVAILABLE");
            assertTrue(observed.mysqlFailures>failures);
            assertEquals("42S02",observed.lastSqlState);
            assertEquals(1146,observed.lastMysqlError);
            assertEquals(analyses,count("ra_ai_analysis_query_session"));
            assertEquals(comparisons,count("ra_ai_compare_run"));
            unchanged(r);
            assertEquals(source,sourceDigest());
            exited();
        }
        pageError(r.analysis,503,"SERVICE_UNAVAILABLE");fileError(xlsx(fileBody(r.analysis,false)),503,"SERVICE_UNAVAILABLE");sameRetainedComparison(r.comparison,readComparison(r.comparison.path("compareResultId").asText()));
        observed.failSecondSql=false;
        int failures=comparisonObserved.mysqlFailures;
        long auditBefore=count("ra_ai_export_audit");
        int written=context.getBean(ObservedWriter.class).completed;
        comparisonObserved.failSqlContains="FROM patient_basic_info";
        pageError(r.analysis,503,"SERVICE_UNAVAILABLE");
        readError(r.comparison.path("compareResultId").asText(),503,"SERVICE_UNAVAILABLE");
        fileError(xlsx(fileBody(r.analysis,false)),503,"SERVICE_UNAVAILABLE");
        fileError(xlsx(fileBody(r.comparison,true)),503,"SERVICE_UNAVAILABLE");
        assertTrue(comparisonObserved.mysqlFailures>=failures+4);
        assertEquals("42S02",comparisonObserved.lastSqlState);
        assertEquals(1146,comparisonObserved.lastMysqlError);
        assertEquals(auditBefore,count("ra_ai_export_audit"));
        assertEquals(written,context.getBean(ObservedWriter.class).completed);
        unchanged(r);
        assertEquals(source,sourceDigest());
        exited();
        comparisonObserved.failSqlContains=null;
        retained(r,0);
        observed.failSecondSql=false;
        JsonNode fresh=success("{}"),opened=open(r.definition),comparison=compareSuccess(compareBody(r.aDefinition,"now"));
        assertCohort(fresh,0,37,37);
        assertCohort(opened,0,37,37);
        assertComparison(comparison,0,37);
        unchanged(r);
        assertEquals(source,sourceDigest());
        exited();
        System.out.println("P07b real MySQL42S02/1146 new three consumers503/no new run; clinical fault query page/files503; scope fault old four consumers503/no file/audit; recovered");
    }
    @Test void trustedDoctorAndAnonymousIdentityIsolateAllConsumerResources() throws Exception {
        population();
        committed("INSERT INTO patient_basic_info(id,name,gender,card_no) VALUES(201,'lifecycle-other-CANARY',1,'110101198610070011')",1);
        committed("INSERT INTO patient_relation_doctor(doctor_id,patient_id,research_type) VALUES(202,201,0)",1);
        committed("INSERT INTO patient_follow_up_history(id,patient_basic_info_id,research_type,follow_up_date,bqpg) VALUES(2010,201,0,'2026-10-07','{\"result\":{\"crpScore\":1.10}}')",1);
        Roots r=roots("now",null);
        String source=sourceDigest();
        long audits=count("ra_ai_export_audit");
        context.getBean(FakePrincipalProvider.class).doctor=202;
        JsonNode other=success("{\"filters\":{\"ids\":[\"111\",\"201\"]}}");
        assertEquals(1,other.path("n").asInt());
        assertEquals(1,other.path("studyTotal").asInt());
        assertEquals(Collections.singletonList("201"),ids(other));
        decimal(patientById(other,201).path("das28At"),"1.10");
        assertEquals("2010",patientById(other,201).path("selection").path("evalVisitId").asText());
        for(String definition:new String[]{r.definition,r.aDefinition})routeError("/api/ra/cohort/open",json.writeValueAsBytes(AiCohortVo.object("cohortId",definition)),404,"RESOURCE_NOT_FOUND");
        compareError(compareBody(r.aDefinition,"now"),404,"RESOURCE_NOT_FOUND");
        pageError(r.analysis,404,"RESOURCE_NOT_FOUND");
        readError(r.comparison.path("compareResultId").asText(),404,"RESOURCE_NOT_FOUND");
        fileError(xlsx(fileBody(r.analysis,false)),404,"RESOURCE_NOT_FOUND");
        fileError(xlsx(fileBody(r.comparison,true)),404,"RESOURCE_NOT_FOUND");
        assertEquals(audits,count("ra_ai_export_audit"));
        assertEquals(0,context.getBean(ObservedWriter.class).completed);
        unchanged(r);
        assertEquals(source,sourceDigest());
        exited();
        context.close();
        buildContext(false);
        resetOwners();
        String[] routes={"/api/ra/ai/cohort","/api/ra/ai/compare","/api/ra/cohort/open","/api/ra/cohort/open","/api/ra/ai/cohortPatients","/api/ra/ai/compareResult","/api/ra/export/xlsx","/api/ra/export/xlsx"};
        String[] bodies={"{}",compareBody(r.aDefinition,"now"),json.writeValueAsString(AiCohortVo.object("cohortId",r.definition)),json.writeValueAsString(AiCohortVo.object("cohortId",r.aDefinition)),json.writeValueAsString(AiCohortVo.object("analysisId",r.analysis.path("analysisId").asText(),"cursor",r.analysis.path("patients").path("nextCursor").asText())),json.writeValueAsString(AiCohortVo.object("compareResultId",r.comparison.path("compareResultId").asText())),fileBody(r.analysis,false),fileBody(r.comparison,true)};
        for(int i=0;i<routes.length;i++){
            if(routes[i].equals("/api/ra/export/xlsx"))fileError(xlsx(bodies[i]),401,"UNAUTHENTICATED");
            else routeError(routes[i],bodies[i].getBytes(StandardCharsets.UTF_8),401,"UNAUTHENTICATED");
            assertEquals(0,bodyRead.bytes);
            assertEquals(0,observed.borrowed);
            assertEquals(0,privateObserved.borrowed.get());
            assertEquals(0,comparisonObserved.borrowed.get());
            assertEquals(0,exportObserved.borrowed.get());
            exited();
        }
        assertEquals(0,context.getBean(ObservedWriter.class).completed);
        assertEquals(audits,count("ra_ai_export_audit"));
        unchanged(r);
        assertEquals(source,sourceDigest());
        exited();
        System.out.println("P07b trusted202 sees only201;101 resources404; anonymous eight HTTP consumers401/body0/SQL0/files0, stored facts unchanged");
    }
    @Test void changingConditionsEmptyIdsAndSimilarEntryKeepActualCurrentScope() throws Exception {
        population();
        Roots r=roots("now",null);
        String source=sourceDigest();
        JsonNode target=success("{\"filters\":{\"act\":\"target\"}}");
        assertEquals(27,target.path("n").asInt());
        assertEquals(37,target.path("studyTotal").asInt());
        assertEquals(range(111,137),rowIds(allRows(target)));
        assertEquals(27,target.path("activity").path("evalN").asInt());
        decimal(target.path("stats").path("targetRate"),"1");
        assertEquals(27.0/37,target.path("stats").path("cohortRate").asDouble(),1e-12);
        for(int i=0;i<4;i++)assertEquals(new int[]{22,5,0,0}[i],target.path("activity").path("current").get(i).path("count").asInt());
        JsonNode cleared=success("{\"filters\":{}}");
        assertCohort(cleared,0,37,37);
        cohortFile(cleared,0,37);
        JsonNode empty=success("{\"filters\":{\"ids\":[]}}");
        emptyCohort(empty,37);
        String definition=saveDefinition("lifecycle-empty",json.readTree("{\"ids\":[]}")).path("cohortId").asText();
        byte[] bytes=bytes("ra_private_cohort_definition","filters_utf8",definition);
        JsonNode opened=open(definition);
        emptyCohort(opened,37);
        assertNotEquals(empty.path("analysisId"),opened.path("analysisId"));
        for(JsonNode a:Arrays.asList(empty,opened)){
            MvcResult response=xlsx(json.writeValueAsString(AiCohortVo.object("source","COHORT","analysisId",a.path("analysisId").asText())));
            assertEquals(200,response.getResponse().getStatus());
            try(XSSFWorkbook book=new XSSFWorkbook(new ByteArrayInputStream(response.getResponse().getContentAsByteArray()))){
                assertEquals(0,book.getSheet("cohort").getLastRowNum());
                assertEquals(15,book.getSheet("cohort").getRow(0).getLastCellNum());
                assertEquals("EMPTY",metadata(book,"status"));
                assertEquals("0",metadata(book,"rowN"));
                noCanary(book);
            }
            audit(response,"READY","WRITTEN",0,response.getResponse().getContentAsByteArray().length);
        }
        JsonNode comparison=compareSuccess(json.writeValueAsString(AiCohortVo.object("a",AiCohortVo.object("source","SAVED","cohortId",definition),"b",AiCohortVo.object("source","FILTERS","filters",json.readTree("{\"ids\":[]}")))));
        assertEquals(37,comparison.path("studyTotal").asInt());
        assertEquals(0,comparison.path("a").path("n").asInt());
        assertEquals(0,comparison.path("b").path("n").asInt());
        for(JsonNode row:comparison.path("rows")){
            if(!"n".equals(row.path("metric").asText())){
                assertTrue(row.path("test").path("pValue").isNull());
                assertTrue(row.path("test").path("significant").isNull());
                assertEquals("INSUFFICIENT_SAMPLE",row.path("test").path("status").asText());
            }
            if("CONTINUOUS".equals(row.path("kind").asText()))for(String side:new String[]{"a","b"}){
                assertEquals("NO_DATA",row.path(side).path("status").asText());
                for(String key:new String[]{"q1","median","q3","iqr"})assertTrue(row.path(side).path(key).isNull());
            }
        }
        MvcResult exported=xlsx(json.writeValueAsString(AiCohortVo.object("source","COMPARE","compareResultId",comparison.path("compareResultId").asText())));
        assertEquals(200,exported.getResponse().getStatus(),exported.getResponse().getContentAsString());
        try(XSSFWorkbook book=new XSSFWorkbook(new ByteArrayInputStream(exported.getResponse().getContentAsByteArray()))){
            Sheet sheet=book.getSheet("comparison");
            assertEquals(17,sheet.getLastRowNum());
            Map<String,Integer> cols=columns(sheet);
            for(int i=1;i<=17;i++)assertEquals(CellType.BLANK,sheet.getRow(i).getCell(cols.get("pValue")).getCellTypeEnum());
            assertEquals("0",metadata(book,"aN"));
            assertEquals("0",metadata(book,"bN"));
            assertEquals("17",metadata(book,"rowN"));
            noCanary(book);
        }
        audit(exported,"READY","WRITTEN",17,exported.getResponse().getContentAsByteArray().length);
        JsonNode entry=similar();
        assertEquals("M",entry.path("filters").path("sex").asText());
        assertEquals("35-45",entry.path("filters").path("age").asText());
        assertTrue(entry.path("filters").path("sero").isNull());
        JsonNode applied=success(json.writeValueAsString(AiCohortVo.object("filters",entry.path("filters"))));
        assertCohort(applied,0,37,37);
        cohortFile(applied,0,37);
        assertEquals(source,sourceDigest());
        unchanged(r);
        assertArrayEquals(bytes,bytes("ra_private_cohort_definition","filters_utf8",definition));
        committed("UPDATE patient_basic_info SET gender=2,card_no='110101199610070011' WHERE id=101",1);
        String changed=sourceDigest();
        JsonNode current=similar();
        assertEquals("F",current.path("filters").path("sex").asText());
        assertEquals("25-35",current.path("filters").path("age").asText());
        JsonNode single=success(json.writeValueAsString(AiCohortVo.object("filters",current.path("filters"))));
        assertEquals(1,single.path("n").asInt());
        assertEquals(37,single.path("studyTotal").asInt());
        assertEquals(Collections.singletonList("101"),ids(single));
        decimal(patientById(single,101).path("das28At"),"3.7");
        assertEquals(changed,sourceDigest());
        committed("DELETE FROM patient_relation_doctor WHERE doctor_id=101 AND patient_id=101",2);
        String revoked=sourceDigest();
        JsonNode noPermission=success(json.writeValueAsString(AiCohortVo.object("filters",current.path("filters"))));
        emptyCohort(noPermission,36);
        assertEquals(revoked,sourceDigest());
        unchanged(r);
        assertArrayEquals(bytes,bytes("ra_private_cohort_definition","filters_utf8",definition));
        exited();
        System.out.println("P07b target27/clear37/ids[]0; empty saved/open, XLSX0/17 with blank P; SIMILAR M35-45=>37, current F25-35=>101, revoked candidate=>0/U36");
    }
    JsonNode similar()throws Exception{
        return routeSuccess("/api/ra/ai/resolveEntry","{\"source\":\"SIMILAR\",\"indexPatientId\":\"101\"}");
    }
    void emptyCohort(JsonNode a,int total) {
        assertEquals(0,a.path("n").asInt());
        assertEquals(total,a.path("studyTotal").asInt());
        assertEquals(0,a.path("patients").path("items").size());
        assertFalse(a.path("patients").path("hasMore").asBoolean());
        assertTrue(a.path("patients").path("nextCursor").isNull());
        assertEquals(0,a.path("activity").path("evalN").asInt());
        assertEquals(0,a.path("activity").path("unknownN").asInt());
        for(JsonNode bucket:a.path("activity").path("current")){
            assertEquals(0,bucket.path("count").asInt());
            decimal(bucket.path("value"),"0");
            assertEquals("NO_DATA",bucket.path("status").asText());
        }
        for(String key:new String[]{"cohortRate","femaleRate","seroRate","targetRate","completeRate"}){
            decimal(a.path("stats").path(key),"0");
            assertEquals("NO_DATA",a.path("metricMeta").path(key).path("status").asText());
        }
        assertTrue(a.path("stats").path("ageMedian").isNull());
        assertTrue(a.path("stats").path("durationMedian").isNull());
        assertEquals(0,a.path("fm").path("rows").size());
    }
    void population()throws Exception {
        exportPopulation37();
        committed("INSERT INTO patient_relation_doctor(doctor_id,patient_id,research_type) VALUES(101,101,1)",1);
        committed("UPDATE patient_relation_doctor SET miss=1 WHERE doctor_id=101 AND patient_id=111",1);
        resetOwners();
    }
    JsonNode filters(int first,int last,String at) {
        ArrayNode ids=json.createArrayNode();
        for(int i=first;i<=last;i++)ids.add(Integer.toString(i));
        return json.valueToTree(AiCohortVo.object("ids",ids,"at",at));
    }
    String compareBody(String saved,String at)throws Exception {
        return json.writeValueAsString(AiCohortVo.object("a",saved==null?AiCohortVo.object("source","FILTERS","filters",filters(109,114,at)):AiCohortVo.object("source","SAVED","cohortId",saved),"b",AiCohortVo.object("source","FILTERS","filters",filters(121,126,at))));
    }
    final class Roots {
        JsonNode analysis,comparison;
        String definition,aDefinition;
        byte[] analysisBytes,comparisonBytes,definitionBytes,aBytes;
    }
    Roots roots(String at,JsonNode selected)throws Exception {
        Roots r=new Roots();
        JsonNode all=json.valueToTree(AiCohortVo.object("at",at));
        r.analysis=success(json.writeValueAsString(AiCohortVo.object("filters",selected==null?all:selected)));
        r.definition=saveDefinition("lifecycle-all-"+UUID.randomUUID().toString().substring(0,8),all).path("cohortId").asText();
        r.aDefinition=saveDefinition("lifecycle-a-"+UUID.randomUUID().toString().substring(0,8),filters(109,114,at)).path("cohortId").asText();
        r.comparison=compareSuccess(compareBody(r.aDefinition,at));
        r.analysisBytes=analysisPayload(r.analysis.path("analysisId").asText());
        r.comparisonBytes=payload(r.comparison.path("compareResultId").asText());
        r.definitionBytes=bytes("ra_private_cohort_definition","filters_utf8",r.definition);
        r.aBytes=bytes("ra_private_cohort_definition","filters_utf8",r.aDefinition);
        return r;
    }
    JsonNode open(String id)throws Exception{
        return routeSuccess("/api/ra/cohort/open",json.writeValueAsString(AiCohortVo.object("cohortId",id))).path("analysis");
    }
    JsonNode page(JsonNode analysis,String cursor)throws Exception{
        return routeSuccess("/api/ra/ai/cohortPatients",json.writeValueAsString(AiCohortVo.object("analysisId",analysis.path("analysisId").asText(),"cursor",cursor)));
    }
    List<JsonNode> allRows(JsonNode analysis)throws Exception {
        List<JsonNode> out=new ArrayList<>();
        analysis.path("patients").path("items").forEach(out::add);
        JsonNode current=analysis;
        while(current.path("patients").path("hasMore").asBoolean()){
            current=page(analysis,current.path("patients").path("nextCursor").asText());
            int expected=Math.min(20,analysis.path("n").asInt()-out.size());
            assertEquals(expected,current.path("patients").path("returnedCount").asInt());
            current.path("patients").path("items").forEach(out::add);
        }
        assertTrue(current.path("patients").path("nextCursor").isNull());
        assertEquals(analysis.path("n").asInt(),out.size());
        Set<String> unique=new HashSet<>();
        for(JsonNode row:out)assertTrue(unique.add(row.path("patientId").asText()));
        return out;
    }
    List<String> order(int state,int last) {
        List<String> out=new ArrayList<>();
        if(state==1)out.add("111");
        for(int i=101;i<=last;i++)if(i!=111||state==0||state==3)out.add(Integer.toString(i));
        if(state==2||state==4)out.add("111");
        return out;
    }
    JsonNode find(List<JsonNode> rows,int id){
        for(JsonNode row:rows)if(Integer.toString(id).equals(row.path("patientId").asText()))return row;
        throw new AssertionError("缺患者"+id);
    }
    void assertCohort(JsonNode analysis,int state,int n,int total)throws Exception {
        assertCohort(analysis,state,n,total,state==4?null:state==1||state==2?"1111":"1110");
    }
    void assertCohort(JsonNode analysis,int state,int n,int total,String selectedVisit)throws Exception {
        assertEquals(n,analysis.path("n").asInt());
        assertEquals(total,analysis.path("studyTotal").asInt());
        assertEquals(state==4?n-1:n,analysis.path("activity").path("evalN").asInt());
        assertEquals(state==4?1:0,analysis.path("activity").path("unknownN").asInt());
        for(int i=0;i<4;i++){
            JsonNode bucket=analysis.path("activity").path("current").get(i);
            assertEquals(LEVELS[i],bucket.path("level").asText());
            assertEquals(COUNTS[state][i]-(n==36&&i==0?1:0),bucket.path("count").asInt());
        }
        int target=(state==1||state==4?26:27)-(n==36?1:0);
        assertEquals(target,analysis.path("metricMeta").path("targetRate").path("numerator").asInt());
        assertEquals(state==4?n-1:n,analysis.path("metricMeta").path("targetRate").path("denominator").asInt());
        assertEquals((double)target/(state==4?n-1:n),analysis.path("stats").path("targetRate").asDouble(),1e-12);
        List<JsonNode> rows=allRows(analysis);
        List<String> actual=new ArrayList<>();
        rows.forEach(x->actual.add(x.path("patientId").asText()));
        assertEquals(order(state,n==36?136:137),actual);
        JsonNode patient=find(rows,111);
        decimal(patient.path("das28At"),SCORE[state]);
        assertEquals(selectedVisit,patient.path("selection").path("evalVisitId").isNull()?null:patient.path("selection").path("evalVisitId").asText());
        if(state==1||state==2)decimal(patient.path("crpAt").path("value"),"1111".equals(selectedVisit)?"99":"20");
    }
    void assertComparison(JsonNode comparison,int state,int total) {
        assertEquals(total,comparison.path("studyTotal").asInt());
        assertEquals(6,comparison.path("a").path("n").asInt());
        assertEquals(6,comparison.path("b").path("n").asInt());
        assertEquals(state==4?5:6,comparison.path("a").path("evalN").asInt());
        assertEquals(6,comparison.path("b").path("evalN").asInt());
        assertEquals(0,comparison.path("overlapN").asInt());
        JsonNode das=row(comparison,"das28At");
        for(String side:new String[]{"a","b"}){
            JsonNode value=das.path(side);
            assertEquals("a".equals(side)&&state==4?5:6,value.path("validN").asInt());
            assertEquals("a".equals(side)&&state==4?1:0,value.path("unknownN").asInt());
            String[] q="a".equals(side)?QUARTILES[state]:new String[]{"1.325","1.45","1.575",".25"};
            String[] keys={"q1","median","q3","iqr"};
            for(int i=0;i<4;i++)decimal(value.path(keys[i]),q[i]);
        }
        decimal(das.path("difference"),DIFFERENCE[state]);
        assertEquals("MANN_WHITNEY_EXACT",das.path("test").path("method").asText());
        assertEquals("OK",das.path("test").path("status").asText());
        assertEquals(P[state],das.path("test").path("pValue").asDouble(),1e-12);
        assertEquals(DISPLAY_P[state],das.path("test").path("displayP").asText());
        assertEquals(state!=2,das.path("test").path("significant").asBoolean());
        assertEquals(state==4?5:6,das.path("test").path("nA").asInt());
        assertEquals(6,das.path("test").path("nB").asInt());
        JsonNode target=row(comparison,"target");
        for(String side:new String[]{"a","b"}){
            boolean a="a".equals(side);
            JsonNode t=target.path(side);
            assertEquals(a?TARGET[state]:6,t.path("numerator").asInt());
            assertEquals(a&&state==4?5:6,t.path("denominator").asInt());
            assertEquals(a&&state==4?1:0,t.path("unknownN").asInt());
        }
        JsonNode activity=row(comparison,"activity");
        for(int i=0;i<4;i++)for(String side:new String[]{"a","b"}){
            JsonNode b=activity.path(side).path("groups").get(i);
            assertEquals(LEVELS[i],b.path("category").asText());
            assertEquals("a".equals(side)?A_COUNTS[state][i]:i==0?6:0,b.path("count").asInt());
        }
        assertEquals(state==4?5:6,activity.path("a").path("denominator").asInt());
        assertEquals(state==4?1:0,activity.path("a").path("unknownN").asInt());
    }
    void retained(Roots r,int state)throws Exception {retained(r,state,state==4?null:state==1||state==2?"1111":"1110");}
    void retained(Roots r,int state,String selectedVisit)throws Exception {
        JsonNode middle=page(r.analysis,r.analysis.path("patients").path("nextCursor").asText());List<String> expected=order(state,137);assertEquals(expected.subList(10,30),ids(middle));JsonNode last=page(r.analysis,middle.path("patients").path("nextCursor").asText());assertEquals(expected.subList(30,37),ids(last));
        List<JsonNode> visible=new ArrayList<>(middleRows(middle));visible.addAll(middleRows(last));if(expected.indexOf("111")>=10){JsonNode patient=find(visible,111);decimal(patient.path("das28At"),SCORE[state]);assertEquals(selectedVisit,patient.path("selection").path("evalVisitId").isNull()?null:patient.path("selection").path("evalVisitId").asText());}
        for(String key:new String[]{"at","asOfDate","policyVersions","expiresAt"})assertEquals(r.analysis.path("meta").path(key),middle.path("meta").path(key),key);
        assertTrue(middle.path("meta").has("displayReadCompletedAt"));sameRetainedComparison(r.comparison,readComparison(r.comparison.path("compareResultId").asText()));
        JsonNode current=json.valueToTree(context.getBean(com.wenwen.ai.result.AnalysisResultStore.class).export(101,r.analysis.path("analysisId").asText(),clock.instant(),1000).result);
        cohortFile(current,state,37);comparisonFile(r.comparison,0);unchanged(r);
    }
    List<JsonNode> middleRows(JsonNode page){
        List<JsonNode> out=new ArrayList<>();
        page.path("patients").path("items").forEach(out::add);
        return out;
    }
    List<String> range(int first,int last){
        List<String> out=new ArrayList<>();
        for(int i=first;i<=last;i++)out.add(Integer.toString(i));
        return out;
    }
    void unchanged(Roots r)throws Exception {
        assertArrayEquals(r.analysisBytes,analysisPayload(r.analysis.path("analysisId").asText()));
        assertArrayEquals(r.comparisonBytes,payload(r.comparison.path("compareResultId").asText()));
        assertArrayEquals(r.definitionBytes,bytes("ra_private_cohort_definition","filters_utf8",r.definition));
        assertArrayEquals(r.aBytes,bytes("ra_private_cohort_definition","filters_utf8",r.aDefinition));
    }
    byte[] analysisPayload(String id)throws Exception{
        return bytes("ra_ai_analysis_query_session","query_json",id);
    }
    byte[] bytes(String table,String field,String id)throws Exception{
        try(Connection c=raw.getConnection();PreparedStatement q=c.prepareStatement("SELECT "+field+" FROM "+table+" WHERE id=?")){
            q.setString(1,id);
            try(ResultSet rs=q.executeQuery()){
                assertTrue(rs.next());
                byte[] out=rs.getBytes(1);
                assertFalse(rs.next());
                return out;
            }
        }
    }
    void committed(String statement,int affected)throws Exception {
        try(Connection c=raw.getConnection();Statement s=c.createStatement()){
            c.setAutoCommit(false);
            assertEquals(affected,s.executeUpdate(statement));
            c.commit();
            assertFalse(c.getAutoCommit());
        }
        System.out.println("P07b raw committed affected="+affected+" "+statement);
    }
    static void decimal(JsonNode actual,String expected){
        if(expected==null)assertTrue(actual.isNull());
        else{
            assertTrue(actual.isNumber(),actual.toString());
            assertEquals(0,new BigDecimal(expected).compareTo(actual.decimalValue()),actual.toString());
        }
    }
    String fileBody(JsonNode resource,boolean comparison)throws Exception{
        return json.writeValueAsString(comparison?AiCohortVo.object("source","COMPARE","compareResultId",resource.path("compareResultId").asText(),"metrics",Arrays.asList("n","das28At","target","activity")):AiCohortVo.object("source","COHORT","analysisId",resource.path("analysisId").asText(),"fields",Collections.singletonList("das28At")));
    }
    void cohortFile(JsonNode resource,int state,int n)throws Exception {
        if(state==4){
            int before=context.getBean(ObservedWriter.class).completed;
            MvcResult r=xlsx(fileBody(resource,false));
            assertEquals(before,context.getBean(ObservedWriter.class).completed);
            JsonNode d=fileError(r,409,"NEED_APPLY");
            assertEquals(1,d.path("blocked").size());
            JsonNode b=d.path("blocked").get(0);
            assertEquals("das28At",b.path("field").asText());
            assertTrue(b.path("side").isNull());
            assertEquals("MISSING_SOURCE_DATE",b.path("reason").asText());
            assertEquals(1,b.path("affectedN").asInt());
            audit(r,"NEED_APPLY","NOT_STARTED",null,null);
            return;
        }
        MvcResult r=xlsx(fileBody(resource,false));
        assertEquals(200,r.getResponse().getStatus(),r.getResponse().getContentAsString());
        byte[] bytes=r.getResponse().getContentAsByteArray();
        try(XSSFWorkbook book=new XSSFWorkbook(new ByteArrayInputStream(bytes))){
            Sheet sheet=book.getSheet("cohort");
            assertEquals(n,sheet.getLastRowNum());
            assertEquals(1,sheet.getRow(0).getLastCellNum());
            assertEquals("评估 DAS28（"+resource.path("meta").path("at").asText()+"）",sheet.getRow(0).getCell(0).getStringCellValue());
            List<String> ids=order(state,n==36?136:137);
            for(int i=0;i<n;i++){
                int id=Integer.parseInt(ids.get(i));
                numericCell(sheet.getRow(i+1).getCell(0),id==111?SCORE[state]:BigDecimal.valueOf(138-id,1).toPlainString());
            }
            assertEquals(Integer.toString(n),metadata(book,"rowN"));
            assertEquals(resource.path("analysisId").asText(),metadata(book,"referenceId"));
            noCanary(book);
        }
        audit(r,"READY","WRITTEN",n,bytes.length);
    }
    void comparisonFile(JsonNode resource,int state)throws Exception {
        MvcResult r=xlsx(fileBody(resource,true));
        assertEquals(200,r.getResponse().getStatus(),r.getResponse().getContentAsString());
        byte[] bytes=r.getResponse().getContentAsByteArray();
        try(XSSFWorkbook book=new XSSFWorkbook(new ByteArrayInputStream(bytes))){
            Sheet sheet=book.getSheet("comparison");
            assertEquals(7,sheet.getLastRowNum());
            Map<String,Integer> columns=columns(sheet);
            numeric(sheet.getRow(1),columns,"aN","6");
            numeric(sheet.getRow(1),columns,"bN","6");
            Row das=sheet.getRow(2);
            assertEquals("das28At",das.getCell(0).getStringCellValue());
            String[] keys={"Q1","Median","Q3","Iqr"};
            for(int i=0;i<4;i++){
                numeric(das,columns,"a"+keys[i],QUARTILES[state][i]);
                numeric(das,columns,"b"+keys[i],new String[]{"1.325","1.45","1.575",".25"}[i]);
            }
            numeric(das,columns,"aValidN",state==4?"5":"6");
            numeric(das,columns,"aUnknownN",state==4?"1":"0");
            numeric(das,columns,"difference",DIFFERENCE[state]);
            assertEquals(P[state],das.getCell(columns.get("pValue")).getNumericCellValue(),1e-12);
            assertEquals(DISPLAY_P[state],das.getCell(columns.get("displayP")).getStringCellValue());
            Row target=sheet.getRow(3);
            numeric(target,columns,"aNumerator",Integer.toString(TARGET[state]));
            numeric(target,columns,"aDenominator",state==4?"5":"6");
            numeric(target,columns,"aUnknownN",state==4?"1":"0");
            numeric(target,columns,"bNumerator","6");
            for(int i=0;i<4;i++){
                Row activity=sheet.getRow(i+4);
                assertEquals(LEVELS[i],activity.getCell(columns.get("category")).getStringCellValue());
                numeric(activity,columns,"aCount",Integer.toString(A_COUNTS[state][i]));
                numeric(activity,columns,"bCount",i==0?"6":"0");
                numeric(activity,columns,"aDenominator",state==4?"5":"6");
                if(i>0)assertEquals(CellType.BLANK,activity.getCell(columns.get("pValue")).getCellTypeEnum());
            }
            assertEquals("7",metadata(book,"rowN"));
            assertEquals("6",metadata(book,"aN"));
            assertEquals("6",metadata(book,"bN"));
            assertEquals(resource.path("compareResultId").asText(),metadata(book,"referenceId"));
            noCanary(book);
        }
        audit(r,"READY","WRITTEN",7,bytes.length);
    }
    JsonNode fileError(MvcResult r,int status,String code)throws Exception {
        assertEquals(status,r.getResponse().getStatus(),r.getResponse().getContentAsString());
        assertNull(r.getResponse().getHeader("Content-Disposition"));
        assertFalse(r.getResponse().getContentType().contains("spreadsheet"));
        JsonNode e=json.readTree(r.getResponse().getContentAsByteArray());
        assertFalse(e.path("success").asBoolean());
        assertEquals(code,e.path("code").asText());
        if(!"NEED_APPLY".equals(code))assertTrue(e.path("data").isNull());
        exited();
        return e.path("data");
    }
    void audit(MvcResult r,String generation,String transfer,Integer rows,Integer bytes)throws Exception {
        try(Connection c=raw.getConnection();PreparedStatement q=c.prepareStatement("SELECT generation_status,transfer_status,file_rows,file_bytes,reference_id FROM ra_ai_export_audit WHERE id=?")){
            q.setString(1,r.getResponse().getHeader("X-Trace-Id"));
            try(ResultSet a=q.executeQuery()){
                assertTrue(a.next());
                assertEquals(generation,a.getString(1));
                assertEquals(transfer,a.getString(2));
                assertEquals(rows,a.getObject(3));
                assertEquals(bytes,a.getObject(4));
                JsonNode request=json.readTree(r.getRequest().getContentAsByteArray());
                assertEquals(request.path(request.path("source").asText().equals("COHORT")?"analysisId":"compareResultId").asText(),a.getString(5));
                assertFalse(a.next());
            }
        }
        exited();
    }
    static Map<String,Integer> columns(Sheet sheet){
        Map<String,Integer> out=new LinkedHashMap<>();
        for(Cell c:sheet.getRow(0))out.put(c.getStringCellValue(),c.getColumnIndex());
        return out;
    }
    static void numeric(Row row,Map<String,Integer> columns,String name,String expected){
        numericCell(row.getCell(columns.get(name)),expected);
    }
    static void numericCell(Cell cell,String expected){
        assertEquals(CellType.NUMERIC,cell.getCellTypeEnum());
        assertEquals(0,new BigDecimal(expected).compareTo(new BigDecimal(((org.apache.poi.xssf.usermodel.XSSFCell)cell).getRawValue())));
    }
    static String metadata(XSSFWorkbook book,String key){
        for(Row row:book.getSheet("metadata"))if(key.equals(row.getCell(0).getStringCellValue())){
            Cell c=row.getCell(1);
            return c.getCellTypeEnum()==CellType.NUMERIC?((org.apache.poi.xssf.usermodel.XSSFCell)c).getRawValue():c.getStringCellValue();
        }
        throw new AssertionError("缺metadata "+key);
    }
    static void noCanary(XSSFWorkbook book){
        for(Sheet sheet:book)for(Row row:sheet)for(Cell cell:row)if(cell.getCellTypeEnum()==CellType.STRING){
            String s=cell.getStringCellValue();
            for(String forbidden:new String[]{"CANARY","110101198610070011","lifecycle-current-name","lifecycle-current-study"})assertFalse(s.contains(forbidden),forbidden);
        }
    }
}
