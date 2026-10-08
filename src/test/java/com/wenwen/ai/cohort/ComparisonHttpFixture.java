package com.wenwen.ai.cohort;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.context.annotation.*;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.mybatis.spring.*;
import org.springframework.core.io.ClassPathResource;
import javax.sql.DataSource;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.wenwen.config.AiCohortConfiguration;
import com.wenwen.controller.ra.AiCohortController;
import com.wenwen.service.impl.AiCohortServiceImpl;
import com.wenwen.ai.source.CohortSourceAdapter;
import com.wenwen.mapper.AiCohortSourceMapper;
import java.sql.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.junit.jupiter.api.Assertions.*;

/** 新比较独立fixture；原定义/分析fixture与断言保持不变。 */
abstract class ComparisonHttpFixture extends PrivateCohortHttpFixture {
    ComparisonObservedDataSource comparisonObserved;
    @Configuration @EnableWebMvc
    @Import({
        AiCohortConfiguration.class, AiCohortController.class, AiCohortServiceImpl.class, CohortSourceAdapter.class, com.wenwen.ai.result.AnalysisResultStore.class,com.wenwen.ai.result.AnalysisPageReader.class, com.wenwen.controller.ra.PrivateCohortController.class, com.wenwen.service.impl.PrivateCohortServiceImpl.class, com.wenwen.ai.cohort.DefinitionStore.class,com.wenwen.controller.ra.ComparisonController.class,com.wenwen.service.impl.ComparisonServiceImpl.class,com.wenwen.ai.result.ComparisonResultStore.class,com.wenwen.ai.result.ComparisonScopeReader.class
    })
    static class ComparisonInfrastructure {
        @Bean public DriverManagerDataSource dataSource() {
            String url = System.getenv("RA_TEST_MYSQL_URL"), user = System.getenv("RA_TEST_MYSQL_USER"), password = System.getenv("RA_TEST_MYSQL_PASSWORD");
            guard(url, user, password);
            DriverManagerDataSource ds = new DriverManagerDataSource(url, user, password);
            ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
            return ds;
        }
        @Bean public ObservedDataSource observedDataSource(DriverManagerDataSource raw) {
            return new ObservedDataSource(raw);
        }
        @Bean public PrivateCohortObservedDataSource privateObserved(ObservedDataSource observed){
            return new PrivateCohortObservedDataSource(observed);
        }
        @Bean @Primary public ComparisonObservedDataSource comparisonObserved(PrivateCohortObservedDataSource priv){return new ComparisonObservedDataSource(priv);}
        @Bean public PlatformTransactionManager transactions(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
        @Bean public org.apache.ibatis.session.SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
            SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            factory.setMapperLocations(new org.springframework.core.io.Resource[] {
                new ClassPathResource("mybatis/ProjectMapper.xml"),new ClassPathResource("mybatis/AiCohortSourceMapper.xml"),new ClassPathResource("mybatis/PatientMapper.xml"),new ClassPathResource("mybatis/AiAnalysisRunMapper.xml"),new ClassPathResource("mybatis/PrivateCohortMapper.xml"),new ClassPathResource("mybatis/AiComparisonRunMapper.xml")
            });
            return factory.getObject();
        }
        @Bean public AiCohortSourceMapper sourceMapper(org.apache.ibatis.session.SqlSessionFactory factory) {
            return new SqlSessionTemplate(factory).getMapper(AiCohortSourceMapper.class);
        }
        @Bean public com.wenwen.mapper.AiAnalysisRunMapper runMapper(org.apache.ibatis.session.SqlSessionFactory factory) {
            return new SqlSessionTemplate(factory).getMapper(com.wenwen.mapper.AiAnalysisRunMapper.class);
        }
        @Bean public com.wenwen.mapper.PrivateCohortMapper privateMapper(org.apache.ibatis.session.SqlSessionFactory factory) {
            return new SqlSessionTemplate(factory).getMapper(com.wenwen.mapper.PrivateCohortMapper.class);
        }
        @Bean public com.wenwen.mapper.AiComparisonRunMapper comparisonMapper(org.apache.ibatis.session.SqlSessionFactory factory){return new SqlSessionTemplate(factory).getMapper(com.wenwen.mapper.AiComparisonRunMapper.class);}
        @Bean @Primary public CountingDictionary comparisonDictionary(){return new CountingDictionary();}
        @Bean @Primary public MutableClock testClock() {
            return new MutableClock();
        }
    }
    void buildContext(boolean trusted) {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        if(syntheticCursorKey!=null)context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("synthetic-analysis",Collections.singletonMap("ra.ai.analysis.cursor-key-base64",syntheticCursorKey)));
        context.register(ComparisonInfrastructure.class);
        if (trusted) context.register(TrustedIdentity.class);
        beforeRefresh();
        context.refresh();
        bodyRead = new CountingBodyFilter();
        http = MockMvcBuilders.webAppContextSetup(context).addFilters(bodyRead).build();
        raw = context.getBean(DriverManagerDataSource.class);
        clock = context.getBean(MutableClock.class);
        observed = context.getBean(ObservedDataSource.class);
        privateObserved=context.getBean(PrivateCohortObservedDataSource.class);comparisonObserved=context.getBean(ComparisonObservedDataSource.class);
    }
    @Override @org.junit.jupiter.api.BeforeEach void setup() throws Exception {
        super.setup();
        try(Connection c=raw.getConnection();Statement st=c.createStatement();java.io.InputStream in=getClass().getResourceAsStream("/sql/20261008_ai_compare_run.sql")){
            String ddl=new Scanner(in,java.nio.charset.StandardCharsets.UTF_8.name()).useDelimiter("\\A").next();for(String statement:ddl.split(";"))if(!statement.trim().isEmpty())st.execute(statement);ownedTables.add("ra_ai_compare_run");
        }
    }
    ComparisonHttpFixture(){json.enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);}
    static final class CountingDictionary implements com.wenwen.ai.treatment.DrugDictionary {
        int calls;Runnable onIdentify;final com.wenwen.ai.treatment.DrugDictionary delegate=new com.wenwen.ai.treatment.DevelopmentDrugDictionary();
        public Snapshot snapshot(){calls++;Snapshot actual=delegate.snapshot();return new Snapshot(actual.version,Collections.emptyMap()){@Override public com.wenwen.ai.treatment.DrugDictionary.Drug identify(String name){if(onIdentify!=null)onIdentify.run();return actual.identify(name);}};}
    }
    final Map<String,Object> analysisProperties=new LinkedHashMap<>();
    @Override void beforeRefresh(){if(!analysisProperties.isEmpty())context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("comparison-settings",analysisProperties));}
    void fixedPatients() throws Exception {
        sql("DELETE FROM patient_follow_up_history");sql("DELETE FROM patient_relation_doctor");sql("DELETE FROM patient_comorbidity");sql("DELETE FROM patient_basic_info");
        String[] scores={"1","2","2.3","2.7","3","4","4.1","4.5","4.6","4.7","5","5.1","6","6.1"};
        String[] haq={".1",".2",".3",".3",".4",".5",".6",null,".8",".9","1","1.1","1.2","1.3"};
        Set<Integer> female=new HashSet<>(Arrays.asList(1,7,9,10,11,12,13)),positive=new HashSet<>(Arrays.asList(1,4,6,7,10,11,12)),unknown=new HashSet<>(Arrays.asList(3,8)),complete=new HashSet<>(Arrays.asList(1,2,4,7,8,9,11,12,14));
        int[] line={1,1,2,2,3,3,0,2,1,2,2,3,3,3};
        LocalDate asOf=LocalDate.of(2026,10,7);
        for(int i=1;i<=14;i++) {
            try(Connection c=raw.getConnection();PreparedStatement s=c.prepareStatement("INSERT INTO patient_basic_info(id,name,gender,card_no,confirm_date,jws) VALUES(?,?,?,?,?,?)")) {
                s.setInt(1,i);s.setString(2,"synthetic-comparison-name-CANARY");s.setInt(3,female.contains(i)?2:1);s.setString(4,"110101"+asOf.minusYears(i+19).toString().replace("-","")+"0011");s.setString(5,asOf.minusYears(i).toString());s.setString(6,complete.contains(i)?"{\"record\":1}":null);s.executeUpdate();
            }
            sql("INSERT INTO patient_relation_doctor(doctor_id,patient_id,research_type,miss) VALUES(101,"+i+",0,0)");
            String bqpg="{\"result\":{\"crpScore\":"+scores[i-1]+"},\"caseCanary\":\"synthetic-comparison-case-CANARY\""+(haq[i-1]==null?"":",\"hqaScore\":"+new BigDecimal(haq[i-1]).toPlainString())+"}";
            String labs="{\"record\":1,\"cfydb\":"+new BigDecimal(".4").multiply(BigDecimal.valueOf(i))+(unknown.contains(i)?"":",\"lfsyz\":"+(positive.contains(i)?"35":"20"))+"}";
            visit(100+i,i,0,bqpg,"2026-10-07",null,labs);
            String drug=line[i-1]==1?"甲氨蝶呤":line[i-1]==2?"阿达木单抗":line[i-1]==3?"依那西普":"synthetic-unmapped-drug-CANARY";
            sql("UPDATE patient_follow_up_history SET zlfa='"+medication(drug,"2026-04-01",null)+"' WHERE id="+(100+i));
            if(line[i-1]==3){visit(200+i,i,0,null,"2025-02-01",null,"{\"record\":1}");sql("UPDATE patient_follow_up_history SET zlfa='"+medication("阿达木单抗","2025-01-01","2025-03-01")+"' WHERE id="+(200+i));}
        }
        observed.reset();privateObserved.reset();comparisonObserved.reset();context.getBean(CountingDictionary.class).calls=0;
    }
    static String medication(String drug,String start,String end){return "{\"xyList\":[{\"drugName\":\""+drug+"\",\"startTime\":\""+start+"\",\"endTime\":"+(end==null?"null":"\""+end+"\"")+"}]}";}
    static String filtersBody(boolean exclude){return "{\"a\":{\"source\":\"FILTERS\",\"filters\":{\"ids\":[\"1\",\"2\",\"3\",\"4\",\"5\",\"6\",\"7\",\"8\"]}},\"b\":{\"source\":\"FILTERS\",\"filters\":{\"ids\":[\"7\",\"8\",\"9\",\"10\",\"11\",\"12\",\"13\",\"14\"]}},\"excludeOverlap\":"+exclude+"}";}
    MvcResult compareRequest(String body) throws Exception{return http.perform(post("/api/ra/ai/compare").contentType("application/json").content(body)).andReturn();}
    JsonNode compareSuccess(String body) throws Exception {
        MvcResult r=compareRequest(body);assertEquals(200,r.getResponse().getStatus(),r.getResponse().getContentAsString());JsonNode e=json.readTree(r.getResponse().getContentAsByteArray());assertTrue(e.path("success").asBoolean());assertEquals("200",e.path("code").asText());assertEquals(r.getResponse().getHeader("X-Trace-Id"),e.path("data").path("meta").path("traceId").asText());return e.path("data");
    }
    static JsonNode row(JsonNode data,String metric){for(JsonNode r:data.path("rows"))if(metric.equals(r.path("metric").asText()))return r;throw new AssertionError("缺比较行 "+metric);}
    JsonNode routeSuccess(String route,String body) throws Exception {MvcResult r=http.perform(post(route).contentType("application/json").content(body)).andReturn();assertEquals(200,r.getResponse().getStatus(),r.getResponse().getContentAsString());JsonNode e=json.readTree(r.getResponse().getContentAsByteArray());assertTrue(e.path("success").asBoolean());assertEquals(r.getResponse().getHeader("X-Trace-Id"),e.path("data").path("meta").path("traceId").asText());return e.path("data");}
    JsonNode saveDefinition(String name,JsonNode filters) throws Exception {return routeSuccess("/api/ra/cohort/save",json.writeValueAsString(com.wenwen.vo.AiCohortVo.object("name",name,"note","synthetic-comparison-note-CANARY","filters",filters,"requestId",java.util.UUID.randomUUID().toString())));}
    long count(String table)throws Exception{try(Connection c=raw.getConnection();Statement st=c.createStatement();ResultSet rs=st.executeQuery("SELECT COUNT(*) FROM "+table)){assertTrue(rs.next());return rs.getLong(1);}}
    void routeError(String route,byte[] body,int status,String code) throws Exception {MvcResult r=http.perform(post(route).contentType("application/json").content(body)).andReturn();assertEquals(status,r.getResponse().getStatus(),r.getResponse().getContentAsString());JsonNode e=json.readTree(r.getResponse().getContentAsByteArray());assertFalse(e.path("success").asBoolean());assertTrue(e.path("data").isNull());assertEquals(code,e.path("code").asText());assertNotNull(r.getResponse().getHeader("X-Trace-Id"));}
    void compareError(String body,int status,String code) throws Exception {routeError("/api/ra/ai/compare",body.getBytes(java.nio.charset.StandardCharsets.UTF_8),status,code);}
    JsonNode readComparison(String id) throws Exception {return routeSuccess("/api/ra/ai/compareResult",json.writeValueAsString(com.wenwen.vo.AiCohortVo.object("compareResultId",id)));}
    void readError(String id,int status,String code) throws Exception {routeError("/api/ra/ai/compareResult",json.writeValueAsBytes(com.wenwen.vo.AiCohortVo.object("compareResultId",id)),status,code);}
    static void sameRetainedComparison(JsonNode created,JsonNode read){for(String key:new String[]{"compareResultId","studyTotal","a","b","overlapN","excludeOverlap","rows"})assertEquals(created.path(key),read.path(key),key);created.path("meta").fields().forEachRemaining(field->{if(!"traceId".equals(field.getKey()))assertEquals(field.getValue(),read.path("meta").path(field.getKey()),field.getKey());});assertEquals(created.path("meta").path("traceId"),read.path("meta").path("comparisonTraceId"));assertNotEquals(created.path("meta").path("traceId"),read.path("meta").path("traceId"));}
    byte[] payload(String id)throws Exception{try(Connection c=raw.getConnection();PreparedStatement st=c.prepareStatement("SELECT payload FROM ra_ai_compare_run WHERE id=?")){st.setString(1,id);try(ResultSet rs=st.executeQuery()){assertTrue(rs.next());return rs.getBytes(1);}}}
    void replacePayload(String id,byte[] bytes,boolean correctHash)throws Exception{try(Connection c=raw.getConnection();PreparedStatement st=c.prepareStatement("UPDATE ra_ai_compare_run SET payload=?,payload_sha256=? WHERE id=?")){st.setBytes(1,bytes);st.setString(2,correctHash?com.wenwen.ai.result.AnalysisValues.sha256(bytes):String.join("",Collections.nCopies(64,"0")));st.setString(3,id);assertEquals(1,st.executeUpdate());}}
    String digest(String...tables)throws Exception {java.security.MessageDigest sha=java.security.MessageDigest.getInstance("SHA-256");try(Connection c=raw.getConnection();Statement st=c.createStatement()){for(String table:tables){sha.update(table.getBytes(java.nio.charset.StandardCharsets.UTF_8));try(ResultSet rs=st.executeQuery("SELECT * FROM "+table+" ORDER BY id")){int cols=rs.getMetaData().getColumnCount();while(rs.next())for(int i=1;i<=cols;i++){byte[] bytes=rs.getBytes(i);sha.update(java.nio.ByteBuffer.allocate(4).putInt(bytes==null?-1:bytes.length).array());if(bytes!=null)sha.update(bytes);}}}}StringBuilder out=new StringBuilder();for(byte b:sha.digest())out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();}
    String sourceDigest()throws Exception{return digest("patient_basic_info","patient_relation_doctor","patient_follow_up_history","patient_comorbidity");}
    void exited(){assertEquals(0,comparisonObserved.active.get());assertEquals(comparisonObserved.borrowed.get(),comparisonObserved.returned.get());assertEquals(0,privateObserved.active.get());assertEquals(privateObserved.borrowed.get(),privateObserved.returned.get());assertEquals(0,observed.active.get());assertEquals(observed.borrowed,observed.returned);}
    void resetOwners(){observed.reset();privateObserved.reset();comparisonObserved.reset();}
    static int queryCount(Connection connection,String sql)throws Exception{try(Statement st=connection.createStatement();ResultSet rs=st.executeQuery(sql)){assertTrue(rs.next());return rs.getInt(1);}}
    int rawCount(String sql)throws Exception{try(Connection c=raw.getConnection()){return queryCount(c,sql);}}
    void seedComparisonClones(String sourceId,int first,int number,long shiftMillis)throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode body=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(payload(sourceId));com.fasterxml.jackson.databind.node.ObjectNode meta=(com.fasterxml.jackson.databind.node.ObjectNode)body.path("meta");for(String key:new String[]{"readStartedAt","readCompletedAt","computedAt","expiresAt"})meta.put(key,Instant.parse(meta.path(key).asText()).plusMillis(shiftMillis).toString());byte[] bytes=shiftMillis==0?payload(sourceId):json.writeValueAsBytes(body);
        try(Connection c=raw.getConnection();PreparedStatement st=c.prepareStatement("INSERT INTO ra_ai_compare_run SELECT ?,owner_doctor_id,scope_fingerprint,payload_version,?,?,created_at_ms+?,expires_at_ms+? FROM ra_ai_compare_run WHERE id=?")){
            for(int i=0;i<number;i++){st.setString(1,cloneId(first+i));st.setBytes(2,bytes);st.setString(3,com.wenwen.ai.result.AnalysisValues.sha256(bytes));st.setLong(4,shiftMillis);st.setLong(5,shiftMillis);st.setString(6,sourceId);st.addBatch();}int[] result=st.executeBatch();assertEquals(number,result.length);for(int value:result)assertTrue(value==1||value==Statement.SUCCESS_NO_INFO);
        }
    }
    static String cloneId(int number){return String.format(java.util.Locale.ROOT,"00000000-0000-0000-0000-%012d",number);}
}
