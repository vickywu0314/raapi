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

	/** 删除前：后一次随访的 last_follow_up_id 改指向被删这次的上一次，入参 visitId / prevVisitId */
	int relinkNextVisit(Map<String, Object> map);

	/** 物理删除随访，入参 visitId */
	int deleteVisit(Map<String, Object> map);

	/** 重新计算患者表的随访次数、最近随访日期，入参 patientId */
	int refreshPatientCounters(Map<String, Object> map);

	/** 重新计算医患关系表的随访次数、首次 / 最近随访日期，入参 patientId / visitDoctorId / researchType */
	int refreshRelationCounters(Map<String, Object> map);

	/** 医生姓名（user.name），入参 doctorId */
	String getUserName(Map<String, Object> map);
}
