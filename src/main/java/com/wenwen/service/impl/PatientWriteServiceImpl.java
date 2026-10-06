package com.wenwen.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wenwen.mapper.PatientWriteMapper;
import com.wenwen.service.AuditLogService;
import com.wenwen.service.PatientWriteService;
import com.wenwen.util.BizException;
import com.wenwen.util.IdCardUtil;
import com.wenwen.util.StudyNoUtil;
import com.wenwen.vo.AcrEularForm;
import com.wenwen.vo.ComorbidityInput;
import com.wenwen.vo.FieldChange;
import com.wenwen.vo.PatientBasicForm;
import com.wenwen.vo.PatientCheckVo;
import com.wenwen.vo.PatientCreateRequest;
import com.wenwen.vo.PatientCreateResultVo;

@Service
public class PatientWriteServiceImpl implements PatientWriteService {

	public static final String NEW = "NEW";
	public static final String OTHER_DISEASE = "OTHER_DISEASE";
	public static final String RA_OTHER_DOCTOR = "RA_OTHER_DOCTOR";
	public static final String MINE = "MINE";

	/** 非 RA 研究类型 → 常见相关疾病编码 / 病种名（只收录已确认的；其余按「研究类型 N」显示，不记入其他病史） */
	private static final Map<Integer, String[]> OTHER_RESEARCH = new HashMap<Integer, String[]>();
	/** 复用已有患者档案时可更新的字段：列名 → 中文名（修改记录用） */
	private static final Map<String, String> LABELS = new LinkedHashMap<String, String>();
	private static final List<Integer> FOLLOW_CYCLES = Arrays.asList(3, 6, 12, 24);
	private static final List<Integer> JOINT_SCORES = Arrays.asList(0, 1, 2, 3, 5);
	private static final List<Integer> SEROLOGY_SCORES = Arrays.asList(0, 2, 3);
	private static final List<String> COMORBIDITY_CODES = Arrays.asList("FM", "AS", "SS", "RA-ILD", "RA-MS");
	static {
		OTHER_RESEARCH.put(6, new String[] { "AS", "强直性脊柱炎" });

		LABELS.put("name", "姓名");
		LABELS.put("card_no", "身份证号");
		LABELS.put("gender", "性别");
		LABELS.put("mobile", "手机号");
		LABELS.put("nation", "民族");
		LABELS.put("marry", "婚史");
		LABELS.put("confirm_date", "确诊日期");
		LABELS.put("happen_date", "发病时间");
		LABELS.put("height", "身高");
		LABELS.put("weight", "体重");
		LABELS.put("waistline", "腰围");
		LABELS.put("xl", "心率");
		LABELS.put("xy_h", "收缩压");
		LABELS.put("xy_l", "舒张压");
		LABELS.put("smoke", "吸烟史");
		LABELS.put("smoke_years", "吸烟年数");
		LABELS.put("smoke_count_by_day", "每天支数");
		LABELS.put("smoke_stop", "已戒烟");
		LABELS.put("gms", "过敏史");
		LABELS.put("jzs", "家族史");
		LABELS.put("jws", "既往史");
		LABELS.put("follow_cycle", "随访周期");
		LABELS.put("acr_eular_score", "ACR/EULAR 2010");
	}

	/** 新建患者写 patient_relation_doctor.research_type 的值；待业务确认，未配置时不允许新建 */
	@Value("${ra.patient.research-type:}")
	private String researchType;

	@Autowired
	private PatientWriteMapper mapper;

	@Autowired
	private AuditLogService auditLogService;

	@Override
	public PatientCheckVo checkCardNo(Long doctorId, String cardNo) {
		if (doctorId == null) {
			throw new IllegalArgumentException("缺少医生ID");
		}
		cardNo = trim(cardNo);
		if (cardNo == null) {
			PatientCheckVo vo = new PatientCheckVo();
			vo.setResult(NEW);
			return vo;
		}
		if (IdCardUtil.birthDate(cardNo) == null) {
			throw new IllegalArgumentException("身份证号格式不正确");
		}
		return check(doctorId, cardNo);
	}

