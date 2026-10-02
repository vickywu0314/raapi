package com.wenwen.controller.ra;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wenwen.mapper.ProjectMapper;
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
	/** 参与研究中心数，一期固定为 1 */
	private static final int CENTER_COUNT = 1;

	@Autowired
	private ProjectMapper projectMapper;

	/** 随访周期（天），用于计划随访完成率和待随访判断 */
	@Value("${ra.followup.cycle-days:90}")
	private int followUpCycleDays;

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
			Map<String, Object> map = new HashMap<String, Object>();
			map.put("cycleDays", followUpCycleDays);

			int total = projectMapper.countAllPatients();
			int active = projectMapper.countActivePatients();

			Map<String, Object> plan = projectMapper.countFollowUpPlan(map);
			int due = toInt(plan.get("dueCount"));
			int done = toInt(plan.get("doneCount"));

			Map<String, Object> qc = projectMapper.countPendingQc();
			int issueCount = toInt(qc.get("issueCount"));
			int issuePatients = toInt(qc.get("patientCount"));

			ProjectsDataVo vo = new ProjectsDataVo();
			vo.setEnrolledPatients(total);
			vo.setFollowUpDueCount(due);
			vo.setFollowUpDoneCount(done);
			vo.setFollowUpCompletionRate(percent(done, due));
			vo.setDataQualityRate(percent(total - issuePatients, total));
			vo.setCenterCount(CENTER_COUNT);
			vo.setTrackingPatients(active);
			vo.setTotalPatients(total);
			vo.setTrackingRate(percent(active, total));
			vo.setPendingFollowUpPatients(projectMapper.countPendingFollowUp(map));
			vo.setPendingQcIssues(issueCount);
			vo.setQcIssuePatients(issuePatients);
			vo.setResearchUsableRecords(projectMapper.countUsableRecords());

			result.setCode("200");
			result.setData(vo);
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

	/** 分子/分母*100，保留1位小数；分母为0时返回0.0 */
	private static BigDecimal percent(int numerator, int denominator) {
		if (denominator <= 0) {
			return BigDecimal.ZERO.setScale(1);
		}
		return new BigDecimal(numerator).multiply(new BigDecimal(100))
				.divide(new BigDecimal(denominator), 1, RoundingMode.HALF_UP);
	}

	private static int toInt(Object value) {
		return value == null ? 0 : ((Number) value).intValue();
	}
}
