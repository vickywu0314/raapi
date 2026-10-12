package com.wenwen.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson.JSONObject;
import com.wenwen.mapper.QualityMapper;
import com.wenwen.service.AuditLogService;
import com.wenwen.service.PatientService;
import com.wenwen.service.QualityService;
import com.wenwen.util.BizException;
import com.wenwen.util.VisitJson;
import com.wenwen.vo.PatientItemVo;

/**
 * 数据质控规则（只读老表，按当前数据实时算；数据改好后问题自动消失）：
 * 缺失 —— 与患者列表「待补全」同一规则（缺 DAS28 评分 / 缺基线检验 / 缺用药史 / 缺合并疾病记录）；
 * 异常值 —— 化验值超出合理范围或填了非数字内容（勾了「未查」的不算）；档案血压收缩压 ≤ 舒张压；
 * 逻辑冲突 —— 随访日期晚于今天、随访日期早于发病时间、确诊日期早于发病时间、同一天多条随访。
 */
@Service
public class QualityServiceImpl implements QualityService {

	private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
	/** 化验合理范围（超出多半是录错，不是临床正常值范围）：字段 → 名称、单位、下限、上限 */
	private static final Map<String, Object[]> LAB_RANGES = new LinkedHashMap<String, Object[]>();
	static {
		LAB_RANGES.put("xc", new Object[] { "血沉", "mm/h", 0, 150 });
		LAB_RANGES.put("cfydb", new Object[] { "C反应蛋白", "mg/L", 0, 300 });
		LAB_RANGES.put("cmcfydb", new Object[] { "超敏C反应蛋白", "mg/L", 0, 300 });
		LAB_RANGES.put("bxb", new Object[] { "白细胞", "×10⁹/L", 0.5, 100 });
		LAB_RANGES.put("xhdb", new Object[] { "血红蛋白", "g/L", 30, 250 });
		LAB_RANGES.put("xxb", new Object[] { "血小板", "×10⁹/L", 5, 2000 });
		LAB_RANGES.put("alt", new Object[] { "谷丙转氨酶", "U/L", 0, 5000 });
		LAB_RANGES.put("ast", new Object[] { "谷草转氨酶", "U/L", 0, 5000 });
		LAB_RANGES.put("crea", new Object[] { "肌酐", "μmol/L", 10, 2000 });
		LAB_RANGES.put("urea", new Object[] { "尿素", "mmol/L", 0.5, 100 });
	}
	/** 患者列表缺失项文字 → 编码、定位 */
	private static final Map<String, String[]> MISSING = new LinkedHashMap<String, String[]>();
	static {
		MISSING.put("缺 DAS28 评分", new String[] { "M_DAS28", "2", "病情评估" });
		MISSING.put("缺基线检验", new String[] { "M_BASELINE_LAB", "1", "辅助检查" });
		MISSING.put("缺用药史", new String[] { "M_MEDICATION", "4", "治疗方案" });
		MISSING.put("缺合并疾病记录", new String[] { "M_COMORBIDITY", null, null });
	}

	@Autowired
	private PatientService patientService;
	@Autowired
	private QualityMapper mapper;
	@Autowired
	private AuditLogService auditLogService;
	@Autowired
	private com.wenwen.mapper.PatientWriteMapper writeMapper;

	@Override
	public Map<String, Object> issues(Long doctorId) {
		if (doctorId == null) {
			throw new IllegalArgumentException("缺少医生ID");
		}
		return issues(doctorId, patientService.listAllPatients(doctorId));
	}