	/** 按身份证号查重：本医生的 RA 关系 → MINE；其他医生的 RA 关系 → RA_OTHER_DOCTOR；只有其他病种 → OTHER_DISEASE */
	private PatientCheckVo check(Long doctorId, String cardNo) {
		PatientCheckVo vo = new PatientCheckVo();
		vo.setResult(NEW);
		List<Map<String, Object>> patients = mapper.findByCardNo(cardNo);
		if (patients.isEmpty()) {
			return vo;
		}
		Map<String, Object> raOther = null, raOtherPatient = null;
		for (Map<String, Object> p : patients) {
			for (Map<String, Object> r : mapper.listRelations(toLong(p.get("id")))) {
				if (toInt(r.get("isRa")) != 1) {
					continue;
				}
				if (doctorId.equals(toLong(r.get("doctorId")))) {
					vo.setResult(MINE);
					vo.setPatientId(toLong(p.get("id")));
					vo.setName((String) p.get("name"));
					return vo;
				}
				if (raOther == null) {
					raOther = r;
					raOtherPatient = p;
				}
			}
		}
		if (raOther != null) {
			vo.setResult(RA_OTHER_DOCTOR);
			vo.setPatientId(toLong(raOtherPatient.get("id")));
			vo.setName((String) raOtherPatient.get("name"));
			String doctorName = trim((String) raOther.get("doctorName"));
			vo.setOtherDoctorName(doctorName != null ? doctorName : "医生 ID " + raOther.get("doctorId"));
			return vo;
		}
		Map<String, Object> p = patients.get(0);
		Long patientId = toLong(p.get("id"));
		vo.setResult(OTHER_DISEASE);
		vo.setPatientId(patientId);
		vo.setName((String) p.get("name"));
		List<String> diseases = new ArrayList<String>();
		for (Map<String, Object> r : mapper.listRelations(patientId)) {
			String d = diseaseName(toInteger(r.get("researchType")));
			if (!diseases.contains(d)) {
				diseases.add(d);
			}
		}
		vo.setOtherDiseases(diseases);
		vo.setBasic(toForm(mapper.getBasicForm(patientId)));
		return vo;
	}

