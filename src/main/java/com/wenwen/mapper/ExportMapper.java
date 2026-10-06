package com.wenwen.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;

/**
 * 数据导出（SQL 见 resources/mybatis/ExportMapper.xml）
 */
@Mapper
public interface ExportMapper {

	/** 指定患者在日期范围内的 RA 随访（含患者基本信息和 7 个模块原文），入参 patientIds / startDate / endDate */
	List<Map<String, Object>> listVisits(Map<String, Object> map);

	/** 指定患者各自的基线访视ID（时间最早的一次 RA 随访），入参 patientIds；返回 patientId、visitId，每个患者最早的在前 */
	List<Map<String, Object>> listVisitOrder(Map<String, Object> map);

	/** 新增导出申请 */
	int insertApplication(Map<String, Object> map);
}
