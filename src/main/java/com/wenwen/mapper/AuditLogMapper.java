package com.wenwen.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;

/**
 * 患者修改记录（SQL 见 resources/mybatis/AuditLogMapper.xml）
 */
@Mapper
public interface AuditLogMapper {

	/** 写入一条：patientId / visitId / action / detail / changes / operatorId / operatorName */
	int insert(Map<String, Object> map);

	/** 某患者的修改记录，最新的在前 */
	List<Map<String, Object>> listByPatient(Map<String, Object> map);
}
