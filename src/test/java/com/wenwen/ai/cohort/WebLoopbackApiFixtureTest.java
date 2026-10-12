package com.wenwen.ai.cohort;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** 首连接核库决策在任何DDL前执行，字符串负例无需触碰其他库。 */
class WebLoopbackApiFixtureTest {
    @Test void p07CompositionAssemblesAllFifteenRealRoutesBeforeAnySql() throws Exception {
        WebLoopbackApiFixture fixture=new WebLoopbackApiFixture();fixture.scenario="p07-composition";
        java.util.concurrent.atomic.AtomicInteger connections=new java.util.concurrent.atomic.AtomicInteger();
        org.springframework.web.context.support.AnnotationConfigWebApplicationContext context=compositionUnitContext(fixture,connections,"true","true");
        try {
            context.refresh();
            java.util.Set<String> actual=new java.util.TreeSet<>();
            org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping mapping=context.getBean(org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping.class);
            for(org.springframework.web.servlet.mvc.method.RequestMappingInfo info:mapping.getHandlerMethods().keySet())actual.addAll(info.getPatternsCondition().getPatterns());
            assertEquals(new java.util.TreeSet<>(java.util.Arrays.asList("/api/ra/ai/cohort","/api/ra/ai/cohortPatients","/api/ra/ai/resolveEntry","/api/ra/ai/parse","/api/ra/ai/applyParse","/api/ra/ai/compare","/api/ra/ai/compareResult","/api/ra/cohort/save","/api/ra/cohort/list","/api/ra/cohort/open","/api/ra/cohort/rename","/api/ra/cohort/delete","/api/ra/export/xlsx","/api/ra/export/applyAnalysis","/api/ra/export/retryNotification")),actual);
            for(Class<?> type:new Class<?>[]{com.wenwen.controller.ra.AiCohortController.class,com.wenwen.controller.ra.AiParseController.class,com.wenwen.controller.ra.PrivateCohortController.class,com.wenwen.controller.ra.ComparisonController.class,com.wenwen.controller.ra.AiExportController.class,com.wenwen.controller.ra.AiExportApplicationController.class,com.wenwen.service.impl.AiCohortServiceImpl.class,com.wenwen.service.impl.AiParseServiceImpl.class,com.wenwen.service.impl.PrivateCohortServiceImpl.class,com.wenwen.service.impl.ComparisonServiceImpl.class,com.wenwen.service.impl.AiExportServiceImpl.class,com.wenwen.service.impl.AiExportApplicationServiceImpl.class})assertEquals(1,context.getBeansOfType(type).size(),type.getName());
            assertEquals(1,context.getBeansOfType(com.wenwen.ai.scope.AiFeatureAvailability.class).size());
            assertSame(fixture.scenarioClock,context.getBean(java.time.Clock.class));
            org.apache.ibatis.session.Configuration sql=context.getBean(org.apache.ibatis.session.SqlSessionFactory.class).getConfiguration();
            for(String statement:new String[]{"AiCohortSourceMapper.patientIds","AiAnalysisRunMapper.insert","PrivateCohortMapper.insertDefinition","AiComparisonRunMapper.insert","AiParseAuditMapper.insert","AiExportAuditMapper.insert","ExportMapper.insertApplication","AiExportApplicationMapper.reserve"})assertTrue(sql.hasStatement("com.wenwen.mapper."+statement),statement);
            assertEquals("AI_NOT_ENABLED",context.getBean(com.wenwen.ai.parse.AiParseGateway.class).parse("女性",new com.wenwen.ai.parse.RuleParser().parse("女性"),new com.wenwen.ai.scope.TrustedDoctor(101)).getFallback());
            assertNull(fixture.parsePeer);assertEquals(org.apache.ibatis.logging.nologging.NoLoggingImpl.class,sql.getLogImpl());
            assertEquals(0,connections.get());
        } finally {context.close();}
        assertFalse(context.isActive());assertEquals(0,connections.get());
    }
    private org.springframework.web.context.support.AnnotationConfigWebApplicationContext compositionUnitContext(WebLoopbackApiFixture fixture,java.util.concurrent.atomic.AtomicInteger connections,String global,String file) {
        org.springframework.web.context.support.AnnotationConfigWebApplicationContext context=new org.springframework.web.context.support.AnnotationConfigWebApplicationContext();
        context.setServletContext(new org.springframework.mock.web.MockServletContext());
        for(String name:new String[]{"systemProperties","systemEnvironment","servletContextInitParams","servletConfigInitParams","jndiProperties"})context.getEnvironment().getPropertySources().remove(name);
        java.util.Map<String,Object> properties=WebLoopbackApiFixture.applicationSmtpProperties(43307);
        properties.put("ra.ai.api.enabled",global);properties.put("ra.export.xlsx.enabled",file);
        properties.put("ra.ai.gateway.enabled","false");properties.put("ra.ai.gateway.outbound-approved","false");
        properties.put("ra.ai.analysis.result-ttl-seconds","900");properties.put("ra.ai.analysis.result-max-bytes","8388608");
        properties.put("ra.ai.analysis.cursor-key-base64","MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
        properties.put("ra.serology.rf-uln","20");properties.put("ra.serology.ccp-uln","25");
        context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("synthetic-only",properties));
        javax.sql.DataSource datasource=(javax.sql.DataSource)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{javax.sql.DataSource.class},(proxy,method,args)->{
            if(method.getName().equals("getConnection")){connections.incrementAndGet();throw new AssertionError("真实装配或关闭门不得借SQL");}
            if(method.getName().equals("toString"))return "synthetic-not-connected-datasource";
            if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);
            if(method.getName().equals("equals"))return proxy==args[0];
            throw new AssertionError("非预定DataSource方法");
        });
        context.addBeanFactoryPostProcessor(factory->{factory.registerSingleton("observedDataSource",datasource);factory.registerSingleton("principalProvider",(com.wenwen.ai.scope.PrincipalProvider)fixture::scenarioPrincipal);factory.registerSingleton("clock",fixture.scenarioClock);});
        context.register(fixture.contextConfiguration());return context;
    }
    @Test void p07GlobalGateRejectsEveryRealRouteBeforeBodyAndSqlIncludingAnonymousPriority() throws Exception {
        WebLoopbackApiFixture fixture=new WebLoopbackApiFixture();fixture.scenario="p07-composition";
        java.util.concurrent.atomic.AtomicInteger connections=new java.util.concurrent.atomic.AtomicInteger();
        org.springframework.web.context.support.AnnotationConfigWebApplicationContext context=compositionUnitContext(fixture,connections,"false","false");
        final java.util.concurrent.atomic.AtomicReference<WebLoopbackApiFixture.CountingRequest> observed=new java.util.concurrent.atomic.AtomicReference<>();
        try {
            context.refresh();
            javax.servlet.Filter observer=(request,response,chain)->{WebLoopbackApiFixture.CountingRequest counted=new WebLoopbackApiFixture.CountingRequest((javax.servlet.http.HttpServletRequest)request);observed.set(counted);chain.doFilter(counted,response);};
            org.springframework.test.web.servlet.MockMvc http=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context).addFilters(observer).build();
            for(String path:WebLoopbackApiFixture.COMPOSITION_PATHS)for(String body:new String[]{"{}","{"}) {
                org.springframework.test.web.servlet.MvcResult result=http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).contentType("application/json").content(body)).andReturn();
                assertEquals(503,result.getResponse().getStatus(),path);assertEquals(0,observed.get().bodyBytes,path);
                com.fasterxml.jackson.databind.JsonNode envelope=WebLoopbackApiFixture.JSON.readTree(result.getResponse().getContentAsByteArray());assertEquals("SERVICE_UNAVAILABLE",envelope.path("code").asText());assertEquals("队列功能暂不可用",envelope.path("message").asText());assertFalse(envelope.path("success").asBoolean());assertTrue(envelope.path("data").isNull());assertNotNull(result.getResponse().getHeader("X-Trace-Id"));
            }
            fixture.scenarioDoctor.set(0);
            for(String path:WebLoopbackApiFixture.COMPOSITION_PATHS){org.springframework.test.web.servlet.MvcResult result=http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).contentType("application/json").content("{")).andReturn();assertEquals(401,result.getResponse().getStatus(),path);assertEquals(0,observed.get().bodyBytes,path);}
            assertEquals(0,connections.get());
        } finally {context.close();}
        assertFalse(context.isActive());assertEquals(0,connections.get());
    }
    @Test void p07FileGateLeavesOtherFourteenControllersReadingTheirOwnProtocol() throws Exception {
        WebLoopbackApiFixture fixture=new WebLoopbackApiFixture();fixture.scenario="p07-composition";
        java.util.concurrent.atomic.AtomicInteger connections=new java.util.concurrent.atomic.AtomicInteger();
        org.springframework.web.context.support.AnnotationConfigWebApplicationContext context=compositionUnitContext(fixture,connections,"true","false");
        final java.util.concurrent.atomic.AtomicReference<WebLoopbackApiFixture.CountingRequest> observed=new java.util.concurrent.atomic.AtomicReference<>();
        try {
            context.refresh();javax.servlet.Filter observer=(request,response,chain)->{WebLoopbackApiFixture.CountingRequest counted=new WebLoopbackApiFixture.CountingRequest((javax.servlet.http.HttpServletRequest)request);observed.set(counted);chain.doFilter(counted,response);};
            org.springframework.test.web.servlet.MockMvc http=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context).addFilters(observer).build();
            for(String path:WebLoopbackApiFixture.COMPOSITION_PATHS) {
                org.springframework.test.web.servlet.MvcResult result=http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).contentType("application/json").content("{")).andReturn();
                if(path.equals("/api/ra/export/xlsx")){assertEquals(503,result.getResponse().getStatus());assertEquals(0,observed.get().bodyBytes);assertEquals("文件导出暂不可用",WebLoopbackApiFixture.JSON.readTree(result.getResponse().getContentAsByteArray()).path("message").asText());assertNull(result.getResponse().getHeader("Content-Disposition"));}
                else {assertEquals(400,result.getResponse().getStatus(),path);assertEquals(1,observed.get().bodyBytes,path);}
            }
            assertEquals(0,connections.get());
        } finally {context.close();}
        assertFalse(context.isActive());assertEquals(0,connections.get());
    }
    @Test void p07CompositionHasOnlyExistingOrdinarySeedExactControlsAndFixedDayBoundary() throws Exception {
        for(String scenario:new String[]{"p07-composition","p07-composition-time"}) {
            assertEquals("seed-p06.sql",WebLoopbackApiFixture.seedForScenario(scenario));
            assertEquals(37,WebLoopbackApiFixture.resource(WebLoopbackApiFixture.seedForScenario(scenario)).split("INSERT INTO patient_basic_info ",-1).length-1);
            for(String path:WebLoopbackApiFixture.COMPOSITION_PATHS){assertTrue(WebLoopbackApiFixture.allowsHttpPath(scenario,path));assertFalse(WebLoopbackApiFixture.allowsHttpPath(scenario,path+"/"));assertFalse(WebLoopbackApiFixture.allowsHttpPath(scenario,path+"?x=1"));}
            for(String path:new String[]{"/api/ra/patientDetail","/api/ra/ai/control","/api/ra/export/apply","/api/ra/export/range"})assertFalse(WebLoopbackApiFixture.allowsHttpPath(scenario,path));
            WebLoopbackApiFixture fixture=new WebLoopbackApiFixture();fixture.scenario=scenario;
            assertFalse(fixture.control("CONTROL 1 SELECT_SQL"));assertFalse(fixture.control("CONTROL 1 IDENTITY_202"));
            assertTrue(fixture.control("CONTROL 1 CLOCK_DAY_BEFORE"));assertEquals(java.time.Instant.parse("2026-10-31T15:59:59.999Z"),fixture.scenarioClock.instant());assertEquals(java.time.LocalDate.parse("2026-10-31"),java.time.LocalDate.now(fixture.scenarioClock.withZone(java.time.ZoneId.of("Asia/Shanghai"))));
            assertTrue(fixture.control("CONTROL 2 CLOCK_DAY_AFTER"));assertEquals(java.time.Instant.parse("2026-10-31T16:00:00Z"),fixture.scenarioClock.instant());assertEquals(java.time.LocalDate.parse("2026-11-01"),java.time.LocalDate.now(fixture.scenarioClock.withZone(java.time.ZoneId.of("Asia/Shanghai"))));
            assertTrue(fixture.control("CONTROL 3 CLOCK_EXACT_EXPIRY"));assertEquals(java.time.Instant.parse("2026-10-31T16:14:59.999Z"),fixture.scenarioClock.instant());
            assertTrue(fixture.control("CONTROL 4 IDENTITY_ANONYMOUS"));assertThrows(com.wenwen.ai.query.CohortException.class,fixture::scenarioPrincipal);assertTrue(fixture.control("CONTROL 5 IDENTITY_101"));assertEquals(101,fixture.scenarioPrincipal().getId());
            assertThrows(IllegalStateException.class,()->fixture.control("CONTROL 6 REBUILD_ENABLED"));assertEquals(5,fixture.controlSeq);
            fixture.close();assertFalse(fixture.control("CONTROL 6 CLOCK_DAY_BEFORE"));
        }
    }
    @Test void explicitDatabaseAndIsolationRejectBeforeSchema() {
        assertDoesNotThrow(() -> WebLoopbackApiFixture.requireDatabase("ra_synthetic_test","REPEATABLE-READ"));
        assertThrows(IllegalStateException.class, () -> WebLoopbackApiFixture.requireDatabase("other_schema","REPEATABLE-READ"));
        assertThrows(IllegalStateException.class, () -> WebLoopbackApiFixture.requireDatabase(null,"REPEATABLE-READ"));
        assertThrows(IllegalStateException.class, () -> WebLoopbackApiFixture.requireDatabase("ra_synthetic_test","READ-COMMITTED"));
    }
    @Test void guardDoesNotRequireInventedUsernameWhitelist() {
        assertDoesNotThrow(() -> CohortHttpFixture.guard("jdbc:mysql://127.0.0.1:43306/ra_synthetic_test","explicit-test-user","synthetic-only"));
        assertThrows(IllegalStateException.class, () -> CohortHttpFixture.guard("jdbc:mysql://127.0.0.1:43306/other_schema","explicit-test-user","synthetic-only"));
    }
    @Test void ownedControlsKeepFixedScenarioAndAtomicVisibility() throws Exception {
        WebLoopbackApiFixture f=new WebLoopbackApiFixture();
        assertFalse(f.control("CONTROL 1 IDENTITY_202"));assertEquals(101,f.scenarioPrincipal().getId());
        f.scenario="p01b";
        assertFalse(f.control("CONTROL 1 SELECT_SQL"));assertFalse(f.control("CONTROL 0 IDENTITY_202"));
        assertTrue(f.control("CONTROL 1 IDENTITY_202"));assertEquals(202,f.scenarioPrincipal().getId());
        assertFalse(f.control("CONTROL 1 IDENTITY_101"));assertFalse(f.control("CONTROL 3 IDENTITY_101"));
        assertTrue(f.control("CONTROL 2 IDENTITY_ANONYMOUS"));assertThrows(com.wenwen.ai.query.CohortException.class,f::scenarioPrincipal);
        assertTrue(f.control("CONTROL 3 IDENTITY_101"));assertEquals(101,f.scenarioPrincipal().getId());
        java.time.Clock otherZone=f.scenarioClock.withZone(java.time.ZoneId.of("Asia/Shanghai"));
        java.util.concurrent.ExecutorService executor=java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            java.time.Instant before=executor.submit(otherZone::instant).get(5,java.util.concurrent.TimeUnit.SECONDS);
            assertTrue(f.control("CONTROL 4 ADVANCE_TTL"));
            assertEquals(before.plusMillis(900000),executor.submit(otherZone::instant).get(5,java.util.concurrent.TimeUnit.SECONDS));
        } finally {executor.shutdownNow();assertTrue(executor.awaitTermination(5,java.util.concurrent.TimeUnit.SECONDS));}
        assertFalse(f.control("CONTROL 5 ADVANCE_TTL /tmp/file"));
    }
    @Test void p02SelectsOnlyOrdinarySixPatientSeedAndRejectsControls() throws Exception {
        assertEquals("seed.sql",WebLoopbackApiFixture.seedForScenario("default"));
        assertEquals("seed-p01b.sql",WebLoopbackApiFixture.seedForScenario("p01b"));
        assertEquals("seed-p02.sql",WebLoopbackApiFixture.seedForScenario("p02"));
        assertThrows(IllegalArgumentException.class,()->WebLoopbackApiFixture.seedForScenario("../../other"));
        assertThrows(IllegalArgumentException.class,()->WebLoopbackApiFixture.seedForScenario(null));
        assertTrue(WebLoopbackApiFixture.resource(WebLoopbackApiFixture.seedForScenario("p02")).contains("(101,3,0,1)"));
        WebLoopbackApiFixture f=new WebLoopbackApiFixture();f.scenario="p02";
        assertFalse(f.control("CONTROL 1 IDENTITY_202"));assertFalse(f.control("CONTROL 1 ADVANCE_TTL"));
        assertEquals(101,f.scenarioPrincipal().getId());assertEquals(java.time.Instant.parse("2026-10-07T02:00:00Z"),f.scenarioClock.instant());
    }
    @Test void p03ReusesOrdinarySeedAndOwnedControls() throws Exception {
        assertEquals("seed-p01b.sql",WebLoopbackApiFixture.seedForScenario("p03"));
        WebLoopbackApiFixture f=new WebLoopbackApiFixture();f.scenario="p03";
        assertTrue(f.control("CONTROL 1 IDENTITY_202"));assertEquals(202,f.scenarioPrincipal().getId());
        assertFalse(f.control("CONTROL 2 SELECT_SQL"));assertTrue(f.control("CONTROL 2 IDENTITY_ANONYMOUS"));assertThrows(com.wenwen.ai.query.CohortException.class,f::scenarioPrincipal);
        assertTrue(f.control("CONTROL 3 IDENTITY_101"));assertTrue(f.control("CONTROL 4 ADVANCE_TTL"));assertEquals(java.time.Instant.parse("2026-10-07T02:15:00Z"),f.scenarioClock.instant());
    }
    @Test void p03PrivateDdlRegistersOnlySuccessfulCreates() throws Exception {
        java.util.List<String> sql=WebLoopbackApiFixture.privateStatements();assertEquals(2,sql.size());
        assertTrue(sql.get(0).contains("CREATE TABLE ra_private_cohort_definition ("));assertTrue(sql.get(1).contains("CREATE TABLE ra_private_cohort_command ("));
        WebLoopbackApiFixture f=new WebLoopbackApiFixture();java.util.concurrent.atomic.AtomicInteger called=new java.util.concurrent.atomic.AtomicInteger();
        // JDBC边界替身仅验证fixture登记时序；真实MySQL冲突/自动清理由T3同case另证。
        java.sql.Statement statement=(java.sql.Statement)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{java.sql.Statement.class},(proxy,method,args)->{
            if(method.getName().equals("execute")){
                int index=called.getAndIncrement();assertEquals(index,f.ownedTables.size());assertEquals(sql.get(index),args[0]);
                if(index==1)throw new java.sql.SQLException("synthetic CREATE collision","42S01",1050);return false;
            }
            throw new AssertionError("非预定JDBC方法");
        });
        assertThrows(java.sql.SQLException.class,()->f.createPrivateTables(statement));assertEquals(java.util.Collections.singletonList("ra_private_cohort_definition"),f.ownedTables);assertEquals(2,called.get());
    }
    @Test void p03ExposesOnlyExactPrivateCommandPaths() {
        for(String scenario:new String[]{"default","p01b","p02","p03"}) {
            assertTrue(WebLoopbackApiFixture.allowsHttpPath(scenario,"/api/ra/ai/cohort"));
            assertTrue(WebLoopbackApiFixture.allowsHttpPath(scenario,"/api/ra/ai/cohortPatients"));
            for(String action:new String[]{"save","list","open","rename","delete"})
                assertEquals("p03".equals(scenario),WebLoopbackApiFixture.allowsHttpPath(scenario,"/api/ra/cohort/"+action));
            for(String path:new String[]{"/api/ra/cohort/rename/","/api/ra/cohort/delete?x=1","/api/ra/cohort/compare","/api/ra/cohort/export","/api/ra/cohort/control"})
                assertFalse(WebLoopbackApiFixture.allowsHttpPath(scenario,path));
        }
    }

    @Test void p04UsesOnlyFixedFourteenPatientFactsAndOwnedControls() throws Exception {
        assertEquals("seed-p04.sql",WebLoopbackApiFixture.seedForScenario("p04"));
        String seed=WebLoopbackApiFixture.resource("seed-p04.sql");
        assertEquals(14,seed.split("INSERT INTO patient_basic_info ",-1).length-1);
        assertEquals(14,seed.split("INSERT INTO patient_relation_doctor ",-1).length-1);
        assertEquals(19,seed.split("INSERT INTO patient_follow_up_history ",-1).length-1);
        assertFalse(seed.contains("INSERT INTO patient_comorbidity"));
        String[] scores={"1","2","2.3","2.7","3","4","4.1","4.5","4.6","4.7","5","5.1","6","6.1"};
        String[] crp={"0.4","0.8","1.2","1.6","2.0","2.4","2.8","3.2","3.6","4.0","4.4","4.8","5.2","5.6"};
        String[] haq={"0.1","0.2","0.3","0.3","0.4","0.5","0.6",null,"0.8","0.9","1.0","1.1","1.2","1.3"};
        String[] birthdays={"20061007","20051007","20041007","20031007","20021007","20011007","20001007","19991007","19981007","19971007","19961007","19951007","19941007","19931007"};
        String[] diagnosis={"2025-10-07","2024-10-07","2023-10-07","2022-10-07","2021-10-07","2020-10-07","2019-10-07","2018-10-07","2017-10-07","2016-10-07","2015-10-07","2014-10-07","2013-10-07","2012-10-07"};
        int[] gender={2,1,1,1,1,1,2,1,2,2,2,2,2,1};
        String[] rf={"35","20",null,"35","20","35","35",null,"20","35","35","35","20","20"};
        String[] drugs={"甲氨蝶呤","甲氨蝶呤","阿达木单抗","阿达木单抗","依那西普","依那西普","synthetic-unmapped-drug-CANARY","阿达木单抗","甲氨蝶呤","阿达木单抗","阿达木单抗","依那西普","依那西普","依那西普"};
        java.util.Set<Integer> complete=new java.util.HashSet<>(java.util.Arrays.asList(1,2,4,7,8,9,11,12,14));
        java.util.Set<Integer> past=new java.util.HashSet<>(java.util.Arrays.asList(5,6,12,13,14));
        for(int i=1;i<=14;i++) {
            assertTrue(seed.contains("VALUES ("+i+",'synthetic-comparison-name-CANARY',"+gender[i-1]+",'110101"+birthdays[i-1]+"0011','"+diagnosis[i-1]+"',"+(complete.contains(i)?"'{\"record\":1}'":"NULL")+");"));
            assertTrue(seed.contains("'110101"+birthdays[i-1]+"0011','"+diagnosis[i-1]+"'"));
            assertTrue(seed.contains("VALUES (101,"+i+",0,0);"));
            final int patient=i;
            String main=java.util.Arrays.stream(seed.split("\n")).filter(line->line.startsWith("INSERT INTO patient_follow_up_history ")&&line.contains("VALUES ("+(100+patient)+","+patient+",0,")).findFirst().orElseThrow(()->new AssertionError("缺固定访视"));
            assertTrue(main.contains("'2026-10-07'"));
            assertTrue(main.contains("\"crpScore\":"+scores[i-1]+"}"));
            assertTrue(main.contains("\"cfydb\":"+crp[i-1]));
            if(rf[i-1]==null)assertFalse(main.contains("lfsyz"));
            else assertTrue(main.contains("\"lfsyz\":"+rf[i-1]+"}"));
            assertTrue(main.contains("\"drugName\":\""+drugs[i-1]+"\",\"startTime\":\"2026-04-01\",\"endTime\":null"));
            assertEquals(past.contains(i),seed.contains("VALUES ("+(200+i)+","+i+",0,'2025-02-01',NULL,'{\"record\":1}','{\"xyList\":[{\"drugName\":\"阿达木单抗\",\"startTime\":\"2025-01-01\",\"endTime\":\"2025-03-01\"}]}');"));
            if(haq[i-1]==null)assertFalse(main.contains("hqaScore"));
            else assertTrue(main.contains("\"hqaScore\":"+haq[i-1]+"}"));
        }
        WebLoopbackApiFixture f=new WebLoopbackApiFixture();f.scenario="p04";
        assertTrue(f.control("CONTROL 1 IDENTITY_202"));assertEquals(202,f.scenarioPrincipal().getId());
        assertFalse(f.control("CONTROL 2 SELECT_SQL"));assertTrue(f.control("CONTROL 2 IDENTITY_ANONYMOUS"));assertThrows(com.wenwen.ai.query.CohortException.class,f::scenarioPrincipal);
        assertTrue(f.control("CONTROL 3 IDENTITY_101"));assertTrue(f.control("CONTROL 4 ADVANCE_TTL"));
        assertEquals(java.time.Instant.parse("2026-10-07T02:15:00Z"),f.scenarioClock.instant());
        assertFalse(f.control("CONTROL 4 ADVANCE_TTL"));assertFalse(f.control("CONTROL 6 IDENTITY_202"));
    }
    @Test void p04ExposesCompareAndRetainedReadWithPrivateCommands() {
        for(String action:new String[]{"save","list","open","rename","delete"})
            assertTrue(WebLoopbackApiFixture.allowsHttpPath("p04","/api/ra/cohort/"+action));
        assertTrue(WebLoopbackApiFixture.allowsHttpPath("p04","/api/ra/ai/cohort"));
        assertTrue(WebLoopbackApiFixture.allowsHttpPath("p04","/api/ra/ai/cohortPatients"));
        assertTrue(WebLoopbackApiFixture.allowsHttpPath("p04","/api/ra/ai/compare"));
        assertTrue(WebLoopbackApiFixture.allowsHttpPath("p04","/api/ra/ai/compareResult"));
        for(String scenario:new String[]{"default","p01b","p02","p03"})
            {assertFalse(WebLoopbackApiFixture.allowsHttpPath(scenario,"/api/ra/ai/compare"));assertFalse(WebLoopbackApiFixture.allowsHttpPath(scenario,"/api/ra/ai/compareResult"));}
        for(String path:new String[]{"/api/ra/ai/compare/","/api/ra/ai/compare?x=1","/api/ra/ai/compareResult/","/api/ra/ai/compareResult?x=1","/api/ra/cohort/compare","/api/ra/ai/control","/api/ra/ai/export"})
            assertFalse(WebLoopbackApiFixture.allowsHttpPath("p04",path));
    }
    @Test void p04ComparisonDdlRegistersOnlySuccessfulCreate() throws Exception {
        java.util.List<String> sql=WebLoopbackApiFixture.comparisonStatements();assertEquals(1,sql.size());
        assertTrue(sql.get(0).contains("CREATE TABLE ra_ai_compare_run ("));
        WebLoopbackApiFixture failed=new WebLoopbackApiFixture();failed.ownedTables.addAll(java.util.Arrays.asList(WebLoopbackApiFixture.PRIVATE_TABLES));
        java.sql.Statement collision=(java.sql.Statement)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{java.sql.Statement.class},(proxy,method,args)->{
            if(method.getName().equals("execute")) {
                assertEquals(sql.get(0),args[0]);assertEquals(2,failed.ownedTables.size());
                throw new java.sql.SQLException("synthetic CREATE collision","42S01",1050);
            }
            throw new AssertionError("非预定JDBC方法");
        });
        assertThrows(java.sql.SQLException.class,()->failed.createComparisonTables(collision));
        assertEquals(java.util.Arrays.asList(WebLoopbackApiFixture.PRIVATE_TABLES),failed.ownedTables);
        WebLoopbackApiFixture created=new WebLoopbackApiFixture();
        java.sql.Statement success=(java.sql.Statement)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{java.sql.Statement.class},(proxy,method,args)->{
            if(method.getName().equals("execute")){assertEquals(sql.get(0),args[0]);assertTrue(created.ownedTables.isEmpty());return false;}
            throw new AssertionError("非预定JDBC方法");
        });
        created.createComparisonTables(success);assertEquals(java.util.Collections.singletonList("ra_ai_compare_run"),created.ownedTables);
    }
    @Test void p05ParseReusesOrdinarySeedAndExposesSameControllerApply() {
        for(String scenario:new String[]{"p05-rule","p05-safety"}) {
            assertEquals("seed-p01b.sql",WebLoopbackApiFixture.seedForScenario(scenario));
            assertTrue(WebLoopbackApiFixture.allowsHttpPath(scenario,"/api/ra/ai/parse"));
            assertTrue(WebLoopbackApiFixture.allowsHttpPath(scenario,"/api/ra/ai/applyParse"));
            assertFalse(WebLoopbackApiFixture.allowsHttpPath(scenario,"/api/ra/ai/compare"));
            assertFalse(WebLoopbackApiFixture.allowsHttpPath(scenario,"/api/ra/ai/parse/"));
        }
        assertFalse(WebLoopbackApiFixture.allowsHttpPath("p04","/api/ra/ai/parse"));
    }
    @Test void parseAuditRegistersOnlySuccessfulCreate() throws Exception {
        String sql=WebLoopbackApiFixture.parseAuditStatement();assertTrue(sql.contains("CREATE TABLE ra_ai_parse_audit ("));
        WebLoopbackApiFixture f=new WebLoopbackApiFixture();
        java.sql.Statement failed=(java.sql.Statement)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{java.sql.Statement.class},(proxy,method,args)->{if(method.getName().equals("execute")){assertEquals(sql,args[0]);assertTrue(f.ownedTables.isEmpty());throw new java.sql.SQLException("synthetic CREATE collision","42S01",1050);}throw new AssertionError("非预定JDBC方法");});
        assertThrows(java.sql.SQLException.class,()->f.createParseAuditTable(failed));assertTrue(f.ownedTables.isEmpty());
        java.sql.Statement successful=(java.sql.Statement)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{java.sql.Statement.class},(proxy,method,args)->{if(method.getName().equals("execute")){assertEquals(sql,args[0]);assertTrue(f.ownedTables.isEmpty());return false;}throw new AssertionError("非预定JDBC方法");});
        f.createParseAuditTable(successful);assertEquals(java.util.Collections.singletonList("ra_ai_parse_audit"),f.ownedTables);
    }
    @Test void p05ApplyUsesOnlyNewOrdinaryFactsAndExactTwoPaths() throws Exception {
        for(String scenario:new String[]{"p05-apply-rule","p05-apply-safety"}) {
            assertEquals("seed-p05.sql",WebLoopbackApiFixture.seedForScenario(scenario));
            WebLoopbackApiFixture fixture=new WebLoopbackApiFixture();fixture.scenario=scenario;assertTrue(fixture.parseScenario());
            assertTrue(WebLoopbackApiFixture.allowsHttpPath(scenario,"/api/ra/ai/parse"));assertTrue(WebLoopbackApiFixture.allowsHttpPath(scenario,"/api/ra/ai/applyParse"));
            for(String path:new String[]{"/api/ra/ai/parse/","/api/ra/ai/applyParse/","/api/ra/ai/compare","/api/ra/cohort/open","/api/ra/ai/export"})assertFalse(WebLoopbackApiFixture.allowsHttpPath(scenario,path));
        }
        for(String scenario:new String[]{"default","p01b","p02","p03","p04"})assertFalse(WebLoopbackApiFixture.allowsHttpPath(scenario,"/api/ra/ai/applyParse"));
        String seed=WebLoopbackApiFixture.resource("seed-p05.sql");assertEquals(5,seed.split("INSERT INTO patient_basic_info ",-1).length-1);assertEquals(5,seed.split("INSERT INTO patient_relation_doctor ",-1).length-1);assertEquals(10,seed.split("INSERT INTO patient_follow_up_history ",-1).length-1);
        assertTrue(seed.contains("VALUES (101,9223372036854775807,0,0)"));assertTrue(seed.contains("VALUES (202,4,0,0)"));assertFalse(seed.contains("VALUES (101,4,0,0)"));
        assertTrue(seed.contains("VALUES (1,'P05合成女TNFi','P05-00001',2"));assertTrue(seed.contains("VALUES (2,'P05合成男TNFi','P05-00002',1"));assertTrue(seed.contains("VALUES (3,'P05合成女传统药','P05-00003',2"));
        assertTrue(seed.contains("2026-04-05"));assertTrue(seed.contains("2026-10-06"));assertTrue(seed.contains("2026-04-06"));assertTrue(seed.contains("依那西普"));assertTrue(seed.contains("甲氨蝶呤"));
    }

    @Test void p07EntryExposesOnlyCurrentEntryAndAnalysisPaths() {
        for(String path:new String[]{"/api/ra/ai/cohort","/api/ra/ai/cohortPatients","/api/ra/ai/parse","/api/ra/ai/applyParse","/api/ra/ai/resolveEntry"}) {
            assertTrue(WebLoopbackApiFixture.allowsHttpPath("p07-entry",path),path);
            assertFalse(WebLoopbackApiFixture.allowsHttpPath("p07-entry",path+"/"),path);
            assertFalse(WebLoopbackApiFixture.allowsHttpPath("p07-entry",path+"?x=1"),path);
        }
        for(String path:new String[]{"/api/ra/ai/resolveEntryOther","/api/ra/ai/compare","/api/ra/cohort/open","/api/ra/export/xlsx","/api/ra/export/applyAnalysis","/api/ra/export/retryNotification","/api/ra/ai/control"})
            assertFalse(WebLoopbackApiFixture.allowsHttpPath("p07-entry",path),path);
        for(String scenario:new String[]{"default","p01b","p02","p03","p04","p05-rule","p05-apply-rule","p06-cohort","p06-comparison"})
            assertFalse(WebLoopbackApiFixture.allowsHttpPath(scenario,"/api/ra/ai/resolveEntry"),scenario);
    }

    @Test void p07EntrySelectsIndependentSixPatientSeed() throws Exception {
        assertEquals("seed-p07.sql",WebLoopbackApiFixture.seedForScenario("p07-entry"));
        String seed=WebLoopbackApiFixture.resource("seed-p07.sql");
        assertEquals(6,seed.split("INSERT INTO patient_basic_info ",-1).length-1);
        assertEquals(6,seed.split("INSERT INTO patient_relation_doctor ",-1).length-1);
        assertEquals(6,seed.split("INSERT INTO patient_follow_up_history ",-1).length-1);
        assertTrue(seed.contains("VALUES (11,'P07合成索引女','P07-00011',2,NULL"));
        assertTrue(seed.contains("VALUES (9007199254740993,'P07合成长ID女','P07-LONG',2,NULL"));
        assertTrue(seed.contains("VALUES (21,'P07合成男','P07-00021',1,NULL"));
        assertTrue(seed.contains("VALUES (22,'P07合成未知','P07-00022',NULL,NULL"));
        for(String id:new String[]{"11","9007199254740993","21","22"})assertTrue(seed.contains("VALUES (101,"+id+",0,0)"),id);
        assertTrue(seed.contains("VALUES (202,23,0,0)"));assertFalse(seed.contains("VALUES (101,23,0,0)"));
        assertTrue(seed.contains("VALUES (101,24,6,0)"));assertFalse(seed.contains("VALUES (101,24,0,0)"));
        assertTrue(seed.contains("VALUES (23,'P07其它医生女','P07-00023',2,NULL"));
        assertTrue(seed.contains("VALUES (24,'P07非RA女','P07-00024',2,NULL"));
        assertFalse(seed.contains("lfsyz"));assertFalse(seed.contains("kccpkt"));
        assertFalse(seed.contains("UPDATE "));assertFalse(seed.contains("DELETE "));assertFalse(seed.contains("CREATE TABLE "));
    }

    @Test void p07EntryAssemblesRealResolverAndRuleParseWithoutConnectingSql() throws Exception {
        WebLoopbackApiFixture fixture=new WebLoopbackApiFixture();fixture.scenario="p07-entry";
        assertTrue(fixture.parseScenario(),"p07-entry必须沿真实parse/apply最小装配");
        assertFalse(fixture.applicationScenario());
        assertFalse(fixture.control("CONTROL 1 IDENTITY_202"));
        assertFalse(fixture.control("CONTROL 1 ADVANCE_TTL"));
        org.springframework.web.context.support.AnnotationConfigWebApplicationContext context=new org.springframework.web.context.support.AnnotationConfigWebApplicationContext();
        context.setServletContext(new org.springframework.mock.web.MockServletContext());
        for(String name:new String[]{"systemProperties","systemEnvironment","servletContextInitParams","servletConfigInitParams","jndiProperties"})context.getEnvironment().getPropertySources().remove(name);
        java.util.Map<String,Object> properties=new java.util.LinkedHashMap<>();
        properties.put("ra.ai.api.enabled","true");properties.put("ra.export.xlsx.enabled","false");
        properties.put("ra.ai.gateway.enabled","false");properties.put("ra.ai.gateway.outbound-approved","false");
        properties.put("ra.ai.analysis.result-ttl-seconds","900");properties.put("ra.ai.analysis.result-max-bytes","8388608");
        properties.put("ra.ai.analysis.cursor-key-base64","MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
        properties.put("ra.serology.rf-uln","20");properties.put("ra.serology.ccp-uln","25");
        context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("synthetic-only",properties));
        java.util.concurrent.atomic.AtomicInteger connections=new java.util.concurrent.atomic.AtomicInteger();
        javax.sql.DataSource datasource=(javax.sql.DataSource)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{javax.sql.DataSource.class},(proxy,method,args)->{
            if(method.getName().equals("getConnection")){connections.incrementAndGet();throw new AssertionError("准入装配和格式拒绝不应连接SQL");}
            if(method.getName().equals("toString"))return "synthetic-entry-not-connected-datasource";
            if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);
            if(method.getName().equals("equals"))return proxy==args[0];
            throw new AssertionError("非预定DataSource方法");
        });
        context.addBeanFactoryPostProcessor(factory->{factory.registerSingleton("observedDataSource",datasource);factory.registerSingleton("principalProvider",(com.wenwen.ai.scope.PrincipalProvider)()->new com.wenwen.ai.scope.TrustedDoctor(101));});
        context.register(WebLoopbackApiFixture.ParseInfrastructure.class);
        try {
            context.refresh();assertEquals(0,connections.get());
            assertEquals(1,context.getBeansOfType(com.wenwen.controller.ra.AiCohortController.class).size());
            assertEquals(1,context.getBeansOfType(com.wenwen.service.impl.AiCohortServiceImpl.class).size());
            assertEquals(1,context.getBeansOfType(com.wenwen.ai.source.CohortSourceAdapter.class).size());
            assertEquals(1,context.getBeansOfType(com.wenwen.controller.ra.AiParseController.class).size());
            assertEquals(1,context.getBeansOfType(com.wenwen.service.impl.AiParseServiceImpl.class).size());
            assertEquals(1,context.getBeansOfType(com.wenwen.ai.parse.ParseAuditStore.class).size());
            assertEquals(1,context.getBeansOfType(com.wenwen.ai.parse.AiParseGateway.class).size());
            assertEquals(1,context.getBeansOfType(com.wenwen.ai.parse.AiGatewaySettings.class).size());
            assertEquals("false",context.getEnvironment().getProperty("ra.ai.gateway.enabled"));
            assertEquals("false",context.getEnvironment().getProperty("ra.ai.gateway.outbound-approved"));
            com.wenwen.ai.parse.AiGatewayResult ruleOnly=context.getBean(com.wenwen.ai.parse.AiParseGateway.class).parse("女性",new com.wenwen.ai.parse.RuleParser().parse("女性"),new com.wenwen.ai.scope.TrustedDoctor(101));
            assertEquals("AI_NOT_ENABLED",ruleOnly.getFallback());assertNull(ruleOnly.getCandidate());assertNull(ruleOnly.getModelVersion());
            assertTrue(context.getBeansOfType(com.wenwen.controller.ra.AiExportController.class).isEmpty());
            assertTrue(context.getBeansOfType(com.wenwen.controller.ra.PrivateCohortController.class).isEmpty());
            org.apache.ibatis.session.Configuration configuration=context.getBean(org.apache.ibatis.session.SqlSessionFactory.class).getConfiguration();
            assertTrue(configuration.hasStatement("com.wenwen.mapper.AiCohortSourceMapper.patientIds"));
            assertTrue(configuration.hasStatement("com.wenwen.mapper.AiParseAuditMapper.insert"));
            org.springframework.test.web.servlet.MockMvc http=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context).build();
            org.springframework.test.web.servlet.MvcResult result=http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/ra/ai/resolveEntry").contentType("application/json").content("{\"source\":\"SIMILAR\",\"indexPatientId\":\"0\"}")).andReturn();
            assertEquals(400,result.getResponse().getStatus());
            com.fasterxml.jackson.databind.JsonNode envelope=WebLoopbackApiFixture.JSON.readTree(result.getResponse().getContentAsByteArray());
            assertFalse(envelope.path("success").asBoolean());assertEquals("INVALID_REQUEST",envelope.path("code").asText());assertTrue(envelope.path("data").isNull());
            assertNotNull(result.getResponse().getHeader("X-Trace-Id"));assertEquals(0,connections.get());
        } finally {context.close();}
        assertFalse(context.isActive());assertEquals(0,connections.get());
    }

    @Test void p06ComparisonReusesFixedFactsAndOnlyNecessaryRoutes() throws Exception {
        assertEquals("seed-p04.sql",WebLoopbackApiFixture.seedForScenario("p06-comparison"));
        String seed=WebLoopbackApiFixture.resource(WebLoopbackApiFixture.seedForScenario("p06-comparison"));
        assertEquals(14,seed.split("INSERT INTO patient_basic_info ",-1).length-1);
        assertEquals(14,seed.split("INSERT INTO patient_relation_doctor ",-1).length-1);
        assertEquals(19,seed.split("INSERT INTO patient_follow_up_history ",-1).length-1);
        WebLoopbackApiFixture f=new WebLoopbackApiFixture();f.scenario="p06-comparison";
        assertEquals(101,f.scenarioPrincipal().getId());
        assertEquals(java.time.Instant.parse("2026-10-07T02:00:00Z"),f.scenarioClock.instant());
        assertFalse(f.control("CONTROL 1 ADVANCE_TTL"));assertFalse(f.control("CONTROL 1 IDENTITY_202"));
        for(String path:new String[]{"/api/ra/ai/cohort","/api/ra/ai/cohortPatients","/api/ra/cohort/save","/api/ra/cohort/list","/api/ra/ai/compare","/api/ra/ai/compareResult","/api/ra/export/xlsx"}) {
            assertTrue(WebLoopbackApiFixture.allowsHttpPath("p06-comparison",path),path);
            assertFalse(WebLoopbackApiFixture.allowsHttpPath("p06-comparison",path+"/"),path);
            assertFalse(WebLoopbackApiFixture.allowsHttpPath("p06-comparison",path+"?x=1"),path);
        }
        for(String path:new String[]{"/api/ra/cohort/open","/api/ra/cohort/rename","/api/ra/cohort/delete","/api/ra/ai/parse","/api/ra/ai/applyParse","/api/ra/export/applyAnalysis","/api/ra/export/retryNotification","/api/ra/ai/control"})
            assertFalse(WebLoopbackApiFixture.allowsHttpPath("p06-comparison",path),path);
        assertFalse(WebLoopbackApiFixture.allowsHttpPath("p04","/api/ra/export/xlsx"));
        assertFalse(WebLoopbackApiFixture.allowsHttpPath("p06-cohort","/api/ra/ai/compare"));
        assertEquals(WebLoopbackApiFixture.ComparisonInfrastructure.class,WebLoopbackApiFixture.ComparisonExportInfrastructure.class.getSuperclass());
        assertEquals(java.util.Arrays.asList(com.wenwen.controller.ra.AiExportController.class,com.wenwen.service.impl.AiExportServiceImpl.class,com.wenwen.ai.export.ExportContentReader.class,com.wenwen.ai.export.ExportFieldPolicy.class,com.wenwen.ai.export.OrdinaryXlsxWriter.class,com.wenwen.ai.export.ExportAuditStore.class),java.util.Arrays.asList(WebLoopbackApiFixture.ComparisonExportInfrastructure.class.getAnnotation(org.springframework.context.annotation.Import.class).value()));
    }
    @Test void p06ComparisonDdlPreservesSuccessfulOwnershipAtExportAuditCollision() throws Exception {
        WebLoopbackApiFixture f=new WebLoopbackApiFixture();f.scenario="p06-comparison";
        java.util.List<String> sql=new java.util.ArrayList<>(WebLoopbackApiFixture.privateStatements());
        sql.addAll(WebLoopbackApiFixture.comparisonStatements());sql.add(WebLoopbackApiFixture.exportAuditStatement());
        java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();
        java.sql.Statement statement=(java.sql.Statement)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{java.sql.Statement.class},(proxy,method,args)->{
            assertEquals("execute",method.getName());int index=calls.getAndIncrement();
            assertEquals(index,f.ownedTables.size());assertEquals(sql.get(index),args[0]);
            if(index==3)throw new java.sql.SQLException("synthetic export audit CREATE collision","42S01",1050);
            return false;
        });
        f.createPrivateTables(statement);f.createComparisonTables(statement);
        assertThrows(java.sql.SQLException.class,()->f.createExportAuditTable(statement));
        assertEquals(java.util.Arrays.asList("ra_private_cohort_definition","ra_private_cohort_command","ra_ai_compare_run"),f.ownedTables);
        assertEquals(4,calls.get());
    }
    @Test void p06OrdinaryExportHasExactSeedPathsAndCreateOwnership() throws Exception {
        assertEquals("seed-p06.sql",WebLoopbackApiFixture.seedForScenario("p06-cohort"));
        String seed=WebLoopbackApiFixture.resource("seed-p06.sql");
        assertEquals(37,seed.split("INSERT INTO patient_basic_info ",-1).length-1);
        assertEquals(37,seed.split("INSERT INTO patient_relation_doctor ",-1).length-1);
        assertEquals(37,seed.split("INSERT INTO patient_follow_up_history ",-1).length-1);
        assertFalse(seed.contains("INSERT INTO patient_comorbidity"));assertFalse(seed.contains("zlfa"));
        assertTrue(seed.contains("\"crpScore\":3.7"));assertTrue(seed.contains("\"crpScore\":0.1"));assertTrue(seed.contains("\"cfydb\":10"));assertTrue(seed.contains("\"cfydb\":46"));
        assertTrue(WebLoopbackApiFixture.allowsHttpPath("p06-cohort","/api/ra/export/xlsx"));
        for(String other:new String[]{"default","p01b","p02","p03","p04","p05-rule"})assertFalse(WebLoopbackApiFixture.allowsHttpPath(other,"/api/ra/export/xlsx"));
        for(String path:new String[]{"/api/ra/export/xlsx/","/api/ra/export/xlsx?x=1","/api/ra/ai/compare","/api/ra/export/apply","/api/ra/export/retryNotification"})assertFalse(WebLoopbackApiFixture.allowsHttpPath("p06-cohort",path));
        WebLoopbackApiFixture f=new WebLoopbackApiFixture();f.scenario="p06-cohort";assertFalse(f.control("CONTROL 1 ADVANCE_TTL"));assertEquals(101,f.scenarioPrincipal().getId());
        java.sql.Statement collision=(java.sql.Statement)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{java.sql.Statement.class},(proxy,method,args)->{assertEquals("execute",method.getName());assertEquals(WebLoopbackApiFixture.exportAuditStatement(),args[0]);assertTrue(f.ownedTables.isEmpty());throw new java.sql.SQLException("synthetic collision","42S01",1050);});
        assertThrows(java.sql.SQLException.class,()->f.createExportAuditTable(collision));assertTrue(f.ownedTables.isEmpty());
        java.sql.Statement success=(java.sql.Statement)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{java.sql.Statement.class},(proxy,method,args)->{assertEquals("execute",method.getName());assertTrue(f.ownedTables.isEmpty());return false;});
        f.createExportAuditTable(success);assertEquals(java.util.Collections.singletonList("ra_ai_export_audit"),f.ownedTables);
    }
    @Test void p06ApplicationUsesFixedThirtySevenPatientSeedAndExactRoutes() throws Exception {
        for(String scenario:new String[]{"p06-application-replay","p06-application-retry","p06-application-unknown"}) {
            assertEquals("seed-p06.sql",WebLoopbackApiFixture.seedForScenario(scenario));
            String seed=WebLoopbackApiFixture.resource(WebLoopbackApiFixture.seedForScenario(scenario));
            assertEquals(37,seed.split("INSERT INTO patient_basic_info ",-1).length-1);
            assertEquals(37,seed.split("INSERT INTO patient_relation_doctor ",-1).length-1);
            assertEquals(37,seed.split("INSERT INTO patient_follow_up_history ",-1).length-1);
            WebLoopbackApiFixture fixture=new WebLoopbackApiFixture();fixture.scenario=scenario;
            assertTrue(fixture.applicationScenario());assertEquals(101,fixture.scenarioPrincipal().getId());
            assertEquals(java.time.Instant.parse("2026-10-07T02:00:00Z"),fixture.scenarioClock.instant());
            for(String path:new String[]{"/api/ra/ai/cohort","/api/ra/ai/cohortPatients","/api/ra/export/xlsx","/api/ra/export/applyAnalysis","/api/ra/export/retryNotification"}) {
                assertTrue(WebLoopbackApiFixture.allowsHttpPath(scenario,path),path);
                assertFalse(WebLoopbackApiFixture.allowsHttpPath(scenario,path+"/"),path);
                assertFalse(WebLoopbackApiFixture.allowsHttpPath(scenario,path+"?x=1"),path);
            }
            for(String path:new String[]{"/api/ra/export/apply","/api/ra/export/visitsCsv","/api/ra/export/range","/api/ra/ai/compare","/api/ra/ai/compareResult","/api/ra/ai/parse","/api/ra/ai/resolveEntry","/api/ra/cohort/save","/api/ra/cohort/list","/api/ra/ai/control"})assertFalse(WebLoopbackApiFixture.allowsHttpPath(scenario,path),path);
            for(String command:new String[]{"ADVANCE_TTL","IDENTITY_202","SELECT_SQL","SMTP_SUCCESS","SMTP_ASSERT"})assertFalse(fixture.control("CONTROL 1 "+command));
        }
        assertFalse(WebLoopbackApiFixture.applicationScenario("p06-application-other"));
        assertThrows(IllegalArgumentException.class,()->WebLoopbackApiFixture.seedForScenario("p06-application-other"));
        assertEquals(NotificationSmtpPeer.Mode.SUCCESS,WebLoopbackApiFixture.applicationPeerMode("p06-application-replay"));
        assertEquals(NotificationSmtpPeer.Mode.RCPT_REJECT,WebLoopbackApiFixture.applicationPeerMode("p06-application-retry"));
        assertEquals(NotificationSmtpPeer.Mode.DATA_SILENT,WebLoopbackApiFixture.applicationPeerMode("p06-application-unknown"));
        assertThrows(IllegalArgumentException.class,()->WebLoopbackApiFixture.applicationPeerMode("p06-cohort"));
    }
    @Test void p06ApplicationDdlRejectsCollisionWithoutClaimingForeignTables() throws Exception {
        java.util.List<String> sql=WebLoopbackApiFixture.applicationStatements();assertEquals(2,sql.size());
        assertTrue(sql.get(0).contains("CREATE TABLE `export_application` ("));assertTrue(sql.get(1).contains("CREATE TABLE ra_ai_export_request ("));
        for(String statement:sql)assertFalse(statement.contains("IF NOT EXISTS"));
        for(int collisionIndex=0;collisionIndex<=2;collisionIndex++) {
            final int failAt=collisionIndex;WebLoopbackApiFixture fixture=new WebLoopbackApiFixture();
            java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();
            java.sql.Statement statement=(java.sql.Statement)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{java.sql.Statement.class},(proxy,method,args)->{
                assertEquals("execute",method.getName());int index=calls.getAndIncrement();
                assertEquals(index,fixture.ownedTables.size());assertEquals(sql.get(index),args[0]);
                if(index==failAt)throw new java.sql.SQLException("synthetic application CREATE collision","42S01",1050);return false;
            });
            if(failAt<2)assertThrows(java.sql.SQLException.class,()->fixture.createApplicationTables(statement));
            else fixture.createApplicationTables(statement);
            assertEquals(java.util.Arrays.asList(WebLoopbackApiFixture.APPLICATION_TABLES).subList(0,failAt),fixture.ownedTables);
            assertEquals(Math.min(failAt+1,2),calls.get());
        }
    }
    @Test void p06ApplicationSmtpSettingsAndDecodedCanaryChecksStaySynthetic() {
        java.util.Map<String,Object> properties=WebLoopbackApiFixture.applicationSmtpProperties(43307);
        assertEquals(8,properties.size());assertEquals("127.0.0.1",properties.get("ra.export.smtp.host"));
        assertEquals("LOOPBACK_PLAIN",properties.get("ra.export.smtp.mode"));assertEquals("",properties.get("ra.export.smtp.username"));assertEquals("",properties.get("ra.export.smtp.password"));
        com.wenwen.ai.export.mail.ExportNotificationSettings settings=new com.wenwen.config.ExportNotificationConfiguration().exportNotificationSettings(new org.springframework.core.env.StandardEnvironment(){
            @Override public String getProperty(String key,String fallback){Object value=properties.get(key);return value==null?fallback:value.toString();}
        });
        assertNull(settings.failure());assertEquals("fixture-from@notification.invalid",settings.from);assertEquals("fixture-admin@notification.invalid",settings.to);
        assertThrows(IllegalArgumentException.class,()->WebLoopbackApiFixture.applicationSmtpProperties(0));
        assertDoesNotThrow(()->WebLoopbackApiFixture.assertApplicationBody("申请编号：1\n引用人数：37\n申请字段：patientId,sex"));
        assertThrows(AssertionError.class,()->WebLoopbackApiFixture.assertApplicationBody("ordinary-application-canary"));
        for(int id=101;id<=137;id++) {
            final int patient=id;
            assertThrows(AssertionError.class,()->WebLoopbackApiFixture.assertApplicationBody("合成文件患者"+patient));
            assertThrows(AssertionError.class,()->WebLoopbackApiFixture.assertApplicationBody("EXPORT-"+patient));
        }
    }
    @Test void p06ApplicationContextAssemblesRealServicesAndBothMappersWithoutLegacyEndpoints() throws Exception {
        assertEquals(WebLoopbackApiFixture.ExportInfrastructure.class,WebLoopbackApiFixture.ApplicationInfrastructure.class.getSuperclass());
        org.springframework.web.context.support.AnnotationConfigWebApplicationContext context=new org.springframework.web.context.support.AnnotationConfigWebApplicationContext();
        context.setServletContext(new org.springframework.mock.web.MockServletContext());
        for(String name:new String[]{"systemProperties","systemEnvironment","servletContextInitParams","servletConfigInitParams","jndiProperties"})context.getEnvironment().getPropertySources().remove(name);
        java.util.Map<String,Object> properties=WebLoopbackApiFixture.applicationSmtpProperties(43307);
        properties.put("ra.ai.api.enabled","true");properties.put("ra.export.xlsx.enabled","true");
        properties.put("ra.ai.analysis.result-ttl-seconds","900");properties.put("ra.ai.analysis.result-max-bytes","8388608");
        properties.put("ra.ai.analysis.cursor-key-base64","MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
        properties.put("ra.serology.rf-uln","20");properties.put("ra.serology.ccp-uln","25");
        context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("synthetic-only",properties));
        java.util.concurrent.atomic.AtomicInteger connections=new java.util.concurrent.atomic.AtomicInteger();
        javax.sql.DataSource datasource=(javax.sql.DataSource)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{javax.sql.DataSource.class},(proxy,method,args)->{
            if(method.getName().equals("getConnection")){connections.incrementAndGet();throw new AssertionError("准入装配不应连接SQL");}
            if(method.getName().equals("toString"))return "synthetic-not-connected-datasource";
            if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);
            if(method.getName().equals("equals"))return proxy==args[0];
            throw new AssertionError("非预定DataSource方法");
        });
        context.addBeanFactoryPostProcessor(factory->{factory.registerSingleton("observedDataSource",datasource);factory.registerSingleton("principalProvider",(com.wenwen.ai.scope.PrincipalProvider)()->new com.wenwen.ai.scope.TrustedDoctor(101));});
        context.register(WebLoopbackApiFixture.ApplicationInfrastructure.class);
        try {
            context.refresh();assertEquals(0,connections.get());
            assertEquals(1,context.getBeansOfType(com.wenwen.controller.ra.AiExportApplicationController.class).size());
            assertEquals(1,context.getBeansOfType(com.wenwen.service.impl.AiExportApplicationServiceImpl.class).size());
            assertEquals(1,context.getBeansOfType(com.wenwen.ai.export.ExportApplicationStore.class).size());
            assertEquals(1,context.getBeansOfType(com.wenwen.mapper.AiExportApplicationMapper.class).size());
            assertEquals(1,context.getBeansOfType(com.wenwen.mapper.ExportMapper.class).size());
            assertEquals(1,context.getBeansOfType(com.wenwen.ai.export.mail.ExportNotificationSender.class).size());
            assertNull(context.getBean(com.wenwen.ai.export.mail.ExportNotificationSettings.class).failure());
            assertEquals(java.time.Instant.parse("2026-10-07T02:00:00Z"),context.getBean(java.time.Clock.class).instant());
            org.apache.ibatis.session.Configuration configuration=context.getBean(org.apache.ibatis.session.SqlSessionFactory.class).getConfiguration();
            assertEquals(org.apache.ibatis.logging.nologging.NoLoggingImpl.class,configuration.getLogImpl());
            assertTrue(configuration.hasStatement("com.wenwen.mapper.ExportMapper.insertApplication"));
            assertTrue(configuration.hasStatement("com.wenwen.mapper.AiExportApplicationMapper.reserve"));
            org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping mapping=context.getBean(org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping.class);
            java.util.Set<String> paths=new java.util.HashSet<>();for(org.springframework.web.servlet.mvc.method.RequestMappingInfo info:mapping.getHandlerMethods().keySet())paths.addAll(info.getPatternsCondition().getPatterns());
            assertTrue(paths.contains("/api/ra/export/applyAnalysis"));assertTrue(paths.contains("/api/ra/export/retryNotification"));assertTrue(paths.contains("/api/ra/export/xlsx"));
            assertFalse(paths.contains("/api/ra/export/apply"));assertFalse(paths.contains("/api/ra/export/visitsCsv"));assertFalse(paths.contains("/api/ra/ai/compare"));
            assertEquals(0,connections.get());
        } finally {context.close();}
        assertFalse(context.isActive());assertEquals(0,connections.get());
    }


    @Test void trustedPatientCookieUsesProductionProviderAndInvalidationOverRealTcp()throws Exception{
        WebLoopbackApiFixture fixture=new WebLoopbackApiFixture();fixture.scenario="p00-trusted";
        java.util.concurrent.atomic.AtomicInteger servletFailures=new java.util.concurrent.atomic.AtomicInteger();
        java.util.logging.Logger servletLogger=java.util.logging.Logger.getLogger("");
        java.util.logging.Handler observer=new java.util.logging.Handler(){
            public void publish(java.util.logging.LogRecord record){
                Throwable failure=record.getThrown();
                if(failure!=null)for(StackTraceElement frame:failure.getStackTrace())
                    if(frame.getClassName().startsWith(WebLoopbackApiFixture.class.getName()+"$")){servletFailures.incrementAndGet();break;}
            }
            public void flush(){}public void close(){}
        };
        servletLogger.addHandler(observer);
        java.nio.file.Path base=java.nio.file.Files.createTempDirectory("trusted-patient-tomcat-");
        try{
            fixture.connect();fixture.schema();int port=fixture.start("synthetic",false,base.toFile());
            assertTrue(fixture.application.getBean(com.wenwen.ai.scope.PrincipalProvider.class) instanceof com.wenwen.ai.scope.SessionPrincipalProvider);
            String body="{\"patientId\":\"9007199254740993\"}";
            assertEquals(401,tcp(port,"/api/ra/ai/patientDetail",null,body).status);
            TcpResponse login=tcp(port,"/api/ra/test/session",null,"");assertEquals(204,login.status);assertNotNull(login.cookie);
            TcpResponse detail=tcp(port,"/api/ra/ai/patientDetail",login.cookie,body);assertEquals(200,detail.status);com.fasterxml.jackson.databind.JsonNode d=WebLoopbackApiFixture.JSON.readTree(detail.body).path("data");assertEquals("9007199254740993",d.path("patientId").textValue());assertEquals("synthetic-patient-odd",d.path("name").textValue());assertEquals("9007199254741003",d.path("visits").get(0).path("visitId").textValue());
            TcpResponse visit=tcp(port,"/api/ra/ai/visitDetail",login.cookie,"{\"patientId\":\"9007199254740992\",\"visitId\":\"9007199254741001\"}");assertEquals(200,visit.status);com.fasterxml.jackson.databind.JsonNode v=WebLoopbackApiFixture.JSON.readTree(visit.body).path("data");assertEquals("9007199254741001",v.path("visitId").textValue());assertEquals("SYNTHETIC-HISTORY",v.path("modules").get(0).path("record").textValue());
            assertEquals(404,tcp(port,"/api/ra/ai/visitDetail",login.cookie,"{\"patientId\":\"9007199254740992\",\"visitId\":\"9007199254741003\"}").status);
            TcpResponse sensitive=tcp(port,"/api/ra/ai/patientSensitive",login.cookie,"{\"patientId\":\"9007199254740992\"}");assertEquals(200,sensitive.status);assertEquals("SYNTHETIC-CARD-EVEN",WebLoopbackApiFixture.JSON.readTree(sensitive.body).path("data").path("cardNo").textValue());
            TcpResponse audit=tcp(port,"/api/ra/ai/patientAuditLogs",login.cookie,"{\"patientId\":\"9007199254740992\"}");assertEquals(200,audit.status);assertEquals("9007199254741103",WebLoopbackApiFixture.JSON.readTree(audit.body).path("data").path("logs").get(0).path("id").textValue());
            assertEquals(204,tcp(port,"/api/ra/test/logout",login.cookie,"").status);assertEquals(401,tcp(port,"/api/ra/ai/patientDetail",login.cookie,body).status);assertEquals(0,fixture.observed.active.get());
        }finally{try{fixture.close();}finally{servletLogger.removeHandler(observer);}}
        System.out.println("P00a servlet observer failures="+servletFailures.get());
        assertEquals(0,servletFailures.get(),"patient fixture must finish requests without servlet observation exceptions");
        assertNull(fixture.applicationPeer,"patient fixture has no SMTP peer or SMTP observation rights");
        try(java.sql.Connection c=fixture.raw.getConnection();java.sql.Statement q=c.createStatement();java.sql.ResultSet r=q.executeQuery("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()")){assertTrue(r.next());assertEquals(0,r.getInt(1));}
    }
    static final class TcpResponse {int status;String cookie,body;}
    static TcpResponse tcp(int port,String path,String cookie,String body)throws Exception{
        java.net.HttpURLConnection c=(java.net.HttpURLConnection)new java.net.URL("http://127.0.0.1:"+port+path).openConnection();c.setRequestMethod("POST");c.setConnectTimeout(5000);c.setReadTimeout(5000);c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");if(cookie!=null)c.setRequestProperty("Cookie",cookie);
        try(java.io.OutputStream out=c.getOutputStream()){out.write(body.getBytes("UTF-8"));}TcpResponse result=new TcpResponse();result.status=c.getResponseCode();String set=c.getHeaderField("Set-Cookie");result.cookie=set==null?null:set.split(";")[0];java.io.InputStream in=result.status>=400?c.getErrorStream():c.getInputStream();result.body="";if(in!=null)try(java.util.Scanner reader=new java.util.Scanner(in,"UTF-8").useDelimiter("\\A")){if(reader.hasNext())result.body=reader.next();}
        c.disconnect();return result;
    }

    @Test void overviewScenarioHasIndependentSeedTypedContextAndExactPaths()throws Exception {
        assertEquals("seed-overview.sql",WebLoopbackApiFixture.seedForScenario("p01-overview"));
        String seed=WebLoopbackApiFixture.resource("seed-overview.sql");assertTrue(seed.contains("(101,3,0,1)"));assertTrue(seed.contains("crpScore\":2.70"));assertTrue(seed.contains("crpScore\":2.71"));
        WebLoopbackApiFixture f=new WebLoopbackApiFixture();f.scenario="p01-overview";assertEquals("OverviewInfrastructure",f.contextConfiguration().getSimpleName());
        for(String path:new String[]{"/api/ra/ai/overview","/api/ra/ai/cohort","/api/ra/ai/cohortPatients","/api/ra/test/session","/api/ra/test/logout"})assertTrue(WebLoopbackApiFixture.allowsHttpPath(f.scenario,path));
        for(String path:new String[]{"/api/ra/ai/overview/","/api/ra/ai/overview?x=1","/api/ra/test/chooseDoctor","/api/ra/ai/patientDetail","/api/ra/export/xlsx"})assertFalse(WebLoopbackApiFixture.allowsHttpPath(f.scenario,path));
        assertFalse(f.control("CONTROL 1 IDENTITY_202"));assertFalse(WebLoopbackApiFixture.applicationScenario(f.scenario));assertFalse(f.compositionScenario());
    }

    @Test void overviewCookieUsesProductionProviderAndReadOnlySourceOverRealTcp()throws Exception {
        WebLoopbackApiFixture fixture=new WebLoopbackApiFixture();fixture.scenario="p01-overview";
        java.nio.file.Path base=java.nio.file.Files.createTempDirectory("overview-tomcat-");
        java.util.concurrent.atomic.AtomicInteger servletFailures=new java.util.concurrent.atomic.AtomicInteger();java.util.logging.Logger logger=java.util.logging.Logger.getLogger("");
        java.util.logging.Handler observer=new java.util.logging.Handler(){public void publish(java.util.logging.LogRecord r){if(r.getThrown()!=null)for(StackTraceElement frame:r.getThrown().getStackTrace())if(frame.getClassName().startsWith(WebLoopbackApiFixture.class.getName()+"$")){servletFailures.incrementAndGet();break;}}public void flush(){}public void close(){}};logger.addHandler(observer);
        try {
            fixture.connect();fixture.schema();int port=fixture.start("synthetic",false,base.toFile());
            assertTrue(fixture.application.getBean(com.wenwen.ai.scope.PrincipalProvider.class) instanceof com.wenwen.ai.scope.SessionPrincipalProvider);
            assertEquals(401,tcp(port,"/api/ra/ai/overview",null,"{").status);assertEquals(0,fixture.observed.borrowed);
            TcpResponse login=tcp(port,"/api/ra/test/session",null,"");assertEquals(204,login.status);assertNotNull(login.cookie);fixture.observed.failInsertSql=true;
            TcpResponse overview=tcp(port,"/api/ra/ai/overview",login.cookie,"{}");assertEquals(200,overview.status);
            com.fasterxml.jackson.databind.JsonNode d=WebLoopbackApiFixture.JSON.readTree(overview.body).path("data");assertEquals(6,d.path("studyTotal").asInt());assertEquals(.4,d.path("targetRate").path("value").asDouble());assertEquals(3,d.path("modHighN").asInt());assertEquals(3,d.path("fmN").asInt());assertEquals(2,d.path("missingN").asInt());
            assertEquals(5,fixture.observed.selects);assertEquals(1,fixture.observed.borrowed);assertEquals(1,fixture.observed.returned);assertEquals(0,fixture.observed.active.get());assertEquals(0,fixture.compositionWrites.get());assertEquals(0,fixture.observed.inserts);assertEquals(0,fixture.observed.deletes);
            assertEquals(java.util.Collections.nCopies(5,false),fixture.observed.autoCommits);assertEquals(java.util.Collections.nCopies(5,java.sql.Connection.TRANSACTION_REPEATABLE_READ),fixture.observed.isolations);
            try(java.sql.Connection c=fixture.raw.getConnection();java.sql.Statement q=c.createStatement();java.sql.ResultSet rows=q.executeQuery("SELECT COUNT(*) FROM ra_ai_analysis_run")){rows.next();assertEquals(0,rows.getInt(1));}
            fixture.observed.reset();assertEquals(400,tcp(port,"/api/ra/ai/overview",login.cookie,"{\"doctorId\":\"202\"}").status);assertEquals(0,fixture.observed.borrowed);
            fixture.observed.failSecondSql=true;assertEquals(503,tcp(port,"/api/ra/ai/overview",login.cookie,"{}").status);assertEquals(0,fixture.observed.active.get());assertEquals(fixture.observed.borrowed,fixture.observed.returned);assertTrue(fixture.observed.mysqlFailures>0);
            assertEquals(204,tcp(port,"/api/ra/test/logout",login.cookie,"").status);fixture.observed.reset();assertEquals(401,tcp(port,"/api/ra/ai/overview",login.cookie,"{").status);assertEquals(0,fixture.observed.borrowed);assertNull(fixture.applicationPeer);assertEquals(0,servletFailures.get());
        } finally {fixture.close();logger.removeHandler(observer);}
        assertEquals(0,servletFailures.get());assertTrue(fixture.closed);assertEquals(0,fixture.observed.active.get());
        try(java.sql.Connection c=fixture.raw.getConnection();java.sql.Statement q=c.createStatement();java.sql.ResultSet rows=q.executeQuery("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()")){rows.next();assertEquals(0,rows.getInt(1));}
    }
    @Test void homepageSearchOverviewAndTrustedDetailsComposeOverRealTcp()throws Exception{
        WebLoopbackApiFixture f=new WebLoopbackApiFixture();f.scenario="p00-trusted";java.nio.file.Path base=java.nio.file.Files.createTempDirectory("homepage-patient-tcp-");
        try{
            f.connect();f.schema();int port=f.start("synthetic",false,base.toFile());
            assertEquals(401,tcp(port,"/api/ra/ai/patientSearch",null,"{").status);assertEquals(0,f.observed.borrowed);
            TcpResponse login=tcp(port,"/api/ra/test/session",null,"");assertEquals(204,login.status);assertNotNull(login.cookie);f.observed.failInsertSql=true;
            TcpResponse overview=tcp(port,"/api/ra/ai/overview",login.cookie,"{}");assertEquals(200,overview.status);com.fasterxml.jackson.databind.JsonNode o=WebLoopbackApiFixture.JSON.readTree(overview.body).path("data");assertEquals(3,o.path("studyTotal").intValue());assertEquals("NO_DATA",o.path("targetRate").path("meta").path("status").textValue());assertEquals(0,o.path("targetRate").path("meta").path("denominator").intValue());assertEquals(3,o.path("targetRate").path("meta").path("unknownN").intValue());assertEquals(0,f.observed.active.get());
            TcpResponse search=tcp(port,"/api/ra/ai/patientSearch",login.cookie,"{\"keyword\":\"9007199254740993\",\"page\":1,\"size\":20}");assertEquals(200,search.status);com.fasterxml.jackson.databind.JsonNode d=WebLoopbackApiFixture.JSON.readTree(search.body).path("data");assertEquals(1,d.path("total").intValue());assertEquals(3,d.path("items").get(0).size());assertEquals("9007199254740993",d.path("items").get(0).path("patientId").textValue());assertEquals("synthetic-patient-odd",d.path("items").get(0).path("name").textValue());
            assertEquals(200,tcp(port,"/api/ra/ai/patientDetail",login.cookie,"{\"patientId\":\"9007199254740993\"}").status);assertEquals(200,tcp(port,"/api/ra/ai/visitDetail",login.cookie,"{\"patientId\":\"9007199254740993\",\"visitId\":\"9007199254741003\"}").status);
            assertEquals(0,f.observed.active.get());assertEquals(f.observed.borrowed,f.observed.returned);assertEquals(0,f.observed.inserts);assertEquals(0,f.observed.deletes);assertEquals(0,f.compositionWrites.get());assertNull(f.applicationPeer);
            for(String path:new String[]{"/api/ra/ai/patientSearch/","/api/ra/ai/patientSearch?x=1","/api/ra/ai/overview/","/api/ra/test/chooseDoctor","/api/ra/export/xlsx"})assertFalse(WebLoopbackApiFixture.allowsHttpPath(f.scenario,path));
            assertEquals(204,tcp(port,"/api/ra/test/logout",login.cookie,"").status);f.observed.reset();assertEquals(401,tcp(port,"/api/ra/ai/patientSearch",login.cookie,"{").status);assertEquals(0,f.observed.borrowed);
        }finally{f.close();}
        assertTrue(f.closed);assertEquals(0,f.observed.active.get());try(java.sql.Connection c=f.raw.getConnection();java.sql.Statement q=c.createStatement();java.sql.ResultSet r=q.executeQuery("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()")){r.next();assertEquals(0,r.getInt(1));}
    }
    @Test void configuredSourcesComposeOverRealTcp()throws Exception{
        WebLoopbackApiFixture f=new WebLoopbackApiFixture();f.scenario="p03-sources";java.nio.file.Path base=java.nio.file.Files.createTempDirectory("configured-sources-tcp-");int port=0;
        try{
            f.connect();f.schema();port=f.start("synthetic",false,base.toFile());
            assertTrue(f.application.getBean(com.wenwen.ai.scope.PrincipalProvider.class) instanceof com.wenwen.ai.scope.SessionPrincipalProvider);
            assertEquals(401,tcp(port,"/api/ra/ai/cohort",null,"{").status);assertEquals(0,f.observed.borrowed);
            TcpResponse login=tcp(port,"/api/ra/test/session",null,"");assertEquals(204,login.status);assertNotNull(login.cookie);
            TcpResponse response=tcp(port,"/api/ra/ai/cohort",login.cookie,"{}");assertEquals(200,response.status);com.fasterxml.jackson.databind.JsonNode d=WebLoopbackApiFixture.JSON.readTree(response.body).path("data");
            assertEquals(3,d.path("fm").path("nOther").asInt());assertEquals(3,d.path("fm").path("nFM").asInt());assertEquals(0,d.path("fm").path("unknownN").asInt());assertEquals(6,d.path("fm").path("rows").size());assertEquals("synthetic-fm-v1",d.path("meta").path("policyVersions").path("fmSource").asText());assertEquals("synthetic-drug-v1",d.path("meta").path("policyVersions").path("drugDictionary").asText());
            assertTrue(f.application.getBean(com.wenwen.ai.treatment.DrugDictionary.class) instanceof com.wenwen.ai.treatment.ConfiguredDrugDictionary);
            assertEquals(0,f.observed.active.get());assertEquals(f.observed.borrowed,f.observed.returned);assertEquals(1,f.observed.inserts);f.observed.reset();f.compositionWrites.set(0);f.observed.failInsertSql=true;
            TcpResponse overview=tcp(port,"/api/ra/ai/overview",login.cookie,"{}");assertEquals(200,overview.status);com.fasterxml.jackson.databind.JsonNode o=WebLoopbackApiFixture.JSON.readTree(overview.body).path("data");assertEquals(6,o.path("studyTotal").asInt());assertEquals(3,o.path("fmN").asInt());assertEquals("synthetic-fm-v1",o.path("meta").path("policyVersions").path("fmSource").asText());assertEquals(5,f.observed.selects);assertEquals(1,f.observed.borrowed);assertEquals(1,f.observed.returned);assertEquals(0,f.compositionWrites.get());assertEquals(0,f.observed.inserts);assertEquals(java.util.Collections.nCopies(5,java.sql.Connection.TRANSACTION_REPEATABLE_READ),f.observed.isolations);assertEquals(1,new java.util.HashSet<>(f.observed.queryConnections).size());
            f.observed.reset();TcpResponse tx=tcp(port,"/api/ra/ai/cohort",login.cookie,"{\"filters\":{\"tx\":\"TNFi\"}}");assertEquals(200,tx.status);com.fasterxml.jackson.databind.JsonNode t=WebLoopbackApiFixture.JSON.readTree(tx.body).path("data");assertEquals(1,t.path("n").asInt());assertEquals("2",t.path("patients").path("items").get(0).path("patientId").asText());assertEquals(WebLoopbackApiFixture.JSON.readTree("[\"synthetic:g2\"]"),t.path("patients").path("items").get(0).path("treatment").path("genericDrugIds"));
            f.observed.reset();f.observed.failSecondSql=true;assertEquals(503,tcp(port,"/api/ra/ai/cohort",login.cookie,"{}").status);assertEquals(0,f.observed.active.get());assertEquals(f.observed.borrowed,f.observed.returned);assertEquals(0,f.observed.inserts);assertTrue(f.observed.mysqlFailures>0);f.observed.reset();
            for(String path:new String[]{"/api/ra/ai/overview/","/api/ra/ai/overview?x=1","/api/ra/ai/patientDetail","/api/ra/test/chooseDoctor","/api/ra/ai/configureFm"})assertFalse(WebLoopbackApiFixture.allowsHttpPath(f.scenario,path));
            assertEquals(204,tcp(port,"/api/ra/test/logout",login.cookie,"").status);f.observed.reset();assertEquals(401,tcp(port,"/api/ra/ai/cohort",login.cookie,"{").status);assertEquals(0,f.observed.selects);assertNull(f.applicationPeer);assertFalse(f.compositionScenario());
        }finally{f.close();}
        assertTrue(f.closed);assertEquals(0,f.observed.active.get());final int stoppedPort=port;assertThrows(java.io.IOException.class,()->{try(java.net.Socket socket=new java.net.Socket()){socket.connect(new java.net.InetSocketAddress("127.0.0.1",stoppedPort),1000);}});
        try(java.sql.Connection c=f.raw.getConnection();java.sql.Statement q=c.createStatement();java.sql.ResultSet rows=q.executeQuery("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()")){assertTrue(rows.next());assertEquals(0,rows.getInt(1));}
    }

}