	@Override
	public Map<String, Object> issues(Long doctorId, PatientService.AllPatients all) {
		LocalDate today = LocalDate.now(SHANGHAI);
		Map<Long, PatientItemVo> byId = new LinkedHashMap<Long, PatientItemVo>();
		for (PatientItemVo p : all.items) {
			byId.put(p.getPatientId(), p);
		}
		Map<Long, Map<String, Object>> facts = new HashMap<Long, Map<String, Object>>();
		for (Map<String, Object> f : mapper.listPatientFacts(doctorId)) {
			facts.put(((Number) f.get("patientId")).longValue(), f);
		}
		Map<Long, List<Map<String, Object>>> visits = new HashMap<Long, List<Map<String, Object>>>();
		for (Map<String, Object> v : mapper.listVisits(doctorId)) {
			visits.computeIfAbsent(((Number) v.get("patientId")).longValue(), k -> new ArrayList<Map<String, Object>>()).add(v);
		}
		Map<String, Map<String, Object>> resolved = new HashMap<String, Map<String, Object>>();
		boolean tableReady = true;
		try {
			for (Map<String, Object> r : mapper.listResolutions(doctorId)) {
				resolved.put(r.get("patientId") + "|" + r.get("issueKey"), r);
			}
		} catch (DataAccessException e) {
			tableReady = false; // 新表还没建：都按待处理显示，标记已处理时提示建表
		}

		List<Map<String, Object>> issues = new ArrayList<Map<String, Object>>();
		for (PatientItemVo p : all.items) {
			Long pid = p.getPatientId();
			List<Map<String, Object>> vs = visits.containsKey(pid) ? visits.get(pid) : new ArrayList<Map<String, Object>>();
			Object baseline = vs.isEmpty() ? null : vs.get(0).get("visitId");
			Map<String, Object> f = facts.containsKey(pid) ? facts.get(pid) : new HashMap<String, Object>();

			// 缺失（患者列表「待补全」同一规则）
			for (String label : p.getMissingItems() == null ? new ArrayList<String>() : p.getMissingItems()) {
				String[] m = MISSING.get(label);
				String code = m == null ? label : m[0];
				Map<String, Object> target;
				if (m != null && m[1] == null) {
					target = target("edit", null, null, "编辑档案 · 常见相关疾病");
				} else if (baseline == null) {
					target = target("create", null, null, "尚无访视 · 新增随访录入基线访视");
				} else {
					target = target("visit", baseline, m == null ? null : m[1], "基线访视 · " + (m == null ? "病历" : m[2]));
				}
				issues.add(issue(p, "missing:" + code, "缺失", label, target));
			}

			// 异常值：化验
			for (Map<String, Object> v : vs) {
				JSONObject fz = VisitJson.parse((String) v.get("fzjc"));
				if (fz == null) {
					continue;
				}
				JSONObject wx = fz.get("xcgWx") instanceof JSONObject ? fz.getJSONObject("xcgWx") : null;
				for (Map.Entry<String, Object[]> e : LAB_RANGES.entrySet()) {
					String raw = VisitJson.text(fz.get(e.getKey()));
					if (raw == null || (wx != null && VisitJson.isTrue(wx.get(e.getKey())))) {
						continue;
					}
					Object[] r = e.getValue();
					BigDecimal n = number(raw);
					String problem = null;
					if (n == null) {
						problem = "填了非数字内容「" + raw + "」";
					} else if (n.doubleValue() < ((Number) r[2]).doubleValue() || n.doubleValue() > ((Number) r[3]).doubleValue()) {
						problem = raw + " " + r[1] + "，超出合理范围 " + r[2] + "~" + r[3];
					}
					if (problem != null) {
						issues.add(issue(p, "lab:" + v.get("visitId") + ":" + e.getKey(), "异常值", r[0] + " " + problem + "（" + dateText(v) + " 随访）",
								target("visit", v.get("visitId"), "1", dateText(v) + " 随访 · 辅助检查")));
					}
				}
			}
			// 异常值：血压
			BigDecimal sbp = number(str(f.get("xyH"))), dbp = number(str(f.get("xyL")));
			if (sbp != null && dbp != null && sbp.compareTo(dbp) <= 0) {
				issues.add(issue(p, "value:bp", "异常值", "血压收缩压 " + sbp.toPlainString() + " ≤ 舒张压 " + dbp.toPlainString() + "，疑似填反",
						target("edit", null, null, "编辑档案 · 体征")));
			}

			// 逻辑冲突
			LocalDate happen = date(str(f.get("happenDate"))), confirm = date(str(f.get("confirmDate")));
			if (happen != null && confirm != null && confirm.isBefore(happen)) {
				issues.add(issue(p, "conflict:confirm-before-onset", "逻辑冲突", "确诊日期 " + confirm + " 早于发病时间 " + happen,
						target("edit", null, null, "编辑档案 · 疾病资料")));
			}
			Map<String, Integer> perDay = new HashMap<String, Integer>();
			for (Map<String, Object> v : vs) {
				LocalDate d = date((String) v.get("visitDate"));
				if (d == null) {
					continue;
				}
				perDay.put(d.toString(), perDay.containsKey(d.toString()) ? perDay.get(d.toString()) + 1 : 1);
				if (d.isAfter(today)) {
					issues.add(issue(p, "conflict:visit-future:" + v.get("visitId"), "逻辑冲突", "随访日期 " + d + " 晚于今天",
							target("visit", v.get("visitId"), "0", d + " 随访 · 病史病情")));
				}
				if (happen != null && d.isBefore(happen)) {
					issues.add(issue(p, "conflict:visit-before-onset:" + v.get("visitId"), "逻辑冲突", "随访日期 " + d + " 早于发病时间 " + happen,
							target("visit", v.get("visitId"), "0", d + " 随访 · 病史病情")));
				}
			}
			for (Map.Entry<String, Integer> e : perDay.entrySet()) {
				if (e.getValue() > 1) {
					Object firstId = null;
					for (Map<String, Object> v : vs) {
						if (e.getKey().equals(v.get("visitDate"))) {
							firstId = v.get("visitId");
							break;
						}
					}
					issues.add(issue(p, "conflict:same-day:" + e.getKey(), "逻辑冲突", e.getKey() + " 同一天有 " + e.getValue() + " 条随访，疑似重复录入",
							target("patient", null, null, "患者详情 · 随访时间线")));
				}
			}
		}

		// 处理状态、统计
		Set<Long> openPatients = new HashSet<Long>();
		int open = 0;
		for (Map<String, Object> x : issues) {
			Map<String, Object> r = resolved.get(x.get("patientId") + "|" + x.get("key"));
			if (r != null) {
				x.put("state", "已处理");
				x.put("handledBy", r.get("doctorName") == null ? "医生 ID " + r.get("doctorId") : r.get("doctorName"));
				x.put("handledAt", r.get("time"));
				x.put("note", r.get("note"));
			} else {
				x.put("state", "待处理");
				open++;
				openPatients.add((Long) x.get("patientId"));
			}
		}
		int total = all.items.size(), ready = 0;
		for (PatientItemVo p : all.items) {
			ready += p.getVisitCount() > 0 && !"withdrawn".equals(p.getFollowStatus()) && !openPatients.contains(p.getPatientId()) ? 1 : 0;
		}
		Map<String, Object> out = new LinkedHashMap<String, Object>();
		out.put("asOf", today.toString());
		out.put("totalPatients", total);
		out.put("issueCount", issues.size());
		out.put("openCount", open);
		out.put("affectedPatients", openPatients.size());
		out.put("cleanRate", total == 0 ? null : BigDecimal.valueOf((total - openPatients.size()) * 100.0 / total).setScale(1, java.math.RoundingMode.HALF_UP));
		out.put("researchReady", ready);
		out.put("resolutionReady", tableReady);
		out.put("issues", issues);
		return out;
	}

