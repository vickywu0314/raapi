package com.wenwen.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.wenwen.ai.source.ClinicalScalarReader;
import com.wenwen.mapper.DashboardMapper;
import com.wenwen.service.DashboardService;
import com.wenwen.service.FollowupService;
import com.wenwen.service.PatientService;
import com.wenwen.util.Das28Util;
import com.wenwen.vo.FollowupPlanVo;
import com.wenwen.vo.PatientItemVo;

/**
 * 数据看板。口径与其他页面一致：
 * 随访状态 / DAS28 活动度 / 资料完整性 / 合并症 取患者列表同一套计算（PatientService.listAllPatients）；
 * 逾期 / 到期 / 按期随访率 / 疑似失访 取随访管理同一套计划（FollowupService.listPlans）。
 */
@Service
public class DashboardServiceImpl implements DashboardService {

	private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
	/** 名单最多返回多少人（页面显示前 12 位，其余去对应页面看） */
	private static final int LIST_MAX = 50;
	private static final BigDecimal CRP_HIGH = new BigDecimal("10"), CRP_ABNORMAL = new BigDecimal("20"), REBOUND = new BigDecimal("0.6");

	@Autowired
	private PatientService patientService;
	@Autowired
	private FollowupService followupService;
	@Autowired
	private DashboardMapper mapper;
	@Autowired
	private com.wenwen.service.QualityService qualityService;

