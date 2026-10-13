package com.wenwen.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 数据看板（SQL 见 resources/mybatis/DashboardMapper.xml），只读；范围都是当前医生名下的 RA 患者
 */
@Mapper
public interface DashboardMapper {

	/** 每位患者的建档日期 patientId / createDate(yyyy-MM-dd) */
	List<Map<String, Object>> listCreateDates(@Param("doctorId") Long doctorId);

	/** 填了 C 反应蛋白的 RA 随访，最近的在前：patientId / visitId / visitDate / fzjc */
	List<Map<String, Object>> listCrpVisits(@Param("doctorId") Long doctorId);

	/** 最近的 RA 随访（截至今天）：visitId / patientId / name / studyNo / visitDate / seq（该患者第几条记录，1 = 基线） */
	List<Map<String, Object>> listRecentVisits(@Param("doctorId") Long doctorId, @Param("limit") int limit);
}
