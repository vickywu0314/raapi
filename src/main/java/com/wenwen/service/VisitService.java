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

	/**
	 * 删除随访（物理删除，不可恢复）：接好上一次随访的关联、删除、重新计算老系统的随访次数和日期、写修改记录
	 *
	 * @param reason 删除原因，必填
	 * @return 患者ID（前端删除后回到该患者详情）
	 * @throws com.wenwen.util.BizException 403 随访不存在或患者不在该医生名下
	 */
	Long deleteVisit(Long doctorId, Long visitId, String reason);
}
