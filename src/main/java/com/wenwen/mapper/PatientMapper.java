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

	/** 指定患者各次随访的 DAS28-CRP（patientId、das28 文本），每个患者最近的在前 */
	List<Map<String, Object>> listDas28(@Param("patientIds") List<Long> patientIds);

	/** 指定患者的其他病史：patientId、code、sinceYear */
	List<Map<String, Object>> listComorbidities(@Param("patientIds") List<Long> patientIds);

	/** 详情页患者表其它字段，入参 doctorId / patientId；不在该医生名下返回 null */
	Map<String, Object> getPatientBasic(Map<String, Object> map);

	/** 随访时间线，入参 patientId；最近的在前 */
	List<Map<String, Object>> listVisits(Map<String, Object> map);

	/** 患者是否在该医生名下，入参 doctorId / patientId */
	int countDoctorPatient(Map<String, Object> map);

	/** 身份证号明文，入参 patientId */
	String getCardNo(Map<String, Object> map);
}
