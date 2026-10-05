package com.wenwen.service;

import java.util.List;
import java.util.Map;

import com.wenwen.vo.AuditLogVo;
import com.wenwen.vo.FieldChange;

/**
 * 患者修改记录：写操作留痕、详情页查询
 *
 * 用法（以后的修改档案接口里，与业务更新放在同一事务）：
 * <pre>
 * List&lt;FieldChange&gt; changes = auditLogService.diff(oldValues, newValues, LABELS);
 * if (!changes.isEmpty()) {
 *     patientMapper.update(...);
 *     auditLogService.record(patientId, null, "修改档案", changes, null, doctorId, "陈医生（研究者）");
 * }
 * </pre>
 */
public interface AuditLogService {

	/**
	 * 比较修改前后的值，返回有变化的字段（按 labels 的顺序；只比较 labels 里列出的字段）
	 *
	 * @param before 修改前 字段 → 值
	 * @param after 修改后 字段 → 值
	 * @param labels 字段 → 中文名（需有序，如 LinkedHashMap）
	 */
	List<FieldChange> diff(Map<String, ?> before, Map<String, ?> after, Map<String, String> labels);

	/**
	 * 写一条修改记录
	 *
	 * @param visitId 涉及的随访，档案类操作传 null
	 * @param action 新建档案 / 修改档案 / 新增随访 / 编辑随访 / 删除随访 / 标记脱落 / 质控处理
	 * @param changes 字段变化，可为空
	 * @param note 附加说明（如删除原因），可为 null；与字段变化一起用「；」拼成 detail
	 */
	void record(Long patientId, Long visitId, String action, List<FieldChange> changes, String note, Long operatorId, String operatorName);

	/** 某患者的修改记录，最新的在前；老数据没有记录时返回空列表 */
	List<AuditLogVo> listByPatient(Long patientId);
}