	@Override
	public Map<String, Object> summary(Long doctorId) {
		if (doctorId == null) {
			throw new IllegalArgumentException("缺少医生ID");
		}
		LocalDate today = LocalDate.now(SHANGHAI);
		PatientService.AllPatients all = patientService.listAllPatients(doctorId);
		List<PatientItemVo> items = all.items;
		List<FollowupPlanVo> plans = followupService.listPlans(doctorId);
		Map<Long, PatientItemVo> byId = new HashMap<Long, PatientItemVo>();
		for (PatientItemVo p : items) {
			byId.put(p.getPatientId(), p);
		}
		Map<String, Object> out = new LinkedHashMap<String, Object>();
		out.put("asOf", today.toString());

		// 患者规模
		int total = items.size(), withdrawn = 0, active = 0, newThisMonth = 0, totalVisits = 0, visited30 = 0, visited90 = 0, valid = 0;
		for (PatientItemVo p : items) {
			boolean out_ = "withdrawn".equals(p.getFollowStatus());
			LocalDate last = date(p.getLastVisitDate());
			withdrawn += out_ ? 1 : 0;
			active += !out_ && last != null && !last.isBefore(today.minusDays(183)) ? 1 : 0;
			visited30 += last != null && !last.isBefore(today.minusDays(30)) ? 1 : 0;
			visited90 += last != null && !last.isBefore(today.minusDays(90)) ? 1 : 0;
			totalVisits += p.getVisitCount();
			valid += p.getVisitCount() > 0 && !p.isIncomplete() && !out_ ? 1 : 0;
		}
		String month = today.toString().substring(0, 7);
		for (Map<String, Object> r : mapper.listCreateDates(doctorId)) {
			String d = (String) r.get("createDate");
			newThisMonth += d != null && d.startsWith(month) ? 1 : 0;
		}
		int lost = count(plans, "lost");
		out.put("scale", map("total", total, "active", active, "newThisMonth", newThisMonth, "withdrawn", withdrawn, "lost", lost));

		// 随访（与随访管理同一口径；不含已脱落、疑似失访）
		int overdue = count(plans, "overdue"), pendingFirst = count(plans, "pending_first"), d7 = 0, d30 = 0;
		for (FollowupPlanVo x : plans) {
			boolean due = ("soon".equals(x.getStatus()) || "active".equals(x.getStatus())) && x.getDays() != null && x.getDays() >= 0;
			d7 += due && x.getDays() <= 7 ? 1 : 0;
			d30 += due && x.getDays() <= 30 ? 1 : 0;
		}
		int planned = plans.size() - lost, based = planned - pendingFirst;
		out.put("followup", map("planned", planned, "pendingFirst", pendingFirst, "overdue", overdue, "due7", d7, "due30", d30,
				"onTimeRate", based == 0 ? null : Math.round((based - overdue) * 100.0 / based)));

		// 疾病活动度（最近一次 DAS28-CRP，CRP 专用阈值）
		Map<String, Integer> dist = new LinkedHashMap<String, Integer>();
		for (String k : Arrays.asList(Das28Util.REMISSION, Das28Util.LOW, Das28Util.MODERATE, Das28Util.HIGH)) {
			dist.put(k, 0);
		}
		int evaluable = 0, fm = 0;
		for (PatientItemVo p : items) {
			if (p.getDas28Activity() != null && dist.containsKey(p.getDas28Activity())) {
				dist.put(p.getDas28Activity(), dist.get(p.getDas28Activity()) + 1);
				evaluable++;
			}
			fm += "TRUE".equals(p.getFmState()) ? 1 : 0;
		}
		List<Map<String, Object>> distList = new ArrayList<Map<String, Object>>();
		for (Map.Entry<String, Integer> e : dist.entrySet()) {
			distList.add(map("key", e.getKey(), "label", Das28Util.label(e.getKey()), "n", e.getValue()));
		}
		int target = dist.get(Das28Util.REMISSION) + dist.get(Das28Util.LOW);
		out.put("activity", map("evaluable", evaluable, "targetRate", evaluable == 0 ? null : Math.round(target * 100.0 / evaluable), "dist", distList));

		// 病情变化（与患者列表 trend 同一计算）：有 ≥2 次有效 DAS28-CRP 的患者，最近一次 − 首次 ≤ −0.6 改善 / ≥ +0.6 加重 / 其余稳定
		int better = 0, stable = 0, worse = 0;
		for (PatientItemVo p : items) {
			better += Das28Util.BETTER.equals(p.getTrend()) ? 1 : 0;
			stable += Das28Util.STABLE.equals(p.getTrend()) ? 1 : 0;
			worse += Das28Util.WORSE.equals(p.getTrend()) ? 1 : 0;
		}
		out.put("outcome", map("n", better + stable + worse, "better", better, "stable", stable, "worse", worse));

		// 最近一次 CRP（最近一次填了 C 反应蛋白的随访）
		Map<Long, BigDecimal> crp = new HashMap<Long, BigDecimal>();
		List<Map<String, Object>> recentHighCrp = new ArrayList<Map<String, Object>>();
		for (Map<String, Object> r : mapper.listCrpVisits(doctorId)) {
			Long pid = ((Number) r.get("patientId")).longValue();
			if (crp.containsKey(pid) || !byId.containsKey(pid)) {
				continue;
			}
			BigDecimal v = number(ClinicalScalarReader.read((String) r.get("fzjc"), new LinkedHashSet<String>()).get("/cfydb"));
			if (v == null) {
				continue;
			}
			crp.put(pid, v);
			if (v.compareTo(CRP_ABNORMAL) > 0 && recentHighCrp.size() < 5) {
				PatientItemVo p = byId.get(pid);
				recentHighCrp.add(map("patientId", pid, "name", p.getName(), "crp", v, "visitDate", r.get("visitDate")));
			}
		}
		int highCrp = 0;
		for (BigDecimal v : crp.values()) {
			highCrp += v.compareTo(CRP_HIGH) > 0 ? 1 : 0;
		}
		out.put("attention", map("notTarget", dist.get(Das28Util.MODERATE) + dist.get(Das28Util.HIGH), "highActivity", dist.get(Das28Util.HIGH),
				"highCrp", highCrp, "fm", fm));

		// 数据质量（患者列表「待补全」同一规则）
		int incomplete = 0;
		Map<String, Integer> byItem = new LinkedHashMap<String, Integer>();
		for (PatientItemVo p : items) {
			if (!p.isIncomplete()) {
				continue;
			}
			incomplete++;
			for (String m : p.getMissingItems()) {
				byItem.put(m, byItem.containsKey(m) ? byItem.get(m) + 1 : 1);
			}
		}
		List<Map<String, Object>> items2 = new ArrayList<Map<String, Object>>();
		for (Map.Entry<String, Integer> e : byItem.entrySet()) {
			items2.add(map("item", e.getKey(), "n", e.getValue()));
		}
		out.put("quality", map("completeRate", total == 0 ? null : Math.round((total - incomplete) * 100.0 / total), "incomplete", incomplete, "byItem", items2));
		// 有效研究样本 = 数据质控「满足研究级质量要求」（有随访、未脱落、没有待处理的质控问题），两页一致
		Map<String, Object> qc = qualityService.issues(doctorId, all);
		valid = ((Number) qc.get("researchReady")).intValue();
		out.put("research", map("validSample", valid, "totalVisits", totalVisits, "visited30", visited30, "visited90", visited90));

		// 风险名单
		List<Map<String, Object>> rebound = new ArrayList<Map<String, Object>>(), high = new ArrayList<Map<String, Object>>(), longOverdue = new ArrayList<Map<String, Object>>();
		int reboundN = 0, highN = 0, longOverdueN = 0;
		for (PatientItemVo p : items) {
			BigDecimal prev = all.previousDas28.get(p.getPatientId());
			if (p.getLatestDas28() != null && prev != null && p.getLatestDas28().subtract(prev).compareTo(REBOUND) >= 0) {
				reboundN++;
				add(rebound, p, "DAS28-CRP " + prev.stripTrailingZeros().toPlainString() + " → " + p.getLatestDas28().stripTrailingZeros().toPlainString());
			}
			if (Das28Util.HIGH.equals(p.getDas28Activity())) {
				highN++;
				add(high, p, "DAS28-CRP " + p.getLatestDas28().stripTrailingZeros().toPlainString());
			}
		}
		for (FollowupPlanVo x : plans) {
			if ("overdue".equals(x.getStatus()) && x.getDays() != null && x.getDays() < -90) {
				longOverdueN++;
				if (longOverdue.size() < LIST_MAX) {
					longOverdue.add(map("patientId", x.getPatientId(), "name", x.getName(), "studyNo", x.getStudyNo(), "detail", "应于 " + x.getDueDate() + " 随访 · 逾期 " + (-x.getDays()) + " 天"));
				}
			}
		}
		List<Map<String, Object>> risks = new ArrayList<Map<String, Object>>();
		risks.add(map("key", "rebound", "name", "疾病活动度回升", "level", "高", "note", "最近一次 DAS28-CRP 比上一次升高 ≥ 0.6", "count", reboundN, "patients", rebound));
		risks.add(map("key", "high", "name", "高疾病活动", "level", "高", "note", "最近一次 DAS28-CRP > 4.1", "count", highN, "patients", high));
		risks.add(map("key", "overdue", "name", "随访逾期超 3 个月", "level", "中", "note", "存在失访风险，建议电话随访", "count", longOverdueN, "patients", longOverdue));
		out.put("risks", risks);
		out.put("recentHighCrp", recentHighCrp);

		// 最近完成的随访
		List<Map<String, Object>> recent = new ArrayList<Map<String, Object>>();
		for (Map<String, Object> r : mapper.listRecentVisits(doctorId, 5)) {
			int seq = ((Number) r.get("seq")).intValue();
			recent.add(map("patientId", r.get("patientId"), "visitId", r.get("visitId"), "name", r.get("name"), "visitDate", r.get("visitDate"),
					"label", seq <= 1 ? "基线访视" : "第 " + (seq - 1) + " 次随访"));
		}
		out.put("recentVisits", recent);
		return out;
	}

	private static void add(List<Map<String, Object>> list, PatientItemVo p, String detail) {
		if (list.size() < LIST_MAX) {
			list.add(map("patientId", p.getPatientId(), "name", p.getName(), "studyNo", p.getStudyNo(), "detail", detail));
		}
	}

	private static int count(List<FollowupPlanVo> plans, String status) {
		int n = 0;
		for (FollowupPlanVo x : plans) {
			n += status.equals(x.getStatus()) ? 1 : 0;
		}
		return n;
	}

	private static Map<String, Object> map(Object... kv) {
		Map<String, Object> m = new LinkedHashMap<String, Object>();
		for (int i = 0; i < kv.length; i += 2) {
			m.put((String) kv[i], kv[i + 1]);
		}
		return m;
	}

	private static BigDecimal number(String s) {
		try {
			return s == null || s.trim().isEmpty() ? null : new BigDecimal(s.trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static LocalDate date(String s) {
		try {
			return s == null ? null : LocalDate.parse(s);
		} catch (DateTimeParseException e) {
			return null;
		}
	}
}
