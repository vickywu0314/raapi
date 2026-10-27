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

	@Autowired(required = false)
	private java.time.Clock clock = java.time.Clock.systemUTC();

	/** 按月入组统计的月数（含本月） */
	private static final int ENROLL_MONTHS = 12;

	/** 随访周期（天），用于计划随访完成率和待随访判断 */
	@Value("${ra.followup.cycle-days:90}")
	private int followUpCycleDays;

	@Override
	public ProjectsDataVo getProjectsData() {
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("cycleDays", followUpCycleDays);
		// 最近 12 个月（含本月，上海日期）：[11 个月前的 1 号, 下月 1 号)
		java.time.YearMonth thisMonth = java.time.YearMonth.from(clock.instant().atZone(java.time.ZoneId.of("Asia/Shanghai")));
		java.time.YearMonth firstMonth = thisMonth.minusMonths(ENROLL_MONTHS - 1);
		map.put("enrollFrom", firstMonth.atDay(1).toString());
		map.put("enrollTo", thisMonth.plusMonths(1).atDay(1).toString());

		com.wenwen.ai.qc.QcSnapshot snapshot=qcReader.readProject();
        int total = snapshot.getPatientIds().size();
		Map<String, Object> plan = projectMapper.countProjectMetrics(map);
		int active = toInt(plan.get("activeCount"));
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
		vo.setPendingFollowUpPatients(toInt(plan.get("pendingCount")));
		vo.setPendingQcIssues(issueCount);
		vo.setQcIssuePatients(issuePatients);
		vo.setResearchUsableRecords(toInt(plan.get("usableCount")));
		// 随访完成 2 / 3 / 4 次（不含基线）：与上面同一次查询
		String[] times = String.valueOf(plan.get("followUpTimes") == null ? "0,0,0" : plan.get("followUpTimes")).split(",");
		vo.setFollowUp2Count(Integer.parseInt(times[0].trim()));
		vo.setFollowUp3Count(Integer.parseInt(times[1].trim()));
		vo.setFollowUp4Count(Integer.parseInt(times[2].trim()));
		vo.setFollowUp2Rate(percent(vo.getFollowUp2Count(), total));
		vo.setFollowUp3Rate(percent(vo.getFollowUp3Count(), total));
		vo.setFollowUp4Rate(percent(vo.getFollowUp4Count(), total));
		vo.setEnrollByMonth(enrollByMonth(firstMonth, plan.get("enrollByMonth")));
		return vo;
	}

	/** 「yyyy-MM=人数,…」→ 连续 12 个月 [{month, count}]，按月份升序，没有人的月份为 0 */
	public static java.util.List<Map<String, Object>> enrollByMonth(java.time.YearMonth firstMonth, Object raw) {
		Map<String, Integer> counts = new HashMap<String, Integer>();
		if (raw != null) {
			for (String part : String.valueOf(raw).split(",")) {
				String[] kv = part.trim().split("=");
				if (kv.length == 2) {
					counts.put(kv[0], Integer.parseInt(kv[1].trim()));
				}
			}
		}
		java.util.List<Map<String, Object>> out = new java.util.ArrayList<Map<String, Object>>();
		for (int i = 0; i < ENROLL_MONTHS; i++) {
			String month = firstMonth.plusMonths(i).toString();
			Map<String, Object> m = new java.util.LinkedHashMap<String, Object>();
			m.put("month", month);
			m.put("count", counts.containsKey(month) ? counts.get(month) : 0);
			out.add(m);
		}
		return out;
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