	@Override
	@Transactional
	public PatientCreateResultVo createPatient(PatientCreateRequest req) {
		if (req == null || req.getDoctorId() == null || req.getBasic() == null) {
			throw new IllegalArgumentException("缺少医生ID或基本信息");
		}
		Integer rt = researchType();
		Long doctorId = req.getDoctorId();
		PatientBasicForm f = req.getBasic();
		Map<String, Object> cols = validate(f);
		AcrEularForm acr = validateAcr(f.getAcrEular());
		if (acr != null) {
			cols.put("acr_eular_score", acr.getTotalScore());
		}
		String operator = trim(mapper.getUserName(doctorId));
		operator = operator != null ? operator : "医生 ID " + doctorId;

		String cardNo = (String) cols.get("card_no");
		PatientCheckVo existing = cardNo == null ? null : check(doctorId, cardNo);
		String result = existing == null ? NEW : existing.getResult();
		PatientCreateResultVo vo = new PatientCreateResultVo();

		if (MINE.equals(result)) {
			throw new BizException("409", "该患者已在您名下（" + existing.getName() + "，ID号 " + existing.getPatientId() + "）");
		}
		if (RA_OTHER_DOCTOR.equals(result)) {
			if (!req.isTransfer()) {
				throw new BizException("409", "该患者已在 " + existing.getOtherDoctorName() + " 名下，确认要转到自己名下吗？");
			}
			Long relationId = raRelationOf(existing.getPatientId(), doctorId);
			mapper.transferRelation(relationId, doctorId);
			auditLogService.record(existing.getPatientId(), null, "转入名下", null,
					"从 " + existing.getOtherDoctorName() + " 名下转入", doctorId, operator);
			vo.setPatientId(existing.getPatientId());
			vo.setStudyNo((String) mapper.getBasicForm(existing.getPatientId()).get("studyNo"));
			vo.setAction("transferred");
			return vo;
		}

		Long patientId;
		String studyNo;
		String note;
		List<FieldChange> changes = null;
		if (OTHER_DISEASE.equals(result)) {
			// 复用其他病种的患者档案：只更新医生填了、且与原值不同的字段（不清空原有信息）
			patientId = existing.getPatientId();
			Map<String, Object> before = mapper.getBasicForm(patientId);
			Map<String, Object> beforeCols = toCols(before);
			Map<String, Object> update = new LinkedHashMap<String, Object>();
			for (Map.Entry<String, Object> e : cols.entrySet()) {
				if (e.getValue() != null && !same(e.getValue(), beforeCols.get(e.getKey()))) {
					update.put(e.getKey(), e.getValue());
				}
			}
			if (update.containsKey("xy_h")) {
				update.put("xy", update.get("xy_h"));
			}
			studyNo = (String) before.get("studyNo");
			if (studyNo == null) {
				studyNo = nextStudyNo();
				update.put("study_no", studyNo);
			}
			if (!update.isEmpty()) {
				mapper.updatePatientFields(patientId, update);
			}
			Map<String, Object> after = new HashMap<String, Object>(beforeCols);
			after.putAll(update);
			// 医患关系里的确诊 / 发病日期取更新后的患者档案
			cols.put("confirm_date", after.get("confirm_date"));
			cols.put("happen_date", after.get("happen_date"));
			changes = auditLogService.diff(readable(beforeCols), readable(after), LABELS);
			// 原来所在的其他病种记入「其他病史」
			for (Map<String, Object> r : mapper.listRelations(patientId)) {
				String[] d = OTHER_RESEARCH.get(toInteger(r.get("researchType")));
				if (d != null) {
					insertComorbidity(patientId, d[0], null, "auto", patientId);
				}
			}
			note = "复用已有患者档案（原在：" + String.join("、", existing.getOtherDiseases()) + "）";
			vo.setAction("linked");
		} else {
			// 新患者：研究编号当天序号冲突（并发）时重试
			cols.put("age", age(cardNo));
			Map<String, Object> ins = insertParams(cols);
			patientId = null;
			studyNo = null;
			for (int i = 0; i < 5 && patientId == null; i++) {
				studyNo = nextStudyNo();
				ins.put("studyNo", studyNo);
				try {
					mapper.insertPatient(ins);
					patientId = toLong(ins.get("id"));
				} catch (DuplicateKeyException e) {
					if (i == 4) {
						throw e;
					}
				}
			}
			note = "研究编号 " + studyNo;
			vo.setAction("created");
		}

		Map<String, Object> rel = new HashMap<String, Object>();
		rel.put("doctorId", doctorId);
		rel.put("patientId", patientId);
		rel.put("researchType", rt);
		rel.put("confirmDate", cols.get("confirm_date"));
		rel.put("happenDate", cols.get("happen_date"));
		mapper.insertRelation(rel);

		if (acr != null) {
			Map<String, Object> a = new HashMap<String, Object>();
			a.put("patientId", patientId);
			a.put("jointScore", acr.getJointScore());
			a.put("serologyScore", acr.getSerologyScore());
			a.put("durationScore", acr.getDurationScore());
			a.put("acuteScore", acr.getAcuteScore());
			a.put("totalScore", acr.getTotalScore());
			a.put("doctorId", doctorId);
			mapper.insertAcrEular(a);
			note += "；ACR/EULAR 2010 " + acr.getTotalScore() + " 分";
		}
		if (f.getComorbidities() != null) {
			for (ComorbidityInput c : f.getComorbidities()) {
				insertComorbidity(patientId, c.getCode(), c.getSinceYear(), "manual", null);
			}
		}
		auditLogService.record(patientId, null, "新建档案", changes, note, doctorId, operator);
		vo.setPatientId(patientId);
		vo.setStudyNo(studyNo);
		return vo;
	}

	private Integer researchType() {
		String v = trim(researchType);
		if (v == null) {
			throw new BizException("400", "新建患者的研究类型未配置（application.properties 的 ra.patient.research-type），请联系管理员");
		}
		try {
			return Integer.valueOf(v);
		} catch (NumberFormatException e) {
			throw new BizException("400", "ra.patient.research-type 配置不正确：" + v);
		}
	}

	/** 该患者在 RA 研究库、不在本医生名下的医患关系（转入用） */
	private Long raRelationOf(Long patientId, Long doctorId) {
		for (Map<String, Object> r : mapper.listRelations(patientId)) {
			if (toInt(r.get("isRa")) == 1 && !doctorId.equals(toLong(r.get("doctorId")))) {
				return toLong(r.get("id"));
			}
		}
		throw new BizException("409", "患者的医患关系已变化，请刷新后重试");
	}

	private void insertComorbidity(Long patientId, String code, Integer sinceYear, String source, Long linkedPatientId) {
		Map<String, Object> m = new HashMap<String, Object>();
		m.put("patientId", patientId);
		m.put("code", code);
		m.put("sinceYear", sinceYear);
		m.put("source", source);
		m.put("linkedPatientId", linkedPatientId);
		mapper.insertComorbidity(m);
	}

