package com.wenwen.controller.ra;

import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import com.wenwen.ai.scope.PrincipalProvider;
import com.wenwen.ai.query.CohortException;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wenwen.service.PatientService;
import com.wenwen.service.PatientWriteService;
import com.wenwen.util.BizException;
import com.wenwen.vo.AuditLogVo;
import com.wenwen.vo.DataResult;
import com.wenwen.vo.PatientCheckVo;
import com.wenwen.vo.PatientCreateRequest;
import com.wenwen.vo.PatientCreateResultVo;
import com.wenwen.vo.PatientDetailVo;
import com.wenwen.vo.PatientEditFormVo;
import com.wenwen.vo.PatientUpdateRequest;
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

	@Autowired
	private PrincipalProvider principalProvider;

	@Autowired
	private PatientWriteService patientWriteService;

	@Autowired
	private com.wenwen.ocr.OcrService ocrService;

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

	@ApiOperation(value = "患者详情", notes = "患者详情页（patient-visits.html）：基本信息 + 常见相关疾病 + 随访时间线；只能查看自己名下患者", response = DataResult.class, httpMethod = "POST")
	@PostMapping("/patientDetail")
	public ResponseEntity<DataResult<PatientDetailVo>> patientDetail(HttpServletRequest request) {
        String trace = UUID.randomUUID().toString();
        DataResult<PatientDetailVo> result = new DataResult<PatientDetailVo>();
        int status = 200;
        try {
            long doctorId = principalProvider.requireCurrent().getId();
            long patientId = detailId(request.getParameterValues("patientId"));
            String[] hint = request.getParameterValues("doctorId");
            if (hint != null && detailId(hint) != doctorId) {
                throw new CohortException(403, "FORBIDDEN", "无权访问");
            }
            ok(result, patientService.getPatientDetail(doctorId, patientId));
        } catch (CohortException e) {
            status = e.getStatus();
            detailFailure(result, e.getCode(), status == 401 ? "请先登录" : "请求无法完成");
        } catch (BizException e) {
            status = 404;
            detailFailure(result, "RESOURCE_NOT_FOUND", "患者不可访问");
        } catch (Exception e) {
            status = 503;
            detailFailure(result, "SERVICE_UNAVAILABLE", "服务暂不可用");
        }
        return ResponseEntity.status(status).header("X-Trace-Id", trace).body(result);
    }

    private static long detailId(String[] values) {
        if (values == null || values.length != 1 || !values[0].matches("[1-9][0-9]{0,18}")) {
            throw new CohortException(400, "INVALID_REQUEST", "请求无效");
        }
        try {
            return Long.parseLong(values[0]);
        } catch (NumberFormatException e) {
            throw new CohortException(400, "INVALID_REQUEST", "请求无效");
        }
    }

    private static void detailFailure(DataResult<PatientDetailVo> result, String code, String message) {
        result.setCode(code);
        result.setMessage(message);
        result.setSuccess(false);
        result.setData(null);
    }

	@ApiOperation(value = "查看身份证号明文", notes = "患者详情页身份证号旁的「眼睛」按钮", response = DataResult.class, httpMethod = "POST")
	@PostMapping("/patientSensitive")
	public DataResult<String> patientSensitive(
			@RequestParam(value = "doctorId", required = true) @ApiParam(value = "医生ID", required = true) Long doctorId,
			@RequestParam(value = "patientId", required = true) @ApiParam(value = "患者ID", required = true) Long patientId) {
		DataResult<String> result = new DataResult<String>();
		try {
			ok(result, patientService.getCardNo(doctorId, patientId));
		} catch (Exception e) {
			fail(result, e, "获取身份证号失败");
		}
		return result;
	}

	@ApiOperation(value = "修改记录", notes = "患者详情页「修改记录」展开时查询；老数据没有记录时返回空数组", response = DataResult.class, httpMethod = "POST")
	@PostMapping("/auditLogs")
	public DataResult<List<AuditLogVo>> auditLogs(
			@RequestParam(value = "doctorId", required = true) @ApiParam(value = "医生ID", required = true) Long doctorId,
			@RequestParam(value = "patientId", required = true) @ApiParam(value = "患者ID", required = true) Long patientId) {
		DataResult<List<AuditLogVo>> result = new DataResult<List<AuditLogVo>>();
		try {
			ok(result, patientService.listAuditLogs(doctorId, patientId));
		} catch (Exception e) {
			fail(result, e, "获取修改记录失败");
		}
		return result;
	}

	@ApiOperation(value = "新建患者前按身份证号查重", notes = "新增患者页填完身份证号时调用。result：NEW 新患者 / OTHER_DISEASE 已在其他病种库（返回已有基本信息预填）/ RA_OTHER_DOCTOR 已在 RA 库其他医生名下 / MINE 已在本医生名下", response = DataResult.class, httpMethod = "POST")
	@PostMapping("/checkCardNo")
	public DataResult<PatientCheckVo> checkCardNo(
			@RequestParam(value = "doctorId", required = true) @ApiParam(value = "医生ID", required = true) Long doctorId,
			@RequestParam(value = "cardNo", required = false) @ApiParam(value = "身份证号") String cardNo) {
		DataResult<PatientCheckVo> result = new DataResult<PatientCheckVo>();
		try {
			ok(result, patientWriteService.checkCardNo(doctorId, cardNo));
		} catch (Exception e) {
			fail(result, e, "身份证号查重失败");
		}
		return result;
	}

	@ApiOperation(value = "新建患者", notes = "请求体为 JSON（PatientCreateRequest）。写 patient_basic_info + patient_relation_doctor，ACR/EULAR 各部分存 patient_acr_eular，写修改记录；身份证号已存在时按 checkCardNo 的规则处理，409 = 已存在（RA 库其他医生名下时传 transfer=true 转到自己名下）", response = DataResult.class, httpMethod = "POST")
	@PostMapping("/createPatient")
	public DataResult<PatientCreateResultVo> createPatient(@RequestBody PatientCreateRequest req) {
		DataResult<PatientCreateResultVo> result = new DataResult<PatientCreateResultVo>();
		try {
			PatientCreateResultVo r = patientWriteService.createPatient(req);
			ocrService.link(req.getOcrFileId(), req.getDoctorId(), r.getPatientId(), null);
			ok(result, r);
			result.setMessage("transferred".equals(r.getAction()) ? "已转到您名下" : "建档成功");
		} catch (Exception e) {
			fail(result, e, "新建患者失败");
		}
		return result;
	}

	@ApiOperation(value = "编辑档案 · 表单当前值", notes = "编辑档案页打开时调用；患者须在本医生名下", response = DataResult.class, httpMethod = "POST")
	@PostMapping("/editForm")
	public DataResult<PatientEditFormVo> editForm(
			@RequestParam(value = "doctorId", required = true) @ApiParam(value = "医生ID", required = true) Long doctorId,
			@RequestParam(value = "patientId", required = true) @ApiParam(value = "患者ID", required = true) Long patientId) {
		DataResult<PatientEditFormVo> result = new DataResult<PatientEditFormVo>();
		try {
			ok(result, patientWriteService.getEditForm(doctorId, patientId));
		} catch (Exception e) {
			fail(result, e, "获取档案失败");
		}
		return result;
	}

	@ApiOperation(value = "编辑档案 · 保存", notes = "请求体为 JSON（PatientUpdateRequest）。只更新 changed 里列出、且与原值不同的字段；写修改记录「修改档案」。data = 实际修改项数", response = DataResult.class, httpMethod = "POST")
	@PostMapping("/updatePatient")
	public DataResult<Integer> updatePatient(@RequestBody PatientUpdateRequest req) {
		DataResult<Integer> result = new DataResult<Integer>();
		try {
			int n = patientWriteService.updatePatient(req);
			ok(result, n);
			result.setMessage(n == 0 ? "没有修改" : "已保存 " + n + " 项修改");
		} catch (Exception e) {
			fail(result, e, "保存档案失败");
		}
		return result;
	}

	@ApiOperation(value = "标记脱落", notes = "本医生名下该患者的 RA 医患关系 miss=1；reason 取 失访 / 撤回知情同意 / 不良事件 / 死亡 / 其他（选其他时 remark 必填），remark 存 other_miss_reason；写修改记录「标记脱落」", response = DataResult.class, httpMethod = "POST")
	@PostMapping("/withdraw")
	public DataResult<Void> withdraw(
			@RequestParam(value = "doctorId", required = true) @ApiParam(value = "医生ID", required = true) Long doctorId,
			@RequestParam(value = "patientId", required = true) @ApiParam(value = "患者ID", required = true) Long patientId,
			@RequestParam(value = "reason", required = false) @ApiParam(value = "脱落原因", required = true) String reason,
			@RequestParam(value = "remark", required = false) @ApiParam(value = "补充说明") String remark) {
		DataResult<Void> result = new DataResult<Void>();
		try {
			patientWriteService.withdraw(doctorId, patientId, reason, remark);
			ok(result, null);
			result.setMessage("已标记脱落");
		} catch (Exception e) {
			fail(result, e, "标记脱落失败");
		}
		return result;
	}

	@ApiOperation(value = "恢复随访", notes = "脱落的患者又回来继续随访：miss 改回 0，原脱落原因保留；写修改记录「恢复随访」", response = DataResult.class, httpMethod = "POST")
	@PostMapping("/reactivate")
	public DataResult<Void> reactivate(
			@RequestParam(value = "doctorId", required = true) @ApiParam(value = "医生ID", required = true) Long doctorId,
			@RequestParam(value = "patientId", required = true) @ApiParam(value = "患者ID", required = true) Long patientId,
			@RequestParam(value = "remark", required = false) @ApiParam(value = "说明") String remark) {
		DataResult<Void> result = new DataResult<Void>();
		try {
			patientWriteService.reactivate(doctorId, patientId, remark);
			ok(result, null);
			result.setMessage("已恢复随访");
		} catch (Exception e) {
			fail(result, e, "恢复随访失败");
		}
		return result;
	}

	private static <T> void ok(DataResult<T> result, T data) {
		result.setCode("200");
		result.setData(data);
		result.setMessage("成功");
		result.setSuccess(true);
	}

	/** 参数错误 400、无权限等业务异常按其返回码，其余 500 */
	private static void fail(DataResult<?> result, Exception e, String message) {
		if (e instanceof BizException) {
			result.setCode(((BizException) e).getCode());
			result.setMessage(e.getMessage());
		} else if (e instanceof IllegalArgumentException) {
			result.setCode("400");
			result.setMessage(e.getMessage());
		} else {
			e.printStackTrace();
			result.setCode("500");
			result.setMessage(message);
		}
		result.setSuccess(false);
	}
}
