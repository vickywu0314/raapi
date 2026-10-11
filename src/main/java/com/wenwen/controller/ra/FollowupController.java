package com.wenwen.controller.ra;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wenwen.service.FollowupService;
import com.wenwen.vo.DataResult;
import com.wenwen.vo.FollowupPlanVo;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

@Api(description = "随访管理")
@CrossOrigin
@RestController
@RequestMapping(value = "/api/ra/followup")
public class FollowupController {

	@Autowired
	private FollowupService followupService;

	@ApiOperation(value = "随访计划", notes = "随访管理页：当前医生名下未脱落的 RA 患者，下次随访预计日期 = 最近随访 + 随访周期（没有随访取建档日期），按预计日期从早到晚；不分页", response = DataResult.class, httpMethod = "POST")
	@PostMapping("/plans")
	public DataResult<List<FollowupPlanVo>> plans(
			@RequestParam(value = "doctorId", required = true) @ApiParam(value = "医生ID", required = true) Long doctorId) {
		DataResult<List<FollowupPlanVo>> result = new DataResult<List<FollowupPlanVo>>();
		try {
			result.setData(followupService.listPlans(doctorId));
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
			result.setMessage("获取随访计划失败");
			result.setSuccess(false);
		}
		return result;
	}
}
