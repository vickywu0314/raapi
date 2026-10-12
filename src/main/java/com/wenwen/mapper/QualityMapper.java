package com.wenwen.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 数据质控（SQL 见 resources/mybatis/QualityMapper.xml）；范围是当前医生名下的 RA 患者
 */
@Mapper
public interface QualityMapper {

	/** 患者档案里质控要用的字段：patientId / createDate / confirmDate / happenDate / xyH / xyL */
	List<Map<String, Object>> listPatientFacts(@Param("doctorId") Long doctorId);

	/** RA 随访：visitId / patientId / visitDate / fzjc，按患者、日期、ID 排 */
	List<Map<String, Object>> listVisits(@Param("doctorId") Long doctorId);

	/** 已处理记录：patientId / issueKey / doctorId / doctorName / note / time */
	List<Map<String, Object>> listResolutions(@Param("doctorId") Long doctorId);

	int insertResolution(Map<String, Object> map);
}
