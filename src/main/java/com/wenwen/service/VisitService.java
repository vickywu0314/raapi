package com.wenwen.service;

import com.wenwen.vo.VisitDetailVo;
import com.wenwen.vo.VisitEditFormVo;
import com.wenwen.vo.VisitUpdateRequest;
import com.wenwen.vo.VisitUpdateResultVo;

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

	/** 编辑随访表单：按字段字典给出当前值、清单、只读项和数据版本 */
	VisitEditFormVo getEditForm(Long doctorId, Long visitId);

	/**
	 * 保存编辑：只写有变化的字段，保持老系统 JSON 结构；病情评估的计算项按老系统算法重算；改随访日期时重算随访次数和日期；写修改记录
	 *
	 * @throws com.wenwen.util.BizException 403 无权限；409 打开后数据已被修改
	 */
	VisitUpdateResultVo updateVisit(VisitUpdateRequest req);
}
