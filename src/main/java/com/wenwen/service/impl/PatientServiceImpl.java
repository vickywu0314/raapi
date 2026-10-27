package com.wenwen.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.wenwen.mapper.PatientMapper;
import com.wenwen.service.AuditLogService;
import com.wenwen.service.PatientService;
import com.wenwen.util.BizException;
import com.wenwen.util.Das28Util;
import com.wenwen.util.IdCardUtil;
import com.wenwen.util.SerologyUtil;
import com.wenwen.vo.AntibodyVo;
import com.wenwen.vo.AuditLogVo;
import com.wenwen.vo.ComorbidityVo;
import com.wenwen.vo.PatientDetailVo;
import com.wenwen.vo.PatientItemVo;
import com.wenwen.vo.PatientListFilter;
import com.wenwen.vo.PatientsListVo;
import com.wenwen.vo.VisitItemVo;

@Service
public class PatientServiceImpl implements PatientService {

	@Autowired
    private java.time.Clock clock=java.time.Clock.systemUTC();
    private LocalDate asOf() { return clock.instant().atZone(java.time.ZoneId.of("Asia/Shanghai")).toLocalDate(); }

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
    private com.wenwen.ai.qc.QcSnapshotReader qcReader;

	@Autowired
	private AuditLogService auditLogService;

	/** 疾病分型判定用的参考范围上限（老数据没存各化验室的参考范围，统一用配置值） */
	@Value("${ra.serology.rf-uln:20}")
	private double rfUln;
	@Value("${ra.serology.ccp-uln:25}")
	private double ccpUln;

	@Override
	public PatientsListVo listPatients(Long doctorId, String keyword, String followStatus, String completeness, int page, int size) {
		return listPatients(doctorId, keyword, followStatus, completeness, null, page, size);
	}

