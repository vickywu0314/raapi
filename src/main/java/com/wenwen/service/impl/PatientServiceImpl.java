package com.wenwen.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.wenwen.mapper.PatientMapper;
import com.wenwen.service.AuditLogService;
import com.wenwen.service.PatientService;
import com.wenwen.util.BizException;
import com.wenwen.vo.AuditLogVo;
import com.wenwen.vo.ComorbidityVo;
import com.wenwen.vo.PatientDetailVo;
import com.wenwen.vo.PatientItemVo;
import com.wenwen.vo.PatientsListVo;
import com.wenwen.vo.VisitItemVo;

@Service
public class PatientServiceImpl implements PatientService {

	private static final int MAX_PAGE_SIZE = 200;

	/** 随访状态编码 → 中文 */
	private static final Map<String, String> FOLLOW_STATUS = new LinkedHashMap<String, String>();
	/** 「缺失」类质控规则编码 → 缺失项中文 */
	private static final Map<String, String> MISSING_ITEMS = new HashMap<String, String>();
	/** 婚史 patient_basic_info.marry 编码 → 中文（已与业务确认） */
	private static final Map<Integer, String> MARRY = new HashMap<Integer, String>();
	/** 其他病史病种编码 → 病名 */
	private static final Map<String, String> COMORBIDITY_NAMES = new HashMap<String, String>();
	static {
		FOLLOW_STATUS.put("active", "随访中");
		FOLLOW_STATUS.put("soon", "近期需随访");
		FOLLOW_STATUS.put("overdue", "随访逾期");
		FOLLOW_STATUS.put("pending_first", "待首次随访");
		FOLLOW_STATUS.put("withdrawn", "已脱落");

		MISSING_ITEMS.put("M_DAS28", "缺 DAS28 评分");
		MISSING_ITEMS.put("M_BASELINE_LAB", "缺基线检验");
		MISSING_ITEMS.put("M_COMORBIDITY", "缺合并疾病记录");
		MISSING_ITEMS.put("M_MEDICATION", "缺用药史");

		MARRY.put(0, "未婚");
		MARRY.put(1, "已婚");
		MARRY.put(2, "离异");
		MARRY.put(3, "丧偶");

		COMORBIDITY_NAMES.put("FM", "纤维肌痛");
		COMORBIDITY_NAMES.put("AS", "强直性脊柱炎");
		COMORBIDITY_NAMES.put("SS", "系统性硬化症");
		COMORBIDITY_NAMES.put("RA-ILD", "类风湿关节炎相关间质性肺病");
		COMORBIDITY_NAMES.put("RA-MS", "类风湿关节炎肌少症");
	}
	private static final List<String> COMPLETENESS = Arrays.asList("complete", "missing");

	@Autowired
	private PatientMapper patientMapper;

	@Autowired
	private AuditLogService auditLogService;

	@Override
	public PatientsListVo listPatients(Long doctorId, String keyword, String followStatus, String completeness, int page, int size) {
		followStatus = emptyToNull(followStatus);
		completeness = emptyToNull(completeness);
		if (doctorId == null) {
			throw new IllegalArgumentException("缺少医生ID");
		}
		if (followStatus != null && !FOLLOW_STATUS.containsKey(followStatus)) {
			throw new IllegalArgumentException("随访状态不正确：" + followStatus);
		}
		if (completeness != null && !COMPLETENESS.contains(completeness)) {
			throw new IllegalArgumentException("数据完整性不正确：" + completeness);
		}
		if (page < 1) {
			throw new IllegalArgumentException("页码从 1 开始");
		}
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw new IllegalArgumentException("每页条数应为 1~" + MAX_PAGE_SIZE);
		}

		Map<String, Object> map = new HashMap<String, Object>();
		map.put("doctorId", doctorId);
		map.put("keyword", escapeLike(emptyToNull(keyword == null ? null : keyword.trim())));
		map.put("followStatus", followStatus);
		map.put("completeness", completeness);
		map.put("offset", (page - 1) * size);
		map.put("size", size);

