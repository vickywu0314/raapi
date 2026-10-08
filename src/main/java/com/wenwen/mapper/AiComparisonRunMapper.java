package com.wenwen.mapper;
import com.wenwen.ai.result.ComparisonRun;
import org.apache.ibatis.annotations.*;
@Mapper public interface AiComparisonRunMapper {
    int deleteExpired(@Param("now") long now);
    int insert(ComparisonRun run);
    ComparisonRun find(@Param("id") String id);
}
