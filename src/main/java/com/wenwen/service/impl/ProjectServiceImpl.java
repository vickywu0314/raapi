package com.wenwen.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.wenwen.mapper.ProjectMapper;
import com.wenwen.service.ProjectService;
import com.wenwen.vo.ProjectsDataVo;

@Service
public class ProjectServiceImpl implements ProjectService {

	/** 参与研究中心数，一期固定为 1 */
	private static final int CENTER_COUNT = 1;

	@Autowired
	private ProjectMapper projectMapper;

    @Autowired
    private com.wenwen.ai.qc.QcSnapshotReader qcReader;

	/** 随访周期（天），用于计划随访完成率和待随访判断 */
	@Value("${ra.followup.cycle-days:90}")
	private int followUpCycleDays;

	@Override
	public ProjectsDataVo getProjectsData() {
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("cycleDays", followUpCycleDays);

		com.wenwen.ai.qc.QcSnapshot snapshot=qcReader.readProject();
        int total = snapshot.getPatientIds().size();
		int active = projectMapper.countActivePatients();

		Map<String, Object> plan = projectMapper.countFollowUpPlan(map);
		int due = toInt(plan.get("dueCount"));
		int done = toInt(plan.get("doneCount"));

		int issueCount = snapshot.getIssueCount();
        int issuePatients = snapshot.getIssuePatients();

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
		return vo;
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
