package com.wenwen.service;

import java.util.Map;

/**
 * 数据看板：只统计当前医生名下的 RA 患者
 */
public interface DashboardService {

	/** 看板全部数字和名单（结构见 docs/API.md「数据看板」） */
	Map<String, Object> summary(Long doctorId);
}
