package com.wenwen.mapper;

import com.wenwen.ai.source.VisitRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AiCohortSourceMapper {
    List<java.util.Map<String,Object>> missingIssues(java.util.Map<String,Object> scope);
    List<Long> patientIds(@Param("doctorId") long doctorId);
    List<com.wenwen.ai.source.PatientRow> patients(@Param("doctorId") long doctorId);
    List<com.wenwen.ai.source.ComorbidityRow> comorbidities(@Param("doctorId") long doctorId);
    /** 与患者/范围/合并症共享短视图，包含必要zlfa原文；释放后解析。 */
    List<VisitRow> visits(@Param("doctorId") long doctorId);
}
