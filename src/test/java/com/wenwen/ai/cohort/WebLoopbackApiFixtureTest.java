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

}
