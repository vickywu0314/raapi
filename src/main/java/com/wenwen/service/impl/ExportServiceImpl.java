package com.wenwen.service.impl;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.wenwen.mapper.ExportMapper;
import com.wenwen.mapper.VisitMapper;
import com.wenwen.service.ExportService;
import com.wenwen.service.PatientService;
import com.wenwen.util.BizException;
import com.wenwen.util.IdCardUtil;
import com.wenwen.util.VisitJson;
import com.wenwen.vo.ExportRangeVo;

@Service
public class ExportServiceImpl implements ExportService {

	/** CSV 列：标题（病历字段含义见 docs/随访字段字典.md） */
	private static final String[] HEADERS = { "患者ID", "研究编号", "姓名（脱敏）", "性别", "出生年份", "年龄", "随访ID", "随访日期", "访视类型",
			"DAS28-CRP", "DAS28-ESR", "肿胀关节数", "压痛关节数", "HAQ 健康评分", "C反应蛋白 CRP (mg/L)", "血沉 ESR (mm/h)", "类风湿因子 RF (IU/mL)",
			"主证", "西药", "中成药", "本次是否发生不良反应" };

	@Value("${ra.export.free-months:6}")
	private int freeMonths;

	@Value("${ra.export.apply-mail-to:}")
	private String applyMailTo;

	@Autowired
	private ExportMapper exportMapper;

	@Autowired
	private VisitMapper visitMapper;

	@Autowired
	private PatientService patientService;

	/** 可直接下载的最早日期：当前月份往前 freeMonths 个月的 1 号 */
	private LocalDate freeStart() {
		return LocalDate.now().withDayOfMonth(1).minusMonths(freeMonths);
	}

	@Override
	public ExportRangeVo getRange() {
		ExportRangeVo vo = new ExportRangeVo();
		vo.setFreeStartDate(freeStart().toString());
		vo.setToday(LocalDate.now().toString());
		vo.setFreeMonths(freeMonths);
		vo.setMailConfigured(applyMailTo != null && !applyMailTo.trim().isEmpty());
		return vo;
	}

	@Override
	public byte[] exportVisitsCsv(Long doctorId, String startDate, String endDate, String keyword, String followStatus, String completeness, List<Long> patientIds) {
		LocalDate[] range = range(startDate, endDate);
		if (range[0].isBefore(freeStart())) {
			throw new BizException("NEED_APPLY", "开始日期早于 " + freeStart() + "，超过半年的数据需要提交申请");
		}
		List<Long> ids = scope(doctorId, keyword, followStatus, completeness, patientIds);
		StringBuilder csv = new StringBuilder("﻿");
		line(csv, HEADERS);
		if (!ids.isEmpty()) {
			Map<String, Object> map = new HashMap<String, Object>();
			map.put("patientIds", ids);
			map.put("startDate", range[0].toString());
			map.put("endDate", range[1].toString());
			Set<Long> baselines = new HashSet<Long>();
			Long last = null;
			for (Map<String, Object> r : exportMapper.listVisitOrder(map)) {
				Long pid = toLong(r.get("patientId"));
				if (!pid.equals(last)) {
					baselines.add(toLong(r.get("visitId")));
					last = pid;
				}
			}
			int thisYear = Calendar.getInstance().get(Calendar.YEAR);
			for (Map<String, Object> r : exportMapper.listVisits(map)) {
				line(csv, row(r, baselines, thisYear));
			}
		}
		return csv.toString().getBytes(StandardCharsets.UTF_8);
	}

	@Override
	public Long apply(Long doctorId, String startDate, String endDate, String keyword, String followStatus, String completeness, List<Long> patientIds, String reason) {
		LocalDate[] range = range(startDate, endDate);
		reason = reason == null ? "" : reason.trim();
		if (reason.isEmpty()) {
			throw new IllegalArgumentException("请填写申请原因 / 用途");
		}
		if (reason.length() > 1000) {
			throw new IllegalArgumentException("申请原因不能超过 1000 字");
		}
		scope(doctorId, keyword, followStatus, completeness, patientIds); // 校验参数和权限
		Map<String, Object> scope = new LinkedHashMap<String, Object>();
		if (patientIds != null && !patientIds.isEmpty()) {
			scope.put("patientIds", patientIds);
		} else {
			scope.put("keyword", keyword);
			scope.put("followStatus", followStatus);
			scope.put("completeness", completeness);
		}
		Map<String, Object> um = new HashMap<String, Object>();
		um.put("doctorId", doctorId);
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("doctorId", doctorId);
		map.put("doctorName", VisitJson.blankToNull(visitMapper.getUserName(um)));
		map.put("startDate", range[0].toString());
		map.put("endDate", range[1].toString());
		map.put("scope", VisitJson.write(scope));
		map.put("reason", reason);
		map.put("mailTo", VisitJson.blankToNull(applyMailTo));
		exportMapper.insertApplication(map);
		// TODO 发送邮件到 applyMailTo（待实现）
		return toLong(map.get("id"));
	}