	/** 研究编号：RA-今天-当天序号 */
	private String nextStudyNo() {
		Date today = new Date();
		String prefix = StudyNoUtil.build("RA", today, 1);
		prefix = prefix.substring(0, prefix.lastIndexOf('-') + 1);
		return StudyNoUtil.build("RA", today, mapper.countStudyNoPrefix(prefix) + 1);
	}

	/** 校验表单，返回 患者表列名 → 值（空值为 null） */
	private Map<String, Object> validate(PatientBasicForm f) {
		Map<String, Object> c = new LinkedHashMap<String, Object>();
		String name = trim(f.getName());
		if (name == null) {
			throw new IllegalArgumentException("请填写患者姓名");
		}
		c.put("name", maxLen(name, 100, "姓名"));
		String cardNo = trim(f.getCardNo());
		if (cardNo != null && IdCardUtil.birthDate(cardNo) == null) {
			throw new IllegalArgumentException("身份证号格式不正确");
		}
		c.put("card_no", cardNo == null ? null : cardNo.toUpperCase());
		if (f.getGender() == null || (f.getGender() != 1 && f.getGender() != 2)) {
			throw new IllegalArgumentException("请选择性别");
		}
		c.put("gender", f.getGender());
		String mobile = trim(f.getMobile());
		if (mobile != null && !mobile.matches("1\\d{10}")) {
			throw new IllegalArgumentException("手机号应为 11 位数字");
		}
		c.put("mobile", mobile);
		c.put("nation", maxLen(trim(f.getNation()), 100, "民族"));
		if (f.getMarry() != null && (f.getMarry() < 0 || f.getMarry() > 3)) {
			throw new IllegalArgumentException("婚史不正确");
		}
		c.put("marry", f.getMarry());
		c.put("confirm_date", date(f.getConfirmDate(), "确诊日期"));
		c.put("happen_date", date(f.getHappenDate(), "发病时间"));
		c.put("height", number(f.getHeight(), "身高", 30, 250));
		c.put("weight", number(f.getWeight(), "体重", 2, 300));
		c.put("waistline", number(f.getWaistline(), "腰围", 20, 250));
		c.put("xl", number(f.getHeartRate(), "心率", 20, 250));
		c.put("xy_h", number(f.getSystolic(), "收缩压", 40, 300));
		c.put("xy_l", number(f.getDiastolic(), "舒张压", 20, 200));
		Integer smoke = f.getSmoke();
		if (smoke != null && smoke != 0 && smoke != 1) {
			throw new IllegalArgumentException("吸烟史不正确");
		}
		c.put("smoke", smoke);
		boolean smoking = smoke != null && smoke == 1;
		c.put("smoke_years", smoking ? range(f.getSmokeYears(), "吸烟年数", 0, 100) : null);
		c.put("smoke_count_by_day", smoking ? range(f.getSmokeCountByDay(), "每天支数", 0, 200) : null);
		c.put("smoke_stop", smoking ? (f.getSmokeStop() != null && f.getSmokeStop() == 1 ? 1 : 0) : null);
		c.put("gms", maxLen(trim(f.getAllergyHistory()), 255, "过敏史"));
		c.put("jzs", maxLen(trim(f.getFamilyHistory()), 255, "家族史"));
		c.put("jws", maxLen(trim(f.getPastHistory()), 255, "既往史"));
		Integer cycle = f.getFollowCycle() == null ? 12 : f.getFollowCycle();
		if (!FOLLOW_CYCLES.contains(cycle)) {
			throw new IllegalArgumentException("随访周期应为 3 / 6 / 12 / 24 个月");
		}
		c.put("follow_cycle", cycle);
		if (f.getComorbidities() != null) {
			for (ComorbidityInput ci : f.getComorbidities()) {
				if (ci == null || !COMORBIDITY_CODES.contains(ci.getCode())) {
					throw new IllegalArgumentException("常见相关疾病不正确：" + (ci == null ? null : ci.getCode()));
				}
			}
		}
		return c;
	}

