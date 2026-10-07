package com.wenwen.mapper;

import java.util.Map;

import org.apache.ibatis.annotations.Mapper;

/**
 * RA 项目总览统计（SQL 见 resources/mybatis/ProjectMapper.xml）
 */
@Mapper
public interface ProjectMapper {
    java.util.List<Long> qcPatientIds(Map<String,Object> scope);
    java.util.List<Map<String,Object>> remainingQcIssues(Map<String,Object> scope);

	/** 有效（未脱落）患者数 */
	int countActivePatients();

	/** 计划随访：返回 dueCount（应随访）、doneCount（已随访），入参 cycleDays */
	Map<String, Object> countFollowUpPlan(Map<String, Object> map);

	/** 待随访患者数，入参 cycleDays */
	int countPendingFollowUp(Map<String, Object> map);

	/** 研究级可用记录数 */
	int countUsableRecords();
}
