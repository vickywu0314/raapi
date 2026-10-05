package com.wenwen.controller.ra;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wenwen.service.VisitService;
import com.wenwen.util.BizException;
import com.wenwen.vo.DataResult;
import com.wenwen.vo.VisitDetailVo;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

@Api(description = "随访（访视）")
@CrossOrigin
@RestController
@RequestMapping(value = "/api/ra/visit")
public class VisitController {

	@Autowired
	private VisitService visitService;

	@ApiOperation(value = "访视详情", notes = "访视详情页（visit-detail.html）：7 个病历模块；患者基本信息另调 /api/ra/patient/patientDetail", response = DataResult.class, httpMethod = "POST")
	@PostMapping("/visitDetail")
	public DataResult<VisitDetailVo> visitDetail(
			@RequestParam(value = "doctorId", required = true) @ApiParam(value = "医生ID", required = true) Long doctorId,
			@RequestParam(value = "visitId", required = true) @ApiParam(value = "随访ID（patient_follow_up_history.id）", required = true) Long visitId) {
		DataResult<VisitDetailVo> result = new DataResult<VisitDetailVo>();
		try {
			result.setData(visitService.getVisitDetail(doctorId, visitId));
			result.setCode("200");
			result.setMessage("成功");
			result.setSuccess(true);
		} catch (BizException e) {
			result.setCode(e.getCode());
			result.setMessage(e.getMessage());
			result.setSuccess(false);
		} catch (IllegalArgumentException e) {
			result.setCode("400");
			result.setMessage(e.getMessage());
			result.setSuccess(false);
		} catch (Exception e) {
			e.printStackTrace();
			result.setCode("500");
			result.setMessage("获取访视详情失败");
			result.setSuccess(false);
		}
		return result;
	}

	@ApiOperation(value = "删除随访", notes = "访视详情页「删除本次随访」：物理删除，不可恢复；同时接好上一次随访关联、重新计算老系统的随访次数和日期、写修改记录", response = DataResult.class, httpMethod = "POST")
	@PostMapping("/deleteVisit")
	public DataResult<Long> deleteVisit(
			@RequestParam(value = "doctorId", required = true) @ApiParam(value = "医生ID", required = true) Long doctorId,
			@RequestParam(value = "visitId", required = true) @ApiParam(value = "随访ID", required = true) Long visitId,
			@RequestParam(value = "reason", required = true) @ApiParam(value = "删除原因（必填，≤500 字）", required = true) String reason) {
		DataResult<Long> result = new DataResult<Long>();
		try {
			result.setData(visitService.deleteVisit(doctorId, visitId, reason));
			result.setCode("200");
			result.setMessage("已删除");
			result.setSuccess(true);
		} catch (BizException e) {
			result.setCode(e.getCode());
			result.setMessage(e.getMessage());
			result.setSuccess(false);
		} catch (IllegalArgumentException e) {
			result.setCode("400");
			result.setMessage(e.getMessage());
			result.setSuccess(false);
		} catch (DataIntegrityViolationException e) {
			e.printStackTrace();
			result.setCode("409");
			result.setMessage("这次随访还被其他数据引用，无法删除，已撤销本次操作");
			result.setSuccess(false);
		} catch (Exception e) {
			e.printStackTrace();
			result.setCode("500");
			result.setMessage("删除随访失败，已撤销本次操作");
			result.setSuccess(false);
		}
		return result;
	}
}