	private static LocalDate[] range(String startDate, String endDate) {
		LocalDate start, end;
		try {
			start = LocalDate.parse(startDate);
			end = LocalDate.parse(endDate);
		} catch (DateTimeParseException | NullPointerException e) {
			throw new IllegalArgumentException("请选择导出的开始和结束日期（yyyy-MM-dd）");
		}
		if (start.isAfter(end)) {
			throw new IllegalArgumentException("开始日期不能晚于结束日期");
		}
		if (end.isAfter(LocalDate.now())) {
			throw new IllegalArgumentException("结束日期不能晚于今天");
		}
		return new LocalDate[] { start, end };
	}

	/** 导出的患者：勾选的（只保留该医生名下的）或符合筛选条件的全部 */
	private List<Long> scope(Long doctorId, String keyword, String followStatus, String completeness, List<Long> patientIds) {
		if (patientIds != null && !patientIds.isEmpty()) {
			Set<Long> mine = new HashSet<Long>(patientService.listPatientIds(doctorId, null, null, null));
			List<Long> ids = new ArrayList<Long>();
			for (Long id : patientIds) {
				if (mine.contains(id)) {
					ids.add(id);
				}
			}
			return ids;
		}
		return patientService.listPatientIds(doctorId, keyword, followStatus, completeness);
	}

	private static String[] row(Map<String, Object> r, Set<Long> baselines, int thisYear) {
		JSONObject fzjc = VisitJson.parse((String) r.get("fzjc"));
		JSONObject bqpg = VisitJson.parse((String) r.get("bqpg"));
		JSONObject zyzd = VisitJson.parse((String) r.get("zyzd"));
		JSONObject zlfa = VisitJson.parse((String) r.get("zlfa"));
		JSONObject blsj = VisitJson.parse((String) r.get("blsj"));
		Integer gender = r.get("gender") == null ? null : ((Number) r.get("gender")).intValue();
		String birthYear = "", age = "";
		LocalDate birth = IdCardUtil.birthDate((String) r.get("cardNo"));
		if (birth != null) {
			birthYear = String.valueOf(birth.getYear());
			age = String.valueOf(Period.between(birth, LocalDate.now()).getYears());
		} else if (r.get("age") != null) {
			int by = (r.get("createYear") == null ? thisYear : ((Number) r.get("createYear")).intValue()) - ((Number) r.get("age")).intValue();
			birthYear = String.valueOf(by);
			age = String.valueOf(thisYear - by);
		}
		Long visitId = toLong(r.get("visitId"));
		return new String[] { str(r.get("patientId")), str(r.get("studyNo")), maskName((String) r.get("name")),
				gender == null ? "" : gender == 1 ? "男" : gender == 2 ? "女" : "", birthYear, age,
				str(visitId), str(r.get("visitDate")), baselines.contains(visitId) ? "基线访视" : "常规随访",
				get(bqpg, "result.crpScore"), get(bqpg, "result.esrScore"), get(bqpg, "result.zzgjs"), get(bqpg, "result.ytgjs"), get(bqpg, "hqaScore"),
				get(fzjc, "cfydb"), get(fzjc, "xc"), get(fzjc, "lfsyz"), get(zyzd, "zz"),
				drugs(zlfa, "xyList"), drugs(zlfa, "zcyList"), get(blsj, "event") };
	}

	private static String get(JSONObject o, String key) {
		return o == null ? "" : str(VisitJson.text(VisitJson.path(o, key)));
	}

	/** 药品清单：药名 剂量单位 频次，多个用「；」分隔 */
	private static String drugs(JSONObject zlfa, String key) {
		if (zlfa == null || !(zlfa.get(key) instanceof JSONArray)) {
			return "";
		}
		List<String> out = new ArrayList<String>();
		for (Object o : zlfa.getJSONArray(key)) {
			if (o instanceof JSONObject) {
				JSONObject d = (JSONObject) o;
				StringBuilder sb = new StringBuilder(str(VisitJson.text(d.get("drugName"))));
				String dose = str(VisitJson.text(d.get("dosis"))) + str(VisitJson.text(d.get("dosisUnit")));
				if (!dose.isEmpty()) {
					sb.append(' ').append(dose);
				}
				String freq = str(VisitJson.text(d.get("drugFreq")));
				if (!freq.isEmpty()) {
					sb.append(' ').append(freq);
				}
				if (sb.length() > 0) {
					out.add(sb.toString());
				}
			}
		}
		return String.join("；", out);
	}

	/** 姓名脱敏：保留第一个字，其余用 * */
	private static String maskName(String name) {
		if (name == null || name.trim().isEmpty()) {
			return "";
		}
		String n = name.trim();
		StringBuilder sb = new StringBuilder(n.substring(0, 1));
		for (int i = 1; i < n.length(); i++) {
			sb.append('*');
		}
		return sb.toString();
	}

	private static void line(StringBuilder csv, String[] cells) {
		for (int i = 0; i < cells.length; i++) {
			if (i > 0) {
				csv.append(',');
			}
			String v = cells[i] == null ? "" : cells[i];
			if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
				v = "\"" + v.replace("\"", "\"\"") + "\"";
			}
			csv.append(v);
		}
		csv.append("\r\n");
	}

	private static String str(Object v) {
		return v == null ? "" : String.valueOf(v);
	}

	private static Long toLong(Object v) {
		return v == null ? null : ((Number) v).longValue();
	}
}
