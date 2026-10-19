package com.wenwen.ai.cohort;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 当前生产身份/配置经真实 TCP 与 SQL 保留引用，再独立重开实际文件。 */
class CurrentExportCompositionTest {
    static final String AI="/api/ra/ai/", EXPORT="/api/ra/export/";
    static class Client {
        final String base; String cookie;
        Client(int port){base="http://127.0.0.1:"+port;}
        Reply post(String path,String body)throws Exception{
            HttpURLConnection c=(HttpURLConnection)new URL(base+path).openConnection();
            c.setConnectTimeout(5000);c.setReadTimeout(20000);c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");if(cookie!=null)c.setRequestProperty("Cookie",cookie);
            try(OutputStream out=c.getOutputStream()){out.write(body.getBytes("UTF-8"));}
            int status=c.getResponseCode();String set=c.getHeaderField("Set-Cookie");if(set!=null)cookie=set.split(";",2)[0];
            InputStream in=status>=400?c.getErrorStream():c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream();if(in!=null)try(InputStream stream=in){byte[] b=new byte[4096];int n;while((n=stream.read(b))!=-1)out.write(b,0,n);}
            Reply r=new Reply(status,out.toByteArray(),c.getHeaderField("Content-Type"),c.getHeaderField("Content-Disposition"));c.disconnect();return r;
        }
        JsonNode data(String path,String body)throws Exception{Reply r=post(path,body);assertEquals(200,r.status,new String(r.bytes,"UTF-8"));return WebLoopbackApiFixture.JSON.readTree(r.bytes).path("data");}
    }
    static class Reply {final int status;final byte[] bytes;final String type,disposition;Reply(int s,byte[] b,String t,String d){status=s;bytes=b;type=t;disposition=d;}}
    static String request(String id,String fields){return "{\"source\":\"COHORT\",\"analysisId\":\""+id+"\",\"fields\":"+fields+"}";}
    JsonNode inspect(Reply r,String name)throws Exception{
        assertEquals(200,r.status,new String(r.bytes,"UTF-8"));assertTrue(r.type.contains("spreadsheetml"));assertNotNull(r.disposition);
        Path dir=Paths.get("../.tmp/ai-analysis-real-data/P06a",name).toAbsolutePath();Files.createDirectories(dir);Path file=dir.resolve(name+".xlsx"),report=dir.resolve("workbook-observation.json");Files.write(file,r.bytes);WebExportWorkbookInspector.main(new String[]{file.toString(),report.toString()});return WebLoopbackApiFixture.JSON.readTree(Files.readAllBytes(report));
    }
    static JsonNode cell(JsonNode book,int row,int column){for(JsonNode c:book.path("sheets").get(0).path("cells"))if(c.path("row").asInt()==row&&c.path("column").asInt()==column)return c;throw new AssertionError("missing cell "+row+":"+column);}
    static void number(JsonNode book,int row,int column,String expected){JsonNode c=cell(book,row,column);assertEquals("NUMERIC",c.path("type").asText());assertEquals(0,new java.math.BigDecimal(expected).compareTo(new java.math.BigDecimal(c.path("raw").asText())));}
    static void text(JsonNode book,int row,int column,String expected){JsonNode c=cell(book,row,column);assertEquals("STRING",c.path("type").asText());assertEquals(expected,c.path("string").asText());}
    static void blank(JsonNode book,int row,int column){assertEquals("BLANK",cell(book,row,column).path("type").asText());}
    static Client start(WebLoopbackApiFixture f)throws Exception{f.scenario="p06-current";f.connect();f.schema();Client c=new Client(f.start("synthetic",false,Files.createTempDirectory("p06-current-").toFile()));assertEquals(204,c.post("/api/ra/test/session","{}").status);return c;}
    static void versions(JsonNode meta)throws Exception{JsonNode versions=WebLoopbackApiFixture.JSON.readTree(meta.path("policyVersions").asText());assertEquals("synthetic-fm-v1",versions.path("fmSource").asText());assertEquals("synthetic-drug-v1",versions.path("drugDictionary").asText());}
    @Test void typedCurrentCohortProducesNativeWorkbook()throws Exception{
        try(WebLoopbackApiFixture f=new WebLoopbackApiFixture()){
            f.scenario="p06-current";f.connect();f.schema();Client c=new Client(f.start("synthetic",false,Files.createTempDirectory("p06-cohort-").toFile()));assertEquals(204,c.post("/api/ra/test/session","{}").status);
            JsonNode d=c.data(AI+"cohort","{\"filters\":{\"ids\":[\"1\",\"2\",\"3\",\"4\",\"5\"]}}");assertEquals(5,d.path("n").asInt());
            String id=d.path("analysisId").asText();assertFalse(id.isEmpty());JsonNode book=inspect(c.post(EXPORT+"xlsx",request(id,"[\"das28Current\",\"crpCurrent\"]")),"current-cohort");
            assertEquals(WebLoopbackApiFixture.JSON.readTree("[\"cohort\",\"metadata\"]"),book.path("sheetNames"));
            assertEquals(WebLoopbackApiFixture.JSON.readTree("[\"5\",\"4\",\"3\",\"2\",\"1\"]"),WebLoopbackApiFixture.JSON.valueToTree(d.path("patients").path("items").findValuesAsText("patientId")));
            text(book,0,0,"das28Current");text(book,0,1,"crpCurrent");String[] expected={"4.11","4.10","2.71","2.70","2"};for(int i=0;i<5;i++){number(book,i+1,0,expected[i]);number(book,i+1,1,"6");assertEquals("2026-10-06",d.path("patients").path("items").get(i).path("scoreProvenance").path("observedAt").asText());}
            JsonNode meta=book.path("metadata");assertEquals(id,meta.path("referenceId").asText());assertEquals("2026-04-01",meta.path("freeStart").asText());assertEquals("2026-10-07",meta.path("today").asText());versions(meta);assertFalse(book.toString().contains("合成患者"));assertEquals(6,book.path("sheets").get(0).path("rows").asInt());assertEquals("5",book.path("metadata").path("rowN").asText());assertEquals(0,f.observed.active.get());
        }
    }
    @Test void typedCurrentComparisonProducesIndependentQuantiles()throws Exception{
        try(WebLoopbackApiFixture f=new WebLoopbackApiFixture()){
            Client c=start(f);JsonNode d=c.data(AI+"compare","{\"a\":{\"source\":\"FILTERS\",\"filters\":{\"sex\":\"F\"}},\"b\":{\"source\":\"FILTERS\",\"filters\":{\"sex\":\"M\"}},\"excludeOverlap\":false}");
            String id=d.path("compareResultId").asText();JsonNode book=inspect(c.post(EXPORT+"xlsx","{\"source\":\"COMPARE\",\"compareResultId\":\""+id+"\",\"metrics\":[\"n\",\"das28At\"]}"),"current-comparison");
            String[] columns={"metric","kind","category","aN","bN","aValidN","bValidN","aUnknownN","bUnknownN","aDenominator","bDenominator","aKnownN","bKnownN","aNumerator","bNumerator","aQ1","aMedian","aQ3","aIqr","bQ1","bMedian","bQ3","bIqr","aValue","bValue","aCount","bCount","difference","pValue","displayP","method","testStatus","reason","nA","nB","testDenominator","warnings"};
            for(int i=0;i<columns.length;i++)text(book,0,i,columns[i]);assertEquals(3,book.path("sheets").get(0).path("rows").asInt());text(book,1,0,"n");text(book,1,1,"COUNT");number(book,1,3,"3");number(book,1,4,"3");for(int i=5;i<columns.length;i++)blank(book,1,i);
            text(book,2,0,"das28At");text(book,2,1,"CONTINUOUS");number(book,2,5,"2");number(book,2,6,"3");number(book,2,7,"1");number(book,2,8,"0");String[] q={"2.1775","2.355","2.5325",".355","3.4","4.1","4.105",".705"};for(int i=0;i<8;i++)number(book,2,15+i,q[i]);number(book,2,27,"-1.745");blank(book,2,28);text(book,2,29,"—");blank(book,2,30);text(book,2,31,"INSUFFICIENT_SAMPLE");text(book,2,32,"VALID_N_LT_5");number(book,2,33,"2");number(book,2,34,"3");text(book,2,35,"NON_MISSING_METRIC");assertEquals(id,book.path("metadata").path("referenceId").asText());assertEquals("2",book.path("metadata").path("rowN").asText());versions(book.path("metadata"));assertEquals(0,f.observed.active.get());
        }
    }

