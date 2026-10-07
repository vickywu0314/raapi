package com.wenwen.mapper;

import com.wenwen.ai.source.VisitRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AiCohortSourceMapper {
    List<Long> patientIds(@Param("doctorId") long doctorId);
    List<VisitRow> visits(@Param("doctorId") long doctorId);
}
