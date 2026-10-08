package com.wenwen.mapper;
import com.wenwen.ai.result.AnalysisRun;
import org.apache.ibatis.annotations.*;
@Mapper public interface AiAnalysisRunMapper {
    int deleteExpired(@Param("now") long now);
    int insert(AnalysisRun run);
    AnalysisRun find(@Param("id") String id);
}
