package com.wenwen.ai.cohort;

/** 独立手写表A；真实HTTP/MyBatis/合成MySQL，不用生产政策生成预期。 */
import static org.junit.jupiter.api.Assertions.*;

abstract class MissingDataHttpFixture extends CohortHttpFixture {
    @org.junit.jupiter.api.BeforeEach @Override void setup() throws Exception {
        super.setup();
        sql("DELETE FROM patient_follow_up_history");
        sql("DELETE FROM patient_relation_doctor");
        sql("DELETE FROM patient_basic_info");
        sql("INSERT INTO patient_basic_info(id,name,jws) VALUES (1,'synthetic-one','record'),(2,'synthetic-two','record')");
        sql("INSERT INTO patient_relation_doctor(doctor_id,patient_id,research_type) VALUES (101,1,0),(101,2,0)");
        visit(10,1,0,"{\"result\":{\"crpScore\":0}}","2024-01-01",null,"record");
        visit(20,2,0,"{\"result\":{\"esrScore\":8}}","2024-01-01",null,"record");
        sql("UPDATE patient_follow_up_history SET zlfa='record'");
        observed.reset();
    }
    java.util.concurrent.atomic.AtomicInteger committedWriter(boolean deleteFirst) {
        java.util.concurrent.atomic.AtomicInteger commits=new java.util.concurrent.atomic.AtomicInteger();
        observed.afterFirstQuery=()-> {
            assertEquals(1,observed.selects);assertEquals(1,observed.active.get());
            try(java.sql.Connection writer=raw.getConnection();java.sql.Statement s=writer.createStatement()) {
                assertFalse(observed.queryConnections.contains(System.identityHashCode(writer)));writer.setAutoCommit(false);
                s.execute("UPDATE patient_follow_up_history SET bqpg='{\"result\":{\"crpScore\":2.295}}',fzjc='',zlfa='' WHERE id=20");
                s.execute("UPDATE patient_basic_info SET jws='' WHERE id=2");
                if(deleteFirst) s.execute("DELETE FROM patient_basic_info WHERE id=1");
                writer.commit();commits.incrementAndGet();assertEquals(1,observed.selects);
            } catch(java.sql.SQLException e) {throw new AssertionError("独立合成writer提交失败",e);}
        };
        return commits;
    }
    void released() {assertEquals(0,observed.active.get());assertEquals(observed.borrowed,observed.returned);}
    void snapshotQueries(int selects) {
        assertEquals(1,new java.util.HashSet<>(observed.queryConnections.subList(0,selects)).size());
        assertEquals(java.util.Collections.nCopies(selects,java.sql.Connection.TRANSACTION_REPEATABLE_READ),observed.isolations.subList(0,selects));
        assertEquals(java.util.Collections.nCopies(selects,false),observed.autoCommits.subList(0,selects));released();
    }
}
