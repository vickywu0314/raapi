package com.wenwen.service;

import com.wenwen.vo.VisitDetailVo;

/**
 * 随访（访视）业务
 */
public interface VisitService {

	/**
	 * 访视详情：7 个病历模块
	 *
	 * @throws com.wenwen.util.BizException 403 随访不存在或患者不在该医生名下
	 */
	VisitDetailVo getVisitDetail(Long doctorId, Long visitId);
}
