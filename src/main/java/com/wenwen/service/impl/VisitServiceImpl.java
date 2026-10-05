package com.wenwen.service.impl;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.wenwen.mapper.VisitMapper;
import com.wenwen.service.AuditLogService;
import com.wenwen.service.VisitService;
import com.wenwen.util.BizException;
import com.wenwen.util.VisitFieldDict;
import com.wenwen.util.VisitJson;
import com.wenwen.vo.FieldChange;
import com.wenwen.vo.VisitDetailVo;
import com.wenwen.vo.VisitEditFieldVo;
import com.wenwen.vo.VisitEditFormVo;
import com.wenwen.vo.VisitEditGroupVo;
import com.wenwen.vo.VisitEditModuleVo;
import com.wenwen.vo.VisitEditTableVo;
import com.wenwen.vo.VisitModuleUpdate;
import com.wenwen.vo.VisitUpdateRequest;
import com.wenwen.vo.VisitUpdateResultVo;
import com.wenwen.vo.VisitFieldGroupVo;
import com.wenwen.vo.VisitFieldVo;
import com.wenwen.vo.VisitImageGroupVo;
import com.wenwen.vo.VisitModuleVo;
import com.wenwen.vo.VisitTableVo;

@Service
public class VisitServiceImpl implements VisitService {

	private static final String NOT_CHECKED = "未查";

	@Autowired
	private VisitMapper visitMapper;

	@Autowired
	private AuditLogService auditLogService;

