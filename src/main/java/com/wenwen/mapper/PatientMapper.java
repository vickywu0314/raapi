package com.wenwen.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * RA 患者列表（SQL 见 resources/mybatis/PatientMapper.xml）
 */
@Mapper
public interface PatientMapper {

	/** 页头汇总：totalPatients、incompleteCount，入参 doctorId；不受筛选影响 */
	Map<String, Object> countSummary(Map<String, Object> map);

	/** 符合筛选条件的患者数，入参 doctorId / keyword / followStatus / completeness */
	int countPatients(Map<String, Object> map);

	/** 当前页患者，入参同 countPatients，另加 offset / size */
	List<Map<String, Object>> listPatients(Map<String, Object> map);

	/** 指定患者的其他病史：patientId、code、sinceYear */
	List<Map<String, Object>> listComorbidities(@Param("patientIds") List<Long> patientIds);
}
