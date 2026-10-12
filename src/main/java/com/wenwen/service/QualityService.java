package com.wenwen.service;

import java.util.Map;

/**
 * 数据质控：按当前数据实时检查医生名下 RA 患者的缺失、异常值、逻辑冲突；「已处理」存新表 qc_issue_resolution
 */
public interface QualityService {

	/** 质控问题清单 + 统计（结构见 docs/API.md「数据质控」） */
	Map<String, Object> issues(Long doctorId);

	/** 同上，复用已算好的患者列表（数据看板用，避免重复计算） */
	Map<String, Object> issues(Long doctorId, PatientService.AllPatients all);

	/**
	 * 标记已处理：写 qc_issue_resolution 和修改记录「质控处理」
	 *
	 * @throws com.wenwen.util.BizException 404 问题不存在（数据已改好或不在名下）
	 */
	void resolve(Long doctorId, Long patientId, String issueKey, String note);
}