	/** ACR/EULAR：4 部分都选了才算评估，总分后端重算；全没选视为未评估 */
	private static AcrEularForm validateAcr(AcrEularForm a) {
		if (a == null || (a.getJointScore() == null && a.getSerologyScore() == null && a.getDurationScore() == null && a.getAcuteScore() == null)) {
			return null;
		}
		if (a.getJointScore() == null || a.getSerologyScore() == null || a.getDurationScore() == null || a.getAcuteScore() == null) {
			throw new IllegalArgumentException("ACR/EULAR 2010 的 4 个部分需全部选择");
		}
		if (!JOINT_SCORES.contains(a.getJointScore()) || !SEROLOGY_SCORES.contains(a.getSerologyScore())
				|| a.getDurationScore() < 0 || a.getDurationScore() > 1 || a.getAcuteScore() < 0 || a.getAcuteScore() > 1) {
			throw new IllegalArgumentException("ACR/EULAR 2010 的选项不正确");
		}
		a.setTotalScore(a.getJointScore() + a.getSerologyScore() + a.getDurationScore() + a.getAcuteScore());
		return a;
	}

	private static Map<String, Object> insertParams(Map<String, Object> c) {
		Map<String, Object> m = new HashMap<String, Object>();
		m.put("name", c.get("name"));
		m.put("cardNo", c.get("card_no"));
		m.put("gender", c.get("gender"));
		m.put("age", c.get("age"));
		m.put("mobile", c.get("mobile"));
		m.put("nation", c.get("nation"));
		m.put("marry", c.get("marry"));
		m.put("confirmDate", c.get("confirm_date"));
		m.put("happenDate", c.get("happen_date"));
		m.put("height", c.get("height"));
		m.put("weight", c.get("weight"));
		m.put("waistline", c.get("waistline"));
		m.put("xl", c.get("xl"));
		m.put("xy_h", c.get("xy_h"));
		m.put("xy_l", c.get("xy_l"));
		m.put("smoke", c.get("smoke"));
		m.put("smokeYears", c.get("smoke_years"));
		m.put("smokeCountByDay", c.get("smoke_count_by_day"));
		m.put("smokeStop", c.get("smoke_stop"));
		m.put("gms", c.get("gms"));
		m.put("jzs", c.get("jzs"));
		m.put("jws", c.get("jws"));
		m.put("acrEularScore", c.get("acr_eular_score"));
		m.put("followCycle", c.get("follow_cycle"));
		return m;
	}

	/** getBasicForm 的结果 → 患者表列名（与 validate 的列名一致，用于比较） */
	private static Map<String, Object> toCols(Map<String, Object> b) {
		Map<String, Object> c = new LinkedHashMap<String, Object>();
		c.put("name", b.get("name"));
		c.put("card_no", b.get("cardNo"));
		c.put("gender", b.get("gender"));
		c.put("mobile", b.get("mobile"));
		c.put("nation", b.get("nation"));
		c.put("marry", b.get("marry"));
		c.put("confirm_date", b.get("confirmDate"));
		c.put("happen_date", b.get("happenDate"));
		c.put("height", b.get("height"));
		c.put("weight", b.get("weight"));
		c.put("waistline", b.get("waistline"));
		c.put("xl", b.get("heartRate"));
		c.put("xy_h", b.get("systolic"));
		c.put("xy_l", b.get("diastolic"));
		c.put("smoke", b.get("smoke"));
		c.put("smoke_years", b.get("smokeYears"));
		c.put("smoke_count_by_day", b.get("smokeCountByDay"));
		c.put("smoke_stop", b.get("smokeStop"));
		c.put("gms", b.get("allergyHistory"));
		c.put("jzs", b.get("familyHistory"));
		c.put("jws", b.get("pastHistory"));
		c.put("follow_cycle", b.get("followCycle"));
		c.put("acr_eular_score", null);
		return c;
	}

	/** getBasicForm 的结果 → 预填表单 */
	private static PatientBasicForm toForm(Map<String, Object> b) {
		PatientBasicForm f = new PatientBasicForm();
		if (b == null) {
			return f;
		}
		f.setName((String) b.get("name"));
		f.setCardNo((String) b.get("cardNo"));
		f.setGender(toInteger(b.get("gender")));
		f.setMobile((String) b.get("mobile"));
		f.setNation((String) b.get("nation"));
		f.setMarry(toInteger(b.get("marry")));
		f.setConfirmDate((String) b.get("confirmDate"));
		f.setHappenDate((String) b.get("happenDate"));
		f.setHeight(str(b.get("height")));
		f.setWeight(str(b.get("weight")));
		f.setWaistline(str(b.get("waistline")));
		f.setHeartRate(str(b.get("heartRate")));
		f.setSystolic(str(b.get("systolic")));
		f.setDiastolic(str(b.get("diastolic")));
		f.setSmoke(toInteger(b.get("smoke")) == null ? null : toInteger(b.get("smoke")) == 0 ? 0 : 1);
		f.setSmokeYears(toInteger(b.get("smokeYears")));
		f.setSmokeCountByDay(toInteger(b.get("smokeCountByDay")));
		f.setSmokeStop(toInteger(b.get("smokeStop")));
		f.setAllergyHistory((String) b.get("allergyHistory"));
		f.setFamilyHistory((String) b.get("familyHistory"));
		f.setPastHistory((String) b.get("pastHistory"));
		f.setFollowCycle(toInteger(b.get("followCycle")));
		return f;
	}

