package com.wenwen.ai.cohort;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
class PrivateCohortHttpTest extends PrivateCohortHttpFixture {
    static final String RID="00000000-0000-0000-0000-000000000001";
    static final String FILTERS="{\"studyCode\":\"RA\",\"at\":\"now\",\"act\":\"target\",\"ids\":[\"1\",\"2\",\"3\",\"7\",\"999\"],\"sex\":null,\"age\":null,\"sero\":null,\"cm\":null,\"tx\":null,\"data\":null}";
    static final String SAVE="{\"name\":\"  Trial  \",\"filters\":{\"act\":\"target\",\"ids\":[\"1\",\"3\",\"7\",\"2\",\"1\",\"999\"]},\"requestId\":\""+RID+"\"}";
    @Test void saveStoresOnlyImmutableDefinitionAndReceipt() throws Exception {
        observed.reset();
        privateObserved.reset();
        String oldDigest=sourceDigest();
        MvcResult response=http.perform(post("/api/ra/cohort/save").contentType("application/json").content(SAVE)).andReturn();
        assertEquals(200,response.getResponse().getStatus(),response.getResponse().getContentAsString());
        JsonNode envelope=json.readTree(response.getResponse().getContentAsByteArray()),data=envelope.path("data");
        assertTrue(envelope.path("success").asBoolean());
        assertEquals("200",envelope.path("code").asText());
        String id=data.path("cohortId").asText();
        assertTrue(id.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}"));
        assertEquals(1,data.path("version").asLong());
        assertEquals(response.getResponse().getHeader("X-Trace-Id"),data.path("meta").path("traceId").asText());
        assertEquals(0,observed.active.get());
        assertEquals(observed.borrowed,observed.returned);
        assertEquals(0,privateObserved.active.get());
        assertEquals(0,privateObserved.clinicalReads.get());
        assertEquals(oldDigest,sourceDigest());
        try(Connection c=raw.getConnection();
        Statement s=c.createStatement()) {
            try(ResultSet r=s.executeQuery("SELECT id,owner_doctor_id,name,note,version,filters_version,filters_utf8,filters_sha256 FROM ra_private_cohort_definition")) {
                assertTrue(r.next());
                assertEquals(id,r.getString(1));
                assertEquals(101,r.getLong(2));
                assertEquals("Trial",r.getString(3));
                assertNull(r.getString(4));
                assertEquals(1,r.getLong(5));
                assertEquals(1,r.getInt(6));
                assertArrayEquals(FILTERS.getBytes(StandardCharsets.UTF_8),r.getBytes(7));
                assertEquals(sha(FILTERS.getBytes(StandardCharsets.UTF_8)),r.getString(8));
                assertFalse(r.next());
            }
            try(ResultSet r=s.executeQuery("SELECT cohort_id,result_version,request_id FROM ra_private_cohort_command")){
                assertTrue(r.next());
                assertEquals(id,r.getString(1));
                assertEquals(1,r.getLong(2));
                assertEquals(RID,r.getString(3));
                assertFalse(r.next());
            }
            try(ResultSet r=s.executeQuery("SELECT COUNT(*) FROM ra_ai_analysis_run")){
                r.next();
                assertEquals(0,r.getInt(1));
            }
        }
    }
    JsonNode privateSuccess(String route,String body) throws Exception {
        MvcResult response=http.perform(post("/api/ra/cohort/"+route).contentType("application/json").content(body)).andReturn();
        assertEquals(200,response.getResponse().getStatus(),response.getResponse().getContentAsString());
        JsonNode envelope=json.readTree(response.getResponse().getContentAsByteArray());
        assertTrue(envelope.path("success").asBoolean());
        assertEquals("200",envelope.path("code").asText());
        assertEquals(response.getResponse().getHeader("X-Trace-Id"),envelope.path("data").path("meta").path("traceId").asText());
        assertEquals(0,observed.active.get());
        assertEquals(observed.borrowed,observed.returned);
        assertEquals(0,privateObserved.active.get());
        assertEquals(privateObserved.borrowed.get(),privateObserved.returned.get());
        return envelope.path("data");
    }
    @Test void openConsumesCurrentSourceAndCreatesFreshCompleteAnalysis() throws Exception {
        String id=privateSuccess("save",SAVE).path("cohortId").asText();
        observed.reset();
        JsonNode data=privateSuccess("open","{\"cohortId\":\""+id+"\"}"),a=data.path("analysis");
        assertEquals(2,a.path("n").asInt());
        assertEquals(5,a.path("studyTotal").asInt());
        assertEquals(5,a.path("submittedUniqueIdsN").asInt());
        assertEquals(3,a.path("effectiveIdsN").asInt());
        assertEquals(java.util.Arrays.asList("1","7"),ids(a));
        assertEquals(2.30,patientById(a,1).path("das28At").asDouble());
        assertEquals(0,patientById(a,7).path("das28At").asDouble());
        assertEquals(1,a.path("stats").path("targetRate").asDouble());
        assertEquals(2,a.path("activity").path("evalN").asInt());
        assertEquals(1,a.path("activity").path("current").get(0).path("count").asInt());
        assertEquals(1,a.path("activity").path("current").get(1).path("count").asInt());
        assertEquals(0,a.path("activity").path("current").get(2).path("count").asInt());
        assertEquals(0,a.path("activity").path("current").get(3).path("count").asInt());
        assertEquals(7,a.path("metricMeta").size());
        assertEquals(4,a.path("activity").path("current").size());
        assertEquals(3,a.path("lines").path("groups").size());
        assertTrue(a.has("fm"));
        assertTrue(a.has("byTx"));
        assertEquals("P03_RESULT",a.path("meta").path("completion").asText());
        assertEquals(data.path("meta").path("traceId"),a.path("meta").path("traceId"));
        assertEquals(json.readTree(FILTERS),data.path("cohort").path("filters"));
        assertTrue(data.path("cohort").path("currentN").isNull());
        assertNotEquals(a.path("analysisId"),privateSuccess("open","{\"cohortId\":\""+id+"\"}").path("analysis").path("analysisId"));
    }
    @Test void reopenReflectsNewVisitsPhysicalDeletionAndRevocation() throws Exception {
        String id=privateSuccess("save",SAVE).path("cohortId").asText();
        String body="{\"cohortId\":\""+id+"\"}";
        JsonNode old=privateSuccess("open",body).path("analysis");
        String run=old.path("analysisId").asText();
        String oldPayload=scalar("SELECT HEX(payload) FROM ra_ai_analysis_run WHERE id='"+run+"'");
        String definition=scalar("SELECT HEX(filters_utf8) FROM ra_private_cohort_definition WHERE id='"+id+"'");
        visit(31,3,3,"{\"result\":{\"crpScore\":2.60}}","2026-10-07",null,null);
        observed.reset();
        JsonNode next=privateSuccess("open",body).path("analysis");
        assertEquals(3,next.path("n").asInt());
        assertEquals(java.util.Arrays.asList("3","1","7"),ids(next));
        assertTrue(observed.selects>=6);
        assertNotEquals(old.path("analysisId"),next.path("analysisId"));
        sql("DELETE FROM patient_basic_info WHERE id=1");
        next=privateSuccess("open",body).path("analysis");
        assertEquals(4,next.path("studyTotal").asInt());
        assertEquals(java.util.Arrays.asList("3","7"),ids(next));
        sql("DELETE FROM patient_relation_doctor WHERE doctor_id=101 AND patient_id=3");
        next=privateSuccess("open",body).path("analysis");
        assertEquals(3,next.path("studyTotal").asInt());
        assertEquals(java.util.Arrays.asList("7"),ids(next));
        assertEquals(oldPayload,scalar("SELECT HEX(payload) FROM ra_ai_analysis_run WHERE id='"+run+"'"));
        assertEquals(definition,scalar("SELECT HEX(filters_utf8) FROM ra_private_cohort_definition WHERE id='"+id+"'"));
    }
    @Test void reopenFifteenExplicitIdsUsesOriginalStableNextPage() throws Exception {
        java.util.List<String> submitted=new java.util.ArrayList<>();
        for(int n=115;n>=101;n--){
            submitted.add(Integer.toString(n));
            sql("INSERT INTO patient_basic_info(id,name) VALUES("+n+",'synthetic-page')");
            sql("INSERT INTO patient_relation_doctor(doctor_id,patient_id,research_type,miss) VALUES(101,"+n+",0,0)");
            visit(n*10,n,0,"{\"result\":{\"crpScore\":3.00}}","2026-10-07",null,null);
        }
        String id=privateSuccess("save",json.writeValueAsString(com.wenwen.vo.AiCohortVo.object("name","Page","filters",java.util.Collections.singletonMap("ids",submitted),"requestId",RID))).path("cohortId").asText();
        JsonNode a=privateSuccess("open","{\"cohortId\":\""+id+"\"}").path("analysis");
        assertEquals(15,a.path("n").asInt());
        assertEquals(20,a.path("studyTotal").asInt());
        assertEquals(java.util.Arrays.asList("101","102","103","104","105","106","107","108","109","110"),ids(a));
        MvcResult r=http.perform(post("/api/ra/ai/cohortPatients").contentType("application/json").content(json.writeValueAsBytes(com.wenwen.vo.AiCohortVo.object("analysisId",a.path("analysisId").asText(),"cursor",a.path("patients").path("nextCursor").asText())))).andReturn();
        assertEquals(200,r.getResponse().getStatus());
        JsonNode page=json.readTree(r.getResponse().getContentAsByteArray()).path("data");
        assertEquals(java.util.Arrays.asList("111","112","113","114","115"),ids(page));
        assertFalse(page.path("patients").path("hasMore").asBoolean());
        assertTrue(page.path("patients").path("nextCursor").isNull());
        assertEquals(0,observed.active.get());
        JsonNode f=json.readTree(new String(hexBytes(scalar("SELECT HEX(filters_utf8) FROM ra_private_cohort_definition WHERE id='"+id+"'")),StandardCharsets.UTF_8));
        assertEquals(15,f.path("ids").size());
        assertEquals("101",f.path("ids").get(0).asText());
        assertEquals("115",f.path("ids").get(14).asText());
        assertEquals(10,f.size());
    }
    static byte[] hexBytes(String hex){
        byte[] b=new byte[hex.length()/2];
        for(int i=0;i<b.length;i++)b[i]=(byte)Integer.parseInt(hex.substring(i*2,i*2+2),16);
        return b;
    }
    @Test void emptyAndNullIdsKeepOriginalDistinctCohortMeaning() throws Exception {
        String[] filters={
            "{}","{\"ids\":null}","{\"ids\":[]}","{\"ids\":[\"2\",\"999\"]}","{\"at\":\"6m\"}"
        };
        int[] expected={
            5,5,0,0,0
        };
        for(int i=0;i<filters.length;i++){
            String id=privateSuccess("save","{\"name\":\"Empty"+i+"\",\"filters\":"+filters[i]+",\"requestId\":\"00000000-0000-0000-0000-00000000000"+(i+1)+"\"}").path("cohortId").asText();
            JsonNode a=privateSuccess("open","{\"cohortId\":\""+id+"\"}").path("analysis");
            assertEquals(expected[i],a.path("n").asInt());
            assertEquals(5,a.path("studyTotal").asInt());
            if(i<2)assertTrue(a.path("submittedUniqueIdsN").isNull());
            if(i==2)assertEquals(0,a.path("submittedUniqueIdsN").asInt());
            if(i==4)assertEquals("6m",a.path("meta").path("at").asText());
        }
    }
    @Test void listReturnsOnlyOwnerMetadataWithoutClinicalQueries() throws Exception {
        String id=privateSuccess("save",SAVE).path("cohortId").asText();
        observed.reset();
        privateObserved.reset();
        observed.failSecondSql=true;
        JsonNode list=privateSuccess("list","{}");
        assertEquals(1,list.path("total").asLong());
        assertEquals(1,list.path("page").asLong());
        assertEquals(20,list.path("size").asInt());
        assertEquals(id,list.path("items").get(0).path("cohortId").asText());
        assertEquals(json.readTree(FILTERS),list.path("items").get(0).path("filters"));
        assertTrue(list.path("items").get(0).path("currentN").isNull());
        assertEquals(0,privateObserved.clinicalReads.get());
        assertEquals("0",scalar("SELECT COUNT(*) FROM ra_ai_analysis_run"));
        assertEquals(0,privateSuccess("list","{\"page\":2,\"size\":100}").path("items").size());
        context.getBean(FakePrincipalProvider.class).doctor=202;
        assertEquals(0,privateSuccess("list","{}").path("total").asLong());
    }
    @Test void listCountAndItemsShareStableRepeatableReadView() throws Exception {
        String a=privateSuccess("save",SAVE).path("cohortId").asText();
        String b=privateSuccess("save",SAVE.replace("Trial","B").replace(RID,"00000000-0000-0000-0000-000000000002")).path("cohortId").asText();
        String c=privateSuccess("save",SAVE.replace("Trial","C").replace(RID,"00000000-0000-0000-0000-000000000003")).path("cohortId").asText();
        String aa="00000000-0000-0000-0000-000000000010",bb="00000000-0000-0000-0000-000000000020",cc="00000000-0000-0000-0000-000000000030",dd="00000000-0000-0000-0000-000000000040";
        sql("UPDATE ra_private_cohort_definition SET id='"+aa+"',created_at_ms=1000 WHERE id='"+a+"'");
        sql("UPDATE ra_private_cohort_definition SET id='"+bb+"',created_at_ms=1000 WHERE id='"+b+"'");
        sql("UPDATE ra_private_cohort_definition SET id='"+cc+"',created_at_ms=2000 WHERE id='"+c+"'");
        assertEquals(java.util.Arrays.asList(bb),definitionIds(privateSuccess("list","{\"page\":2,\"size\":2}")));
        privateObserved.reset();
        privateObserved.afterCount=owner->{
            sql("INSERT INTO ra_private_cohort_definition(id,owner_doctor_id,study_code,name,note,version,filters_version,filters_utf8,filters_sha256,created_at_ms,updated_at_ms) SELECT '"+dd+"',owner_doctor_id,study_code,'D',note,version,filters_version,filters_utf8,filters_sha256,3000,3000 FROM ra_private_cohort_definition WHERE id='"+aa+"'");
        };
        JsonNode page=privateSuccess("list","{\"page\":1,\"size\":2}");
        assertEquals(3,page.path("total").asInt());
        assertEquals(java.util.Arrays.asList(cc,aa),definitionIds(page));
        assertEquals(2,privateObserved.privateReads.get());
        assertEquals(1,new java.util.HashSet<>(privateObserved.connections).size());
        for(Boolean value:privateObserved.autoCommits)assertFalse(value);
        page=privateSuccess("list","{\"size\":2}");
        assertEquals(4,page.path("total").asInt());
        assertEquals(java.util.Arrays.asList(dd,cc),definitionIds(page));
    }
    static java.util.List<String> definitionIds(JsonNode list){
        java.util.List<String> ids=new java.util.ArrayList<>();
        list.path("items").forEach(x->ids.add(x.path("cohortId").asText()));
        return ids;
    }
    @Test void renameChangesOnlyPresentMetadataWithVersionCas() throws Exception {
        String id=privateSuccess("save",SAVE.replace("\"filters\":","\"note\":\"keep\",\"filters\":")).path("cohortId").asText();
        java.util.List<String> before=filterState(id);
        JsonNode r=privateSuccess("rename",command(id,1,2,"\"name\":\"New\""));
        assertEquals(2,r.path("version").asLong());
        assertEquals("keep",privateSuccess("list","{}").path("items").get(0).path("note").asText());
        r=privateSuccess("rename",command(id,2,3,"\"note\":\"new-note\""));
        assertEquals(3,r.path("version").asLong());
        assertEquals("New",privateSuccess("list","{}").path("items").get(0).path("name").asText());
        r=privateSuccess("rename",command(id,3,4,"\"note\":null"));
        assertEquals(4,r.path("version").asLong());
        assertTrue(privateSuccess("list","{}").path("items").get(0).path("note").isNull());
        privateError("rename",command(id,1,5,"\"name\":\"Stale\""),409,"VERSION_CONFLICT");
        privateError("rename",command(id,4,5,""),400,"INVALID_REQUEST");
        privateError("rename",command(id,4,5,"\"name\":null"),400,"INVALID_REQUEST");
        privateError("rename",command(id,4,5,"\"filters\":{}"),400,"INVALID_REQUEST");
        assertEquals("4",scalar("SELECT version FROM ra_private_cohort_definition WHERE id='"+id+"'"));
        assertEquals("4",scalar("SELECT COUNT(*) FROM ra_private_cohort_command"));
        assertEquals(before,filterState(id));
    }
    String command(String id,long version,int request,String changes){
        return "{\"cohortId\":\""+id+"\",\"version\":"+version+",\"requestId\":\"00000000-0000-0000-0000-"+String.format("%012d",request)+"\""+(changes.isEmpty()?"":","+changes)+"}";
    }
    MvcResult privateRequest(String route,String body) throws Exception{
        return http.perform(post("/api/ra/cohort/"+route).contentType("application/json").content(body)).andReturn();
    }
    void privateError(String route,String body,int status,String code)throws Exception{
        MvcResult r=privateRequest(route,body);
        assertEquals(status,r.getResponse().getStatus(),r.getResponse().getContentAsString());
        JsonNode e=json.readTree(r.getResponse().getContentAsByteArray());
        assertEquals(code,e.path("code").asText());
        assertFalse(e.path("success").asBoolean());
        assertTrue(e.path("data").isNull());
        assertNotNull(r.getResponse().getHeader("X-Trace-Id"));
        assertEquals(0,observed.active.get());
        assertEquals(0,privateObserved.active.get());
        assertEquals(privateObserved.borrowed.get(),privateObserved.returned.get());
    }
    @Test void deleteIsLogicalAndReleasesNameWithoutRevivingOldResource() throws Exception {
        String id=privateSuccess("save",SAVE).path("cohortId").asText();
        assertEquals(2,privateSuccess("delete",command(id,1,2,"")).path("version").asLong());
        assertEquals(0,privateSuccess("list","{}").path("total").asInt());
        privateError("open","{\"cohortId\":\""+id+"\"}",404,"RESOURCE_NOT_FOUND");
        privateError("rename",command(id,2,3,"\"name\":\"No\""),404,"RESOURCE_NOT_FOUND");
        assertEquals(2,privateSuccess("delete",command(id,1,4,"")).path("version").asLong());
        String next=privateSuccess("save",SAVE.replace(RID,"00000000-0000-0000-0000-000000000005")).path("cohortId").asText();
        assertNotEquals(id,next);
        assertEquals(2,privateSuccess("delete",command(next,1,6,"")).path("version").asLong());
        String third=privateSuccess("save",SAVE.replace(RID,"00000000-0000-0000-0000-000000000007")).path("cohortId").asText();
        assertNotEquals(next,third);
        assertEquals("2",scalar("SELECT COUNT(*) FROM ra_private_cohort_definition WHERE name='Trial' AND deleted_at_ms IS NOT NULL"));
        assertEquals("6",scalar("SELECT COUNT(*) FROM ra_private_cohort_command"));
        context.getBean(FakePrincipalProvider.class).doctor=202;
        privateError("delete",command(id,1,8,""),404,"RESOURCE_NOT_FOUND");
        privateError("delete",command("00000000-0000-0000-0000-000000000999",1,8,""),404,"RESOURCE_NOT_FOUND");
    }
    @Test void databaseActiveNamesAreBinaryAndMetadataLimitsAreCodePoints() throws Exception {
        privateSuccess("save",SAVE);
        privateError("save",SAVE.replace(RID,"00000000-0000-0000-0000-000000000002"),409,"NAME_CONFLICT");
        for(int i=0;i<3;i++)privateSuccess("save",SAVE.replace("  Trial  ",new String[]{
            "trial","é","e\u0301"
        }[i]).replace(RID,"00000000-0000-0000-0000-00000000000"+(i+3)));
        context.getBean(FakePrincipalProvider.class).doctor=202;
        privateSuccess("save",SAVE);
        context.getBean(FakePrincipalProvider.class).doctor=101;
        String emoji=new String(Character.toChars(0x1f680)),name=repeat(emoji,30),note=repeat(emoji,200);
        String body=json.writeValueAsString(com.wenwen.vo.AiCohortVo.object("name",name,"note",note,"filters",java.util.Collections.emptyMap(),"requestId","00000000-0000-0000-0000-000000000006"));
        privateSuccess("save",body);
        privateError("save",json.writeValueAsString(com.wenwen.vo.AiCohortVo.object("name",name+emoji,"note",note,"filters",java.util.Collections.emptyMap(),"requestId",RID)),400,"INVALID_REQUEST");
        privateError("save",body.replace(note,note+emoji),400,"INVALID_REQUEST");
        privateError("save",SAVE.replace("  Trial  ","   "),400,"INVALID_REQUEST");
        try{
            sql("INSERT INTO ra_private_cohort_definition(id,owner_doctor_id,study_code,name,note,version,filters_version,filters_utf8,filters_sha256,created_at_ms,updated_at_ms) SELECT '00000000-0000-0000-0000-000000000999',owner_doctor_id,study_code,name,note,version,filters_version,filters_utf8,filters_sha256,created_at_ms,updated_at_ms FROM ra_private_cohort_definition WHERE owner_doctor_id=101 AND CAST(name AS BINARY)=CAST('Trial' AS BINARY)");
            fail("数据库未保护活动名称");
        }catch(SQLException e){
            assertEquals(1062,e.getErrorCode());
            assertEquals("23000",e.getSQLState());
        }
        assertEquals("6",scalar("SELECT COUNT(*) FROM ra_private_cohort_definition"));
    }
    static String repeat(String s,int n){
        StringBuilder b=new StringBuilder();
        for(int i=0;i<n;i++)b.append(s);
        return b.toString();
    }
    @Test void everyRouteStrictlyRejectsInvalidInputBeforeAnySql() throws Exception {
        String id="00000000-0000-0000-0000-000000000100";
        String[] routes={
            "save","list","open","rename","delete"
        };
        String[] valid={
            SAVE,"{}","{\"cohortId\":\""+id+"\"}",command(id,1,2,"\"note\":null"),command(id,1,2,"")
        };
        for(int i=0;i<routes.length;i++){
            for(String bad:new String[]{
                valid[i].substring(0,valid[i].length()-1)+",\"unknown\":1}",valid[i]+" {}","[]","null","{\"unknown\":1,\"unknown\":2}","{\"x\":\"\\ud800\"}",i==1?"{\"page\":null}":"{}"
            }){
                observed.reset();
                privateObserved.reset();
                privateError(routes[i],bad,400,"INVALID_REQUEST");
                assertEquals(0,privateObserved.borrowed.get());
                assertEquals(0,observed.selects);
            }
            for(byte[] bad:new byte[][]{
                valid[i].getBytes(java.nio.charset.StandardCharsets.UTF_16),new byte[]{
                    '{','"','x','"',':','"',(byte)0xc3,0x28,'"','}'
                }
            }){
                observed.reset();
                privateObserved.reset();
                MvcResult r=http.perform(post("/api/ra/cohort/"+routes[i]).contentType("application/json").content(bad)).andReturn();
                assertEquals(400,r.getResponse().getStatus());
                assertEquals(0,privateObserved.borrowed.get());
            }
            observed.reset();
            privateObserved.reset();
            privateError(routes[i],repeat(" ",i==0?128*1024+1:4097),413,"LIMIT_EXCEEDED");
            assertEquals(i==0?128*1024+1:4097,bodyRead.bytes);
            assertEquals(0,privateObserved.borrowed.get());
        }
        for(String bad:new String[]{
            "{\"page\":0}","{\"page\":1.0}","{\"size\":101}","{\"page\":9223372036854775807,\"size\":100}","{\"page\":\"1\"}"
        })privateError("list",bad,400,"INVALID_REQUEST");
        for(String bad:new String[]{
            "1.0","1e0","\"1\"","0","9223372036854775808","true","null"
        })privateError("delete",command(id,1,2,"").replace("\"version\":1","\"version\":"+bad),400,"INVALID_REQUEST");
        privateError("save",SAVE.replace("\"target\"","\"bogus\""),400,"INVALID_FILTER");
        privateError("save",SAVE.replace("\"1\",\"3\"","1,\"3\""),400,"INVALID_FILTER");
        privateError("save",SAVE.replace("{\"act\":\"target\",\"ids\":[\"1\",\"3\",\"7\",\"2\",\"1\",\"999\"]}","null"),400,"INVALID_REQUEST");
        StringBuilder many=new StringBuilder("{\"name\":\"many\",\"requestId\":\""+RID+"\",\"filters\":{\"ids\":[");
        for(int n=0;n<5001;n++){
            if(n>0)many.append(',');
            many.append("\"1\"");
        }many.append("]}}");
        privateError("save",many.toString(),413,"LIMIT_EXCEEDED");
        for(String uuid:new String[]{
            id.toUpperCase().replace("000100","ABC100"),"1-1-1-1-1"," "+id,"1",null
        })privateError("open",json.writeValueAsString(java.util.Collections.singletonMap("cohortId",uuid)),400,"INVALID_REQUEST");
        PrivateCohortHttpFixture anonymous=new PrivateCohortHttpFixture(){
        };
        anonymous.buildContext(false);
        try{
            for(String route:routes){
                MvcResult r=anonymous.http.perform(post("/api/ra/cohort/"+route).contentType("application/json").content(repeat("X",128*1024+10))).andReturn();
                assertEquals(401,r.getResponse().getStatus());
                assertEquals(0,anonymous.bodyRead.bytes);
                assertEquals(0,anonymous.privateObserved.borrowed.get());
                assertEquals(0,anonymous.privateObserved.active.get());
            }
        }finally{
            anonymous.context.close();
        }
    }
    @Test void corruptDefinitionsNeverBecomeWholeUniverseOrEmptyList() throws Exception {
        String id=privateSuccess("save",SAVE).path("cohortId").asText(),open="{\"cohortId\":\""+id+"\"}";
        byte[] good=FILTERS.getBytes(StandardCharsets.UTF_8);
        java.util.List<byte[]> corrupt=new java.util.ArrayList<>();
        for(String text:new String[]{
            "null","[]","{\"studyCode\":\"RA\"}",FILTERS.replace("\"ids\":[\"1\",\"2\",\"3\",\"7\",\"999\"],",""),FILTERS.substring(0,FILTERS.length()-1)+",\"extra\":null}",FILTERS.replace("\"at\":\"now\"","\"at\":\"now\",\"at\":\"now\""),FILTERS+" {}"," "+FILTERS,FILTERS.replace("\"now\"","\"bad\""),FILTERS.replace("\"Trial\"","\"\\ud800\"")
        })corrupt.add(text.getBytes(StandardCharsets.UTF_8));
        corrupt.remove(corrupt.size()-1);
        corrupt.add(new byte[]{
            '{',(byte)0xc3,0x28,'}'
        });
        for(byte[] bytes:corrupt){
            corrupt(id,bytes,1,sha(bytes));
            observed.reset();
            privateObserved.reset();
            privateError("open",open,503,"SERVICE_UNAVAILABLE");
            privateError("list","{}",503,"SERVICE_UNAVAILABLE");
            assertEquals(0,privateObserved.clinicalReads.get());
            assertEquals("0",scalar("SELECT COUNT(*) FROM ra_ai_analysis_run"));
        }
        for(int version:new int[]{
            0,2
        }){
            corrupt(id,good,version,sha(good));
            privateError("open",open,503,"SERVICE_UNAVAILABLE");
        }
        corrupt(id,good,1,repeat("0",64));
        privateError("open",open,503,"SERVICE_UNAVAILABLE");
        corrupt(id,good,1,sha(good));
        assertEquals(2,privateSuccess("open",open).path("analysis").path("n").asInt());
        sql("UPDATE ra_private_cohort_definition SET version=0 WHERE id='"+id+"'");
        privateError("open",open,503,"SERVICE_UNAVAILABLE");
    }
    void corrupt(String id,byte[] bytes,int version,String hash)throws Exception{
        try(Connection c=raw.getConnection();
        PreparedStatement s=c.prepareStatement("UPDATE ra_private_cohort_definition SET filters_utf8=?,filters_version=?,filters_sha256=? WHERE id=?")){
            s.setBytes(1,bytes);
            s.setInt(2,version);
            s.setString(3,hash);
            s.setString(4,id);
            assertEquals(1,s.executeUpdate());
        }
    }
    @Test void successfulCommandsReplayDurableReceiptsBeforeCurrentResourceState() throws Exception {
        JsonNode save=privateSuccess("save",SAVE);
        String id=save.path("cohortId").asText();
        JsonNode replay=privateSuccess("save",SAVE.replace("[\"1\",\"3\",\"7\",\"2\",\"1\",\"999\"]","[\"999\",\"7\",\"3\",\"2\",\"1\"]"));
        assertEquals(save.path("cohortId"),replay.path("cohortId"));
        assertEquals(save.path("version"),replay.path("version"));
        assertNotEquals(save.path("meta").path("traceId"),replay.path("meta").path("traceId"));
        privateError("save",SAVE.replace("Trial","Other"),409,"IDEMPOTENCY_CONFLICT");
        String rename=command(id,1,2,"\"name\":\"Renamed\""),delete=command(id,2,3,"");
        JsonNode renamed=privateSuccess("rename",rename);
        assertEquals(2,renamed.path("version").asLong());
        assertEquals(renamed.path("version"),privateSuccess("rename",rename).path("version"));
        privateError("rename",rename.replace("\"name\":\"Renamed\"","\"name\":\"Renamed\",\"note\":null"),409,"IDEMPOTENCY_CONFLICT");
        JsonNode deleted=privateSuccess("delete",delete);
        assertEquals(3,deleted.path("version").asLong());
        assertEquals(deleted.path("version"),privateSuccess("delete",delete).path("version"));
        privateError("delete",delete.replace("\"version\":2","\"version\":1"),409,"IDEMPOTENCY_CONFLICT");
        replay=privateSuccess("save",SAVE);
        assertEquals(id,replay.path("cohortId").asText());
        assertEquals(1,replay.path("version").asLong());
        assertEquals(2,privateSuccess("rename",rename).path("version").asLong());
        assertEquals("3",scalar("SELECT COUNT(*) FROM ra_private_cohort_command"));
        assertEquals("1",scalar("SELECT COUNT(*) FROM ra_private_cohort_definition"));
        assertEquals("3",scalar("SELECT version FROM ra_private_cohort_definition"));
        assertEquals(save.path("meta").path("traceId").asText(),scalar("SELECT trace_id FROM ra_private_cohort_command WHERE action='SAVE'"));
        assertEquals(3,privateSuccess("delete",command(id,1,1,"")).path("version").asLong());
        context.getBean(FakePrincipalProvider.class).doctor=202;
        assertNotEquals(id,privateSuccess("save",SAVE).path("cohortId").asText());
        context.getBean(FakePrincipalProvider.class).doctor=101;
        context.close();
        buildContext(true);
        replay=privateSuccess("save",SAVE);
        assertEquals(id,replay.path("cohortId").asText());
        assertEquals(1,replay.path("version").asLong());
        assertEquals("2",scalar("SELECT COUNT(*) FROM ra_private_cohort_definition"));
        assertEquals("5",scalar("SELECT COUNT(*) FROM ra_private_cohort_command"));
    }
    @Test void independentContextsReplaySameConcurrentRequestAcrossActions() throws Exception {
        MvcResult[] results=race("save",SAVE,SAVE);
        JsonNode a=successfulResult(results[0]),b=successfulResult(results[1]);
        assertEquals(a.path("cohortId"),b.path("cohortId"));
        assertEquals(1,a.path("version").asLong());
        assertNotEquals(results[0].getResponse().getHeader("X-Trace-Id"),results[1].getResponse().getHeader("X-Trace-Id"));
        String id=a.path("cohortId").asText();
        assertEquals("1",scalar("SELECT COUNT(*) FROM ra_private_cohort_definition"));
        assertEquals("1",scalar("SELECT COUNT(*) FROM ra_private_cohort_command"));
        String rename=command(id,1,2,"\"name\":\"Changed\"");
        results=race("rename",rename,rename);
        assertEquals(2,successfulResult(results[0]).path("version").asLong());
        assertEquals(2,successfulResult(results[1]).path("version").asLong());
        assertEquals("2",scalar("SELECT version FROM ra_private_cohort_definition"));
        assertEquals("2",scalar("SELECT COUNT(*) FROM ra_private_cohort_command"));
        String delete=command(id,2,3,"");
        results=race("delete",delete,delete);
        assertEquals(3,successfulResult(results[0]).path("version").asLong());
        assertEquals(3,successfulResult(results[1]).path("version").asLong());
        assertEquals("3",scalar("SELECT version FROM ra_private_cohort_definition"));
        assertEquals("3",scalar("SELECT COUNT(*) FROM ra_private_cohort_command"));
    }
    JsonNode successfulResult(MvcResult r)throws Exception{
        assertEquals(200,r.getResponse().getStatus(),r.getResponse().getContentAsString());
        JsonNode e=json.readTree(r.getResponse().getContentAsByteArray());
        assertTrue(e.path("success").asBoolean());
        assertEquals(r.getResponse().getHeader("X-Trace-Id"),e.path("data").path("meta").path("traceId").asText());
        return e.path("data");
    }
    MvcResult[] race(String route,String left,String right)throws Exception{
        PrivateCohortHttpFixture peer=new PrivateCohortHttpFixture(){
        };
        peer.buildContext(true);
        java.util.concurrent.ExecutorService workers=java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CountDownLatch ready=new java.util.concurrent.CountDownLatch(2),go=new java.util.concurrent.CountDownLatch(1);
        Runnable barrier=()->{
            ready.countDown();
            try{
                if(!go.await(10,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("命令入口屏障超时");
            }catch(InterruptedException e){
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        };
        privateObserved.reset();
        peer.privateObserved.reset();
        privateObserved.beforeCommand=barrier;
        peer.privateObserved.beforeCommand=barrier;
        try{
            java.util.concurrent.Future<MvcResult> first=workers.submit(()->privateRequest(route,left)),second=workers.submit(()->peer.http.perform(post("/api/ra/cohort/"+route).contentType("application/json").content(right)).andReturn());
            assertTrue(ready.await(10,java.util.concurrent.TimeUnit.SECONDS),"两个独立context必须实际进入生产SQL边界");
            assertFalse(privateObserved.sqls.isEmpty());
            assertFalse(peer.privateObserved.sqls.isEmpty());
            go.countDown();
            MvcResult[] results={
                first.get(20,java.util.concurrent.TimeUnit.SECONDS),second.get(20,java.util.concurrent.TimeUnit.SECONDS)
            };
            assertEquals(0,privateObserved.active.get());
            assertEquals(0,peer.privateObserved.active.get());
            assertEquals(privateObserved.borrowed.get(),privateObserved.returned.get());
            assertEquals(peer.privateObserved.borrowed.get(),peer.privateObserved.returned.get());
            return results;
        }
        finally{
            go.countDown();
            workers.shutdownNow();
            peer.context.close();
        }
    }
    @Test void independentContextsRejectDifferentParametersForSameConcurrentRequest() throws Exception {
        MvcResult[] results=race("save",SAVE,SAVE.replace("Trial","Different"));
        assertOneConflict(results,"IDEMPOTENCY_CONFLICT");
        String id=successfulResult(results[results[0].getResponse().getStatus()==200?0:1]).path("cohortId").asText();
        assertEquals("1",scalar("SELECT COUNT(*) FROM ra_private_cohort_definition"));
        assertEquals("1",scalar("SELECT COUNT(*) FROM ra_private_cohort_command"));
        results=race("rename",command(id,1,2,"\"name\":\"A\""),command(id,1,2,"\"name\":\"B\""));
        assertOneConflict(results,"IDEMPOTENCY_CONFLICT");
        assertEquals("2",scalar("SELECT version FROM ra_private_cohort_definition"));
        assertEquals("2",scalar("SELECT COUNT(*) FROM ra_private_cohort_command"));
        String other=privateSuccess("save",SAVE.replace("Trial","Other-delete").replace(RID,"00000000-0000-0000-0000-000000000007")).path("cohortId").asText();
        results=race("delete",command(id,2,3,""),command(other,1,3,""));
        assertOneConflict(results,"IDEMPOTENCY_CONFLICT");
        assertEquals("1",scalar("SELECT COUNT(*) FROM ra_private_cohort_definition WHERE deleted_at_ms IS NOT NULL"));
        assertEquals("1",scalar("SELECT COUNT(*) FROM ra_private_cohort_definition WHERE deleted_at_ms IS NULL"));
        assertEquals("4",scalar("SELECT COUNT(*) FROM ra_private_cohort_command"));
    }
    void assertOneConflict(MvcResult[] results,String code)throws Exception{
        int success=0,conflict=0;
        for(MvcResult r:results){
            if(r.getResponse().getStatus()==200){
                successfulResult(r);
                success++;
            }else{
                assertEquals(409,r.getResponse().getStatus(),r.getResponse().getContentAsString());
                assertEquals(code,json.readTree(r.getResponse().getContentAsByteArray()).path("code").asText());
                conflict++;
            }
        }assertEquals(1,success);
        assertEquals(1,conflict);
    }
    @Test void independentContextsEnforceNameUniquenessAndVersionCas() throws Exception {
        MvcResult[] results=race("save",SAVE,SAVE.replace(RID,"00000000-0000-0000-0000-000000000002"));
        assertOneConflict(results,"NAME_CONFLICT");
        String id=successfulResult(results[results[0].getResponse().getStatus()==200?0:1]).path("cohortId").asText();
        assertEquals("1",scalar("SELECT COUNT(*) FROM ra_private_cohort_definition"));
        assertEquals("1",scalar("SELECT COUNT(*) FROM ra_private_cohort_command"));
        results=race("rename",command(id,1,3,"\"name\":\"WinnerA\""),command(id,1,4,"\"name\":\"WinnerB\""));
        assertOneConflict(results,"VERSION_CONFLICT");
        assertEquals("2",scalar("SELECT version FROM ra_private_cohort_definition"));
        assertEquals("2",scalar("SELECT COUNT(*) FROM ra_private_cohort_command"));
        String name=privateSuccess("list","{}").path("items").get(0).path("name").asText();
        assertTrue(java.util.Arrays.asList("WinnerA","WinnerB").contains(name));
        assertEquals(3,privateSuccess("rename",command(id,2,5,"\"note\":\"after-race\"")).path("version").asLong());
    }
    @Test void realMysqlReadAndWriteFailuresRollbackBothTablesBeforeExit() throws Exception {
        String id=privateSuccess("save",SAVE).path("cohortId").asText(),open="{\"cohortId\":\""+id+"\"}";
        String before=privateDigest(),source=sourceDigest();
        String[] routes={
            "open","save","save","save","rename","delete"
        };
        String[] bodies={
            open,SAVE.replace("Trial","SQL-failed").replace(RID,"00000000-0000-0000-0000-000000000002"),SAVE.replace("Trial","SQL-failed").replace(RID,"00000000-0000-0000-0000-000000000002"),SAVE.replace("Trial","SQL-failed").replace(RID,"00000000-0000-0000-0000-000000000002"),command(id,1,2,"\"name\":\"SQL-failed\""),command(id,1,2,"")
        };
        String[] faults={
            "FROM ra_private_cohort_definition","FROM ra_private_cohort_command","INSERT INTO ra_private_cohort_definition","INSERT INTO ra_private_cohort_command","INSERT INTO ra_private_cohort_command","INSERT INTO ra_private_cohort_command"
        };
        for(int i=0;i<routes.length;i++){
            observed.reset();
            privateObserved.reset();
            privateObserved.failSqlContains=faults[i];
            privateError(routes[i],bodies[i],503,"SERVICE_UNAVAILABLE");
            assertTrue(privateObserved.mysqlFailures.get()>0);
            assertEquals("42S02",privateObserved.lastSqlState);
            assertEquals(1146,privateObserved.lastMysqlError);
            if(i>=3){
                assertEquals(1,privateObserved.definitionWrites.get());
                assertEquals(1,privateObserved.rollbacks.get());
                assertEquals(0,privateObserved.receiptWrites.get());
            }assertEquals(0,privateObserved.clinicalReads.get());
            assertEquals(before,privateDigest());
            assertEquals(source,sourceDigest());
            System.out.println("P04a real MySQL "+routes[i]+" SQLSTATE="+privateObserved.lastSqlState+" error="+privateObserved.lastMysqlError+" definitionWrites="+privateObserved.definitionWrites.get());
        }
        privateObserved.reset();
        assertEquals(2,privateSuccess("open",open).path("analysis").path("n").asInt());
    }
    String privateDigest()throws Exception{
        StringBuilder b=new StringBuilder();
        for(String table:java.util.Arrays.asList("ra_private_cohort_definition","ra_private_cohort_command")){
            try(Connection c=raw.getConnection();
            Statement s=c.createStatement();
            ResultSet r=s.executeQuery("SELECT * FROM "+table+" ORDER BY "+(table.endsWith("definition")?"id":"owner_doctor_id,action,request_id"))){
                int n=r.getMetaData().getColumnCount();
                while(r.next()){
                    for(int i=1;i<=n;i++){
                        byte[] v=r.getBytes(i);
                        b.append(v==null?"NULL":sha(v)).append('|');
                    }b.append('\n');
                }
            }
        }return sha(b.toString().getBytes(StandardCharsets.UTF_8));
    }
    @Test void completeDefinitionAndReceiptRollBackTogetherAfterLastWrite() throws Exception {
        String id=privateSuccess("save",SAVE).path("cohortId").asText(),before=privateDigest(),source=sourceDigest();
        String[] routes={
            "save","rename","delete"
        };
        String[] bodies={
            SAVE.replace("Trial","After-write").replace(RID,"00000000-0000-0000-0000-000000000002"),command(id,1,2,"\"name\":\"After-write\""),command(id,1,2,"")
        };
        for(int i=0;i<routes.length;i++){
            final String action=routes[i].toUpperCase(java.util.Locale.ROOT);
            final String[] ownerState={
                null
            };
            observed.reset();
            privateObserved.reset();
            privateObserved.afterCompleteReceipt=owner->{
                ownerState[0]=ownerCompleteState(owner,action,"00000000-0000-0000-0000-000000000002");
                throw new IllegalStateException("controlled P04a after complete receipt");
            };
            privateError(routes[i],bodies[i],503,"SERVICE_UNAVAILABLE");
            assertEquals(before,privateDigest());
            assertEquals(source,sourceDigest());
            assertNotNull(ownerState[0]);
            assertEquals(new String[]{
                "SAVE|receiptVersion=1|definitionVersion=1|deleted=false|name=After-write","RENAME|receiptVersion=2|definitionVersion=2|deleted=false|name=After-write","DELETE|receiptVersion=2|definitionVersion=2|deleted=true|name=Trial"
            }[i],ownerState[0]);
            assertEquals(1,new java.util.HashSet<>(privateObserved.connections).size());
            for(Boolean auto:privateObserved.autoCommits)assertFalse(auto);
            assertEquals(1,privateObserved.definitionWrites.get());
            assertEquals(1,privateObserved.receiptWrites.get());
            assertEquals(1,privateObserved.rollbacks.get());
            assertEquals(0,privateObserved.commits.get());
            System.out.println("P04a precommit "+ownerState[0]);
        }
        privateObserved.reset();
        assertEquals(2,privateSuccess("open","{\"cohortId\":\""+id+"\"}").path("analysis").path("n").asInt());
    }
    String ownerCompleteState(Connection owner,String action,String request)throws Exception{
        try(PreparedStatement s=owner.prepareStatement("SELECT c.action,c.cohort_id,c.result_version,d.version,d.deleted_at_ms,d.name,c.protocol_version,c.trace_id,c.request_sha256 FROM ra_private_cohort_command c JOIN ra_private_cohort_definition d ON d.id=c.cohort_id WHERE c.owner_doctor_id=101 AND c.action=? AND c.request_id=?")){
            s.setString(1,action);
            s.setString(2,request);
            try(ResultSet r=s.executeQuery()){
                if(!r.next())throw new IllegalStateException("完整业务状态尚未写入");
                String value=r.getString(1)+"|receiptVersion="+r.getLong(3)+"|definitionVersion="+r.getLong(4)+"|deleted="+(r.getObject(5)!=null)+"|name="+r.getString(6);
                if(r.getString(2)==null||r.getLong(3)<1||r.getInt(7)!=1||r.getString(8)==null||r.getString(9)==null)throw new IllegalStateException("不完整receipt");
                return value;
            }
        }
    }
    @Test void highestLossDeletePrecommitFailureKeepsOriginalUsableDefinition() throws Exception {
        String id=privateSuccess("save",SAVE).path("cohortId").asText(),open="{\"cohortId\":\""+id+"\"}";
        assertEquals(2,privateSuccess("open",open).path("analysis").path("n").asInt());
        String before=privateDigest(),source=sourceDigest();
        java.util.concurrent.CountDownLatch reached=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();
        final String[] ownerState={
            null
        };
        privateObserved.reset();
        observed.reset();
        privateObserved.afterCompleteReceipt=owner->{
            ownerState[0]=ownerCompleteState(owner,"DELETE","00000000-0000-0000-0000-000000000002");
            reached.countDown();
            if(!release.await(10,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("precommit屏障超时");
            throw new IllegalStateException("controlled highest-loss DELETE precommit");
        };
        try{
            java.util.concurrent.Future<MvcResult> future=worker.submit(()->privateRequest("delete",command(id,1,2,"")));
            assertTrue(reached.await(10,java.util.concurrent.TimeUnit.SECONDS),"必须实际到达完整两表业务状态已写入后");
            String during=scalar("SELECT CONCAT(version,'|',IF(deleted_at_ms IS NULL,'active','deleted')) FROM ra_private_cohort_definition WHERE id='"+id+"'");
            String duringReceipts=scalar("SELECT COUNT(*) FROM ra_private_cohort_command");
            boolean stillPending=!future.isDone();
            int heldActive=privateObserved.active.get();
            release.countDown();
            MvcResult r=future.get(20,java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(503,r.getResponse().getStatus());
            assertEquals("SERVICE_UNAVAILABLE",json.readTree(r.getResponse().getContentAsByteArray()).path("code").asText());
            // 首个业务状态断言在失败HTTP后、任何cleanup或retry前；反事实必须在这里区别永久隐藏资源。
            assertEquals("1|active",scalar("SELECT CONCAT(version,'|',IF(deleted_at_ms IS NULL,'active','deleted')) FROM ra_private_cohort_definition WHERE id='"+id+"'"));
            assertEquals(before,privateDigest());
            assertEquals(source,sourceDigest());
            assertEquals(0,privateObserved.active.get());
            assertEquals(privateObserved.borrowed.get(),privateObserved.returned.get());
            assertEquals(0,observed.active.get());
            assertEquals(1,privateObserved.definitionWrites.get());
            assertEquals(1,privateObserved.receiptWrites.get());
            assertEquals(1,privateObserved.rollbacks.get());
            assertEquals(0,privateObserved.commits.get());
            assertEquals("DELETE|receiptVersion=2|definitionVersion=2|deleted=true|name=Trial",ownerState[0]);
            assertEquals("1|active",during);
            assertEquals("1",duringReceipts);
            assertTrue(stillPending);
            assertEquals(1,heldActive);
            System.out.println("P04a highest-loss owner="+ownerState[0]+" independent="+during+" receipts="+duringReceipts+" exitActive="+privateObserved.active.get());
            privateObserved.reset();
            assertEquals(2,privateSuccess("open",open).path("analysis").path("n").asInt());
            assertEquals(2,privateSuccess("delete",command(id,1,2,"")).path("version").asLong());
        }finally{
            release.countDown();
            worker.shutdownNow();
        }
    }
    @Test void missingSigningKeyDoesNotBlockDefinitionCommandsAndOpenOwnsSeparateExits() throws Exception {
        context.close();
        syntheticCursorKey=null;
        buildContext(true);
        String id=privateSuccess("save",SAVE).path("cohortId").asText(),open="{\"cohortId\":\""+id+"\"}";
        privateSuccess("list","{}");
        privateObserved.reset();
        observed.reset();
        privateError("open",open,503,"SERVICE_UNAVAILABLE");
        assertEquals(0,privateObserved.clinicalReads.get());
        assertEquals("0",scalar("SELECT COUNT(*) FROM ra_ai_analysis_run"));
        assertEquals(2,privateSuccess("rename",command(id,1,2,"\"note\":\"no-key\"")).path("version").asLong());
        MvcResult resolver=http.perform(post("/api/ra/ai/resolveEntry").contentType("application/json").content("{\"source\":\"SIMILAR\",\"indexPatientId\":\"1\"}")).andReturn();
        assertEquals(200,resolver.getResponse().getStatus());
        assertEquals(0,privateObserved.active.get());
        assertEquals(3,privateSuccess("delete",command(id,2,3,"")).path("version").asLong());
        String active=privateSuccess("save",SAVE.replace("Trial","Active").replace(RID,"00000000-0000-0000-0000-000000000004")).path("cohortId").asText();
        context.close();
        syntheticCursorKey="MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
        buildContext(true);
        open="{\"cohortId\":\""+active+"\"}";
        assertEquals(2,privateSuccess("open",open).path("analysis").path("n").asInt());
        String before=privateDigest();
        privateObserved.reset();
        observed.reset();
        observed.failSecondSql=true;
        privateError("open",open,503,"SERVICE_UNAVAILABLE");
        assertEquals(1146,observed.lastMysqlError);
        assertEquals(1,privateObserved.maxActive.get());
        assertEquals(before,privateDigest());
        observed.reset();
        privateObserved.reset();
        observed.failInsertSql=true;
        privateError("open",open,503,"SERVICE_UNAVAILABLE");
        assertEquals(1146,observed.lastMysqlError);
        assertEquals(1,privateObserved.maxActive.get());
        assertEquals(before,privateDigest());
        assertEquals("1",scalar("SELECT COUNT(*) FROM ra_ai_analysis_run"));
        observed.reset();
        privateObserved.reset();
        assertEquals(2,privateSuccess("open",open).path("analysis").path("n").asInt());
        assertEquals(1,privateObserved.maxActive.get());
    }
    @Test void ownerResourcesStayPrivateAndPersistNoClinicalCanaries() throws Exception {
        String source=sourceDigest(),id=privateSuccess("save",SAVE).path("cohortId").asText();
        String definition=scalar("SELECT CAST(filters_utf8 AS CHAR CHARACTER SET utf8mb4) FROM ra_private_cohort_definition WHERE id='"+id+"'");
        for(String canary:new String[]{
            "synthetic-private-name-CANARY","synthetic-private-case-CANARY","110101198610070011","1986-10-07"
        }){
            assertFalse(definition.contains(canary));
            assertFalse(persistedPrivateText().contains(canary));
        }
        context.getBean(FakePrincipalProvider.class).doctor=202;
        observed.reset();
        privateObserved.reset();
        assertEquals(0,privateSuccess("list","{}").path("total").asInt());
        for(String target:new String[]{
            id,"00000000-0000-0000-0000-000000000999"
        }){
            privateError("open","{\"cohortId\":\""+target+"\"}",404,"RESOURCE_NOT_FOUND");
            privateError("rename",command(target,1,2,"\"name\":\"No\""),404,"RESOURCE_NOT_FOUND");
            privateError("delete",command(target,1,2,""),404,"RESOURCE_NOT_FOUND");
        }assertEquals(0,privateObserved.clinicalReads.get());
        assertEquals(0,privateObserved.definitionWrites.get());
        assertEquals(0,privateObserved.receiptWrites.get());
        privateError("save",SAVE.substring(0,SAVE.length()-1)+",\"doctorId\":\"101\"}",400,"INVALID_REQUEST");
        assertEquals(source,sourceDigest());
        assertEquals("1",scalar("SELECT COUNT(*) FROM ra_private_cohort_definition"));
        assertEquals("1",scalar("SELECT COUNT(*) FROM ra_private_cohort_command"));
    }
    String persistedPrivateText()throws Exception{
        StringBuilder b=new StringBuilder();
        for(String table:java.util.Arrays.asList("ra_private_cohort_definition","ra_private_cohort_command")){
            try(Connection c=raw.getConnection();
            Statement s=c.createStatement();
            ResultSet r=s.executeQuery("SELECT * FROM "+table)){
                while(r.next())for(int i=1;i<=r.getMetaData().getColumnCount();i++)b.append(r.getString(i)).append('|');
            }
        }return b.toString();
    }
    @Test void illegalPersistedMetadataAndReceiptsFailClosedWithoutMutations() throws Exception {
        String id=privateSuccess("save",SAVE).path("cohortId").asText(),open="{\"cohortId\":\""+id+"\"}",originalHash=scalar("SELECT request_sha256 FROM ra_private_cohort_command WHERE action='SAVE'"),originalTrace=scalar("SELECT trace_id FROM ra_private_cohort_command WHERE action='SAVE'");
        for(String change:new String[]{
            "result_version=0","protocol_version=2","cohort_id='bad'","trace_id='bad'","request_sha256='bad'"
        }){
            sql("UPDATE ra_private_cohort_command SET "+change+" WHERE action='SAVE'");
            String before=privateDigest();
            privateObserved.reset();
            privateError("save",SAVE,503,"SERVICE_UNAVAILABLE");
            assertEquals(before,privateDigest());
            assertEquals(0,privateObserved.clinicalReads.get());
            assertEquals(0,privateObserved.definitionWrites.get());
            sql("UPDATE ra_private_cohort_command SET result_version=1,protocol_version=1,cohort_id='"+id+"',trace_id='"+originalTrace+"',request_sha256='"+originalHash+"' WHERE action='SAVE'");
        }
        sql("UPDATE ra_private_cohort_definition SET name='' WHERE id='"+id+"'");
        privateObserved.reset();
        privateError("list","{}",503,"SERVICE_UNAVAILABLE");
        privateError("open",open,503,"SERVICE_UNAVAILABLE");
        assertEquals(0,privateObserved.clinicalReads.get());
    }
    @Test void exactTrimAndVersionOverflowPreserveDurableState() throws Exception {
        String id=privateSuccess("save",SAVE).path("cohortId").asText();
        String nbsp="\u00a0";
        String other=privateSuccess("save",json.writeValueAsString(com.wenwen.vo.AiCohortVo.object("name",nbsp+"Trial"+nbsp,"note"," ","filters",java.util.Collections.emptyMap(),"requestId","00000000-0000-0000-0000-000000000002"))).path("cohortId").asText();
        assertNotEquals(id,other);
        assertEquals(nbsp+"Trial"+nbsp,scalar("SELECT name FROM ra_private_cohort_definition WHERE id='"+other+"'"));
        sql("UPDATE ra_private_cohort_definition SET version=9223372036854775807 WHERE id='"+id+"'");
        String before=privateDigest();
        privateError("rename",command(id,Long.MAX_VALUE,3,"\"note\":null"),503,"SERVICE_UNAVAILABLE");
        assertEquals(before,privateDigest());
        privateError("delete",command(id,Long.MAX_VALUE,3,""),503,"SERVICE_UNAVAILABLE");
        assertEquals(before,privateDigest());
        observed.reset();
        privateObserved.reset();
        privateError("save",SAVE.replace("\"act\":\"target\"","\"act\":\"target\",\"act\":\"target\""),400,"INVALID_REQUEST");
        assertEquals(0,privateObserved.borrowed.get());
        for(String route:new String[]{
            "save","list","open","rename","delete"
        }){
            observed.reset();
            privateObserved.reset();
            privateError(route,"{\"unknown\":1}",400,"INVALID_REQUEST");
            assertEquals(0,privateObserved.borrowed.get());
        }
    }
    @Test void saveReceiptRejectsImpossiblePositiveVersionBeforeReplay() throws Exception {
        JsonNode original=privateSuccess("save",SAVE);
        String id=original.path("cohortId").asText();
        assertEquals(1,original.path("version").asLong());
        sql("UPDATE ra_private_cohort_command SET result_version=3 WHERE owner_doctor_id=101 AND action='SAVE' AND request_id='"+RID+"'");
        assertEquals("1",scalar("SELECT version FROM ra_private_cohort_definition WHERE id='"+id+"'"));
        assertEquals("3",scalar("SELECT result_version FROM ra_private_cohort_command WHERE owner_doctor_id=101 AND action='SAVE' AND request_id='"+RID+"'"));
        String before=privateDigest(),source=sourceDigest();
        observed.reset();privateObserved.reset();
        privateError("save",SAVE,503,"SERVICE_UNAVAILABLE");
        assertEquals(before,privateDigest());assertEquals(source,sourceDigest());
        assertEquals("1",scalar("SELECT COUNT(*) FROM ra_private_cohort_definition"));
        assertEquals("1",scalar("SELECT COUNT(*) FROM ra_private_cohort_command"));
        assertEquals(0,privateObserved.definitionWrites.get());assertEquals(0,privateObserved.receiptWrites.get());assertEquals(0,privateObserved.clinicalReads.get());
        // 先证明拒绝后状态/资源退出，再由独立fixture恢复合法回执；不让恢复成为产品guard。
        sql("UPDATE ra_private_cohort_command SET result_version=1 WHERE owner_doctor_id=101 AND action='SAVE' AND request_id='"+RID+"'");
        JsonNode replay=privateSuccess("save",SAVE);
        assertEquals(id,replay.path("cohortId").asText());assertEquals(1,replay.path("version").asLong());
        assertNotEquals(original.path("meta").path("traceId"),replay.path("meta").path("traceId"));
        JsonNode renamed=privateSuccess("rename",command(id,1,2,"\"note\":\"valid-follow-up\""));
        assertEquals(2,renamed.path("version").asLong());assertEquals(2,privateSuccess("rename",command(id,1,2,"\"note\":\"valid-follow-up\"")).path("version").asLong());
        JsonNode deleted=privateSuccess("delete",command(id,2,3,""));
        assertEquals(3,deleted.path("version").asLong());assertEquals(3,privateSuccess("delete",command(id,2,3,"")).path("version").asLong());
        replay=privateSuccess("save",SAVE);assertEquals(id,replay.path("cohortId").asText());assertEquals(1,replay.path("version").asLong());
        assertEquals("1",scalar("SELECT COUNT(*) FROM ra_private_cohort_definition"));assertEquals("3",scalar("SELECT COUNT(*) FROM ra_private_cohort_command"));
    }
    @Test void metadataAndDeleteReceiptsRejectImpossibleVersionOne() throws Exception {
        String id=privateSuccess("save",SAVE).path("cohortId").asText();
        String rename=command(id,1,2,"\"note\":\"minima\""),delete=command(id,2,3,"");
        assertEquals(2,privateSuccess("rename",rename).path("version").asLong());assertEquals(3,privateSuccess("delete",delete).path("version").asLong());
        sql("UPDATE ra_private_cohort_command SET result_version=1 WHERE owner_doctor_id=101 AND action IN ('RENAME','DELETE')");
        assertEquals("2",scalar("SELECT COUNT(*) FROM ra_private_cohort_command WHERE action IN ('RENAME','DELETE') AND result_version=1"));
        assertEquals("3",scalar("SELECT version FROM ra_private_cohort_definition WHERE id='"+id+"'"));
        String before=privateDigest(),source=sourceDigest();MvcResult[] replies=new MvcResult[2];String[] routes={"rename","delete"},bodies={rename,delete};
        for(int i=0;i<routes.length;i++){
            observed.reset();privateObserved.reset();replies[i]=privateRequest(routes[i],bodies[i]);
            assertEquals(0,privateObserved.active.get());assertEquals(privateObserved.borrowed.get(),privateObserved.returned.get());assertEquals(0,observed.active.get());
            assertEquals(0,privateObserved.definitionWrites.get());assertEquals(0,privateObserved.receiptWrites.get());assertEquals(0,privateObserved.clinicalReads.get());
            assertEquals(before,privateDigest());assertEquals(source,sourceDigest());
        }
        // 两个真实HTTP都已执行再断言共同版本下界；RED不会只静态推断第二动作。
        System.out.println("P04a action minima actual renameHTTP="+replies[0].getResponse().getStatus()+" deleteHTTP="+replies[1].getResponse().getStatus());
        assertAll("RENAME/DELETE successful receipt minimum version2",()->assertEquals(503,replies[0].getResponse().getStatus()),()->assertEquals(503,replies[1].getResponse().getStatus()));
        for(MvcResult r:replies){JsonNode e=json.readTree(r.getResponse().getContentAsByteArray());assertFalse(e.path("success").asBoolean());assertTrue(e.path("data").isNull());assertEquals("SERVICE_UNAVAILABLE",e.path("code").asText());assertNotNull(r.getResponse().getHeader("X-Trace-Id"));}
        sql("UPDATE ra_private_cohort_command SET result_version=2 WHERE action='RENAME'");sql("UPDATE ra_private_cohort_command SET result_version=3 WHERE action='DELETE'");
        assertEquals(2,privateSuccess("rename",rename).path("version").asLong());assertEquals(3,privateSuccess("delete",delete).path("version").asLong());
        assertEquals(1,privateSuccess("save",SAVE).path("version").asLong());assertEquals("1",scalar("SELECT COUNT(*) FROM ra_private_cohort_definition"));assertEquals("3",scalar("SELECT COUNT(*) FROM ra_private_cohort_command"));
    }
    @Test void firstDeleteResultUsesSameReceiptValidityBeforeCommit() throws Exception {
        String id=privateSuccess("save",SAVE).path("cohortId").asText();
        String originalDelete=command(id,1,2,""),newDelete=command(id,1,3,"");
        assertEquals(2,privateSuccess("delete",originalDelete).path("version").asLong());
        sql("UPDATE ra_private_cohort_definition SET version=1 WHERE id='"+id+"' AND deleted_at_ms IS NOT NULL");
        assertEquals("1|deleted",scalar("SELECT CONCAT(version,'|',IF(deleted_at_ms IS NULL,'active','deleted')) FROM ra_private_cohort_definition WHERE id='"+id+"'"));
        String before=privateDigest(),source=sourceDigest();observed.reset();privateObserved.reset();
        MvcResult response=privateRequest("delete",newDelete);
        String added=scalar("SELECT COUNT(*) FROM ra_private_cohort_command WHERE owner_doctor_id=101 AND action='DELETE' AND request_id='00000000-0000-0000-0000-000000000003'");
        System.out.println("P04a first-result actual HTTP="+response.getResponse().getStatus()+" newReceiptCount="+added);
        assertEquals(503,response.getResponse().getStatus(),response.getResponse().getContentAsString());
        JsonNode envelope=json.readTree(response.getResponse().getContentAsByteArray());assertFalse(envelope.path("success").asBoolean());assertEquals("SERVICE_UNAVAILABLE",envelope.path("code").asText());assertTrue(envelope.path("data").isNull());
        assertEquals(0,privateObserved.active.get());assertEquals(privateObserved.borrowed.get(),privateObserved.returned.get());assertEquals(0,observed.active.get());
        assertEquals(before,privateDigest());assertEquals(source,sourceDigest());assertEquals("0",added);assertEquals("2",scalar("SELECT COUNT(*) FROM ra_private_cohort_command"));
        assertEquals(0,privateObserved.definitionWrites.get());assertEquals(1,privateObserved.receiptWrites.get());assertEquals(1,privateObserved.rollbacks.get());assertEquals(0,privateObserved.commits.get());assertEquals(0,privateObserved.clinicalReads.get());
        // 拒绝/回滚和所有资源退出已成立，再恢复fixture；首次和重放须消费同一成功结果判据。
        assertEquals(2,privateSuccess("delete",originalDelete).path("version").asLong());
        sql("UPDATE ra_private_cohort_definition SET version=2 WHERE id='"+id+"'");
        assertEquals(2,privateSuccess("delete",newDelete).path("version").asLong());assertEquals(2,privateSuccess("delete",newDelete).path("version").asLong());
        assertEquals("2",scalar("SELECT version FROM ra_private_cohort_definition WHERE id='"+id+"'"));assertEquals("3",scalar("SELECT COUNT(*) FROM ra_private_cohort_command"));
    }
    String sourceDigest() throws Exception{
        StringBuilder b=new StringBuilder();
        for(String table:java.util.Arrays.asList("patient_basic_info","patient_relation_doctor","patient_follow_up_history","patient_comorbidity")){
            try(Connection c=raw.getConnection();
            Statement s=c.createStatement();
            ResultSet r=s.executeQuery("SELECT * FROM "+table+" ORDER BY id")){
                int n=r.getMetaData().getColumnCount();
                while(r.next()){
                    for(int i=1;i<=n;i++){
                        byte[] v=r.getBytes(i);
                        b.append(v==null?"NULL":sha(v)).append('|');
                    }b.append('\n');
                }
            }
        }return sha(b.toString().getBytes(StandardCharsets.UTF_8));
    }
    java.util.List<String> filterState(String id) throws Exception {
        // 逐列读取避免5.7在CONCAT中混合HEX/ASCII/连接排序规则，同时逐字保留三项状态。
        try(Connection c=raw.getConnection();java.sql.PreparedStatement s=c.prepareStatement("SELECT HEX(filters_utf8),filters_sha256,filters_version FROM ra_private_cohort_definition WHERE id=?")) {
            s.setString(1,id);try(ResultSet r=s.executeQuery()){assertTrue(r.next());java.util.List<String> state=java.util.Arrays.asList(r.getString(1),r.getString(2),r.getString(3));assertFalse(r.next());return state;}
        }
    }
    String scalar(String sql) throws Exception{
        try(Connection c=raw.getConnection();
        Statement s=c.createStatement();
        ResultSet r=s.executeQuery(sql)){
            assertTrue(r.next());
            return r.getString(1);
        }
    }
    static String sha(byte[] bytes) throws Exception {
        StringBuilder s=new StringBuilder();
        for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))s.append(String.format("%02x",b&255));
        return s.toString();
    }
}
