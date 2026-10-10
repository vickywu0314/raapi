package com.wenwen.ai.cohort;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** 首连接核库决策在任何DDL前执行，字符串负例无需触碰其他库。 */
class WebLoopbackApiFixtureTest {
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

}