	/** 修改记录里编码字段显示中文 */
	private static Map<String, Object> readable(Map<String, Object> cols) {
		Map<String, Object> m = new HashMap<String, Object>(cols);
		m.put("gender", code(cols.get("gender"), "男", "女", 1));
		m.put("marry", code(cols.get("marry"), "未婚", "已婚", "离异", "丧偶"));
		m.put("smoke", code(cols.get("smoke"), "无", "有"));
		m.put("smoke_stop", code(cols.get("smoke_stop"), "否", "是"));
		return m;
	}

	/** 编码 → 文字：labels 依次对应 0、1、2…；末尾整数参数为起始编码（性别从 1 开始） */
	private static Object code(Object v, Object... labels) {
		if (v == null) {
			return null;
		}
		int start = labels[labels.length - 1] instanceof Integer ? (Integer) labels[labels.length - 1] : 0;
		int n = labels[labels.length - 1] instanceof Integer ? labels.length - 1 : labels.length;
		int i;
		try {
			i = Integer.parseInt(String.valueOf(v).trim()) - start;
		} catch (NumberFormatException e) {
			return v;
		}
		return i >= 0 && i < n ? labels[i] : v;
	}

	private static String diseaseName(Integer researchType) {
		String[] d = OTHER_RESEARCH.get(researchType);
		return d != null ? d[1] : "研究类型 " + researchType;
	}

	/** 建档时年龄（周岁），按身份证号；没有身份证号为 null */
	private static Integer age(String cardNo) {
		LocalDate birth = IdCardUtil.birthDate(cardNo);
		return birth == null ? null : Period.between(birth, LocalDate.now()).getYears();
	}

	private static String date(String v, String label) {
		v = trim(v);
		if (v == null) {
			return null;
		}
		try {
			LocalDate d = LocalDate.parse(v);
			if (d.isAfter(LocalDate.now())) {
				throw new IllegalArgumentException(label + "不能晚于今天");
			}
			return d.toString();
		} catch (DateTimeParseException e) {
			throw new IllegalArgumentException(label + "格式应为 yyyy-MM-dd");
		}
	}

	/** 数字类字段（老表是 varchar）：校验范围，原样存文字 */
	private static String number(String v, String label, double min, double max) {
		v = trim(v);
		if (v == null) {
			return null;
		}
		try {
			double d = new BigDecimal(v).doubleValue();
			if (d < min || d > max) {
				throw new IllegalArgumentException(label + "应在 " + fmt(min) + "~" + fmt(max) + " 之间");
			}
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(label + "应为数字");
		}
		return v;
	}

	private static Integer range(Integer v, String label, int min, int max) {
		if (v != null && (v < min || v > max)) {
			throw new IllegalArgumentException(label + "应在 " + min + "~" + max + " 之间");
		}
		return v;
	}

	private static String maxLen(String v, int len, String label) {
		if (v != null && v.length() > len) {
			throw new IllegalArgumentException(label + "不能超过 " + len + " 个字");
		}
		return v;
	}

	private static boolean same(Object a, Object b) {
		return a == null ? b == null : b != null && String.valueOf(a).trim().equalsIgnoreCase(String.valueOf(b).trim());
	}

	private static String fmt(double d) {
		return d == (long) d ? String.valueOf((long) d) : String.valueOf(d);
	}

	private static String trim(String s) {
		if (s == null) {
			return null;
		}
		String t = s.trim();
		return t.isEmpty() ? null : t;
	}

	private static String str(Object v) {
		return v == null ? null : trim(String.valueOf(v));
	}

	private static int toInt(Object v) {
		return v == null ? 0 : ((Number) v).intValue();
	}

	private static Integer toInteger(Object v) {
		return v == null ? null : ((Number) v).intValue();
	}

	private static Long toLong(Object v) {
		return v == null ? null : ((Number) v).longValue();
	}
}
