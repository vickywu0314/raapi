package com.wenwen.service.impl;

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
import com.wenwen.service.PatientService;
import com.wenwen.vo.ComorbidityVo;
import com.wenwen.vo.PatientItemVo;
import com.wenwen.vo.PatientsListVo;

@Service
public class PatientServiceImpl implements PatientService {

	private static final int MAX_PAGE_SIZE = 200;

	/** 随访状态编码 → 中文 */
	private static final Map<String, String> FOLLOW_STATUS = new LinkedHashMap<String, String>();
	/** 「缺失」类质控规则编码 → 缺失项中文 */
	private static final Map<String, String> MISSING_ITEMS = new HashMap<String, String>();
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

		COMORBIDITY_NAMES.put("FM", "纤维肌痛");
		COMORBIDITY_NAMES.put("AS", "强直性脊柱炎");
		COMORBIDITY_NAMES.put("SS", "系统性硬化症");
		COMORBIDITY_NAMES.put("RA-ILD", "类风湿关节炎相关间质性肺病");
		COMORBIDITY_NAMES.put("RA-MS", "类风湿关节炎肌少症");
	}
	private static final List<String> COMPLETENESS = Arrays.asList("complete", "missing");

	@Autowired
	private PatientMapper patientMapper;

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
		if (!byId.isEmpty()) {
			for (Map<String, Object> c : patientMapper.listComorbidities(new ArrayList<Long>(byId.keySet()))) {
				PatientItemVo item = byId.get(toLong(c.get("patientId")));
				if (item != null) {
					item.getComorbidities().add(toComorbidity(c));
				}
			}
		}
		vo.setItems(items);
		return vo;
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
