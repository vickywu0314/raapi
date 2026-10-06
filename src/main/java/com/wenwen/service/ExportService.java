package com.wenwen.service;

import java.util.List;

import com.wenwen.vo.ExportRangeVo;

/**
 * 数据导出：近半年内的随访数据直接下载；更早的需提交申请
 */
public interface ExportService {

	/** 可直接下载的时间范围（导出弹窗的默认值） */
	ExportRangeVo getRange();

	/**
	 * 导出随访数据 CSV（UTF-8 带 BOM，Excel 可直接打开）。
	 * 患者范围：传了 patientIds 则只导出其中属于该医生的；否则导出符合筛选条件的全部患者。
	 *
	 * @throws com.wenwen.util.BizException NEED_APPLY 开始日期早于可直接下载的范围，需要提交申请
	 */
	byte[] exportVisitsCsv(Long doctorId, String startDate, String endDate, String keyword, String followStatus, String completeness, List<Long> patientIds);

	/**
	 * 提交导出申请（超过半年的数据），记录到 export_application；发邮件功能后续实现
	 *
	 * @return 申请编号
	 */
	Long apply(Long doctorId, String startDate, String endDate, String keyword, String followStatus, String completeness, List<Long> patientIds, String reason);
}