	@Override
	public PatientsListVo listPatients(Long doctorId, String keyword, String followStatus, String completeness, PatientListFilter filter, int page, int size) {
		if (page < 1) {
			throw new IllegalArgumentException("页码从 1 开始");
		}
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw new IllegalArgumentException("每页条数应为 1~" + MAX_PAGE_SIZE);
		}
        LocalDate asOf=asOf();
        ListFilter f = ListFilter.of(filter);
        Map<String, Object> map = listParams(doctorId, keyword, followStatus, completeness,asOf);
        f.sqlParams(map, asOf);
        com.wenwen.ai.qc.QcSnapshot snapshot=qcReader.readPatients(map);
        Map<Long, Das28Series> das = das28Series(snapshot, asOf);
        List<Map<String,Object>> filtered=applyFilters(filtered(snapshot,completeness), f, das, asOf);
        PatientsListVo vo=new PatientsListVo();
        vo.setTotalPatients(snapshot.getPatientIds().size()); vo.setIncompleteCount(snapshot.incompleteCount());
        vo.setTotal(filtered.size()); vo.setPage(page);vo.setSize(size);
        long offset=((long)page-1)*size;
        List<Map<String,Object>> rows=offset>=filtered.size()?Collections.emptyList():filtered.subList((int)offset,(int)Math.min(offset+size,filtered.size()));
		List<PatientItemVo> items = new ArrayList<PatientItemVo>();
		Map<Long, PatientItemVo> byId = new HashMap<Long, PatientItemVo>();
		for (Map<String, Object> row : rows) {
			PatientItemVo item = toItem(row, asOf, snapshot.getQc().get(toLong(row.get("patientId"))));
			items.add(item);
			byId.put(item.getPatientId(), item);
		}
		attachComorbidities(byId,asOf);
		attachDas28(byId,das);
		attachSerology(byId,asOf);
		vo.setItems(items);
		return vo;
	}

	/**
	 * 扩展筛选（年龄 / 活动度 / 病情变化 / 病史 / 分型）与按 DAS28 排序：在完整候选上做，之后才计数、分页。
	 * 与列表展示同一套计算：年龄同 toItem，DAS28 同 attachDas28，病史同 attachComorbidities，分型同 attachSerology。
	 */
	private List<Map<String, Object>> applyFilters(List<Map<String, Object>> rows, ListFilter f, Map<Long, Das28Series> das, LocalDate asOf) {
		if (!f.needsJava()) {
			return rows;
		}
		List<Long> ids = new ArrayList<Long>();
		for (Map<String, Object> row : rows) {
			ids.add(toLong(row.get("patientId")));
		}
		Map<Long, java.util.Set<String>> codes = f.comorbidity == null ? null : comorbidityCodes(ids);
		Map<Long, String> serology = f.serology == null ? null : serology(ids, asOf).states;
		List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
		for (Map<String, Object> row : rows) {
			Long id = toLong(row.get("patientId"));
			if (f.ageMin != null || f.ageMax != null) {
				Integer age = com.wenwen.ai.clinical.ClinicalPolicy.age((String) row.get("cardNo"), asOf);
				if (age == null || (f.ageMin != null && age < f.ageMin) || (f.ageMax != null && age > f.ageMax)) {
					continue;
				}
			}
			Das28Series series = das.get(id);
			if (f.activity != null) {
				String activity = series == null ? null : Das28Util.activity(series.latest());
				if (!f.activity.equals(activity == null ? "none" : activity)) {
					continue;
				}
			}
			if (f.trend != null) {
				String trend = series == null ? null : series.trend();
				if (!f.trend.equals(trend == null ? "none" : trend)) {
					continue;
				}
			}
			if (codes != null) {
				java.util.Set<String> mine = codes.get(id);
				boolean hit = "any".equals(f.comorbidity) ? mine != null : "none".equals(f.comorbidity) ? mine == null : mine != null && mine.contains(f.comorbidity);
				if (!hit) {
					continue;
				}
			}
			if (serology != null) {
				String state = serology.get(id);
				String key = "TRUE".equals(state) ? "pos" : "FALSE".equals(state) ? "neg" : "unknown";
				if (!f.serology.equals(key)) {
					continue;
				}
			}
			out.add(row);
		}
		if (f.dasSort != 0) {
			final int direction = f.dasSort;
			// 稳定排序：同分（及都无评分）保持 SQL 的默认顺序；无有效评分排最后
			Collections.sort(out, (a, b) -> {
				Das28Series sa = das.get(toLong(a.get("patientId"))), sb = das.get(toLong(b.get("patientId")));
				BigDecimal x = sa == null ? null : sa.latest(), y = sb == null ? null : sb.latest();
				if (x == null || y == null) {
					return x == null ? (y == null ? 0 : 1) : -1;
				}
				return direction * x.compareTo(y);
			});
		}
		return out;
	}

	private Map<Long, java.util.Set<String>> comorbidityCodes(List<Long> ids) {
		Map<Long, java.util.Set<String>> codes = new HashMap<Long, java.util.Set<String>>();
		if (ids.isEmpty()) {
			return codes;
		}
		for (Map<String, Object> c : patientMapper.listComorbidities(ids)) {
			codes.computeIfAbsent(toLong(c.get("patientId")), k -> new java.util.HashSet<String>()).add((String) c.get("code"));
		}
		return codes;
	}

	/** 扩展筛选参数（校验后）。sex / created / sort=last、code 下推 SQL，其余在 service 过滤 */
	private static final class ListFilter {
		Integer gender, ageMin, ageMax;
		String activity, comorbidity, serology, trend, created, sort;
		/** 1 = das-asc，-1 = das-desc，0 = 不按 DAS 排 */
		int dasSort;

		static ListFilter of(PatientListFilter in) {
			ListFilter f = new ListFilter();
			if (in == null) {
				return f;
			}
			String sort = blank(in.getSort());
			if (sort != null && !Arrays.asList("created", "last", "das-desc", "das-asc", "code").contains(sort)) {
				throw new IllegalArgumentException("排序方式不正确：" + sort);
			}
			f.sort = sort;
			f.dasSort = "das-desc".equals(sort) ? -1 : "das-asc".equals(sort) ? 1 : 0;
			String sex = blank(in.getSex());
			if (sex != null) {
				if ("男".equals(sex) || "M".equalsIgnoreCase(sex) || "1".equals(sex)) {
					f.gender = 1;
				} else if ("女".equals(sex) || "F".equalsIgnoreCase(sex) || "2".equals(sex)) {
					f.gender = 2;
				} else {
					throw new IllegalArgumentException("性别不正确：" + sex);
				}
			}
			f.ageMin = in.getAgeMin();
			f.ageMax = in.getAgeMax();
			String band = blank(in.getAgeBand());
			if (band != null) {
				java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d{1,3})-(\\d{1,3})$").matcher(band);
				if (!m.matches()) {
					throw new IllegalArgumentException("年龄段不正确：" + band + "（格式如 40-49）");
				}
				int lo = Integer.parseInt(m.group(1)), hi = Integer.parseInt(m.group(2));
				f.ageMin = f.ageMin == null ? lo : Math.max(f.ageMin, lo);
				f.ageMax = f.ageMax == null ? hi : Math.min(f.ageMax, hi);
			}
			if ((f.ageMin != null && (f.ageMin < 0 || f.ageMin > 150)) || (f.ageMax != null && (f.ageMax < 0 || f.ageMax > 150))) {
				throw new IllegalArgumentException("年龄应在 0~150 之间");
			}
			if (in.getAgeMin() != null && in.getAgeMax() != null && in.getAgeMin() > in.getAgeMax()) {
				throw new IllegalArgumentException("年龄下限不能大于上限");
			}
			f.activity = oneOf(in.getActivity(), "活动度", Das28Util.REMISSION, Das28Util.LOW, Das28Util.MODERATE, Das28Util.HIGH, "none");
			f.trend = oneOf(in.getTrend(), "病情变化", Das28Util.BETTER, Das28Util.STABLE, Das28Util.WORSE, "none");
			f.serology = oneOf(in.getSerology(), "血清学分型", "pos", "neg", "unknown");
			f.created = oneOf(in.getCreated(), "建档时间", "30", "90", "365", "old");
			String c = blank(in.getComorbidity());
			if (c != null) {
				String code = c.toUpperCase();
				if ("ANY".equals(code) || "NONE".equals(code)) {
					f.comorbidity = code.toLowerCase();
				} else if (COMORBIDITY_NAMES.containsKey(code)) {
					f.comorbidity = code;
				} else {
					throw new IllegalArgumentException("其他病史不正确：" + c);
				}
			}
			return f;
		}

		boolean needsJava() {
			return ageMin != null || ageMax != null || activity != null || trend != null || comorbidity != null || serology != null || dasSort != 0;
		}

		void sqlParams(Map<String, Object> map, LocalDate asOf) {
			map.put("gender", gender);
			map.put("sort", sort);
			if ("old".equals(created)) {
				map.put("createdBefore", asOf.minusDays(365).toString());
			} else if (created != null) {
				map.put("createdFrom", asOf.minusDays(Integer.parseInt(created)).toString());
			}
		}

		private static String oneOf(String value, String name, String... allowed) {
			value = blank(value);
			if (value != null && !Arrays.asList(allowed).contains(value)) {
				throw new IllegalArgumentException(name + "不正确：" + value);
			}
			return value;
		}

		private static String blank(String s) {
			return s == null || s.trim().isEmpty() ? null : s.trim();
		}
	}

	/** 某患者截至 asOf、有日期的有效 DAS28-CRP，按 日期 / 随访 ID 升序（最后一个 = VisitMatcher.now 选出的最近一次） */
	static final class Das28Series {
		final List<com.wenwen.ai.clinical.VisitMatcher.Visit> visits = new ArrayList<com.wenwen.ai.clinical.VisitMatcher.Visit>();

		BigDecimal latest() {
			return visits.isEmpty() ? null : visits.get(visits.size() - 1).getScore();
		}

		BigDecimal previous() {
			return visits.size() < 2 ? null : visits.get(visits.size() - 2).getScore();
		}

		BigDecimal first() {
			return visits.size() < 2 ? null : visits.get(0).getScore();
		}

		String trend() {
			return Das28Util.trend(first(), visits.size() < 2 ? null : latest());
		}
	}

	/** 快照里的 DAS28 按患者分组；口径同 VisitMatcher.now（有效分值、有日期、不晚于 asOf，同日取大 ID） */
	static Map<Long, Das28Series> das28Series(com.wenwen.ai.qc.QcSnapshot snapshot, LocalDate asOf) {
		Map<Long, Das28Series> out = new HashMap<Long, Das28Series>();
		for (com.wenwen.ai.clinical.VisitMatcher.Visit v : snapshot.getVisits()) {
			if (v.getScore() == null || v.getObservedAt() == null || v.getObservedAt().isAfter(asOf)) {
				continue;
			}
			out.computeIfAbsent(v.getPatientId(), k -> new Das28Series()).visits.add(v);
		}
		java.util.Comparator<com.wenwen.ai.clinical.VisitMatcher.Visit> order = java.util.Comparator
				.comparing(com.wenwen.ai.clinical.VisitMatcher.Visit::getObservedAt)
				.thenComparingLong(com.wenwen.ai.clinical.VisitMatcher.Visit::getId);
		for (Das28Series s : out.values()) {
			s.visits.sort(order);
		}
		return out;
	}

	@Override
	public AllPatients listAllPatients(Long doctorId) {
		LocalDate asOf = asOf();
		Map<String, Object> map = listParams(doctorId, null, null, null, asOf);
		com.wenwen.ai.qc.QcSnapshot snapshot = qcReader.readPatients(map);
		Map<Long, Das28Series> das = das28Series(snapshot, asOf);
		List<PatientItemVo> items = new ArrayList<PatientItemVo>();
		Map<Long, PatientItemVo> byId = new HashMap<Long, PatientItemVo>();
		for (Map<String, Object> row : snapshot.getRows()) {
			PatientItemVo item = toItem(row, asOf, snapshot.getQc().get(toLong(row.get("patientId"))));
			items.add(item);
			byId.put(item.getPatientId(), item);
		}
		attachComorbidities(byId, asOf);
		attachDas28(byId, das);
		attachSerology(byId, asOf);
		// 上一次有效 DAS28：截至今天、按日期 / ID 排序的倒数第二次（与「最近一次」同一 Matcher 规则选出最近一次后，再取它之前的）
		Map<Long, java.math.BigDecimal> previous = new HashMap<Long, java.math.BigDecimal>();
		for (Map.Entry<Long, Das28Series> e : das.entrySet()) {
			if (e.getValue().previous() != null) previous.put(e.getKey(), e.getValue().previous());
		}
		return new AllPatients(items, previous);
	}

	@Override
	public List<Long> listPatientIds(Long doctorId, String keyword, String followStatus, String completeness) {
        LocalDate asOf=asOf();
        Map<String, Object> map = listParams(doctorId, keyword, followStatus, completeness,asOf);
        com.wenwen.ai.qc.QcSnapshot snapshot=qcReader.readPatients(map);
        List<Long> ids=new ArrayList<>();
        for(Map<String,Object> row:filtered(snapshot,completeness)) ids.add(toLong(row.get("patientId")));
		return ids;
	}

    private static List<Map<String,Object>> filtered(com.wenwen.ai.qc.QcSnapshot snapshot,String completeness) {
        List<Map<String,Object>> rows=new ArrayList<>();
        for(Map<String,Object> row:snapshot.getRows()) if(snapshot.getQc().get(toLong(row.get("patientId"))).matches(emptyToNull(completeness))) rows.add(row);
        return rows;
    }

	/** 患者列表的筛选参数（校验后），不含分页 */
	private Map<String, Object> listParams(Long doctorId, String keyword, String followStatus, String completeness,LocalDate asOf) {
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
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("doctorId", doctorId);
		String literalKeyword=emptyToNull(keyword == null ? null : keyword.trim());
        String keywordEscape=likeEscape(literalKeyword);
        map.put("keyword", escapeLike(literalKeyword,keywordEscape));
        map.put("keywordEscape",keywordEscape);
        // 多个词用空白（含全角空格）分隔，每个词都要命中；转义符按整串选，保证不出现在任何一个词里
        if (literalKeyword != null) {
            List<String> words = new ArrayList<String>();
            for (String w : literalKeyword.split("[\\s\u3000]+")) if (!w.isEmpty()) words.add(escapeLike(w, keywordEscape));
            map.put("keywords", words.isEmpty() ? null : words);
        }
		map.put("followStatus", followStatus);
		map.put("completeness", completeness);
        map.put("asOf",asOf.toString());
		return map;
	}

	@Override
	public PatientDetailVo getPatientDetail(Long doctorId, Long patientId) {
        LocalDate asOf=asOf();
		Map<String, Object> map = patientParams(doctorId, patientId);
        map.put("asOf",asOf.toString());
		Map<String, Object> basic = patientMapper.getPatientBasic(map);
		if (basic == null) {
			throw noAccess();
		}
		// 随访状态、待补全、年龄等与列表同一套计算
        com.wenwen.ai.qc.QcSnapshot snapshot=qcReader.readPatients(map);
        List<Map<String,Object>> rows=snapshot.getRows();
		if (rows.isEmpty()) {
			throw noAccess();
		}
		Map<String, Object> row = rows.get(0);
		PatientItemVo item = toItem(row, asOf, snapshot.getQc().get(toLong(row.get("patientId"))));
		Map<Long, PatientItemVo> byId = new HashMap<Long, PatientItemVo>();
		byId.put(item.getPatientId(), item);
		attachComorbidities(byId,asOf);
		attachDas28(byId,das28Series(snapshot,asOf));
		attachSerology(byId,asOf);

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
			d.setNextDueDays((int) ChronoUnit.DAYS.between(asOf, LocalDate.parse(item.getNextDueDate())));
		}
		d.setIncomplete(item.isIncomplete());
		d.setMissingItems(item.getMissingItems());
		d.setSubtype(item.getSubtype());
		d.setRf(item.getRf());
		d.setCcp(item.getCcp());
		d.setComorbidities(item.getComorbidities());
        d.setFmState(item.getFmState()); d.setAsState(item.getAsState());
		d.setLatestDas28(item.getLatestDas28());
		d.setDas28Activity(item.getDas28Activity());
		d.setDas28ActivityLabel(item.getDas28ActivityLabel());
		d.setVisitCount(item.getVisitCount());
        d.setFollowUpCount(item.getFollowUpCount());
        d.setSuspectedLostToFollowUp(item.isSuspectedLostToFollowUp());
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
		// ACR/EULAR 2010 分类标准：老系统只存了总分（各部分选项 acr_eular_info 从未写过）；≥6 分可分类为 RA
		d.setAcrEularScore(toInteger(basic.get("acrEularScore")));
		if (d.getAcrEularScore() != null) {
			d.setAcrEularLabel(d.getAcrEularScore() >= 6 ? "符合 RA 分类" : "暂不符合 RA 分类");
		}
		// 诊断结果告知、知情同意书（新表 patient_ra_extra；表还没建时按未填写显示，不影响详情页）
		try {
			Map<String, Object> extra = patientMapper.getRaExtra(patientId);
			if (extra != null) {
				d.setDiagnosisInformed(toInteger(extra.get("diagnosisInformed")));
				d.setConsentSigned(toInteger(extra.get("consentSigned")));
			}
		} catch (org.springframework.dao.DataAccessException e) {
			// Only the deployed optional table's actual absence is compatible.
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) root = root.getCause();
            if (!(root instanceof java.sql.SQLException)
                    || ((java.sql.SQLException) root).getErrorCode() != 1146
                    || !"42S02".equals(((java.sql.SQLException) root).getSQLState())) throw e;
		}
		d.setWaistline(blankToNull(str(basic.get("waistline"))));
		d.setHeartRate(blankToNull(str(basic.get("xl"))));
		// 血压：xy_h 收缩压、xy_l 舒张压；xy 是老系统另存的一份收缩压（查数据与 xy_h 相同），xy_h 为空时用它兜底
		String systolic = blankToNull(str(basic.get("xyH"))), diastolic = blankToNull(str(basic.get("xyL")));
		if (systolic == null) {
			systolic = blankToNull(str(basic.get("xy")));
		}
		d.setSystolic(systolic);
		d.setDiastolic(diastolic);
		d.setSmoking(smokingText(basic));
		String gms = blankToNull((String) basic.get("gms"));
		Integer allergy = toInteger(basic.get("allergy"));
		d.setAllergy(gms != null ? gms : (allergy != null && allergy == 0 ? "无" : null));
		d.setFamilyHistory(blankToNull((String) basic.get("jzs")));
		d.setPastHistory(blankToNull((String) basic.get("jws")));

		// 随访时间线：最近的在前；时间最早的一次为基线访视
		List<VisitItemVo> visits = new ArrayList<VisitItemVo>();
		map.put("clinical", Boolean.TRUE);
		List<Map<String,Object>> visitRows=patientMapper.listVisits(map);
        Map<Long,Integer> numbers=com.wenwen.ai.patient.PatientFollowUpPolicy.numbers(visitRows);
		for (Map<String, Object> v : visitRows) {
			VisitItemVo vi = new VisitItemVo();
			vi.setVisitId(toLong(v.get("visitId")));
			vi.setVisitDate((String) v.get("visitDate"));
			vi.setDoctorId(toLong(v.get("doctorId")));
			vi.setDoctorName(blankToNull((String) v.get("doctorName")));
			visitClinical(vi, v);
			visits.add(vi);

		}
		for (VisitItemVo vi : visits) {
			int number=numbers.get(vi.getVisitId());
            vi.setFollowUpNumber(number);
            vi.setBaseline(number==0);
            vi.setVisitType(com.wenwen.ai.patient.PatientFollowUpPolicy.visitType(number));
		}
		d.setVisits(visits);
		return d;
	}

	/**
	 * 时间线每次随访的摘要（只读）：bqpg.result.crpScore / result.esrScore、zlfa.tz、bsbq.cause + extraCause（字段见 VisitFieldDict）。
	 * CRP 与列表 latestDas28 同一读取（ClinicalScalarReader + canonicalCrp）；ESR 同样非负、两位 HALF_UP。
	 */
	static void visitClinical(VisitItemVo vi, Map<String, Object> row) {
		vi.setDas28Crp(Das28Util.canonicalCrp(com.wenwen.ai.source.ClinicalScalarReader.read(str(row.get("bqpg")), new java.util.LinkedHashSet<String>()).get("/result/crpScore")));
		com.alibaba.fastjson.JSONObject bqpg = com.wenwen.util.VisitJson.parse(str(row.get("bqpg")));
		Object esr = bqpg == null ? null : com.wenwen.util.VisitJson.path(bqpg, "result.esrScore");
		vi.setDas28Esr(esr instanceof Number || esr instanceof String ? Das28Util.canonicalCrp(String.valueOf(esr)) : null);
		com.alibaba.fastjson.JSONObject zlfa = com.wenwen.util.VisitJson.parse(str(row.get("zlfa")));
		String tz = zlfa == null ? null : com.wenwen.util.VisitJson.text(zlfa.get("tz"));
		vi.setRegimenChanged("是".equals(tz) ? Boolean.TRUE : "否".equals(tz) ? Boolean.FALSE : null);
		com.alibaba.fastjson.JSONObject bsbq = com.wenwen.util.VisitJson.parse(str(row.get("bsbq")));
		if (bsbq != null && !bsbq.containsKey("record")) {
			Object cause = bsbq.get("cause");
			List<String> parts = new ArrayList<String>();
			for (Object o : cause instanceof java.util.Collection ? (java.util.Collection<?>) cause : Collections.singletonList(cause)) {
				String t = o == null || o instanceof java.util.Map || o instanceof java.util.Collection ? null : blankToNull(String.valueOf(o));
				if (t == null || "无".equals(t)) {
					continue;
				}
				String extra = "其他".equals(t) ? com.wenwen.util.VisitJson.text(bsbq.get("extraCause")) : null;
				parts.add(extra == null ? t : t + "：" + extra);
			}
			vi.setCause(parts.isEmpty() ? null : String.join("、", parts));
		}
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

	private void attachComorbidities(Map<Long, PatientItemVo> byId, LocalDate asOf) {
		if (byId.isEmpty()) {
			return;
		}
		for (Map<String, Object> c : patientMapper.listComorbidities(new ArrayList<Long>(byId.keySet()))) {
			PatientItemVo item = byId.get(toLong(c.get("patientId")));
			if (item != null) {
				item.getComorbidities().add(toComorbidity(c));
                if ("TRUE".equals(com.wenwen.ai.clinical.ClinicalPolicy.association(toInteger(c.get("sinceYear")),asOf))) {
                    if ("FM".equals(c.get("code"))) item.setFmState("TRUE");
                    if ("AS".equals(c.get("code"))) item.setAsState("TRUE");
                }
			}
		}
	}

    /** 当前DAS按共享Matcher口径（das28Series）：截至本次上海日期，最新有效LocalDate/最大id；另给上一次、首次和病情变化。 */
    private void attachDas28(Map<Long, PatientItemVo> byId, Map<Long, Das28Series> das) {
        for(PatientItemVo item:byId.values()) {
            Das28Series series=das.get(item.getPatientId());
            if(series==null) continue;
            item.setLatestDas28(series.latest());
            item.setDas28Activity(Das28Util.activity(series.latest()));
            item.setDas28ActivityLabel(Das28Util.label(item.getDas28Activity()));
            item.setDas28Prev(series.previous());
            item.setDas28First(series.first());
            item.setTrend(series.trend());
        }
    }

	/** 分型 / RF / CCP 的计算结果 */
	private static final class Serology {
		final Map<Long, String> states = new HashMap<Long, String>();
		final Map<Long, AntibodyVo> rfs = new HashMap<Long, AntibodyVo>();
		final Map<Long, AntibodyVo> ccps = new HashMap<Long, AntibodyVo>();
	}

	/** 分型取截至 asOf 曾经阳性（states：TRUE 血清阳性 / FALSE 血清阴性 / 无 = 未提供）；rf/ccp 分别取最新可分类的有日期结果。 */
	private Serology serology(List<Long> ids, LocalDate asOf) {
		Serology out = new Serology();
		if (ids.isEmpty()) {
			return out;
		}
		for (Map<String, Object> row : patientMapper.listSerology(ids)) {
			Long patientId = toLong(row.get("patientId"));
			String visitDate = (String) row.get("visitDate");
            if (!com.wenwen.ai.clinical.ClinicalPolicy.datedAt(visitDate,asOf)) continue;
            Map<String,String> scalars=com.wenwen.ai.source.ClinicalScalarReader.read(str(row.get("fzjc")),new java.util.LinkedHashSet<>());
            String rf=scalars.get("/lfsyz"), ccp=scalars.get("/kccpkt");
            String state=com.wenwen.ai.clinical.ClinicalPolicy.serology(out.states.get(patientId),SerologyUtil.classify(rf,rfUln));
            out.states.put(patientId,com.wenwen.ai.clinical.ClinicalPolicy.serology(state,SerologyUtil.classify(ccp,ccpUln)));
			pickAntibody(out.rfs, patientId, rf, rfUln, visitDate);
			pickAntibody(out.ccps, patientId, ccp, ccpUln, visitDate);
		}
		return out;
	}

	private void attachSerology(Map<Long, PatientItemVo> byId, LocalDate asOf) {
		if (byId.isEmpty()) {
			return;
		}
		Serology s = serology(new ArrayList<Long>(byId.keySet()), asOf);
		for (PatientItemVo item : byId.values()) {
			AntibodyVo rf = s.rfs.containsKey(item.getPatientId()) ? s.rfs.get(item.getPatientId()) : untested(rfUln);
			AntibodyVo ccp = s.ccps.containsKey(item.getPatientId()) ? s.ccps.get(item.getPatientId()) : untested(ccpUln);
			item.setRf(rf);
			item.setCcp(ccp);
			String state=s.states.get(item.getPatientId());
            item.setSubtype("TRUE".equals(state)?"血清阳性":"FALSE".equals(state)?"血清阴性":null);
		}
	}

	/** 行按随访从近到远排，每个患者只留第一个能判定的结果 */
	private static void pickAntibody(Map<Long, AntibodyVo> picked, Long patientId, String raw, double uln, String visitDate) {
		if (picked.containsKey(patientId)) {
			return;
		}
		String status = SerologyUtil.classify(raw, uln);
		if (status == null) {
			return;
		}
		AntibodyVo a = new AntibodyVo();
		a.setStatus(status);
		a.setStatusLabel(SerologyUtil.label(status));
		a.setValue(raw.trim());
		a.setUln(uln);
		a.setVisitDate(visitDate);
		picked.put(patientId, a);
	}

	private static AntibodyVo untested(double uln) {
		AntibodyVo a = new AntibodyVo();
		a.setStatus(SerologyUtil.UNTESTED);
		a.setStatusLabel(SerologyUtil.label(SerologyUtil.UNTESTED));
		a.setUln(uln);
		return a;
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

	/** 吸烟史：smoke=0 不吸烟；其它值视为吸烟，附年数、每日支数；smoke_stop=1 已戒烟（均已与业务确认） */
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
		Integer stop = toInteger(basic.get("smokeStop"));
		if (stop != null && stop == 1) {
			sb.append(" · 已戒烟");
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

	private PatientItemVo toItem(Map<String, Object> row, LocalDate asOf,com.wenwen.ai.qc.MissingDataStatus qc) {
		PatientItemVo item = new PatientItemVo();
		item.setPatientId(toLong(row.get("patientId")));
		item.setStudyNo((String) row.get("studyNo"));
		item.setName((String) row.get("name"));
		Integer gender = toInteger(row.get("gender"));
		item.setGender(gender);
		item.setSex(gender == null ? null : gender == 1 ? "男" : gender == 2 ? "女" : null);
        LocalDate birth = IdCardUtil.birthDate((String)row.get("cardNo"),asOf);
        item.setBirthYear(birth==null?null:birth.getYear());
        item.setAge(com.wenwen.ai.clinical.ClinicalPolicy.age((String)row.get("cardNo"),asOf));
		item.setVisitCount(toInt(row.get("visitCount")));
        item.setFollowUpCount(Math.max(item.getVisitCount()-1,0));
		item.setFollowCycle(toInt(row.get("followCycle")));
		item.setLastVisitDate((String) row.get("lastVisitDate"));
		item.setCreateDate((String) row.get("createDate"));
		item.setNextDueDate((String) row.get("nextDueDate"));
		String status = (String) row.get("followStatus");
		item.setFollowStatus(status);
        item.setSuspectedLostToFollowUp(com.wenwen.ai.patient.PatientFollowUpPolicy.suspectedLost(status,item.getVisitCount(),item.getLastVisitDate(),(String)row.get("createDate"),asOf));
		item.setFollowStatusLabel(FOLLOW_STATUS.get(status));
		List<String> missing = new ArrayList<String>();
        for(String code:qc.getMissingCodes()) missing.add(MISSING_ITEMS.get(code));
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

    // 固定安全白名单：ASCII可打印字符（排除LIKE通配符）和BMP私用区；不选控制符/代理/补充平面。
    private static final int[] LIKE_ESCAPE_CANDIDATES=java.util.stream.IntStream.concat(
        java.util.stream.IntStream.rangeClosed(33,126),java.util.stream.IntStream.rangeClosed(0xE000,0xF8FF))
        .filter(value->value!='%'&&value!='_').toArray();
    /** 选择原文中不存在的单字符，避免5.7 unicode_ci下转义符自身重复的不匹配。 */
    static String likeEscape(String keyword) {
        if(keyword==null)return "!";
        java.util.Set<Integer> present=new java.util.HashSet<>();keyword.codePoints().forEach(present::add);
        for(int candidate:LIKE_ESCAPE_CANDIDATES) {
            if(!present.contains(candidate))return new String(Character.toChars(candidate));
        }
        throw new IllegalArgumentException("搜索词包含全部可用转义字符");
    }
    /** 仅转义两个LIKE通配符；转义字符不在原文内，因此不需要自身翻倍。 */
    static String escapeLike(String s,String escape) {
        return s==null?null:s.replace("%",escape+"%").replace("_",escape+"_");
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
