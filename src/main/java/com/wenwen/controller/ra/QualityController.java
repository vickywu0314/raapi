package com.wenwen.controller.ra;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wenwen.service.QualityService;
import com.wenwen.util.BizException;
import com.wenwen.vo.DataResult;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

@Api(description = "数据质控")
@CrossOrigin
@RestController
@RequestMapping(value = "/api/ra/quality")
public class QualityController {

	@Autowired
	private QualityService qualityService;

	@Autowired(required = false)
	private com.wenwen.config.ReadCache readCache = com.wenwen.config.ReadCache.DISABLED;

	@ApiOperation(value = "质控问题清单", notes = "data-quality.html：按当前数据实时检查医生名下 RA 患者的缺失、异常值、逻辑冲突，附统计", response = DataResult.class, httpMethod = "POST")
	@PostMapping("/issues")
	public DataResult<Map<String, Object>> issues(
			@RequestParam(value = "doctorId", required = true) @ApiParam(value = "医生ID", required = true) Long doctorId) {
		DataResult<Map<String, Object>> result = new DataResult<Map<String, Object>>();
		try {
			ok(result, readCache.get("quality:" + doctorId, () -> qualityService.issues(doctorId)));
		} catch (Exception e) {
			fail(result, e, "获取质控问题失败");
		}
		return result;
	}

	@ApiOperation(value = "标记已处理", notes = "写 qc_issue_resolution 和修改记录「质控处理」；数据改好后问题会自动消失，不需要标记", response = DataResult.class, httpMethod = "POST")
	@PostMapping("/resolve")
	public DataResult<Void> resolve(
			@RequestParam(value = "doctorId", required = true) @ApiParam(value = "医生ID", required = true) Long doctorId,
			@RequestParam(value = "patientId", required = true) @ApiParam(value = "患者ID", required = true) Long patientId,
			@RequestParam(value = "issueKey", required = true) @ApiParam(value = "问题编码（issues 返回的 key）", required = true) String issueKey,
			@RequestParam(value = "note", required = false) @ApiParam(value = "处理说明") String note) {
		DataResult<Void> result = new DataResult<Void>();
		try {
			qualityService.resolve(doctorId, patientId, issueKey, note);
			ok(result, null);
			result.setMessage("已标记处理");
		} catch (Exception e) {
			fail(result, e, "标记失败");
		}
		return result;
	}

	private static <T> void ok(DataResult<T> r, T data) {
		r.setCode("200");
		r.setMessage("成功");
		r.setSuccess(true);
		r.setData(data);
	}

	private static void fail(DataResult<?> r, Exception e, String message) {
		r.setSuccess(false);
		if (e instanceof BizException) {
			r.setCode(((BizException) e).getCode());
			r.setMessage(e.getMessage());
		} else if (e instanceof IllegalArgumentException) {
			r.setCode("400");
			r.setMessage(e.getMessage());
		} else {
			e.printStackTrace();
			r.setCode("500");
			r.setMessage(message);
		}
	}
}