		PatientsListVo vo = new PatientsListVo();
		Map<String, Object> summary = patientMapper.countSummary(map);
		vo.setTotalPatients(toInt(summary.get("totalPatients")));
		vo.setIncompleteCount(toInt(summary.get("incompleteCount")));
		vo.setTotal(patientMapper.countPatients(map));
		vo.setPage(page);
		vo.setSize(size);

		List<Map<String, Object>> rows = vo.getTotal() == 0 ? Collections.<Map<String, Object>>emptyList() : patientMapper.listPatients(map);
		List<PatientItemVo> items = new ArrayList<PatientItemVo>();
		Map<Long, PatientItemVo> byId = new HashMap<Long, PatientItemVo>();
		int thisYear = Calendar.getInstance().get(Calendar.YEAR);
		for (Map<String, Object> row : rows) {
			PatientItemVo item = toItem(row, thisYear);
			items.add(item);
			byId.put(item.getPatientId(), item);
		}
		attachComorbidities(byId);
		vo.setItems(items);
		return vo;
	}

	@Override
	public PatientDetailVo getPatientDetail(Long doctorId, Long patientId) {
		Map<String, Object> map = patientParams(doctorId, patientId);
		Map<String, Object> basic = patientMapper.getPatientBasic(map);
		if (basic == null) {
			throw noAccess();
		}
		// 随访状态、待补全、年龄等与列表同一套计算
		map.put("offset", 0);
		map.put("size", 1);
		List<Map<String, Object>> rows = patientMapper.listPatients(map);
		if (rows.isEmpty()) {
			throw noAccess();
		}
		Map<String, Object> row = rows.get(0);
		PatientItemVo item = toItem(row, Calendar.getInstance().get(Calendar.YEAR));
		Map<Long, PatientItemVo> byId = new HashMap<Long, PatientItemVo>();
		byId.put(item.getPatientId(), item);
		attachComorbidities(byId);

		PatientDetailVo d = new PatientDetailVo();
		d.setPatientId(item.getPatientId());
		d.setStudyNo(item.getStudyNo());
		d.setName(item.getName());
		d.setGender(item.getGender());
		d.setSex(item.getSex());
		d.setBirthYear(item.getBirthYear());
		d.setAge(item.getAge());
		d.setFollowStatus(item.getFollowStatus());
		d.setFollowStatusLabel(item.getFollowStatusLabel());
		d.setFollowCycle(item.getFollowCycle());
		d.setNextDueDate(item.getNextDueDate());
		if (item.getNextDueDate() != null) {
			d.setNextDueDays((int) ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(item.getNextDueDate())));
		}
		d.setIncomplete(item.isIncomplete());
		d.setMissingItems(item.getMissingItems());
		d.setComorbidities(item.getComorbidities());
		d.setVisitCount(item.getVisitCount());
		if ("withdrawn".equals(item.getFollowStatus())) {
			d.setWithdrawReason(blankToNull((String) basic.get("withdrawReason")));
		}

		d.setMobile(blankToNull((String) basic.get("mobile")));
		String cardNo = blankToNull((String) basic.get("cardNo"));
		d.setHasCardNo(cardNo != null);
		d.setCardNoMasked(maskCardNo(cardNo));
		d.setNation(blankToNull((String) basic.get("nation")));
		d.setMarry(toInteger(basic.get("marry")));
		d.setMarryLabel(d.getMarry() == null ? null : MARRY.get(d.getMarry()));
		d.setCreateDate((String) basic.get("createDate"));
		String firstVisit = (String) row.get("firstVisitDate");
		d.setFollowStartDate(firstVisit != null ? firstVisit : d.getCreateDate());
		d.setConfirmDate((String) basic.get("confirmDate"));
		d.setHappenDate((String) basic.get("happenDate"));
		d.setHeight(blankToNull(str(basic.get("height"))));
		d.setWeight(blankToNull(str(basic.get("weight"))));
		d.setBmi(bmi(d.getHeight(), d.getWeight()));
		d.setSmoking(smokingText(basic));
		String gms = blankToNull((String) basic.get("gms"));
		Integer allergy = toInteger(basic.get("allergy"));
		d.setAllergy(gms != null ? gms : (allergy != null && allergy == 0 ? "无" : null));
		d.setFamilyHistory(blankToNull((String) basic.get("jzs")));
		d.setPastHistory(blankToNull((String) basic.get("jws")));

		// 随访时间线：最近的在前；时间最早的一次为基线访视
		List<VisitItemVo> visits = new ArrayList<VisitItemVo>();
		VisitItemVo baseline = null;
		for (Map<String, Object> v : patientMapper.listVisits(map)) {
			VisitItemVo vi = new VisitItemVo();
			vi.setVisitId(toLong(v.get("visitId")));
			vi.setVisitDate((String) v.get("visitDate"));
			vi.setDoctorId(toLong(v.get("doctorId")));
			vi.setDoctorName(blankToNull((String) v.get("doctorName")));
			visits.add(vi);
			if (baseline == null || isEarlier(vi, baseline)) {
				baseline = vi;
			}
		}
		for (VisitItemVo vi : visits) {
			vi.setBaseline(vi == baseline);
			vi.setVisitType(vi == baseline ? "基线访视" : "常规随访");
		}
		d.setVisits(visits);
		return d;
	}

	@Override
	public String getCardNo(Long doctorId, Long patientId) {
		Map<String, Object> map = patientParams(doctorId, patientId);
		checkAccess(map);
		return blankToNull(patientMapper.getCardNo(map));
	}

	@Override
	public List<AuditLogVo> listAuditLogs(Long doctorId, Long patientId) {
		checkAccess(patientParams(doctorId, patientId));
		return auditLogService.listByPatient(patientId);
	}

	private Map<String, Object> patientParams(Long doctorId, Long patientId) {
		if (doctorId == null || patientId == null) {
			throw new IllegalArgumentException("缺少医生ID或患者ID");
		}
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("doctorId", doctorId);
		map.put("patientId", patientId);
		return map;
	}

	/** 只能看自己名下的患者 */
	private void checkAccess(Map<String, Object> map) {
		if (patientMapper.countDoctorPatient(map) == 0) {
			throw noAccess();
		}
	}

	private static BizException noAccess() {
		return new BizException("403", "患者不存在，或不在您名下");
	}

	private void attachComorbidities(Map<Long, PatientItemVo> byId) {
		if (byId.isEmpty()) {
			return;
		}
		for (Map<String, Object> c : patientMapper.listComorbidities(new ArrayList<Long>(byId.keySet()))) {
			PatientItemVo item = byId.get(toLong(c.get("patientId")));
			if (item != null) {
				item.getComorbidities().add(toComorbidity(c));
			}
		}
	}

	/** 日期早的在前；没有日期的视为最晚，同日期按 ID */
	private static boolean isEarlier(VisitItemVo a, VisitItemVo b) {
		if (a.getVisitDate() == null || b.getVisitDate() == null) {
			return a.getVisitDate() != null || (b.getVisitDate() == null && a.getVisitId() < b.getVisitId());
		}
		int c = a.getVisitDate().compareTo(b.getVisitDate());
		return c < 0 || (c == 0 && a.getVisitId() < b.getVisitId());
	}

	/** 身份证号后 4 位打码 */
	private static String maskCardNo(String cardNo) {
		if (cardNo == null) {
			return null;
		}
		return cardNo.length() <= 4 ? "****" : cardNo.substring(0, cardNo.length() - 4) + "****";
	}

	/** BMI = 体重 kg ÷ (身高 m)²，1 位小数；身高体重不是数字时为 null */
	private static BigDecimal bmi(String height, String weight) {
		try {
			double h = Double.parseDouble(height) / 100, w = Double.parseDouble(weight);
			if (h <= 0 || w <= 0) {
				return null;
			}
			return new BigDecimal(w / (h * h)).setScale(1, RoundingMode.HALF_UP);
		} catch (Exception e) {
			return null;
		}
	}

	/** 吸烟史：smoke=0 不吸烟（已与业务确认）；其它值视为吸烟，附年数、每日支数 */
	private static String smokingText(Map<String, Object> basic) {
		Integer smoke = toInteger(basic.get("smoke"));
		if (smoke == null) {
			return null;
		}
		if (smoke == 0) {
			return "不吸烟";
		}
		StringBuilder sb = new StringBuilder("吸烟");
		Integer years = toInteger(basic.get("smokeYears"));
		Integer perDay = toInteger(basic.get("smokeCountByDay"));
		if (years != null && years > 0) {
			sb.append(" ").append(years).append(" 年");
		}
		if (perDay != null && perDay > 0) {
			sb.append(" · 每日 ").append(perDay).append(" 支");
		}
		return sb.toString();
	}

	/** 空串、空 JSON（{} / [] / null）都视为未填写 */
	private static String blankToNull(String s) {
		if (s == null) {
			return null;
		}
		String t = s.trim();
		return t.isEmpty() || "{}".equals(t) || "[]".equals(t) || "null".equalsIgnoreCase(t) ? null : t;
	}

	private static String str(Object v) {
		return v == null ? null : String.valueOf(v);
	}

	private PatientItemVo toItem(Map<String, Object> row, int thisYear) {
		PatientItemVo item = new PatientItemVo();
		item.setPatientId(toLong(row.get("patientId")));
		item.setStudyNo((String) row.get("studyNo"));
		item.setName((String) row.get("name"));
		Integer gender = toInteger(row.get("gender"));
		item.setGender(gender);
		item.setSex(gender == null ? null : gender == 1 ? "男" : gender == 2 ? "女" : null);
		// 表里没有出生日期：出生年份 = 建档年份 − 建档时年龄；当前年龄 = 今年 − 出生年份
		Integer age = toInteger(row.get("age"));
		if (age != null) {
			Integer createYear = toInteger(row.get("createYear"));
			int birthYear = (createYear == null ? thisYear : createYear) - age;
			item.setBirthYear(birthYear);
			item.setAge(thisYear - birthYear);
		}
		item.setVisitCount(toInt(row.get("visitCount")));
		item.setFollowCycle(toInt(row.get("followCycle")));
		item.setLastVisitDate((String) row.get("lastVisitDate"));
		item.setNextDueDate((String) row.get("nextDueDate"));
		String status = (String) row.get("followStatus");
		item.setFollowStatus(status);
		item.setFollowStatusLabel(FOLLOW_STATUS.get(status));
		List<String> missing = new ArrayList<String>();
		String codes = (String) row.get("missingCodes");
		if (codes != null && !codes.isEmpty()) {
			for (String code : codes.split(",")) {
				missing.add(MISSING_ITEMS.containsKey(code) ? MISSING_ITEMS.get(code) : code);
			}
		}
		item.setMissingItems(missing);
		item.setIncomplete(!missing.isEmpty());
		item.setComorbidities(new ArrayList<ComorbidityVo>());
		return item;
	}

	private ComorbidityVo toComorbidity(Map<String, Object> row) {
		ComorbidityVo c = new ComorbidityVo();
		String code = (String) row.get("code");
		c.setCode(code);
		c.setName(COMORBIDITY_NAMES.containsKey(code) ? COMORBIDITY_NAMES.get(code) : code);
		c.setSinceYear(toInteger(row.get("sinceYear")));
		c.setLinkedStudyReady(false);
		return c;
	}

	private static String emptyToNull(String s) {
		return s == null || s.isEmpty() ? null : s;
	}

	/** LIKE 模糊查询时把 \ % _ 当普通字符 */
	private static String escapeLike(String s) {
		return s == null ? null : s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

	private static int toInt(Object value) {
		return value == null ? 0 : ((Number) value).intValue();
	}

	private static Integer toInteger(Object value) {
		return value == null ? null : ((Number) value).intValue();
	}

	private static Long toLong(Object value) {
		return value == null ? null : ((Number) value).longValue();
	}
}
