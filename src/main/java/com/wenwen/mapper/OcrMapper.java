package com.wenwen.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** OCR 检验项目叫法对照（SQL 见 resources/mybatis/OcrMapper.xml），新表 ocr_lab_alias */
@Mapper
public interface OcrMapper {

	/** 全部对照：alias / field */
	List<Map<String, Object>> listAliases();

	int upsertAlias(@Param("alias") String alias, @Param("field") String field, @Param("sampleName") String sampleName, @Param("doctorId") Long doctorId);
}
