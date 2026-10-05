package com.wenwen.service;

import java.util.List;

import com.wenwen.vo.AuditLogVo;
import com.wenwen.vo.PatientDetailVo;
import com.wenwen.vo.PatientsListVo;

/**
 * RA 患者业务
 */
public interface PatientService {

	/**
	 * 患者列表（只含该医生名下患者）
	 *
	 * @param doctorId 医生ID
	 * @param keyword 姓名 / ID号 / 研究编号，包含匹配；可空
	 * @param followStatus active / soon / overdue / pending_first / withdrawn；可空
	 * @param completeness complete / missing；可空
	 * @param page 页码，从 1 开始
	 * @param size 每页条数，1~200
	 * @throws IllegalArgumentException 参数不合法
	 */
	PatientsListVo listPatients(Long doctorId, String keyword, String followStatus, String completeness, int page, int size);

	/**
	 * 患者详情：基本信息 + 随访时间线
	 *
	 * @throws com.wenwen.util.BizException 403 患者不存在或不在该医生名下
	 */
	PatientDetailVo getPatientDetail(Long doctorId, Long patientId);

	/** 身份证号明文（详情页「眼睛」按钮） */
	String getCardNo(Long doctorId, Long patientId);

	/** 修改记录，最新的在前；老数据没有记录返回空列表 */
	List<AuditLogVo> listAuditLogs(Long doctorId, Long patientId);
}