    static void sql(WebLoopbackApiFixture f,String sql)throws Exception{try(java.sql.Connection c=f.raw.getConnection();java.sql.Statement q=c.createStatement()){q.execute(sql);}}
    static int count(WebLoopbackApiFixture f,String table)throws Exception{try(java.sql.Connection c=f.raw.getConnection();java.sql.Statement q=c.createStatement();java.sql.ResultSet r=q.executeQuery("SELECT COUNT(*) FROM "+table)){assertTrue(r.next());return r.getInt(1);}}
    static JsonNode denied(Reply r,int status,String code)throws Exception{assertEquals(status,r.status);assertNull(r.disposition);assertFalse(r.type!=null&&r.type.contains("spreadsheetml"));JsonNode e=WebLoopbackApiFixture.JSON.readTree(r.bytes);assertEquals(code,e.path("code").asText());return e.path("data");}
    static void blocked(Reply r,String field,String reason,int n)throws Exception{JsonNode d=denied(r,409,"NEED_APPLY");assertEquals("2026-04-01",d.path("freeStart").asText());assertEquals("2026-10-07",d.path("today").asText());assertEquals(1,d.path("blocked").size());JsonNode b=d.path("blocked").get(0);assertEquals(field,b.path("field").asText());assertEquals(reason,b.path("reason").asText());assertEquals(n,b.path("affectedN").asInt());}
    @Test void fieldDatesIdentityRevocationAndExpiryFailClosed()throws Exception{
        try(WebLoopbackApiFixture f=new WebLoopbackApiFixture()){
            Client c=start(f);Client anonymous=new Client(Integer.parseInt(c.base.substring(c.base.lastIndexOf(':')+1)));int borrowed=f.observed.borrowed;
            for(String path:new String[]{AI+"cohort",AI+"compare",EXPORT+"xlsx",EXPORT+"applyAnalysis",EXPORT+"retryNotification"}){denied(anonymous.post(path,"{"),401,"UNAUTHENTICATED");assertEquals(0,f.currentBodyBytes);}assertEquals(borrowed,f.observed.borrowed);assertEquals(0,f.observed.active.get());
            JsonNode all=c.data(AI+"cohort","{}");assertEquals(6,all.path("n").asInt());String id=all.path("analysisId").asText();blocked(c.post(EXPORT+"xlsx",request(id,"[\"das28Current\"]")),"das28Current","MISSING_SOURCE_DATE",1);
            for(String field:new String[]{"patientId","sex","age","studyNo"})blocked(c.post(EXPORT+"xlsx",request(id,"[\""+field+"\"]")),field,"MISSING_SOURCE_DATE",6);
            blocked(c.post(EXPORT+"xlsx",request(id,"[\"fm\"]")),"fm","INCOMPLETE_SOURCE_EVIDENCE",6);
            sql(f,"UPDATE patient_follow_up_history SET follow_up_date='2026-03-31' WHERE id=110");JsonNode base=c.data(AI+"cohort","{\"filters\":{\"ids\":[\"1\"]}}");assertEquals(0,new java.math.BigDecimal("4.5").compareTo(base.path("patients").path("items").get(0).path("das28Base").decimalValue()));blocked(c.post(EXPORT+"xlsx",request(base.path("analysisId").asText(),"[\"das28Base\"]")),"das28Base","SOURCE_BEFORE_WINDOW",1);
            denied(c.post(AI+"cohort","{\"doctorId\":202}"),400,"INVALID_FILTER");assertEquals(6,c.data(AI+"cohort","{}").path("n").asInt());
            sql(f,"DELETE FROM patient_relation_doctor WHERE doctor_id=101 AND patient_id=1");denied(c.post(EXPORT+"xlsx",request(id,"[\"das28Current\"]")),409,"SCOPE_CHANGED");
            String fresh=c.data(AI+"cohort","{\"filters\":{\"ids\":[\"2\"]}}").path("analysisId").asText();f.scenarioClock.advanceTtl();denied(c.post(EXPORT+"xlsx",request(fresh,"[\"das28Current\"]")),410,"ANALYSIS_EXPIRED");
            String old=c.cookie;assertEquals(204,c.post("/api/ra/test/logout","{}").status);c.cookie=old;borrowed=f.observed.borrowed;denied(c.post(EXPORT+"xlsx","{"),401,"UNAUTHENTICATED");assertEquals(borrowed,f.observed.borrowed);assertEquals(0,f.observed.active.get());
        }
    }

