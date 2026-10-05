package com.wenwen.mapper;

import java.util.Map;

import org.apache.ibatis.annotations.Mapper;

/**
 * 随访（访视）（SQL 见 resources/mybatis/VisitMapper.xml）
 */
@Mapper
public interface VisitMapper {

	/** 单次随访：入参 doctorId / visitId；随访的患者不在该医生名下时返回 null */
	Map<String, Object> getVisit(Map<String, Object> map);

	/** 该患者基线访视（时间最早的一次）的随访ID，入参 patientId */
	Long getBaselineVisitId(Map<String, Object> map);
}
