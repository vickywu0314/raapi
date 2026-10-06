package com.wenwen.controller.ra;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wenwen.service.ExportService;
import com.wenwen.util.BizException;
import com.wenwen.vo.DataResult;
import com.wenwen.vo.ExportRangeVo;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

@Api(description = "数据导出")
@CrossOrigin
@RestController
@RequestMapping(value = "/api/ra/export")
public class ExportController {

	@Autowired
	private ExportService exportService;

	@ApiOperation(value = "可直接下载的时间范围", notes = "患者列表「数据导出」弹窗的默认日期", response = DataResult.class, httpMethod = "POST")
	@PostMapping("/range")
	public DataResult<ExportRangeVo> range() {
		DataResult<ExportRangeVo> result = new DataResult<ExportRangeVo>();
		result.setData(exportService.getRange());
		result.setCode("200");
		result.setMessage("成功");
		result.setSuccess(true);
		return result;
	}

	@ApiOperation(value = "下载随访数据（CSV）", notes = "成功时直接返回 CSV 文件（text/csv）；失败时返回 JSON（DataResult），code=NEED_APPLY 表示超过半年需提交申请", httpMethod = "POST")
	@PostMapping("/visitsCsv")
	public ResponseEntity<?> visitsCsv(
			@RequestParam(value = "doctorId", required = true) @ApiParam(value = "医生ID", required = true) Long doctorId,
			@RequestParam(value = "startDate", required = true) @ApiParam(value = "随访日期起 yyyy-MM-dd", required = true) String startDate,
			@RequestParam(value = "endDate", required = true) @ApiParam(value = "随访日期止 yyyy-MM-dd", required = true) String endDate,
			@RequestParam(value = "keyword", required = false) @ApiParam(value = "同患者列表") String keyword,
			@RequestParam(value = "followStatus", required = false) @ApiParam(value = "同患者列表") String followStatus,
			@RequestParam(value = "completeness", required = false) @ApiParam(value = "同患者列表") String completeness,
			@RequestParam(value = "patientIds", required = false) @ApiParam(value = "勾选的患者ID，逗号分隔；传了则只导出这些患者") String patientIds) {
		try {
			byte[] csv = exportService.exportVisitsCsv(doctorId, startDate, endDate, keyword, followStatus, completeness, ids(patientIds));
			String file = URLEncoder.encode("RA随访数据-" + startDate + "至" + endDate + ".csv", StandardCharsets.UTF_8.name()).replace("+", "%20");
			return ResponseEntity.ok()
					.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + file)
					.contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
					.body(csv);
		} catch (BizException e) {
			return ResponseEntity.ok(fail(e.getCode(), e.getMessage()));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.ok(fail("400", e.getMessage()));
		} catch (Exception e) {
			e.printStackTrace();
			return ResponseEntity.ok(fail("500", "导出失败"));
		}
	}

	@ApiOperation(value = "提交导出申请", notes = "超过半年的数据：填写原因提交申请，记录后发送到指定邮箱（发邮件待实现）；返回申请编号", response = DataResult.class, httpMethod = "POST")
	@PostMapping("/apply")
	public DataResult<Long> apply(
			@RequestParam(value = "doctorId", required = true) @ApiParam(value = "医生ID", required = true) Long doctorId,
			@RequestParam(value = "startDate", required = true) @ApiParam(value = "随访日期起", required = true) String startDate,
			@RequestParam(value = "endDate", required = true) @ApiParam(value = "随访日期止", required = true) String endDate,
			@RequestParam(value = "reason", required = true) @ApiParam(value = "申请原因 / 用途", required = true) String reason,
			@RequestParam(value = "keyword", required = false) String keyword,
			@RequestParam(value = "followStatus", required = false) String followStatus,
			@RequestParam(value = "completeness", required = false) String completeness,
			@RequestParam(value = "patientIds", required = false) String patientIds) {
		DataResult<Long> result = new DataResult<Long>();
		try {
			result.setData(exportService.apply(doctorId, startDate, endDate, keyword, followStatus, completeness, ids(patientIds), reason));
			result.setCode("200");
			result.setMessage("申请已提交");
			result.setSuccess(true);
		} catch (IllegalArgumentException e) {
			result.setCode("400");
			result.setMessage(e.getMessage());
			result.setSuccess(false);
		} catch (Exception e) {
			e.printStackTrace();
			result.setCode("500");
			result.setMessage("提交申请失败");
			result.setSuccess(false);
		}
		return result;
	}

	private static List<Long> ids(String s) {
		List<Long> ids = new ArrayList<Long>();
		if (s == null || s.trim().isEmpty()) {
			return ids;
		}
		for (String p : s.split(",")) {
			try {
				ids.add(Long.valueOf(p.trim()));
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("患者ID不正确：" + p);
			}
		}
		return ids;
	}

	private static DataResult<Object> fail(String code, String message) {
		DataResult<Object> r = new DataResult<Object>();
		r.setCode(code);
		r.setMessage(message);
		r.setSuccess(false);
		return r;
	}
}