    static String application(String id){return "{\"requestId\":\"00000000-0000-4000-8000-000000000601\",\"export\":"+request(id,"[\"patientId\",\"sex\"]")+",\"startDate\":\"2026-01-01\",\"endDate\":\"2026-10-07\",\"purposeCode\":\"RESEARCH\",\"reason\":\"ordinary-application-canary\"}";}
    @Test void typedApplicationReplaysCanonicalCommandAndKeepsDownloadClosed()throws Exception{
        try(WebLoopbackApiFixture f=new WebLoopbackApiFixture()){
            Client c=start(f);String id=c.data(AI+"cohort","{}").path("analysisId").asText(),body=application(id);
            JsonNode d=c.data(EXPORT+"applyAnalysis",body);assertEquals("APPLICATION_RECORDED",d.path("status").asText());assertEquals("SENT",d.path("notification").path("mailStatus").asText());assertEquals("UNKNOWN",d.path("notification").path("deliveryStatus").asText());assertTrue(d.path("notification").path("smtpAccepted").asBoolean());
            String canonical=body.replace("[\"patientId\",\"sex\"]","[\"sex\",\"patientId\"]").replace("ordinary-application-canary","  ordinary-application-canary  ");assertEquals(d.path("applicationId"),c.data(EXPORT+"applyAnalysis",canonical).path("applicationId"));denied(c.post(EXPORT+"applyAnalysis",body.replace("2026-01-01","2026-01-02")),409,"IDEMPOTENCY_CONFLICT");
            assertEquals(1,count(f,"export_application"));assertEquals(1,count(f,"ra_ai_export_request"));
            try(java.sql.Connection connection=f.raw.getConnection();java.sql.Statement query=connection.createStatement();java.sql.ResultSet rows=query.executeQuery("SELECT verified_summary FROM ra_ai_export_request")){assertTrue(rows.next());JsonNode summary=WebLoopbackApiFixture.JSON.readTree(rows.getString(1));assertEquals(id,summary.path("referenceId").asText());assertEquals("synthetic-drug-v1",summary.path("sourcePolicyVersions").path("drugDictionary").asText());assertEquals("synthetic-fm-v1",summary.path("sourcePolicyVersions").path("fmSource").asText());assertEquals(6,summary.path("counts").path("cohortN").asInt());assertFalse(summary.toString().contains("ordinary-application-canary"));assertFalse(rows.next());}
            assertEquals(1,f.applicationPeer.connections.get());assertEquals(1,f.applicationPeer.accepted.get());f.assertApplicationPeer();blocked(c.post(EXPORT+"xlsx",request(id,"[\"sex\"]")),"sex","MISSING_SOURCE_DATE",6);assertEquals(0,f.observed.active.get());
        }
    }

