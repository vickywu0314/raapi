package com.wenwen.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** OCR 识别病历（SQL 见 resources/mybatis/OcrMapper.xml）：新表 ocr_lab_alias（叫法对照）、ocr_record（病历图片记录） */
@Mapper
public interface OcrMapper {

	/** 全部对照：alias / field */
	List<Map<String, Object>> listAliases();

	int upsertAlias(@Param("alias") String alias, @Param("field") String field, @Param("sampleName") String sampleName, @Param("doctorId") Long doctorId);

	int insertRecord(@Param("fileId") String fileId, @Param("doctorId") Long doctorId);

	int updateResult(@Param("fileId") String fileId, @Param("result") String result);

	int linkRecord(@Param("fileId") String fileId, @Param("doctorId") Long doctorId, @Param("patientId") Long patientId, @Param("visitId") Long visitId);

	Map<String, Object> findRecord(@Param("fileId") String fileId);

	int ownsPatient(@Param("patientId") Long patientId, @Param("doctorId") Long doctorId);

	int visitBelongsToPatient(@Param("visitId") Long visitId, @Param("patientId") Long patientId);

	List<Map<String, Object>> listVisitRecords(@Param("doctorId") Long doctorId, @Param("patientId") Long patientId, @Param("visitId") Long visitId);
}
