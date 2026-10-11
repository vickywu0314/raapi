package com.wenwen.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 随访管理页（SQL 见 resources/mybatis/FollowupMapper.xml），只读
 */
@Mapper
public interface FollowupMapper {

	/** 当前医生名下未脱落的 RA 患者：随访次数、最近随访日期、随访周期、建档日期 */
	List<Map<String, Object>> listPlanRows(@Param("doctorId") Long doctorId);
}
