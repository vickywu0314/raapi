package com.wenwen.service;

import com.wenwen.vo.PatientCheckVo;
import com.wenwen.vo.PatientCreateRequest;
import com.wenwen.vo.PatientCreateResultVo;

/**
 * 新建患者（写老表 patient_basic_info、patient_relation_doctor）
 */
public interface PatientWriteService {

	/** 新建患者前按身份证号查重 */
	PatientCheckVo checkCardNo(Long doctorId, String cardNo);

	/**
	 * 新建患者。身份证号已存在时：
	 * 其他病种的患者 → 复用档案、新增 RA 医患关系；RA 库其他医生的患者 → transfer=true 时转到本医生名下，否则 409；
	 * 已在本医生名下 → 409。
	 *
	 * @throws com.wenwen.util.BizException 400 参数不对 / 409 已存在
	 */
	PatientCreateResultVo createPatient(PatientCreateRequest req);
}
