package com.wenwen.service.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
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
import com.alibaba.fastjson.parser.Feature;
import com.wenwen.mapper.VisitMapper;
import com.wenwen.service.AuditLogService;
import com.wenwen.service.VisitService;
import com.wenwen.util.BizException;
import com.wenwen.util.VisitFieldDict;
import com.wenwen.vo.VisitDetailVo;
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
		vo.setDoctorName(blankToNull((String) row.get("doctorName")));
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
		String operator = blankToNull(visitMapper.getUserName(map));
		auditLogService.record(patientId, visitId, "删除随访", null,
				(baseline ? "基线访视" : "常规随访") + " " + (visitDate == null ? "日期未填" : visitDate) + "；删除原因：" + reason,
				doctorId, operator == null ? "医生 ID " + doctorId : operator);
		return patientId;
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
		String text = blankToNull(raw);
		if (text == null) {
			return mod;
		}
		JSONObject json = null;
		if (text.startsWith("{")) {
			try {
				json = JSON.parseObject(text, Feature.OrderedField); // 保持原字段顺序
			} catch (Exception e) {
				json = null;
			}
		}
		if (json == null) {
			mod.setRecord(text);
		} else if (json.containsKey("record")) {
			mod.setRecord(blankToNull(json.getString("record")));
			mod.setRecordDate(blankToNull(json.getString("date")));
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
				boolean notChecked = isTrue(dict.wx.containsKey(f.key) ? json.get(dict.wx.get(f.key)) : null)
						|| (wxMap != null && isTrue(wxMap.get(f.key)));
				String value = f.key.startsWith("@") ? null : text(path(json, f.key));
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
					String u = o == null ? null : blankToNull(String.valueOf(o));
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
					String v = text(sub.getValue());
					if (!used.contains(sk) && v != null) {
						others.add(field(sk, sk, v, "", false));
					}
				}
				continue;
			}
			String v = text(e.getValue());
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
			String a = text(r.get(p[0])), b = text(r.get(p[1]));
			return a == null ? null : (b == null ? a : a + " " + b);
		}
		if (c.key.contains("~")) {
			String[] p = c.key.split("~");
			String a = text(r.get(p[0])), b = text(r.get(p[1]));
			return a == null && b == null ? null : (a == null ? "" : a) + " ~ " + (b == null ? "" : b);
		}
		return text(r.get(c.key));
	}

	/** 取值，支持 result.crpScore 这样的嵌套 */
	private static Object path(JSONObject json, String key) {
		int dot = key.indexOf('.');
		if (dot < 0) {
			return json.get(key);
		}
		Object parent = json.get(key.substring(0, dot));
		return parent instanceof JSONObject ? ((JSONObject) parent).get(key.substring(dot + 1)) : null;
	}

	/** 显示文字：数组用「、」连接，布尔转 是 / 否，对象转 JSON；空值返回 null */
	private static String text(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof Collection) {
			List<String> parts = new ArrayList<String>();
			for (Object o : (Collection<?>) value) {
				String t = text(o);
				if (t != null) {
					parts.add(t);
				}
			}
			return parts.isEmpty() ? null : String.join("、", parts);
		}
		if (value instanceof Boolean) {
			return (Boolean) value ? "是" : "否";
		}
		if (value instanceof JSONObject) {
			return ((JSONObject) value).isEmpty() ? null : JSON.toJSONString(value);
		}
		return blankToNull(String.valueOf(value));
	}

	/** Wx 标记可能是布尔，也可能是字符串 "true" */
	private static boolean isTrue(Object v) {
		return Boolean.TRUE.equals(v) || "true".equalsIgnoreCase(String.valueOf(v));
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

	/** 空串、空 JSON（{} / [] / null）都视为未填写 */
	private static String blankToNull(String s) {
		if (s == null) {
			return null;
		}
		String t = s.trim();
		return t.isEmpty() || "{}".equals(t) || "[]".equals(t) || "null".equalsIgnoreCase(t) ? null : t;
	}

	private static Long toLong(Object value) {
		return value == null ? null : ((Number) value).longValue();
	}
}
