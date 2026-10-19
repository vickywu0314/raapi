package com.wenwen.ai.cohort;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
/** 启动准入的真实HTTP/合成MySQL观察；不启动生产应用。 */
class FeatureAdmissionHttpTest extends ExportApplicationHttpFixture {
    static final String GLOBAL="ra.ai.api.enabled", FILE="ra.export.xlsx.enabled";
    void rebuild(boolean trusted){
        context.close();
        buildContext(trusted);
        resetOwners();
    }
    JsonNode data(String route,String body)throws Exception {
        MvcResult r=http.perform(post(route).contentType("application/json").content(body)).andReturn();
        assertEquals(200,r.getResponse().getStatus(),r.getResponse().getContentAsString());
        JsonNode e=json.readTree(r.getResponse().getContentAsByteArray());
        assertTrue(e.path("success").asBoolean());
        assertEquals("200",e.path("code").asText());
        assertNotNull(r.getResponse().getHeader("X-Trace-Id"));
        if(e.path("data").has("meta"))assertEquals(r.getResponse().getHeader("X-Trace-Id"),e.path("data").path("meta").path("traceId").asText());
        exited();
        return e.path("data");
    }
    @Test void globalClosureRejectsDeleteWithoutChangingDefinition()throws Exception {
        String id=data("/api/ra/cohort/save","{\"name\":\"Admission\",\"filters\":{},\"requestId\":\"00000000-0000-4000-8000-000000000001\"}").path("cohortId").asText();
        String before=digest("ra_private_cohort_definition"), receipts=rows("SELECT * FROM ra_private_cohort_command ORDER BY owner_doctor_id,request_id");
        analysisProperties.put(GLOBAL,"false");
        rebuild(true);
        MvcResult result=http.perform(post("/api/ra/cohort/delete").contentType("application/json").content("{\"cohortId\":\""+id+"\",\"version\":1,\"requestId\":\"00000000-0000-4000-8000-000000000002\"}")).andReturn();
        String after=digest("ra_private_cohort_definition"), afterReceipts=rows("SELECT * FROM ra_private_cohort_command ORDER BY owner_doctor_id,request_id");
        System.out.println("P07c delete observed HTTP="+result.getResponse().getStatus()+" bodyBytes="+bodyRead.bytes+" definitionUnchanged="+before.equals(after)+" receiptsUnchanged="+receipts.equals(afterReceipts));
        assertEquals(503,result.getResponse().getStatus(),result.getResponse().getContentAsString());
        unavailable(result,"队列功能暂不可用");
        zeroOwners();
        assertEquals(before,after);
        assertEquals(receipts,afterReceipts);
        analysisProperties.put(GLOBAL,"true");
        rebuild(true);
        assertEquals(id,data("/api/ra/cohort/open","{\"cohortId\":\""+id+"\"}").path("cohort").path("cohortId").asText());
        exited();
    }
    @Test void globalClosureRejectsAnalysisEntrypointsBeforeClockOrBody()throws Exception {
        analysisProperties.put(GLOBAL,"false");
        rebuild(true);
        clock.onInstant=()->{
            throw new AssertionError("禁用请求不得读Clock");
        }
        ;
        for(String route:new String[]{"/api/ra/ai/cohort","/api/ra/ai/cohortPatients","/api/ra/ai/resolveEntry"})deny(route,"{","队列功能暂不可用");
    }
    @Test void globalClosureRejectsComparisonEntrypointsBeforeBody()throws Exception {
        analysisProperties.put(GLOBAL,"false");
        rebuild(true);
        for(String route:new String[]{"/api/ra/ai/compare","/api/ra/ai/compareResult"})deny(route,"{","队列功能暂不可用");
    }
    @Test void globalClosureRejectsApplicationEntrypointsBeforeBody()throws Exception {
        analysisProperties.put(GLOBAL,"false");
        rebuild(true);
        for(String route:new String[]{"/api/ra/export/applyAnalysis","/api/ra/export/retryNotification"})deny(route,"{","队列功能暂不可用");
    }
    @Test void fileClosureRejectsUsableReferenceWithoutGeneratingXlsx()throws Exception {
        exportPopulation37();
        String id=success("{}").path("analysisId").asText();
        String body="{\"source\":\"COHORT\",\"analysisId\":\""+id+"\",\"fields\":[\"das28Current\"]}";
        analysisProperties.put(GLOBAL,"true");
        analysisProperties.put(FILE,"false");
        rebuild(true);
        MvcResult r=xlsx(body);
        int actualRows=-1;
        if(r.getResponse().getStatus()==200)try(org.apache.poi.xssf.usermodel.XSSFWorkbook book=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(r.getResponse().getContentAsByteArray()))){
            actualRows=book.getSheet("cohort").getLastRowNum();
        }
        System.out.println("P07c file observed HTTP="+r.getResponse().getStatus()+" readableRows="+actualRows+" writer="+context.getBean(ObservedWriter.class).completed);
        assertEquals(503,r.getResponse().getStatus());
        unavailable(r,"文件导出暂不可用");
        zeroOwners();
        assertEquals(0,rawCount("SELECT COUNT(*) FROM ra_ai_export_audit"));
    }
    @Test void missingAndExplicitTrueKeepAllNonParseConsumersCompatible()throws Exception {
        exportPopulation37();
        exerciseEnabledConsumers(10);
        analysisProperties.put(GLOBAL,"true");
        analysisProperties.put(FILE,"true");
        rebuild(true);
        exerciseEnabledConsumers(20);
    }
    void exerciseEnabledConsumers(int sequence)throws Exception {
        exerciseEnabledConsumers(sequence,true);
    }
    void exerciseEnabledConsumers(int sequence,boolean fileAllowed)throws Exception {
        JsonNode run=success("{}");
        assertEquals(37,run.path("n").asInt());
        assertEquals(37,run.path("studyTotal").asInt());
        assertEquals(10,ids(run).size());
        JsonNode page=data("/api/ra/ai/cohortPatients",pageBody(run));
        assertEquals(20,ids(page).size());
        assertEquals("111",ids(page).get(0));
        assertEquals("130",ids(page).get(19));
        assertTrue(data("/api/ra/ai/resolveEntry","{\"source\":\"SIMILAR\",\"indexPatientId\":\"101\"}").path("canApply").asBoolean());
        String id=saveDefinition("Enabled"+sequence,"{}",sequence).path("cohortId").asText();
        assertEquals(37,data("/api/ra/cohort/open",idBody(id)).path("analysis").path("n").asInt());
        assertTrue(data("/api/ra/cohort/list","{}").path("total").asInt()>0);
        assertEquals(2,data("/api/ra/cohort/rename",command(id,1,sequence+1,"Renamed"+sequence)).path("version").asInt());
        JsonNode compare=compareSuccess(compareBody(null));
        assertEquals(6,compare.path("a").path("n").asInt());
        assertEquals(6,compare.path("b").path("n").asInt());
        sameRetainedComparison(compare,readComparison(compare.path("compareResultId").asText()));
        if(fileAllowed)readableFile(xlsx(fileBody(run.path("analysisId").asText())),37);
        else {
            deny("/api/ra/export/xlsx",fileBody(run.path("analysisId").asText()),"文件导出暂不可用");
            deny("/api/ra/export/xlsx","{","文件导出暂不可用");
        }
        JsonNode application=data("/api/ra/export/applyAnalysis",applicationBody(run.path("analysisId").asText(),sequence+2));
        assertEquals("FAILED",application.path("notification").path("mailStatus").asText());
        assertEquals("NOT_CONFIGURED",application.path("notification").path("reason").asText());
        assertEquals("FAILED",data("/api/ra/export/retryNotification",retryBody(application.path("applicationId").asText())).path("notification").path("mailStatus").asText());
        assertEquals(3,data("/api/ra/cohort/delete",command(id,2,sequence+3,null)).path("version").asInt());
        exited();
    }
    static String requestId(int id){
        return String.format(java.util.Locale.ROOT,"00000000-0000-4000-8000-%012d",id);
    }
    JsonNode saveDefinition(String name,String filters,int id)throws Exception{
        return data("/api/ra/cohort/save","{\"name\":\""+name+"\",\"filters\":"+filters+",\"requestId\":\""+requestId(id)+"\"}");
    }
    String idBody(String id){
        return "{\"cohortId\":\""+id+"\"}";
    }
    String command(String id,int version,int request,String name){
        return "{\"cohortId\":\""+id+"\",\"version\":"+version+",\"requestId\":\""+requestId(request)+"\""+(name==null?"":",\"name\":\""+name+"\"")+"}";
    }
    String pageBody(JsonNode run)throws Exception{
        return json.writeValueAsString(com.wenwen.vo.AiCohortVo.object("analysisId",run.path("analysisId").asText(),"cursor",run.path("patients").path("nextCursor").asText()));
    }
    String compareBody(String saved){
        return "{\"a\":"+(saved==null?"{\"source\":\"FILTERS\",\"filters\":{\"ids\":[\"109\",\"110\",\"111\",\"112\",\"113\",\"114\"]}}":"{\"source\":\"SAVED\",\"cohortId\":\""+saved+"\"}")+",\"b\":{\"source\":\"FILTERS\",\"filters\":{\"ids\":[\"121\",\"122\",\"123\",\"124\",\"125\",\"126\"]}}}";
    }
    String fileBody(String id){
        return "{\"source\":\"COHORT\",\"analysisId\":\""+id+"\",\"fields\":[\"das28Current\"]}";
    }
    String applicationBody(String id,int request){
        return "{\"requestId\":\""+requestId(request)+"\",\"export\":{\"source\":\"COHORT\",\"analysisId\":\""+id+"\",\"fields\":[\"patientId\",\"sex\"]},\"startDate\":\"2026-01-01\",\"endDate\":\"2026-10-07\",\"purposeCode\":\"RESEARCH\",\"reason\":\"P07c合成申请说明\"}";
    }
    String retryBody(String id){
        return "{\"applicationId\":\""+id+"\"}";
    }
    void readableFile(MvcResult r,int count)throws Exception {
        assertEquals(200,r.getResponse().getStatus(),r.getResponse().getContentAsString());
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",r.getResponse().getContentType());
        try(org.apache.poi.xssf.usermodel.XSSFWorkbook book=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(r.getResponse().getContentAsByteArray()))){
            assertEquals(count,book.getSheet("cohort").getLastRowNum());
            assertEquals(2,book.getNumberOfSheets());
            for(int n=1;n<=count;n++){
                org.apache.poi.ss.usermodel.Cell c=book.getSheet("cohort").getRow(n).getCell(0);
                assertEquals(org.apache.poi.ss.usermodel.CellType.NUMERIC,c.getCellTypeEnum());
                assertEquals(java.math.BigDecimal.valueOf(38-n,1).doubleValue(),c.getNumericCellValue());
            }
        }
    }
    @Test void allGlobalClosedValuesRejectLegalAndMalformedBodiesWithoutPersistentChanges()throws Exception {
        exportPopulation37();
        JsonNode run=success("{}");
        String d=saveDefinition("ClosedMatrix","{\"ids\":[\"109\",\"110\",\"111\",\"112\",\"113\",\"114\"]}",100).path("cohortId").asText();
        JsonNode k=compareSuccess(compareBody(d));
        String app=data("/api/ra/export/applyAnalysis",applicationBody(run.path("analysisId").asText(),101)).path("applicationId").asText();
        Map<String,String> bodies=legalBodies(run,d,k.path("compareResultId").asText(),app);
        String before=stateDigest();
        for(String value:new String[]{"false","","TRUE","False"," true ","1","yes"}){
            analysisProperties.put(GLOBAL,value);
            analysisProperties.put(FILE,"false");
            rebuild(true);
            assertEquals(value,context.getEnvironment().getProperty(GLOBAL));
            clock.onInstant=()->{
                throw new AssertionError("禁用请求不得读Clock");
            }
            ;
            for(Map.Entry<String,String> entry:bodies.entrySet()){
                deny(entry.getKey(),entry.getValue(),"队列功能暂不可用");
                deny(entry.getKey(),"{","队列功能暂不可用");
            }
            assertEquals(before,stateDigest(),"global="+value);
        }
    }
    Map<String,String> legalBodies(JsonNode r,String d,String k,String app)throws Exception {
        Map<String,String> m=new LinkedHashMap<>();
        m.put("/api/ra/ai/cohort","{}");
        m.put("/api/ra/ai/cohortPatients",pageBody(r));
        m.put("/api/ra/ai/resolveEntry","{\"source\":\"SIMILAR\",\"indexPatientId\":\"101\"}");
        m.put("/api/ra/cohort/save","{\"name\":\"MustNotSave\",\"filters\":{},\"requestId\":\""+requestId(102)+"\"}");
        m.put("/api/ra/cohort/list","{}");
        m.put("/api/ra/cohort/open",idBody(d));
        m.put("/api/ra/cohort/rename",command(d,1,103,"MustNotRename"));
        m.put("/api/ra/cohort/delete",command(d,1,104,null));
        m.put("/api/ra/ai/compare",compareBody(d));
        m.put("/api/ra/ai/compareResult","{\"compareResultId\":\""+k+"\"}");
        m.put("/api/ra/export/xlsx",fileBody(r.path("analysisId").asText()));
        m.put("/api/ra/export/applyAnalysis",applicationBody(r.path("analysisId").asText(),105));
        m.put("/api/ra/export/retryNotification",retryBody(app));
        assertEquals(13,m.size());
        return m;
    }
    String stateDigest()throws Exception{
        return rows("SELECT * FROM ra_private_cohort_definition ORDER BY id")+rows("SELECT * FROM ra_private_cohort_command ORDER BY owner_doctor_id,action,request_id")+rows("SELECT * FROM ra_ai_analysis_query_session ORDER BY id")+rows("SELECT * FROM ra_ai_compare_run ORDER BY id")+rows("SELECT * FROM ra_ai_export_audit ORDER BY id")+rows("SELECT * FROM export_application ORDER BY id")+rows("SELECT * FROM ra_ai_export_request ORDER BY owner_doctor_id,request_id");
    }
    @Test void anonymousIdentityPrecedesBothClosingModesOnAllNonParseRoutes()throws Exception {
        exportPopulation37();
        JsonNode r=success("{}");
        String d=saveDefinition("Anonymous","{}",110).path("cohortId").asText();
        String k=compareSuccess(compareBody(null)).path("compareResultId").asText();
        String app=data("/api/ra/export/applyAnalysis",applicationBody(r.path("analysisId").asText(),111)).path("applicationId").asText();
        Map<String,String> bodies=legalBodies(r,d,k,app);
        String before=stateDigest();
        for(boolean global:new boolean[]{false,true}){
            analysisProperties.put(GLOBAL,Boolean.toString(global));
            analysisProperties.put(FILE,"false");
            rebuild(false);
            clock.onInstant=()->{
                throw new AssertionError("匿名不得读Clock");
            }
            ;
            for(Map.Entry<String,String> e:bodies.entrySet())for(String body:new String[]{e.getValue(),"{"}){
                resetOwners();
                MvcResult response=http.perform(post(e.getKey()).contentType("application/json").content(body)).andReturn();
                assertEquals(401,response.getResponse().getStatus(),e.getKey());
                JsonNode error=json.readTree(response.getResponse().getContentAsByteArray());
                assertFalse(error.path("success").asBoolean());
                assertEquals("UNAUTHENTICATED",error.path("code").asText());
                assertTrue(error.path("data").isNull());
                assertNotNull(response.getResponse().getHeader("X-Trace-Id"));
                zeroOwners();
            }
            assertEquals(before,stateDigest());
        }
    }
    @Test void everyFileClosedValueLeavesOtherConsumersAndSmtpDisabledRecordingAvailable()throws Exception {
        exportPopulation37();
        int sequence=200;
        for(String value:new String[]{"false","","TRUE","False"," true ","1","yes"}){
            analysisProperties.put(GLOBAL,"true");
            analysisProperties.put(FILE,value);
            analysisProperties.put("ra.ai.gateway.enabled","false");
            analysisProperties.put("ra.export.smtp.enabled","false");
            rebuild(true);
            assertEquals(value,context.getEnvironment().getProperty(FILE));
            exerciseEnabledConsumers(sequence,false);
            sequence+=10;
            assertEquals(0,context.getBean(ObservedWriter.class).completed);
            assertEquals(0,rawCount("SELECT COUNT(*) FROM ra_ai_export_audit"));
        }
    }
    @Test void closingBothNewFlagsKeepsLegacyRangeAndInvalidDateContracts()throws Exception {
        Map<String,JsonNode> original=legacyResponses();
        analysisProperties.put(GLOBAL,"false");
        analysisProperties.put(FILE,"false");
        rebuild(false);
        assertEquals(original,legacyResponses());
        assertEquals(0,applicationObserved.borrowed.get());
        assertEquals(0,observed.borrowed);
        assertEquals(0,context.getBean(ObservedWriter.class).completed);
        exited();
    }
    Map<String,JsonNode> legacyResponses()throws Exception {
        Map<String,JsonNode> result=new LinkedHashMap<>();
        MvcResult range=http.perform(post("/api/ra/export/range")).andReturn();
        assertEquals(200,range.getResponse().getStatus());
        JsonNode r=json.readTree(range.getResponse().getContentAsByteArray());
        assertTrue(r.path("success").asBoolean());
        assertEquals("200",r.path("code").asText());
        result.put("range",r);
        for(String route:new String[]{"visitsCsv","apply"}){
            MvcResult response=http.perform(post("/api/ra/export/"+route).param("doctorId","101").param("startDate","bad").param("endDate","2026-10-07").param("reason","合成旧参数检查")).andReturn();
            assertEquals(200,response.getResponse().getStatus());
            JsonNode e=json.readTree(response.getResponse().getContentAsByteArray());
            assertFalse(e.path("success").asBoolean());
            assertEquals("400",e.path("code").asText());
            result.put(route,e);
        }
        return result;
    }
    @Test void originalLlmAndSmtpClosedDoNotCloseAnalysisFileOrApplicationRecording()throws Exception {
        analysisProperties.put(GLOBAL,"true");
        analysisProperties.put(FILE,"true");
        analysisProperties.put("ra.ai.gateway.enabled","false");
        analysisProperties.put("ra.ai.gateway.outbound-approved","false");
        analysisProperties.put("ra.export.smtp.enabled","false");
        rebuild(true);
        exportPopulation37();
        assertEquals("false",context.getEnvironment().getProperty("ra.ai.gateway.enabled"));
        assertEquals("false",context.getEnvironment().getProperty("ra.export.smtp.enabled"));
        exerciseEnabledConsumers(300);
        exited();
    }
    @Test void closureAndReassemblyPreserveDefinitionsRetainedPayloadsAuditAndOriginalExpiry()throws Exception {
        exportPopulation37();
        JsonNode r=success("{}");
        String run=r.path("analysisId").asText();
        String d=saveDefinition("Retained","{\"ids\":[\"109\",\"110\",\"111\",\"112\",\"113\",\"114\"]}",400).path("cohortId").asText();
        JsonNode k=compareSuccess(compareBody(d));
        String comparison=k.path("compareResultId").asText();
        MvcResult file=xlsx(fileBody(run));
        readableFile(file,37);
        String audit=file.getResponse().getHeader("X-Trace-Id");
        assertEquals(1,rawCount("SELECT COUNT(*) FROM ra_ai_export_audit WHERE id='"+audit+"' AND generation_status='READY' AND transfer_status='WRITTEN' AND file_rows=37 AND file_bytes="+file.getResponse().getContentAsByteArray().length));
        String app=data("/api/ra/export/applyAnalysis",applicationBody(run,401)).path("applicationId").asText();
        String before=stateDigest(),definition=rows("SELECT * FROM ra_private_cohort_definition WHERE id='"+d+"'"),commands=rows("SELECT * FROM ra_private_cohort_command ORDER BY owner_doctor_id,action,request_id"),runRow=rows("SELECT * FROM ra_ai_analysis_query_session WHERE id='"+run+"'"),compareRow=rows("SELECT * FROM ra_ai_compare_run WHERE id='"+comparison+"'"),auditRow=rows("SELECT * FROM ra_ai_export_audit WHERE id='"+audit+"'");
        byte[] rBytes=analysisPayload(run),kBytes=payload(comparison);
        analysisProperties.put(GLOBAL,"false");
        analysisProperties.put(FILE,"false");
        rebuild(true);
        for(Map.Entry<String,String> e:legalBodies(r,d,comparison,app).entrySet())deny(e.getKey(),e.getValue(),"队列功能暂不可用");
        assertEquals(before,stateDigest());
        analysisProperties.put(GLOBAL,"true");
        rebuild(true);
        deny("/api/ra/export/xlsx",fileBody(run),"文件导出暂不可用");
        assertEquals(before,stateDigest());
        JsonNode opened=data("/api/ra/cohort/open",idBody(d));
        assertEquals(6,opened.path("analysis").path("n").asInt());
        assertNotEquals(run,opened.path("analysis").path("analysisId").asText());
        assertEquals(20,ids(data("/api/ra/ai/cohortPatients",pageBody(r))).size());
        sameRetainedComparison(k,readComparison(comparison));
        assertEquals(definition,rows("SELECT * FROM ra_private_cohort_definition WHERE id='"+d+"'"));
        assertEquals(commands,rows("SELECT * FROM ra_private_cohort_command ORDER BY owner_doctor_id,action,request_id"));
        assertEquals(runRow,rows("SELECT * FROM ra_ai_analysis_query_session WHERE id='"+run+"'"));
        assertEquals(compareRow,rows("SELECT * FROM ra_ai_compare_run WHERE id='"+comparison+"'"));
        assertArrayEquals(rBytes,analysisPayload(run));
        assertArrayEquals(kBytes,payload(comparison));
        assertEquals(auditRow,rows("SELECT * FROM ra_ai_export_audit WHERE id='"+audit+"'"));
        analysisProperties.put(FILE,"true");
        rebuild(true);
        readableFile(xlsx(fileBody(run)),37);
        clock.now=java.time.Instant.parse(r.path("meta").path("expiresAt").asText());
        assertEquals(r.path("meta").path("expiresAt"),k.path("meta").path("expiresAt"));
        errorAt("/api/ra/ai/cohortPatients",pageBody(r),410,"ANALYSIS_EXPIRED");
        errorAt("/api/ra/ai/compareResult","{\"compareResultId\":\""+comparison+"\"}",410,"COMPARE_EXPIRED");
        errorAt("/api/ra/export/xlsx",fileBody(run),410,"ANALYSIS_EXPIRED");
        JsonNode fresh=data("/api/ra/cohort/open",idBody(d)).path("analysis");
        assertEquals(6,fresh.path("n").asInt());
        assertNotEquals(run,fresh.path("analysisId").asText());
        assertEquals(clock.now.plusSeconds(900).toString(),fresh.path("meta").path("expiresAt").asText());
        compareSuccess(compareBody(d));
        errorAt("/api/ra/ai/compareResult","{\"compareResultId\":\""+comparison+"\"}",404,"RESOURCE_NOT_FOUND");
        errorAt("/api/ra/export/xlsx",fileBody(run),410,"ANALYSIS_EXPIRED");
        assertEquals(auditRow,rows("SELECT * FROM ra_ai_export_audit WHERE id='"+audit+"'"));
        assertEquals(definition,rows("SELECT * FROM ra_private_cohort_definition WHERE id='"+d+"'"));
        exited();
    }
    void errorAt(String route,String body,int status,String code)throws Exception {
        MvcResult r=http.perform(post(route).contentType("application/json").content(body)).andReturn();
        assertEquals(status,r.getResponse().getStatus(),r.getResponse().getContentAsString());
        JsonNode e=json.readTree(r.getResponse().getContentAsByteArray());
        assertFalse(e.path("success").asBoolean());
        assertEquals(code,e.path("code").asText());
        assertTrue(e.path("data").isNull());
        assertNotNull(r.getResponse().getHeader("X-Trace-Id"));
        exited();
    }
    @Test void closureReopenKeepsUnknownSentAndFailedAndOnlyExplicitFailedRetrySends()throws Exception {
        exportPopulation37();
        String run=success("{}").path("analysisId").asText();
        String uBody=applicationBody(run,500),sBody=applicationBody(run,501),fBody=applicationBody(run,502);
        try(NotificationSmtpPeer peer=new NotificationSmtpPeer(NotificationSmtpPeer.Mode.SUCCESS)){
            smtp(peer);
            java.util.concurrent.atomic.AtomicBoolean reached=new java.util.concurrent.atomic.AtomicBoolean();
            applicationObserved.afterSqlContains="SET mail_state=?";
            applicationObserved.afterSql=(owner,sql)->{
                reached.set(true);
                try(java.sql.Statement st=owner.createStatement();java.sql.ResultSet r=st.executeQuery("SELECT COUNT(*) FROM ra_ai_export_request r JOIN export_application a ON a.id=r.application_id WHERE r.mail_state='SENT' AND a.mail_sent=1")){
                    assertTrue(r.next());
                    assertEquals(1,r.getInt(1));
                }
                assertEquals(0,rawCount("SELECT COUNT(*) FROM export_application WHERE mail_sent=1"));
                throw new java.sql.SQLException("P07c synthetic accepted-settle rollback","45000");
            }
            ;
            JsonNode u=data("/api/ra/export/applyAnalysis",uBody);
            String uId=u.path("applicationId").asText();
            assertTrue(reached.get());
            assertEquals(1,applicationObserved.faults);
            assertEquals(1,applicationObserved.rollbacks);
            assertEquals("UNKNOWN",u.path("notification").path("mailStatus").asText());
            assertTrue(u.path("notification").path("smtpAccepted").asBoolean());
            assertFalse(u.path("notification").path("statusConfirmed").asBoolean());
            assertEquals(1,peer.accepted.get());
            assertEquals(1,rawCount("SELECT COUNT(*) FROM ra_ai_export_request r JOIN export_application a ON a.id=r.application_id WHERE a.id="+uId+" AND r.mail_state='UNKNOWN' AND a.mail_sent=0 AND r.attempt_no=1"));
            smtpExited();
            resetOwners();
            JsonNode sent=data("/api/ra/export/applyAnalysis",sBody);
            String sId=sent.path("applicationId").asText();
            assertEquals("SENT",sent.path("notification").path("mailStatus").asText());
            assertEquals(2,peer.accepted.get());
            smtpExited();
            peer.mode=NotificationSmtpPeer.Mode.RCPT_REJECT;
            JsonNode failed=data("/api/ra/export/applyAnalysis",fBody);
            String fId=failed.path("applicationId").asText();
            assertEquals("FAILED",failed.path("notification").path("mailStatus").asText());
            assertEquals("PRE_DATA_REJECTED",failed.path("notification").path("reason").asText());
            assertEquals(3,peer.connections.get());
            assertEquals(2,peer.accepted.get());
            assertEquals(2,peer.messages.size());
            smtpExited();
            Map<String,String> uState=requestFields(uId),sState=requestFields(sId),fState=requestFields(fId);
            assertEquals(1,rawCount("SELECT COUNT(*) FROM export_application WHERE id="+sId+" AND mail_sent=1"));
            assertEquals(1,rawCount("SELECT COUNT(*) FROM export_application WHERE id="+fId+" AND mail_sent=0"));
            String before=stateDigest();
            int connections=peer.connections.get(),accepted=peer.accepted.get();
            analysisProperties.put(GLOBAL,"false");
            rebuild(true);
            deny("/api/ra/export/applyAnalysis",applicationBody(run,503),"队列功能暂不可用");
            for(String id:new String[]{uId,sId,fId})deny("/api/ra/export/retryNotification",retryBody(id),"队列功能暂不可用");
            assertEquals(before,stateDigest());
            assertEquals(uState,requestFields(uId));
            assertEquals(sState,requestFields(sId));
            assertEquals(fState,requestFields(fId));
            assertEquals(connections,peer.connections.get());
            assertEquals(accepted,peer.accepted.get());
            smtpExited();
            analysisProperties.put(GLOBAL,"true");
            rebuild(true);
            for(String body:new String[]{uBody,sBody,fBody})data("/api/ra/export/applyAnalysis",body);
            assertEquals("UNKNOWN",data("/api/ra/export/retryNotification",retryBody(uId)).path("notification").path("mailStatus").asText());
            assertEquals("SENT",data("/api/ra/export/retryNotification",retryBody(sId)).path("notification").path("mailStatus").asText());
            assertEquals(uState,requestFields(uId));
            assertEquals(sState,requestFields(sId));
            assertEquals(fState,requestFields(fId));
            assertEquals(connections,peer.connections.get());
            assertEquals(accepted,peer.accepted.get());
            smtpExited();
            peer.mode=NotificationSmtpPeer.Mode.SUCCESS;
            JsonNode retried=data("/api/ra/export/retryNotification",retryBody(fId));
            assertEquals(fId,retried.path("applicationId").asText());
            assertEquals("SENT",retried.path("notification").path("mailStatus").asText());
            assertNotEquals(failed.path("notification").path("attemptId"),retried.path("notification").path("attemptId"));
            assertEquals(connections+1,peer.connections.get());
            assertEquals(accepted+1,peer.accepted.get());
            assertEquals(3,peer.messages.size());
            assertEquals(1,rawCount("SELECT COUNT(*) FROM ra_ai_export_request r JOIN export_application a ON a.id=r.application_id WHERE a.id="+fId+" AND r.attempt_no=2 AND r.mail_state='SENT' AND a.mail_sent=1"));
            Map<String,String> next=requestFields(fId);
            for(String changed:new String[]{"mail_state","attempt_id","attempt_no","attempt_started_at_ms","decided_at_ms","last_error","mail_sent"}){
                next.remove(changed);
                fState.remove(changed);
            }
            assertEquals(fState,next);
            assertEquals(uState,requestFields(uId));
            assertEquals(sState,requestFields(sId));
            assertEquals(3,rawCount("SELECT COUNT(*) FROM export_application"));
            assertEquals(3,rawCount("SELECT COUNT(*) FROM ra_ai_export_request"));
            MvcResult denied=xlsx("{\"source\":\"COHORT\",\"analysisId\":\""+run+"\"}");
            assertEquals(409,denied.getResponse().getStatus());
            JsonNode need=json.readTree(denied.getResponse().getContentAsByteArray());
            assertFalse(need.path("success").asBoolean());
            assertEquals("NEED_APPLY",need.path("code").asText());
            assertEquals("NEED_APPLY",need.path("data").path("decision").asText());
            assertEquals(15,need.path("data").path("requested").size());
            assertEquals(0,context.getBean(ObservedWriter.class).completed);
            smtpExited();
            assertNull(peer.failure);
            exited();
        }
    }
    void smtp(NotificationSmtpPeer peer){
        analysisProperties.put("ra.export.smtp.enabled","true");
        analysisProperties.put("ra.export.smtp.host","127.0.0.1");
        analysisProperties.put("ra.export.smtp.port",Integer.toString(peer.port()));
        analysisProperties.put("ra.export.smtp.mode","LOOPBACK_PLAIN");
        analysisProperties.put("ra.export.smtp.from","p07c-sender@notification.invalid");
        analysisProperties.put("ra.export.apply-mail-to","p07c-admin@notification.invalid");
        rebuild(true);
    }
    Map<String,String> requestFields(String id)throws Exception {
        Map<String,String> out=new LinkedHashMap<>();
        try(java.sql.Connection c=raw.getConnection();java.sql.PreparedStatement q=c.prepareStatement("SELECT r.*,a.* FROM ra_ai_export_request r JOIN export_application a ON a.id=r.application_id WHERE r.application_id=?")){
            q.setString(1,id);
            try(java.sql.ResultSet r=q.executeQuery()){
                assertTrue(r.next());
                for(int i=1;i<=r.getMetaData().getColumnCount();i++){
                    byte[] bytes=r.getBytes(i);
                    out.put(r.getMetaData().getColumnLabel(i),bytes==null?null:Base64.getEncoder().encodeToString(bytes));
                }
                assertFalse(r.next());
            }
        }
        return out;
    }
    void smtpExited()throws Exception {
        Object sender=context.getBean(com.wenwen.ai.export.mail.ExportNotificationSender.class);
        java.lang.reflect.Field active=sender.getClass().getDeclaredField("active"),executor=sender.getClass().getDeclaredField("executor");
        active.setAccessible(true);
        executor.setAccessible(true);
        java.util.concurrent.ThreadPoolExecutor threads=(java.util.concurrent.ThreadPoolExecutor)executor.get(sender);
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(1);
        while(threads.getActiveCount()!=0&&System.nanoTime()<deadline)Thread.yield();
        assertEquals(0,threads.getActiveCount());
        assertNull(((java.util.concurrent.atomic.AtomicReference<?>)active.get(sender)).get());
        exited();
    }
    @Test void admittedOldInstanceFinishesAndRemainsEnabledWhileNewInstanceCloses()throws Exception {
        analysisProperties.put(GLOBAL,"true");
        rebuild(true);
        exportPopulation37();
        final org.springframework.web.context.support.AnnotationConfigWebApplicationContext oldContext=context;
        final org.springframework.test.web.servlet.MockMvc oldHttp=http;
        final CountingBodyFilter oldBody=bodyRead;
        final ObservedDataSource oldObserved=observed;
        final PrivateCohortObservedDataSource oldPrivate=privateObserved;
        final ComparisonObservedDataSource oldComparison=comparisonObserved;
        final ExportObservedDataSource oldExport=exportObserved;
        final ExportApplicationObservedDataSource oldApplication=applicationObserved;
        final ObservedWriter oldWriter=oldContext.getBean(ObservedWriter.class);
        final com.wenwen.ai.scope.AiFeatureAvailability oldAvailability=oldContext.getBean(com.wenwen.ai.scope.AiFeatureAvailability.class);
        final java.util.concurrent.CountDownLatch sourceReached=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);
        oldComparison.afterPatients=(owner,id)->{
            sourceReached.countDown();
            assertTrue(release.await(10,java.util.concurrent.TimeUnit.SECONDS),"实际source SELECT屏障须释放");
        }
        ;
        java.util.concurrent.ExecutorService caller=java.util.concurrent.Executors.newSingleThreadExecutor();
        java.util.concurrent.Future<MvcResult> result=caller.submit(()->oldHttp.perform(post("/api/ra/ai/cohort").contentType("application/json").content("{}")).andReturn());
        try{
            assertTrue(sourceReached.await(5,java.util.concurrent.TimeUnit.SECONDS),"必须实际完成patients SELECT后才改变配置");
            analysisProperties.put(GLOBAL,"false");
            assertEquals("false",oldContext.getEnvironment().getProperty(GLOBAL));
            assertSame(oldAvailability,oldContext.getBean(com.wenwen.ai.scope.AiFeatureAvailability.class));
            buildContext(true);
            resetOwners();
            assertNotSame(oldContext,context);
            assertNotSame(oldComparison,comparisonObserved);
            assertNotSame(oldApplication,applicationObserved);
            assertEquals("false",context.getEnvironment().getProperty(GLOBAL));
            deny("/api/ra/ai/cohort","{}","队列功能暂不可用");
            assertTrue(oldContext.isActive());
            release.countDown();
            MvcResult completed=result.get(10,java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(200,completed.getResponse().getStatus(),completed.getResponse().getContentAsString());
            JsonNode original=json.readTree(completed.getResponse().getContentAsByteArray());
            assertTrue(original.path("success").asBoolean());
            assertEquals(37,original.path("data").path("n").asInt());
            assertEquals(37,original.path("data").path("studyTotal").asInt());
            assertEquals("P03_RESULT",original.path("data").path("meta").path("completion").asText());
            assertTrue(oldBody.bytes>0);
            oldExited(oldObserved,oldPrivate,oldComparison,oldExport,oldApplication,oldWriter);
            MvcResult next=oldHttp.perform(post("/api/ra/ai/cohort").contentType("application/json").content("{}")).andReturn();
            assertEquals(200,next.getResponse().getStatus());
            assertEquals(37,json.readTree(next.getResponse().getContentAsByteArray()).path("data").path("n").asInt());
            assertEquals("false",oldContext.getEnvironment().getProperty(GLOBAL));
            assertSame(oldAvailability,oldContext.getBean(com.wenwen.ai.scope.AiFeatureAvailability.class));
            oldAvailability.requireApi();
            oldExited(oldObserved,oldPrivate,oldComparison,oldExport,oldApplication,oldWriter);
            deny("/api/ra/ai/cohort","{}","队列功能暂不可用");
            assertEquals(2,rawCount("SELECT COUNT(*) FROM ra_ai_analysis_query_session"));
            exited();
        }
        finally{
            release.countDown();
            caller.shutdownNow();
            assertTrue(caller.awaitTermination(10,java.util.concurrent.TimeUnit.SECONDS),"先退出旧caller，才关闭旧context作cleanup");
            oldContext.close();
        }
    }
    void oldExited(ObservedDataSource o,PrivateCohortObservedDataSource p,ComparisonObservedDataSource c,ExportObservedDataSource e,ExportApplicationObservedDataSource a,ObservedWriter w){
        assertEquals(0,o.active.get());
        assertEquals(o.borrowed,o.returned);
        for(java.util.concurrent.atomic.AtomicInteger active:Arrays.asList(p.active,c.active,e.active,a.active))assertEquals(0,active.get());
        assertEquals(p.borrowed.get(),p.returned.get());
        assertEquals(c.borrowed.get(),c.returned.get());
        assertEquals(e.borrowed.get(),e.returned.get());
        assertEquals(a.borrowed.get(),a.returned.get());
        assertEquals(0,w.workbooks);
        assertEquals(0,w.active);
    }
    void deny(String route,String body,String message)throws Exception {
        resetOwners();
        MvcResult r=http.perform(post(route).contentType("application/json").content(body)).andReturn();
        assertEquals(503,r.getResponse().getStatus(),route+" "+r.getResponse().getContentAsString());
        unavailable(r,message);
        zeroOwners();
    }
    String rows(String sql)throws Exception {
        java.security.MessageDigest sha=java.security.MessageDigest.getInstance("SHA-256");
        try(java.sql.Connection c=raw.getConnection();java.sql.Statement st=c.createStatement();java.sql.ResultSet r=st.executeQuery(sql)){
            while(r.next())for(int i=1;i<=r.getMetaData().getColumnCount();i++){
                byte[] b=r.getBytes(i);
                sha.update(java.nio.ByteBuffer.allocate(4).putInt(b==null?-1:b.length).array());
                if(b!=null)sha.update(b);
            }
        }
        return com.wenwen.ai.result.AnalysisValues.sha256(sha.digest());
    }
    void unavailable(MvcResult result,String message)throws Exception {
        JsonNode e=json.readTree(result.getResponse().getContentAsByteArray());
        assertFalse(e.path("success").asBoolean());
        assertEquals("SERVICE_UNAVAILABLE",e.path("code").asText());
        assertEquals(message,e.path("message").asText());
        assertTrue(e.path("data").isNull());
        assertNotNull(result.getResponse().getHeader("X-Trace-Id"));
    }
    void zeroOwners(){
        assertEquals(0,bodyRead.bytes);
        assertEquals(0,applicationObserved.borrowed.get());
        assertEquals(0,exportObserved.borrowed.get());
        assertEquals(0,comparisonObserved.borrowed.get());
        assertEquals(0,privateObserved.borrowed.get());
        assertEquals(0,observed.borrowed);
        assertEquals(0,context.getBean(ObservedWriter.class).completed);
        exited();
    }
}
