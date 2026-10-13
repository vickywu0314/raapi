package com.wenwen.mapper;

import java.io.InputStream;
import java.util.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 真实 XML/动态SQL 展开；不连接数据库，也不声称执行 SQL。 */
class PatientFollowUpProjectionTest {
    @Test void listProjectsPreciseCoalescedCreationDateAndRaVisitCount() throws Exception {
        Configuration configuration = new Configuration();
        for(String resource:Arrays.asList("mybatis/ProjectMapper.xml","mybatis/PatientMapper.xml")) {
            try(InputStream xml=Resources.getResourceAsStream(resource)) {
                new XMLMapperBuilder(xml,configuration,resource,configuration.getSqlFragments()).parse();
            }
        }
        Map<String,Object> params = new HashMap<>();
        params.put("doctorId",101L);
        params.put("asOf","2026-10-12");
        String sql = configuration.getMappedStatement("com.wenwen.mapper.PatientMapper.listPatients")
            .getBoundSql(params).getSql().replaceAll("\\s+"," ");
        assertTrue(sql.contains("DATE_FORMAT(x.createTime, '%Y-%m-%d') AS createDate"),sql);
        assertTrue(sql.contains("COALESCE(p.create_date, p.createDate) AS createTime"));
        assertTrue(sql.contains("x.visitCount"));
        assertTrue(sql.contains("COUNT(*) AS visitCount"));
        assertTrue(sql.contains("IFNULL(v.visitCount, 0) AS visitCount"));
        assertTrue(sql.contains("f.research_type IN"));
    }
}
