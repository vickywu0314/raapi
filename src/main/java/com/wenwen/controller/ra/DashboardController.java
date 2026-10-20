package com.wenwen.controller.ra;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wenwen.service.DashboardService;
import com.wenwen.vo.DataResult;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

@Api(description = "数据看板")
@CrossOrigin
@RestController
@RequestMapping(value = "/api/ra/dashboard")
public class DashboardController {

	@Autowired
	private DashboardService dashboardService;
	@Autowired(required = false)
	private com.wenwen.config.ReadCache readCache = com.wenwen.config.ReadCache.DISABLED;


	@ApiOperation(value = "数据看板", notes = "dashboard.html 全部数字和名单；只统计当前医生名下的 RA 患者", response = DataResult.class, httpMethod = "POST")
	@PostMapping("/summary")
	public DataResult<Map<String, Object>> summary(
			@RequestParam(value = "doctorId", required = true) @ApiParam(value = "医生ID", required = true) Long doctorId) {
		DataResult<Map<String, Object>> result = new DataResult<Map<String, Object>>();
		try {
			result.setData(readCache.get("dashboard:" + doctorId, () -> dashboardService.summary(doctorId)));
			result.setCode("200");
			result.setMessage("成功");
			result.setSuccess(true);
		} catch (IllegalArgumentException e) {
			result.setCode("400");
			result.setMessage(e.getMessage());
			result.setSuccess(false);
		} catch (Exception e) {
			e.printStackTrace();
			result.setCode("500");
			result.setMessage("获取看板数据失败");
			result.setSuccess(false);
		}
		return result;
	}
}