    @Test void failedNotificationRetriesWithNewAttempt()throws Exception{
        try(WebLoopbackApiFixture f=new WebLoopbackApiFixture()){
            Client c=start(f);f.applicationPeer.mode=NotificationSmtpPeer.Mode.RCPT_REJECT;String body=application(c.data(AI+"cohort","{}").path("analysisId").asText());JsonNode first=c.data(EXPORT+"applyAnalysis",body);assertEquals("FAILED",first.path("notification").path("mailStatus").asText());assertTrue(first.path("notification").path("retryable").asBoolean());f.assertCurrentExportOwners();
            assertEquals(first.path("applicationId"),c.data(EXPORT+"applyAnalysis",body).path("applicationId"));assertEquals(1,f.applicationPeer.connections.get());f.applicationPeer.mode=NotificationSmtpPeer.Mode.SUCCESS;
            JsonNode next=c.data(EXPORT+"retryNotification","{\"applicationId\":\""+first.path("applicationId").asText()+"\"}");assertEquals("SENT",next.path("notification").path("mailStatus").asText());assertNotEquals(first.path("notification").path("attemptId"),next.path("notification").path("attemptId"));assertEquals(2,f.applicationPeer.connections.get());assertEquals(1,f.applicationPeer.accepted.get());assertEquals(1,count(f,"export_application"));assertEquals(1,count(f,"ra_ai_export_request"));f.assertCurrentExportOwners();
        }
    }

