package com.wenwen.service;

import java.util.List;

import com.wenwen.vo.FollowupPlanVo;

/**
 * 随访管理页：当前医生名下未脱落患者的下次随访计划
 */
public interface FollowupService {

	/** 全部计划（不分页，页面自己筛选 / 分页），按预计日期从早到晚，日期未知的排最后 */
	List<FollowupPlanVo> listPlans(Long doctorId);
}
