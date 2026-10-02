package com.wenwen.controller.ra;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wenwen.service.ProjectService;
import com.wenwen.vo.DataResult;
import com.wenwen.vo.ProjectsDataVo;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

@Api(description = "项目总览")
@CrossOrigin
@RestController
@RequestMapping(value = "/project")
public class ProjectController {

	/** 病种：RA 类风湿 */
	private static final int DISEASE_RA = 1;

	@Autowired
	private ProjectService projectService;

	@ApiOperation(value = "项目总览数据", notes = "项目总览页顶部指标与研究执行概览", response = DataResult.class, httpMethod = "POST")
	@PostMapping("/projectsData")
	public DataResult<ProjectsDataVo> projectsData(
			@RequestParam(value = "ra", required = true) @ApiParam(value = "病种，1=RA类风湿", required = true) Integer ra,
			@RequestParam(value = "doctorId", required = true) @ApiParam(value = "医生ID（总览为项目级统计，不按医生过滤）", required = true) Long doctorId,
			HttpServletRequest request) {
		DataResult<ProjectsDataVo> result = new DataResult<ProjectsDataVo>();
		if (ra == null || ra != DISEASE_RA) {
			result.setCode("500");
			result.setMessage("暂不支持该病种");
			result.setSuccess(false);
			return result;
		}
		try {
			result.setCode("200");
			result.setData(projectService.getProjectsData());
			result.setMessage("成功");
			result.setSuccess(true);
		} catch (Exception e) {
			e.printStackTrace();
			result.setCode("500");
			result.setMessage("获取项目总览数据失败");
			result.setSuccess(false);
		}
		return result;
	}
}
