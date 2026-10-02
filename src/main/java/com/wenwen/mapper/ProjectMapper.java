package com.wenwen.mapper;

import java.util.Map;

import org.apache.ibatis.annotations.Mapper;

/**
 * RA 项目总览统计（SQL 见 resources/mybatis/ProjectMapper.xml）
 */
@Mapper
public interface ProjectMapper {

	/** 已入组患者：全部患者（含已脱落） */
	int countAllPatients();

	/** 有效（未脱落）患者数 */
	int countActivePatients();

	/** 计划随访：返回 dueCount（应随访）、doneCount（已随访），入参 cycleDays */
	Map<String, Object> countFollowUpPlan(Map<String, Object> map);

	/** 待随访患者数，入参 cycleDays */
	int countPendingFollowUp(Map<String, Object> map);

	/** 待处理质控问题：返回 issueCount（问题条数）、patientCount（涉及患者数） */
	Map<String, Object> countPendingQc();

	/** 研究级可用记录数 */
	int countUsableRecords();
}
