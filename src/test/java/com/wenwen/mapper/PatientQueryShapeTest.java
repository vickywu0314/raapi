package com.wenwen.mapper;

import java.io.InputStream;
import java.util.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 真正加载生产Mapper，保护窄投影和详情谓词下推，独立于数据库数据量。 */
class PatientQueryShapeTest {
    private Configuration configuration() throws Exception {
        Configuration config=new Configuration();
        for(String resource:Arrays.asList("mybatis/ProjectMapper.xml","mybatis/PatientMapper.xml","mybatis/QualityMapper.xml"))
            try(InputStream in=Resources.getResourceAsStream(resource)) {new XMLMapperBuilder(in,config,resource,config.getSqlFragments()).parse();}
        return config;
    }
    @Test void detailsPushPatientIdIntoMembershipWithdrawalAndVisitDerivedTables() throws Exception {
        Map<String,Object> scope=new HashMap<>();scope.put("doctorId",101L);scope.put("patientId",1L);scope.put("asOf","2026-10-19");
        BoundSql sql=configuration().getMappedStatement("com.wenwen.mapper.PatientMapper.listPatients").getBoundSql(scope);
        assertEquals(4,sql.getParameterMappings().stream().filter(p -> "patientId".equals(p.getProperty())).count());
        scope.put("patientId",null);BoundSql list=configuration().getMappedStatement("com.wenwen.mapper.PatientMapper.listPatients").getBoundSql(scope);
        assertEquals(0,list.getParameterMappings().stream().filter(p -> "patientId".equals(p.getProperty())).count());
    }
    @Test void projectQcAndQualityDoNotFetchUnconsumedColumnsOrSortExistenceInputs() throws Exception {
        Map<String,Object> scope=new HashMap<>();scope.put("doctorId",null);scope.put("patientId",null);
        Configuration config=configuration();
        String qc=config.getMappedStatement("com.wenwen.mapper.PatientMapper.listQcDas28").getBoundSql(scope).getSql().toLowerCase(Locale.ROOT);
        assertFalse(qc.contains("order by"));assertFalse(qc.contains("date_format"));assertFalse(qc.contains("visitid"));
        assertTrue(qc.contains("f.bqpg"));assertTrue(qc.contains("patient_relation_doctor"));
        String quality=config.getMappedStatement("com.wenwen.mapper.QualityMapper.listVisits").getBoundSql(Collections.singletonMap("doctorId",101L)).getSql().toLowerCase(Locale.ROOT);
        assertFalse(quality.contains("fzjc"));assertTrue(quality.contains("visitdate"));assertTrue(quality.contains("order by"));
    }
}
