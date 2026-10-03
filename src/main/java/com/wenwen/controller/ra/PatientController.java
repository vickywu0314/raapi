package com.wenwen.controller.ra;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wenwen.service.PatientService;
import com.wenwen.vo.DataResult;
import com.wenwen.vo.PatientsListVo;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

@Api(description = "患者管理")
@CrossOrigin
@RestController
@RequestMapping(value = "/api/ra/patient")
public class PatientController {

	@Autowired
	private PatientService patientService;

	@ApiOperation(value = "患者列表", notes = "患者列表页：列表、模糊查询、筛选、分页、页头汇总；只返回该医生名下患者", response = DataResult.class, httpMethod = "POST")
	@PostMapping("/patientsList")
	public DataResult<PatientsListVo> patientsList(
			@RequestParam(value = "doctorId", required = true) @ApiParam(value = "医生ID，只返回该医生名下患者", required = true) Long doctorId,
			@RequestParam(value = "keyword", required = false) @ApiParam(value = "模糊查询：姓名 / ID号 / 研究编号，包含匹配") String keyword,
			@RequestParam(value = "followStatus", required = false) @ApiParam(value = "随访状态：active 随访中 / soon 近期需随访 / overdue 随访逾期 / pending_first 待首次随访 / withdrawn 已脱落；不传=全部") String followStatus,
			@RequestParam(value = "completeness", required = false) @ApiParam(value = "数据完整性：complete 数据完整 / missing 数据缺失；不传=全部") String completeness,
			@RequestParam(value = "page", required = false, defaultValue = "1") @ApiParam(value = "页码，从 1 开始，默认 1") int page,
			@RequestParam(value = "size", required = false, defaultValue = "20") @ApiParam(value = "每页条数，默认 20，最大 200") int size,
			HttpServletRequest request) {
		DataResult<PatientsListVo> result = new DataResult<PatientsListVo>();
		try {
			result.setCode("200");
			result.setData(patientService.listPatients(doctorId, keyword, followStatus, completeness, page, size));
			result.setMessage("成功");
			result.setSuccess(true);
		} catch (IllegalArgumentException e) {
			result.setCode("400");
			result.setMessage(e.getMessage());
			result.setSuccess(false);
		} catch (Exception e) {
			e.printStackTrace();
			result.setCode("500");
			result.setMessage("获取患者列表失败");
			result.setSuccess(false);
		}
		return result;
	}
}
