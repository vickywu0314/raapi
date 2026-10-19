package com.wenwen.ocr;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.imageio.ImageIO;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mybatis.spring.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.mock.web.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.interceptor.*;
import com.alibaba.fastjson.JSONObject;
import com.wenwen.auth.AuthService;
import com.wenwen.controller.ra.*;
import com.wenwen.mapper.*;
import com.wenwen.service.*;
import com.wenwen.service.impl.*;
import com.wenwen.util.BizException;
import com.wenwen.vo.*;

/** 显式选择的真实隔离MySQL验收；默认*Test不发现。缺少专属新合成环境直接失败，不skip。 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OcrMysqlIT {
    static final String A=OcrLinkTest.FILE, B="20261017/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb.jpg";
    static final String[] TABLES={"ocr_lab_alias","ocr_record","patient_audit_log","patient_follow_up_history","patient_relation_doctor","patient_basic_info","user"};
    VisitServiceImpl visitTarget; JdbcTemplate jdbc; SqlSessionTemplate sql; OcrMapper real, observed; OcrService service; VisitService visits; MockMvc mvc;
    AtomicReference<String> fault=new AtomicReference<>(); AtomicInteger creates=new AtomicInteger(),ocrCalls=new AtomicInteger();
    volatile CountDownLatch readBarrier; DataSource ds; boolean ownsTables;
    @TempDir static Path upload;
    @BeforeAll void start() throws Exception {
        String url=System.getenv("RA_OCR_TEST_MYSQL_URL"),user=System.getenv("RA_OCR_TEST_MYSQL_USER"),password=System.getenv("RA_OCR_TEST_MYSQL_PASSWORD");
        if(!"true".equals(System.getenv("RA_OCR_SYNTHETIC_TEST"))||url==null||user==null||password==null)throw new IllegalStateException("需要显式新建的OCR专属合成MySQL环境");
        URI u=URI.create(url.substring(5));
        if(!url.startsWith("jdbc:mysql://")||!("127.0.0.1".equals(u.getHost())||"localhost".equals(u.getHost()))||u.getUserInfo()!=null||!u.getPath().matches("/ra_ocr_synthetic_[a-z0-9_]+"))throw new IllegalStateException("仅允许loopback/ra_ocr_synthetic_*独立空库");
        DriverManagerDataSource data=new DriverManagerDataSource(url,user,password);data.setDriverClassName("com.mysql.cj.jdbc.Driver");ds=data;jdbc=new JdbcTemplate(ds);
        com.wenwen.testsupport.Mysql57Contract.verify(ds);
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()",Integer.class),"专属数据库必须为空，禁止复用患者库或旧测试库");
        ownsTables=true;
        jdbc.execute("CREATE TABLE patient_basic_info(id BIGINT PRIMARY KEY,follow_up_count INT,last_follow_up_date DATE,lastFollowUpDate DATE,gender INT,confirm_date DATE,confirmDate DATE,happen_date DATE,happenDate DATE)");
        jdbc.execute("CREATE TABLE patient_relation_doctor(id BIGINT PRIMARY KEY AUTO_INCREMENT,patient_id BIGINT,doctor_id BIGINT,research_type INT,miss INT DEFAULT 0,follow_count INT,first_follow_up_date DATE,last_follow_up_date DATE)");
        jdbc.execute("CREATE TABLE patient_follow_up_history(id BIGINT PRIMARY KEY AUTO_INCREMENT,patient_basic_info_id BIGINT,doctor_id BIGINT,research_type INT,status INT,last_follow_up_id BIGINT,follow_up_date DATE,followUpDate DATE,bsbq TEXT,fzjc TEXT,bqpg TEXT,zyzd TEXT,zlfa TEXT,blsj TEXT,bblsj TEXT)");
        jdbc.execute("CREATE TABLE `user`(id BIGINT PRIMARY KEY,name VARCHAR(100))");
        for(String script:Arrays.asList("sql/20261014_ocr_record.sql","sql/20261005_patient_audit_log.sql","sql/20261012_ocr_lab_alias.sql")){
            String text=new String(Files.readAllBytes(new ClassPathResource(script).getFile().toPath()),StandardCharsets.UTF_8);
            for(String statement:text.replaceAll("(?m)^--.*$","").split(";"))if(!statement.trim().isEmpty())jdbc.execute(statement);
        }
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(ds);factory.setMapperLocations(new org.springframework.core.io.Resource[]{new ClassPathResource("mybatis/ProjectMapper.xml"),new ClassPathResource("mybatis/OcrMapper.xml"),new ClassPathResource("mybatis/VisitMapper.xml"),new ClassPathResource("mybatis/AuditLogMapper.xml")});
        sql=new SqlSessionTemplate(factory.getObject());real=sql.getMapper(OcrMapper.class);
        observed=(OcrMapper)Proxy.newProxyInstance(OcrMapper.class.getClassLoader(),new Class[]{OcrMapper.class},(proxy,m,args)->{
            if(m.getName().equals("linkRecord")&&A.equals(args[0])&&fault.compareAndSet("MIXED","MIXED_B"))jdbc.update("UPDATE ocr_record SET patient_id=20,visit_id=200 WHERE file_id=?",A);
            if(m.getName().equals("linkRecord")&&B.equals(args[0])&&fault.compareAndSet("MIXED_B",null)){
                jdbc.execute("RENAME TABLE ocr_record TO ocr_record_unavailable");
                try{return m.invoke(real,args);}catch(InvocationTargetException e){throw e.getCause();}finally{jdbc.execute("RENAME TABLE ocr_record_unavailable TO ocr_record");}
            }
            if(m.getName().equals("linkRecord")&&fault.compareAndSet("MISSING",null))jdbc.execute("RENAME TABLE ocr_record TO ocr_record_unavailable");
            if(m.getName().equals("linkRecord")&&fault.compareAndSet("BEFORE",null))throw new DataAccessResourceFailureException("synthetic before-update failure");
            Object result;try{result=m.invoke(real,args);}catch(InvocationTargetException e){throw e.getCause();}
            if(m.getName().equals("findRecord")&&readBarrier!=null){CountDownLatch barrier=readBarrier;barrier.countDown();if(!barrier.await(5,TimeUnit.SECONDS))throw new IllegalStateException("synthetic read rendezvous timeout");}
            if(m.getName().equals("linkRecord")&&fault.compareAndSet("AFTER",null))throw new DataAccessResourceFailureException("synthetic update committed but acknowledgement lost");
            return result;
        });
        service=new OcrService();ReflectionTestUtils.setField(service,"mapper",observed);ReflectionTestUtils.setField(service,"uploadDir",upload.toString());ReflectionTestUtils.setField(service,"apis","medical_report_detection");
        ReflectionTestUtils.setField(service,"baidu",new BaiduOcrClient(){@Override public boolean configured(){return true;}@Override public JSONObject call(String api,byte[] bytes){ocrCalls.incrementAndGet();assertTrue(bytes.length>0);return JSONObject.parseObject("{\"words_result\":{\"Item\":[[{\"word_name\":\"项目名称\",\"word\":\"血沉\"},{\"word_name\":\"结果\",\"word\":\"20\"}]]}}");}});
        AuditLogServiceImpl audit=new AuditLogServiceImpl();ReflectionTestUtils.setField(audit,"auditLogMapper",sql.getMapper(AuditLogMapper.class));
        VisitServiceImpl target=new VisitServiceImpl();visitTarget=target;ReflectionTestUtils.setField(target,"visitMapper",sql.getMapper(VisitMapper.class));ReflectionTestUtils.setField(target,"patientMapper",mock(PatientMapper.class));ReflectionTestUtils.setField(target,"auditLogService",audit);
        ProxyFactory tx=new ProxyFactory(target);tx.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds),new MatchAlwaysTransactionAttributeSource()));
        VisitService transactional=(VisitService)tx.getProxy();
        visits=(VisitService)Proxy.newProxyInstance(VisitService.class.getClassLoader(),new Class[]{VisitService.class},(proxy,m,args)->{if(m.getName().equals("createVisit"))creates.incrementAndGet();try{return m.invoke(transactional,args);}catch(InvocationTargetException e){throw e.getCause();}});
        VisitController vc=new VisitController();ReflectionTestUtils.setField(vc,"visitService",visits);ReflectionTestUtils.setField(vc,"ocrService",service);
        OcrController oc=new OcrController();ReflectionTestUtils.setField(oc,"ocrService",service);mvc=MockMvcBuilders.standaloneSetup(vc,oc).build();
    }
    @BeforeEach void reset(){ReflectionTestUtils.setField(visitTarget,"validationRules","");for(String t:TABLES)jdbc.execute("DELETE FROM `"+t+"`");fault.set(null);readBarrier=null;creates.set(0);ocrCalls.set(0);jdbc.update("INSERT INTO patient_basic_info(id) VALUES(10),(20)");jdbc.update("INSERT INTO patient_relation_doctor(patient_id,doctor_id,research_type) VALUES(10,11,0),(20,11,0)");jdbc.update("INSERT INTO `user` VALUES(11,'Synthetic')");jdbc.update("INSERT INTO patient_follow_up_history(id,patient_basic_info_id,doctor_id,research_type,follow_up_date) VALUES(100,10,11,0,'2026-10-01'),(200,20,11,0,'2026-10-01'),(101,10,11,0,'2026-10-02')");real.insertRecord(A,11L);real.insertRecord(B,11L);}
    @AfterAll void close(){if(ownsTables)for(String t:TABLES)jdbc.execute("DROP TABLE IF EXISTS `"+t+"`");}
    MockHttpSession session(long doctor){MockHttpSession s=new MockHttpSession();s.setAttribute(AuthService.SESSION_DOCTOR,doctor);return s;}
    JSONObject json(MvcResult r)throws Exception{return JSONObject.parseObject(r.getResponse().getContentAsString());}
    JSONObject create()throws Exception{return json(mvc.perform(post("/api/ra/visit/createVisit").session(session(11)).contentType(MediaType.APPLICATION_JSON).content("{\"doctorId\":22,\"patientId\":10,\"visitDate\":\"2026-10-10\",\"ocrFileIds\":[\""+A+"\",\""+B+"\"]}")).andReturn());}
    JSONObject retry(long visit,List<String> files)throws Exception{JSONObject body=new JSONObject();body.put("patientId",10);body.put("visitId",visit);body.put("ocrFileIds",files);return json(mvc.perform(post("/api/ra/ocr/linkImages").session(session(11)).contentType(MediaType.APPLICATION_JSON).content(body.toJSONString())).andReturn());}
    @Test void actualCreateUsesSharedValidationAndAtomicConfirmationAudit() throws Exception {
        ReflectionTestUtils.setField(visitTarget,"validationRules","{\"fzjc.bxb\":{\"referenceMin\":3,\"referenceMax\":10}}");
        JSONObject body=JSONObject.parseObject("{\"doctorId\":22,\"patientId\":10,\"visitDate\":\"2026-10-10\",\"modules\":{\"fzjc\":{\"fields\":{\"bxb\":\"10000abc\"}}}}");
        assertEquals("400",createEntry(body).getString("code"));
        body.getJSONObject("modules").getJSONObject("fzjc").getJSONObject("fields").put("bxb","11");
        assertEquals("400",createEntry(body).getString("code"));
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM patient_follow_up_history",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM patient_audit_log",Integer.class));
        VisitFieldValidationVo rule=null;
        for(VisitEditModuleVo module:visits.getCreateForm(11L,10L).getModules())for(VisitEditGroupVo group:module.getGroups())for(VisitEditFieldVo field:group.getFields())if(module.getColumn().equals("fzjc")&&field.getKey().equals("bxb"))rule=field.getValidation();
        assertNotNull(rule);
        JSONObject confirmation=new JSONObject();confirmation.put("value","11");confirmation.put("unit","×10⁹/L");confirmation.put("ruleVersion",rule.ruleVersion);confirmation.put("target","patient:10:new");
        JSONObject confirmations=new JSONObject();confirmations.put("bxb",confirmation);body.getJSONObject("modules").getJSONObject("fzjc").put("confirmations",confirmations);
        jdbc.execute("CREATE TRIGGER entry_create_fail_audit BEFORE INSERT ON patient_audit_log FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='entry_create_fail_audit'");
        try {assertEquals("500",createEntry(body).getString("code"));assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM patient_follow_up_history",Integer.class));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM patient_audit_log",Integer.class));}
        finally {jdbc.execute("DROP TRIGGER entry_create_fail_audit");}
        JSONObject result=createEntry(body);assertEquals("200",result.getString("code"));
        long id=result.getJSONObject("data").getLongValue("visitId");
        assertEquals("11",JSONObject.parseObject(jdbc.queryForObject("SELECT fzjc FROM patient_follow_up_history WHERE id=?",String.class,id)).getString("bxb"));
        assertEquals(11L,jdbc.queryForObject("SELECT operator_id FROM patient_audit_log",Long.class));
        assertTrue(jdbc.queryForObject("SELECT detail FROM patient_audit_log",String.class).contains("白细胞 WBC=11 ×10⁹/L"));
    }
    JSONObject createEntry(JSONObject body) throws Exception {return json(mvc.perform(post("/api/ra/visit/createVisit").session(session(11)).contentType(MediaType.APPLICATION_JSON).content(body.toJSONString())).andReturn());}
    @Test void actualMapperPreservesTargetAndRejectsConcurrentRebinding()throws Exception{
        service.link(A,11L,10L,null);service.link(A,11L,10L,100L);service.link(A,11L,10L,100L);
        assertThrows(BizException.class,()->service.link(A,11L,20L,200L));assertThrows(BizException.class,()->service.link(A,11L,10L,101L));assertEquals(10L,((Number)real.findRecord(A).get("patientId")).longValue());assertEquals(100L,((Number)real.findRecord(A).get("visitId")).longValue());
        assertEquals(0,real.linkRecord(A,11L,20L,200L));assertEquals(0,real.linkRecord(B,11L,20L,100L));
        readBarrier=new CountDownLatch(2);ExecutorService pool=Executors.newFixedThreadPool(2);try{
            Future<Boolean> first=pool.submit(()->attempt(B,10,100)),second=pool.submit(()->attempt(B,20,200));boolean x=first.get(10,TimeUnit.SECONDS),y=second.get(10,TimeUnit.SECONDS);assertNotEquals(x,y,"exactly one competing CAS may succeed");
            readBarrier=null;Map<String,Object> row=real.findRecord(B);assertEquals(x?10L:20L,((Number)row.get("patientId")).longValue());assertEquals(x?100L:200L,((Number)row.get("visitId")).longValue());
        }finally{readBarrier=null;pool.shutdownNow();assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS));}
    }
    boolean attempt(String file,long patient,long visit){try{service.link(file,11L,patient,visit);return true;}catch(BizException e){assertEquals("409",e.getCode());return false;}}
    @Test void realClinicalCommitAndPartialFailureRecoverWithoutAnotherCreate()throws Exception{
        fault.set("MISSING");JSONObject response;try{response=create();}finally{jdbc.execute("RENAME TABLE ocr_record_unavailable TO ocr_record");}assertTrue(response.getBooleanValue("success"));JSONObject saved=response.getJSONObject("data");long visit=saved.getLongValue("visitId");assertEquals("PENDING",saved.getJSONObject("ocrLink").getString("status"));assertEquals(4,jdbc.queryForObject("SELECT COUNT(*) FROM patient_follow_up_history",Integer.class));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM patient_audit_log",Integer.class));
        assertEquals("LINKED",retry(visit,Arrays.asList(A,B)).getJSONObject("data").getString("status"));assertEquals("LINKED",retry(visit,Arrays.asList(A,B)).getJSONObject("data").getString("status"));assertEquals(1,creates.get());assertEquals(4,jdbc.queryForObject("SELECT COUNT(*) FROM patient_follow_up_history",Integer.class));assertEquals(visit,((Number)real.findRecord(A).get("visitId")).longValue());assertEquals(visit,((Number)real.findRecord(B).get("visitId")).longValue());
    }
    @Test void committedLinkAcknowledgementLossAndZeroRowReadbackAreIdempotent()throws Exception{
        fault.set("AFTER");JSONObject saved=create().getJSONObject("data");long visit=saved.getLongValue("visitId");assertEquals("PENDING",saved.getJSONObject("ocrLink").getString("status"));assertEquals(visit,((Number)real.findRecord(A).get("visitId")).longValue());assertEquals("LINKED",retry(visit,Arrays.asList(A,B)).getJSONObject("data").getString("status"));assertEquals(1,creates.get());
    }
    @Test void mixedPermanentAndTransientFilesRecoverIndependently()throws Exception{
        fault.set("MIXED");JSONObject response=create();assertTrue(response.getBooleanValue("success"));JSONObject saved=response.getJSONObject("data"),link=saved.getJSONObject("ocrLink");long visit=saved.getLongValue("visitId");
        assertEquals("PENDING",link.getString("status"));assertEquals(Arrays.asList(A,B),link.getJSONArray("pendingFileIds").toJavaList(String.class));assertEquals(Collections.singletonList(B),link.getJSONArray("retryableFileIds").toJavaList(String.class));assertTrue(link.getBooleanValue("retryable"));
        assertEquals(4,jdbc.queryForObject("SELECT COUNT(*) FROM patient_follow_up_history",Integer.class));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM patient_audit_log",Integer.class));assertEquals(1,creates.get());
        JSONObject recovered=retry(visit,Arrays.asList(A,B)).getJSONObject("data");assertEquals("PENDING",recovered.getString("status"));assertEquals(Collections.singletonList(B),recovered.getJSONArray("linkedFileIds").toJavaList(String.class));assertEquals(Collections.singletonList(A),recovered.getJSONArray("pendingFileIds").toJavaList(String.class));assertTrue(recovered.getJSONArray("retryableFileIds").isEmpty());assertFalse(recovered.getBooleanValue("retryable"));
        assertEquals(20L,((Number)real.findRecord(A).get("patientId")).longValue());assertEquals(200L,((Number)real.findRecord(A).get("visitId")).longValue());assertEquals(visit,((Number)real.findRecord(B).get("visitId")).longValue());assertEquals(1,creates.get());assertEquals(4,jdbc.queryForObject("SELECT COUNT(*) FROM patient_follow_up_history",Integer.class));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM patient_audit_log",Integer.class));
    }
    @Test void sessionUploadRecognitionAndReadUseOnlySyntheticBytes()throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",bytes);MockMultipartFile image=new MockMultipartFile("file","synthetic.png","image/png",bytes.toByteArray());
        assertEquals("401",json(mvc.perform(multipart("/api/ra/ocr/upload").file(image)).andReturn()).getString("code"));
        JSONObject up=json(mvc.perform(multipart("/api/ra/ocr/upload").file(image).session(session(11))).andReturn()).getJSONObject("data");String file=up.getString("fileId");
        assertEquals("403",json(mvc.perform(post("/api/ra/ocr/recognize").param("fileId",file).session(session(22))).andReturn()).getString("code"));assertEquals(0,ocrCalls.get());
        assertTrue(json(mvc.perform(post("/api/ra/ocr/recognize").param("fileId",file).session(session(11))).andReturn()).getBooleanValue("success"));assertEquals(1,ocrCalls.get());
        assertEquals(403,mvc.perform(get("/api/ra/files/ocr/"+file).session(session(22))).andReturn().getResponse().getStatus());assertArrayEquals(bytes.toByteArray(),mvc.perform(get("/api/ra/files/ocr/"+file).session(session(11))).andReturn().getResponse().getContentAsByteArray());
        assertEquals("401",json(mvc.perform(post("/api/ra/ocr/visitImages").param("doctorId","11").param("patientId","10").param("visitId","100")).andReturn()).getString("code"));
        assertEquals("403",json(mvc.perform(post("/api/ra/ocr/visitImages").session(session(22)).param("doctorId","11").param("patientId","10").param("visitId","100")).andReturn()).getString("code"));
    }
    @Test void missingOcrTableFailsBeforeClinicalCreationAndFailsClosed()throws Exception{
        jdbc.execute("RENAME TABLE ocr_record TO ocr_record_unavailable");try{assertFalse(create().getBooleanValue("success"));assertEquals(0,creates.get());assertFalse(service.canView(A,11L));assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM patient_follow_up_history",Integer.class));}finally{jdbc.execute("RENAME TABLE ocr_record_unavailable TO ocr_record");}
    }
}