    @Test void uncertainDataAndDroppedAckCannotDuplicateNotification()throws Exception{
        for(NotificationSmtpPeer.Mode mode:new NotificationSmtpPeer.Mode[]{NotificationSmtpPeer.Mode.DATA_SILENT,NotificationSmtpPeer.Mode.ACK_DROP})try(WebLoopbackApiFixture f=new WebLoopbackApiFixture()){
            Client c=start(f);f.applicationPeer.mode=mode;String body=application(c.data(AI+"cohort","{}").path("analysisId").asText());JsonNode first=c.data(EXPORT+"applyAnalysis",body);assertEquals("UNKNOWN",first.path("notification").path("mailStatus").asText());assertFalse(first.path("notification").path("retryable").asBoolean());assertTrue(first.path("notification").path("smtpAccepted").isNull());assertEquals(0,f.applicationPeer.waiting.getCount());assertEquals(1,f.applicationPeer.dataCommands.get());assertEquals(0,f.applicationPeer.accepted.get());assertEquals(mode==NotificationSmtpPeer.Mode.ACK_DROP?1:0,f.applicationPeer.messages.size());f.assertCurrentExportOwners();
            assertEquals(first.path("notification"),c.data(EXPORT+"applyAnalysis",body).path("notification"));JsonNode retried=c.data(EXPORT+"retryNotification","{\"applicationId\":\""+first.path("applicationId").asText()+"\"}");assertEquals(first.path("notification"),retried.path("notification"));assertEquals(1,f.applicationPeer.connections.get());assertEquals(1,count(f,"export_application"));assertEquals(1,count(f,"ra_ai_export_request"));f.assertCurrentExportOwners();
        }
    }

    @Test void retainedReadAndSourceFailureCannotPublishFileOrEmptySuccess()throws Exception{
        try(WebLoopbackApiFixture f=new WebLoopbackApiFixture()){
            Client c=start(f);String id=c.data(AI+"cohort","{\"filters\":{\"ids\":[\"2\"]}}").path("analysisId").asText();int rows=count(f,"ra_ai_analysis_query_session");
            f.observed.failRunReadSql=true;denied(c.post(EXPORT+"xlsx",request(id,"[\"das28Current\"]")),503,"SERVICE_UNAVAILABLE");assertEquals(1,f.observed.mysqlFailures);assertEquals(0,count(f,"ra_ai_export_audit"));assertEquals(rows,count(f,"ra_ai_analysis_query_session"));f.observed.failRunReadSql=false;
            f.observed.failSecondSql=true;denied(c.post(AI+"cohort","{}"),503,"SERVICE_UNAVAILABLE");assertEquals(rows,count(f,"ra_ai_analysis_query_session"));assertEquals(0,count(f,"ra_ai_export_audit"));f.observed.failSecondSql=false;
            assertEquals(0,f.applicationPeer.connections.get());assertEquals(0,f.observed.active.get());assertEquals(f.observed.borrowed,f.observed.returned);
        }
    }

}
