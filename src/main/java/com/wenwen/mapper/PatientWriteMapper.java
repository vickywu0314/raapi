package com.wenwen.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 新建患者 / 转到本医生名下（SQL 见 resources/mybatis/PatientWriteMapper.xml）。
 * 写老表 patient_basic_info、patient_relation_doctor 时只写蛇形命名的字段（老系统现在写的就是这些；驼峰的是早期旧字段）。
 */
@Mapper
public interface PatientWriteMapper {

	/** 身份证号相同的患者（忽略大小写和首尾空格）：id、name */
	List<Map<String, Object>> findByCardNo(@Param("cardNo") String cardNo);

	/** 患者的全部医患关系：id、doctorId、doctorName、researchType、isRa（1 = RA 研究库） */
	List<Map<String, Object>> listRelations(@Param("patientId") Long patientId);

	/** 预填表单用的基本信息（字段名同 PatientBasicForm，日期 yyyy-MM-dd） */
	Map<String, Object> getBasicForm(@Param("patientId") Long patientId);

	/** 新建患者，回填 map.id；入参见 XML */
	int insertPatient(Map<String, Object> map);

	/** 更新患者表指定字段：fields 为 列名 → 值（列名由调用方按白名单给出） */
	int updatePatientFields(@Param("patientId") Long patientId, @Param("fields") Map<String, Object> fields);

	/** 研究编号以 prefix 开头的患者数（当天已用序号数） */
	int countStudyNoPrefix(@Param("prefix") String prefix);

	/** 新建医患关系 */
	int insertRelation(Map<String, Object> map);

	/** 医患关系转到另一医生名下 */
	int transferRelation(@Param("relationId") Long relationId, @Param("doctorId") Long doctorId);

	/** ACR/EULAR 2010 评估记录 */
	int insertAcrEular(Map<String, Object> map);

	/** 常见相关疾病；同一病种已有则跳过 */
	int insertComorbidity(Map<String, Object> map);

	String getUserName(@Param("doctorId") Long doctorId);
}