	@Override
	public VisitDetailVo getVisitDetail(Long doctorId, Long visitId) {
		if (doctorId == null || visitId == null) {
			throw new IllegalArgumentException("缺少医生ID或随访ID");
		}
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("doctorId", doctorId);
		map.put("visitId", visitId);
		Map<String, Object> row = visitMapper.getVisit(map);
		if (row == null) {
			throw new BizException("403", "随访记录不存在，或患者不在您名下");
		}

		VisitDetailVo vo = new VisitDetailVo();
		vo.setVisitId(toLong(row.get("visitId")));
		vo.setPatientId(toLong(row.get("patientId")));
		vo.setVisitDate((String) row.get("visitDate"));
		vo.setDoctorId(toLong(row.get("doctorId")));
		vo.setDoctorName(VisitJson.blankToNull((String) row.get("doctorName")));
		map.put("patientId", vo.getPatientId());
		boolean baseline = vo.getVisitId().equals(visitMapper.getBaselineVisitId(map));
		vo.setBaseline(baseline);
		vo.setVisitType(baseline ? "基线访视" : "常规随访");

		List<VisitModuleVo> modules = new ArrayList<VisitModuleVo>();
		int filled = 0;
		for (VisitFieldDict.Module m : VisitFieldDict.MODULES) {
			VisitModuleVo mod = toModule(m, (String) row.get(m.column));
			if (mod.isFilled()) {
				filled++;
			}
			modules.add(mod);
		}
		vo.setModules(modules);
		vo.setFilledCount(filled);
		return vo;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public Long deleteVisit(Long doctorId, Long visitId, String reason) {
		reason = reason == null ? "" : reason.trim();
		if (doctorId == null || visitId == null) {
			throw new IllegalArgumentException("缺少医生ID或随访ID");
		}
		if (reason.isEmpty()) {
			throw new IllegalArgumentException("请填写删除原因");
		}
		if (reason.length() > 500) {
			throw new IllegalArgumentException("删除原因不能超过 500 字");
		}
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("doctorId", doctorId);
		map.put("visitId", visitId);
		Map<String, Object> row = visitMapper.getVisit(map);
		if (row == null) {
			throw new BizException("403", "随访记录不存在，或患者不在您名下");
		}
		Long patientId = toLong(row.get("patientId"));
		map.put("patientId", patientId);
		boolean baseline = visitId.equals(visitMapper.getBaselineVisitId(map));
		String visitDate = (String) row.get("visitDate");

		// 1. 接好「上一次随访」链条；2. 删除
		map.put("prevVisitId", toLong(row.get("lastFollowUpId")));
		visitMapper.relinkNextVisit(map);
		visitMapper.deleteVisit(map);
		// 3、4. 老系统的计数和日期按剩下的随访重新计算
		visitMapper.refreshPatientCounters(map);
		map.put("visitDoctorId", toLong(row.get("doctorId")));
		map.put("researchType", row.get("researchType"));
		visitMapper.refreshRelationCounters(map);
		// 5. 修改记录（只记删除原因，不备份数据）
		String operator = VisitJson.blankToNull(visitMapper.getUserName(map));
		auditLogService.record(patientId, visitId, "删除随访", null,
				(baseline ? "基线访视" : "常规随访") + " " + (visitDate == null ? "日期未填" : visitDate) + "；删除原因：" + reason,
				doctorId, operator == null ? "医生 ID " + doctorId : operator);
		return patientId;
	}

	// ===================== 编辑随访 =====================

	@Override
	public VisitEditFormVo getEditForm(Long doctorId, Long visitId) {
		Map<String, Object> row = loadVisit(doctorId, visitId);
		VisitEditFormVo form = new VisitEditFormVo();
		form.setVisitId(visitId);
		form.setPatientId(toLong(row.get("patientId")));
		form.setVisitDate((String) row.get("visitDate"));
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("patientId", form.getPatientId());
		form.setVisitType(visitId.equals(visitMapper.getBaselineVisitId(map)) ? "基线访视" : "常规随访");
		form.setVersion(version(row));
		List<VisitEditModuleVo> modules = new ArrayList<VisitEditModuleVo>();
		for (VisitFieldDict.Module m : VisitFieldDict.MODULES) {
			modules.add(editModule(m, (String) row.get(m.column)));
		}
		form.setModules(modules);
		return form;
	}

	private VisitEditModuleVo editModule(VisitFieldDict.Module m, String raw) {
		VisitEditModuleVo mod = new VisitEditModuleVo();
		mod.setKey(m.key);
		mod.setTitle(m.title);
		mod.setColumn(m.column);
		mod.setGroups(new ArrayList<VisitEditGroupVo>());
		mod.setTables(new ArrayList<VisitEditTableVo>());
		JSONObject json = VisitJson.parse(raw);
		String text = VisitJson.blankToNull(raw);
		if (text != null && (json == null || json.containsKey("record"))) {
			// 普通文字 / {record, date}：不是老系统的结构化格式，只读显示
			mod.setEditable(false);
			mod.setRecord(json == null ? text : VisitJson.blankToNull(json.getString("record")));
			return mod;
		}
		mod.setEditable(true);
		if (json == null) {
			json = new JSONObject(true);
		}
		JSONObject wxMap = m.wxMap != null && json.get(m.wxMap) instanceof JSONObject ? json.getJSONObject(m.wxMap) : null;
		for (VisitFieldDict.Group g : m.groups) {
			List<VisitEditFieldVo> fields = new ArrayList<VisitEditFieldVo>();
			for (VisitFieldDict.Field f : g.fields) {
				VisitEditFieldVo fv = editField(f, f.key.startsWith("@") ? null : VisitJson.path(json, f.key));
				if (m.wx.containsKey(f.key)) {
					fv.setWx(VisitJson.isTrue(json.get(m.wx.get(f.key))));
				} else if (m.wxMap != null && !"list".equals(f.type) && !f.key.startsWith("@")) {
					fv.setWx(wxMap != null && VisitJson.isTrue(wxMap.get(f.key)));
				}
				fields.add(fv);
			}
			VisitEditGroupVo gv = new VisitEditGroupVo();
			gv.setTitle(g.title);
			gv.setFields(fields);
			mod.getGroups().add(gv);
		}
		for (VisitFieldDict.Table t : m.tables) {
			VisitEditTableVo tv = new VisitEditTableVo();
			tv.setKey(t.key);
			tv.setTitle(t.title);
			List<VisitEditFieldVo> cols = new ArrayList<VisitEditFieldVo>();
			for (VisitFieldDict.Field c : t.editColumns) {
				cols.add(editField(c, null));
			}
			tv.setColumns(cols);
			List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
			JSONArray arr = json.get(t.key) instanceof JSONArray ? json.getJSONArray(t.key) : new JSONArray();
			for (int i = 0; i < arr.size(); i++) {
				if (!(arr.get(i) instanceof JSONObject)) {
					continue;
				}
				JSONObject r = arr.getJSONObject(i);
				Map<String, Object> out = new LinkedHashMap<String, Object>();
				out.put("_row", i);
				for (VisitFieldDict.Field c : t.editColumns) {
					out.put(c.key, editValue(c.type, r.get(c.key)));
				}
				rows.add(out);
			}
			tv.setRows(rows);
			mod.getTables().add(tv);
		}
		// 图片、字典外字段：只读，沿用访视详情的解析
		VisitModuleVo view = toModule(m, raw);
		mod.setImages(view.getImages());
		mod.setOthers(new ArrayList<VisitFieldVo>());
		for (VisitFieldGroupVo g : view.getGroups()) {
			if ("其他字段".equals(g.getTitle())) {
				mod.getOthers().addAll(g.getItems());
			}
		}
		return mod;
	}

	private static VisitEditFieldVo editField(VisitFieldDict.Field f, Object value) {
		VisitEditFieldVo fv = new VisitEditFieldVo();
		fv.setKey(f.key);
		fv.setLabel(f.label);
		fv.setUnit(f.unit);
		fv.setType(f.type);
		fv.setValue(editValue(f.type, value));
		return fv;
	}

	/** 表单值：多选为字符串数组，其它为字符串 */
	private static Object editValue(String type, Object value) {
		if ("list".equals(type)) {
			List<String> items = new ArrayList<String>();
			if (value instanceof Collection) {
				for (Object o : (Collection<?>) value) {
					String s = VisitJson.text(o);
					if (s != null) {
						items.add(s);
					}
				}
			} else if (VisitJson.text(value) != null) {
				items.add(VisitJson.text(value));
			}
			return items;
		}
		return VisitJson.text(value);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public VisitUpdateResultVo updateVisit(VisitUpdateRequest req) {
		if (req == null || req.getVisitId() == null) {
			throw new IllegalArgumentException("缺少随访ID");
		}
		Map<String, Object> row = loadVisit(req.getDoctorId(), req.getVisitId());
		if (req.getVersion() == null || !req.getVersion().equals(version(row))) {
			throw new BizException("409", "这次随访在您打开后已被修改（可能是老系统或其他人），请刷新页面后重新编辑");
		}
		Long patientId = toLong(row.get("patientId"));
		Map<String, VisitModuleUpdate> ups = req.getModules() == null ? new HashMap<String, VisitModuleUpdate>() : req.getModules();
		VisitEditor editor = new VisitEditor();
		Map<String, Object> update = new HashMap<String, Object>();
		update.put("visitId", req.getVisitId());

		// 随访日期
		String oldDate = (String) row.get("visitDate");
		String newDate = VisitJson.blankToNull(req.getVisitDate());
		boolean dateChanged = newDate != null && !newDate.equals(oldDate);
		if (dateChanged) {
			LocalDate d;
			try {
				d = LocalDate.parse(newDate);
			} catch (Exception e) {
				throw new IllegalArgumentException("随访日期格式应为 yyyy-MM-dd");
			}
			if (d.isAfter(LocalDate.now())) {
				throw new IllegalArgumentException("随访日期不能晚于今天");
			}
			update.put("visitDate", newDate);
			editor.changes.add(new FieldChange("follow_up_date", "随访日期", oldDate, newDate));
		}

		// 各模块合并（只处理结构化模块）
		Map<String, JSONObject> objs = new HashMap<String, JSONObject>();
		Map<String, Set<String>> changed = new HashMap<String, Set<String>>();
		for (VisitFieldDict.Module m : VisitFieldDict.MODULES) {
			String raw = (String) row.get(m.column);
			JSONObject json = VisitJson.parse(raw);
			boolean structured = VisitJson.blankToNull(raw) == null || (json != null && !json.containsKey("record"));
			if (!structured) {
				continue;
			}
			boolean isNew = json == null;
			JSONObject obj = isNew ? new JSONObject(true) : json;
			Set<String> keys = editor.apply(m, obj, ups.get(m.column));
			// 改了随访日期：病史病情里的随诊时间同步（原来有这个字段才改）
			if (dateChanged && "bsbq".equals(m.column) && obj.containsKey("followDate") && !newDate.equals(obj.getString("followDate"))) {
				obj.put("followDate", newDate);
				keys.add("followDate");
			}
			if (!keys.isEmpty() && isNew) {
				obj.put("finish", true); // 新填的模块，老系统前端按 finish 显示已填写
			}
			objs.put(m.column, obj);
			changed.put(m.column, keys);
		}
		if (editor.recompute(objs.get("fzjc"), objs.get("bqpg"),
				changed.containsKey("fzjc") ? changed.get("fzjc") : new HashSet<String>(),
				changed.containsKey("bqpg") ? changed.get("bqpg") : new HashSet<String>())) {
			changed.get("bqpg").add("result");
		}
		for (Map.Entry<String, Set<String>> e : changed.entrySet()) {
			if (!e.getValue().isEmpty()) {
				update.put(e.getKey(), VisitJson.write(objs.get(e.getKey())));
			}
		}

		VisitUpdateResultVo result = new VisitUpdateResultVo();
		List<String> texts = new ArrayList<String>();
		for (FieldChange c : editor.changes) {
			texts.add(c.toText());
		}
		result.setChanges(texts);
		result.setChangedCount(editor.changes.size());
		if (editor.changes.isEmpty()) {
			return result;
		}
		visitMapper.updateVisit(update);
		if (dateChanged) {
			// 老系统维护的随访次数 / 日期按新日期重算
			Map<String, Object> map = new HashMap<String, Object>();
			map.put("patientId", patientId);
			map.put("visitDoctorId", toLong(row.get("doctorId")));
			map.put("researchType", row.get("researchType"));
			visitMapper.refreshPatientCounters(map);
			visitMapper.refreshRelationCounters(map);
		}
		Map<String, Object> um = new HashMap<String, Object>();
		um.put("doctorId", req.getDoctorId());
		String operator = VisitJson.blankToNull(visitMapper.getUserName(um));
		um.put("patientId", patientId);
		boolean baseline = req.getVisitId().equals(visitMapper.getBaselineVisitId(um));
		auditLogService.record(patientId, req.getVisitId(), "编辑随访", editor.changes,
				null, req.getDoctorId(), operator == null ? "医生 ID " + req.getDoctorId() : operator);
		return result;
	}

	/** 取随访并校验权限（只能是自己名下 RA 患者的随访） */
	private Map<String, Object> loadVisit(Long doctorId, Long visitId) {
		if (doctorId == null || visitId == null) {
			throw new IllegalArgumentException("缺少医生ID或随访ID");
		}
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("doctorId", doctorId);
		map.put("visitId", visitId);
		Map<String, Object> row = visitMapper.getVisit(map);
		if (row == null) {
			throw new BizException("403", "随访记录不存在，或患者不在您名下");
		}
		return row;
	}

	/** 数据版本：随访日期 + 7 个模块原文的 MD5，用于发现打开后被别人（含老系统）改过 */
	private static String version(Map<String, Object> row) {
		StringBuilder sb = new StringBuilder(String.valueOf(row.get("visitDate")));
		for (VisitFieldDict.Module m : VisitFieldDict.MODULES) {
			sb.append('\u0001').append(String.valueOf(row.get(m.column)));
		}
		try {
			byte[] d = MessageDigest.getInstance("MD5").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
			return String.format("%032x", new BigInteger(1, d));
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * 解析一个模块（只读，不改老数据）：
	 * 结构化 JSON → 按字段字典分组 / 清单 / 图片，字典外的字段放「其他字段」；
	 * {"record": "...", "date": "..."} 或普通文字 → record
	 */
	private static VisitModuleVo toModule(VisitFieldDict.Module dict, String raw) {
		VisitModuleVo mod = new VisitModuleVo();
		mod.setKey(dict.key);
		mod.setTitle(dict.title);
		mod.setColumn(dict.column);
		mod.setGroups(new ArrayList<VisitFieldGroupVo>());
		mod.setTables(new ArrayList<VisitTableVo>());
		mod.setImages(new ArrayList<VisitImageGroupVo>());
		String text = VisitJson.blankToNull(raw);
		if (text == null) {
			return mod;
		}
		JSONObject json = VisitJson.parse(text); // 保持原字段顺序
		if (json == null) {
			mod.setRecord(text);
		} else if (json.containsKey("record")) {
			mod.setRecord(VisitJson.blankToNull(json.getString("record")));
			mod.setRecordDate(VisitJson.blankToNull(json.getString("date")));
		} else {
			fillStructured(dict, json, mod);
		}
		mod.setFilled(mod.getRecord() != null || !mod.getGroups().isEmpty() || !mod.getTables().isEmpty() || !mod.getImages().isEmpty());
		return mod;
	}

	private static void fillStructured(VisitFieldDict.Module dict, JSONObject json, VisitModuleVo mod) {
		Set<String> used = new HashSet<String>(dict.hidden);
		JSONObject wxMap = dict.wxMap == null ? null : json.getJSONObject(dict.wxMap);

		for (VisitFieldDict.Group g : dict.groups) {
			List<VisitFieldVo> items = new ArrayList<VisitFieldVo>();
			for (VisitFieldDict.Field f : g.fields) {
				used.add(f.key);
				boolean notChecked = VisitJson.isTrue(dict.wx.containsKey(f.key) ? json.get(dict.wx.get(f.key)) : null)
						|| (wxMap != null && VisitJson.isTrue(wxMap.get(f.key)));
				String value = f.key.startsWith("@") ? null : VisitJson.text(VisitJson.path(json, f.key));
				if (value == null && !notChecked) {
					continue;
				}
				items.add(field(f.key, f.label, value == null ? NOT_CHECKED : value, value == null ? "" : f.unit, value == null));
			}
			if (!items.isEmpty()) {
				mod.getGroups().add(group(g.title, items));
			}
		}

		for (VisitFieldDict.Field img : dict.images) {
			used.add(img.key);
			List<String> urls = new ArrayList<String>();
			Object v = json.get(img.key);
			if (v instanceof Collection) {
				for (Object o : (Collection<?>) v) {
					String u = o == null ? null : VisitJson.blankToNull(String.valueOf(o));
					if (u != null) {
						urls.add(u);
					}
				}
			}
			if (!urls.isEmpty()) {
				VisitImageGroupVo ig = new VisitImageGroupVo();
				ig.setTitle(img.label);
				ig.setUrls(urls);
				mod.getImages().add(ig);
			}
		}

		for (VisitFieldDict.Table t : dict.tables) {
			used.add(t.key);
			JSONArray arr = json.get(t.key) instanceof JSONArray ? json.getJSONArray(t.key) : null;
			if (arr == null || arr.isEmpty()) {
				continue;
			}
			List<List<String>> cells = new ArrayList<List<String>>();
			for (Object o : arr) {
				if (!(o instanceof JSONObject)) {
					continue;
				}
				JSONObject r = (JSONObject) o;
				List<String> line = new ArrayList<String>();
				for (VisitFieldDict.Field c : t.columns) {
					line.add(cell(r, c));
				}
				cells.add(line);
			}
			// 只保留有内容的列
			List<String> columns = new ArrayList<String>();
			List<Integer> keep = new ArrayList<Integer>();
			for (int i = 0; i < t.columns.size(); i++) {
				for (List<String> line : cells) {
					if (line.get(i) != null) {
						keep.add(i);
						VisitFieldDict.Field c = t.columns.get(i);
						columns.add(c.unit.isEmpty() ? c.label : c.label + "（" + c.unit + "）");
						break;
					}
				}
			}
			if (keep.isEmpty()) {
				continue;
			}
			List<List<String>> rows = new ArrayList<List<String>>();
			for (List<String> line : cells) {
				List<String> out = new ArrayList<String>();
				for (int i : keep) {
					out.add(line.get(i) == null ? "" : line.get(i));
				}
				rows.add(out);
			}
			VisitTableVo tv = new VisitTableVo();
			tv.setTitle(t.title);
			tv.setColumns(columns);
			tv.setRows(rows);
			mod.getTables().add(tv);
		}

		// 字典外的字段：按原字段名放「其他字段」，不丢数据
		List<VisitFieldVo> others = new ArrayList<VisitFieldVo>();
		for (Map.Entry<String, Object> e : json.entrySet()) {
			String k = e.getKey();
			if (e.getValue() instanceof JSONObject && !(dict.wxMap != null && dict.wxMap.equals(k))) {
				for (Map.Entry<String, Object> sub : ((JSONObject) e.getValue()).entrySet()) {
					String sk = k + "." + sub.getKey();
					String v = VisitJson.text(sub.getValue());
					if (!used.contains(sk) && v != null) {
						others.add(field(sk, sk, v, "", false));
					}
				}
				continue;
			}
			String v = VisitJson.text(e.getValue());
			if (!used.contains(k) && v != null) {
				others.add(field(k, k, v, "", false));
			}
		}
		if (!others.isEmpty()) {
			mod.getGroups().add(group("其他字段", others));
		}
	}

	/** 清单单元格：a+b 拼接（剂量 + 单位），a~b 为区间（起 ~ 止） */
	private static String cell(JSONObject r, VisitFieldDict.Field c) {
		if (c.key.contains("+")) {
			String[] p = c.key.split("\\+");
			String a = VisitJson.text(r.get(p[0])), b = VisitJson.text(r.get(p[1]));
			return a == null ? null : (b == null ? a : a + " " + b);
		}
		if (c.key.contains("~")) {
			String[] p = c.key.split("~");
			String a = VisitJson.text(r.get(p[0])), b = VisitJson.text(r.get(p[1]));
			return a == null && b == null ? null : (a == null ? "" : a) + " ~ " + (b == null ? "" : b);
		}
		return VisitJson.text(r.get(c.key));
	}




	private static VisitFieldVo field(String key, String label, String value, String unit, boolean notChecked) {
		VisitFieldVo f = new VisitFieldVo();
		f.setKey(key);
		f.setLabel(label);
		f.setValue(value);
		f.setUnit(unit);
		f.setNotChecked(notChecked);
		return f;
	}

	private static VisitFieldGroupVo group(String title, List<VisitFieldVo> items) {
		VisitFieldGroupVo g = new VisitFieldGroupVo();
		g.setTitle(title);
		g.setItems(items);
		return g;
	}


	private static Long toLong(Object value) {
		return value == null ? null : ((Number) value).longValue();
	}
}
