package com.wenwen.service;

import com.wenwen.vo.ProjectsDataVo;

/**
 * RA 项目总览业务
 */
public interface ProjectService {

	/** 项目总览数据（项目级统计，不按医生过滤） */
	ProjectsDataVo getProjectsData();
}