	@Override
	@Transactional
	public void resolve(Long doctorId, Long patientId, String issueKey, String note) {
		if (doctorId == null || patientId == null || issueKey == null || issueKey.trim().isEmpty()) {
			throw new IllegalArgumentException("缺少医生ID、患者ID或问题编码");
		}
		note = note == null || note.trim().isEmpty() ? null : note.trim();
		if (note != null && note.length() > 200) {
			throw new IllegalArgumentException("处理说明不能超过 200 字");
		}
		Map<String, Object> hit = null;
		for (Object o : (List<?>) issues(doctorId).get("issues")) {
			@SuppressWarnings("unchecked")
			Map<String, Object> x = (Map<String, Object>) o;
			if (patientId.equals(x.get("patientId")) && issueKey.equals(x.get("key"))) {
				hit = x;
			}
		}
		if (hit == null) {
			throw new BizException("404", "这个问题已不存在（可能数据已改好），请刷新页面");
		}
		if ("已处理".equals(hit.get("state"))) {
			return;
		}
		Map<String, Object> m = new HashMap<String, Object>();
		m.put("patientId", patientId);
		m.put("issueKey", issueKey);
		m.put("doctorId", doctorId);
		m.put("note", note);
		try {
			mapper.insertResolution(m);
		} catch (DataAccessException e) {
			throw new BizException("500", "质控处理记录表还没建，请先执行 sql/20261012_qc_issue_resolution.sql");
		}
		String operator = writeMapper.getUserName(doctorId);
		auditLogService.record(patientId, null, "质控处理", null,
				"问题：" + hit.get("text") + "（" + hit.get("type") + "）；状态：待处理 → 已处理" + (note == null ? "" : "；说明：" + note),
				doctorId, operator == null || operator.trim().isEmpty() ? "医生 ID " + doctorId : operator.trim());
	}

	private static Map<String, Object> issue(PatientItemVo p, String key, String type, String text, Map<String, Object> target) {
		Map<String, Object> x = new LinkedHashMap<String, Object>();
		x.put("key", key);
		x.put("patientId", p.getPatientId());
		x.put("name", p.getName());
		x.put("sex", p.getSex());
		x.put("birthYear", p.getBirthYear());
		x.put("studyNo", p.getStudyNo());
		x.put("visitCount", p.getVisitCount());
		x.put("type", type);
		x.put("text", text);
		x.put("target", target);
		return x;
	}

	/** 定位：page = visit 访视详情（visitId、section 模块序号）/ edit 编辑档案 / patient 患者详情 / create 新增随访 */
	private static Map<String, Object> target(String page, Object visitId, String section, String where) {
		Map<String, Object> t = new LinkedHashMap<String, Object>();
		t.put("page", page);
		t.put("visitId", visitId);
		t.put("section", section);
		t.put("where", where);
		return t;
	}

	private static String dateText(Map<String, Object> v) {
		return v.get("visitDate") == null ? "日期未填的" : (String) v.get("visitDate");
	}

	private static String str(Object v) {
		return v == null ? null : String.valueOf(v);
	}

	/** 数值：去掉前面的 < > ≤ ≥ 和空格（如「<5」）；不是数字返回 null */
	private static BigDecimal number(String s) {
		if (s == null) {
			return null;
		}
		String t = s.trim().replaceFirst("^[<>≤≥＜＞]=?\\s*", "");
		try {
			return t.isEmpty() ? null : new BigDecimal(t);
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
