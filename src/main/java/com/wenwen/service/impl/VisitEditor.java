package com.wenwen.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.wenwen.util.VisitFieldDict;
import com.wenwen.util.VisitJson;
import com.wenwen.vo.FieldChange;
import com.wenwen.vo.VisitModuleUpdate;

/**
 * 编辑随访：把表单提交的值合并进老系统的 JSON，并按老系统规则重算病情评估的计算项。
 * 原则（老系统仍在使用）：只写有变化的字段；保持原值的类型（数字 / 字符串 / 数组）；字典外字段、清单行里未显示的字段原样保留。
 * 一次请求用一个实例，changes 收集全部修改（写修改记录用）。
 */
class VisitEditor {

	static final List<String> HAQ_OPTIONS = Arrays.asList("无困难", "稍有困难", "很困难", "不能进行");
	/** HAQ 8 个维度（穿衣、起身、进食、行走、卫生、伸手、握力、日常活动）对应的题号 */
	private static final int[][] HAQ_DIMS = { { 1, 2 }, { 3, 4 }, { 5, 6, 7 }, { 8, 9 }, { 10, 11, 12 }, { 13, 14 }, { 15, 16, 17 }, { 18, 19, 20 } };
	private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
	/** 多选的文字分隔符：只按「、」或换行（选项文字本身可能带逗号，如「疼痛遇寒加重，得热通减」） */
	private static final Pattern LIST_SEP = Pattern.compile("[、\\n]");

	final List<FieldChange> changes = new ArrayList<FieldChange>();
	/** 本次DAS输入实际变化；独立于重评后数值是否变化，供保存保护源原文。 */
	boolean dasReevaluated;
	private long newRowSeq = System.currentTimeMillis();

	/**
	 * 把一个模块的提交合并进 obj（就地修改）。
	 *
	 * @return 有变化的字段 key（含清单 key、未查标记 key）
	 */
	Set<String> apply(VisitFieldDict.Module m, JSONObject obj, VisitModuleUpdate upd) {
		Set<String> changed = new HashSet<String>();
		if (upd == null) {
			return changed;
		}
		Map<String, VisitFieldDict.Field> fields = new HashMap<String, VisitFieldDict.Field>();
		for (VisitFieldDict.Group g : m.groups) {
			for (VisitFieldDict.Field f : g.fields) {
				fields.put(f.key, f);
			}
		}

		// 字段
		if (upd.getFields() != null) {
			for (Map.Entry<String, Object> e : upd.getFields().entrySet()) {
				VisitFieldDict.Field f = fields.get(e.getKey());
				if (f == null || !f.editable()) {
					continue; // 只允许改字典里的可编辑字段
				}
				Object cur = VisitJson.path(obj, f.key);
				Object val = convert(e.getValue(), f.type, cur, m.title + "·" + f.label);
				if (put(obj, f.key, cur, val, m.title + "·" + f.label)) {
					changed.add(f.key);
				}
			}
		}

		// 「未查」标记
		if (upd.getWx() != null) {
			for (Map.Entry<String, Boolean> e : upd.getWx().entrySet()) {
				VisitFieldDict.Field f = fields.get(e.getKey());
				if (f == null || e.getValue() == null) {
					continue;
				}
				String single = m.wx.get(f.key);
				JSONObject holder;
				String key;
				if (single != null) {
					holder = obj;
					key = single;
				} else if (m.wxMap != null && !"list".equals(f.type) && !f.key.startsWith("@")) {
					holder = obj.get(m.wxMap) instanceof JSONObject ? obj.getJSONObject(m.wxMap) : null;
					if (holder == null) {
						holder = new JSONObject(true);
						obj.put(m.wxMap, holder);
					}
					key = f.key;
				} else {
					continue;
				}
				Object cur = holder.get(key);
				boolean want = e.getValue();
				if (VisitJson.isTrue(cur) == want) {
					continue;
				}
				holder.put(key, cur instanceof String ? String.valueOf(want) : (Object) want);
				changes.add(new FieldChange(key, m.title + "·" + f.label + "（未查）", VisitJson.isTrue(cur) ? "是" : "否", want ? "是" : "否"));
				changed.add(key);
			}
		}

		// 清单
		if (upd.getTables() != null) {
			for (VisitFieldDict.Table t : m.tables) {
				if (!upd.getTables().containsKey(t.key)) {
					continue;
				}
				if (applyTable(m, t, obj, upd.getTables().get(t.key))) {
					changed.add(t.key);
				}
			}
		}
		return changed;
	}

	private boolean applyTable(VisitFieldDict.Module m, VisitFieldDict.Table t, JSONObject obj, List<Map<String, Object>> rows) {
		JSONArray orig = obj.get(t.key) instanceof JSONArray ? obj.getJSONArray(t.key) : new JSONArray();
		JSONArray out = new JSONArray();
		Set<Integer> kept = new HashSet<Integer>();
		String label = m.title + "·" + t.title;
		List<FieldChange> rowChanges = new ArrayList<FieldChange>();
		for (Map<String, Object> r : rows == null ? new ArrayList<Map<String, Object>>() : rows) {
			Integer idx = r.get("_row") instanceof Number ? ((Number) r.get("_row")).intValue() : null;
			boolean existing = idx != null && idx >= 0 && idx < orig.size() && orig.get(idx) instanceof JSONObject && kept.add(idx);
			JSONObject row = existing ? VisitJson.copy(orig.getJSONObject(idx)) : new JSONObject(true);
			if (!existing) {
				row.put("id", newRowSeq++);
			}
			String before = existing ? rowName(t, row) : null;
			boolean any = false;
			for (VisitFieldDict.Field c : t.editColumns) {
				if (!r.containsKey(c.key)) {
					continue;
				}
				Object cur = row.get(c.key);
				Object val = convert(r.get(c.key), c.type, cur, label + "·" + c.label);
				String curText = VisitJson.text(cur), valText = VisitJson.text(val);
				if (eq(curText, valText) || (cur == null && valText == null)) {
					continue;
				}
				row.put(c.key, valText == null ? (cur instanceof Collection ? new JSONArray() : "") : val);
				any = true;
				if (existing) {
					rowChanges.add(new FieldChange(c.key, label + "·" + before + "·" + c.label, curText, valText));
				}
			}
			if (!existing) {
				if (!any) {
					continue; // 空的新行不保存
				}
				rowChanges.add(new FieldChange(t.key, label + "（新增）", null, rowName(t, row)));
			}
			out.add(row);
		}
		for (int i = 0; i < orig.size(); i++) {
			if (!kept.contains(i) && orig.get(i) instanceof JSONObject) {
				rowChanges.add(new FieldChange(t.key, label + "（删除）", rowName(t, orig.getJSONObject(i)), null));
			}
		}
		if (VisitJson.write(out).equals(VisitJson.write(orig))) {
			return false;
		}
		obj.put(t.key, out);
		changes.addAll(rowChanges);
		return true;
	}

	/** 清单行的名称（修改记录里用）：药名 / 方剂 / 外治方法 */
	private static String rowName(VisitFieldDict.Table t, JSONObject row) {
		for (String k : new String[] { "drugName", "fj", "name", "zz" }) {
			String v = VisitJson.text(row.get(k));
			if (v != null) {
				return v;
			}
		}
		return t.title;
	}

	/**
	 * 病情评估的计算项（与老系统同一算法，业务已确认），只在相关输入有变化时重算：
	 * 肿胀 / 压痛关节数 = 部位列表个数（不计「无」）+ 手部编号个数；
	 * DAS28-CRP = 0.56×√压痛数 + 0.28×√肿胀数 + 0.36×ln(CRP+1) + 0.014×患者总体评分 + 0.96；
	 * DAS28-ESR = 0.56×√压痛数 + 0.28×√肿胀数 + 0.7×ln(血沉) + 0.014×患者总体评分；
	 * HAQ = 8 个维度各取最高分（无困难 0 / 稍有困难 1 / 很困难 2 / 不能进行 3）再平均；以上保留 2 位小数。
	 * ACR20/50/70 需对比上次随访，不重算。
	 *
	 * @return 病情评估是否因此有变化
	 */
	boolean recompute(JSONObject fzjc, JSONObject bqpg, Set<String> fzjcChanged, Set<String> bqpgChanged) {
		if (bqpg == null) {
			return false;
		}
		boolean joints = !java.util.Collections.disjoint(bqpgChanged, Arrays.asList("zzgj", "ytgj", "zzgjHand", "ytgjHand"));
		boolean commonDas = joints || bqpgChanged.contains("ztScoreByPatient");
		boolean crpChanged = commonDas || fzjcChanged.contains("cfydb");
		boolean esrChanged = commonDas || fzjcChanged.contains("xc");
		dasReevaluated = crpChanged || esrChanged;
		boolean haq = false;
		for (String k : bqpgChanged) {
			haq |= k.matches("q\\d+");
		}
		boolean any = false;
		String t = "病情评估·";
		if (joints) {
			any |= putNumber(bqpg, "result.zzgjs", new BigDecimal(count(bqpg.get("zzgj")) + count(bqpg.get("zzgjHand"))), t + "肿胀关节数（自动计算）");
			any |= putNumber(bqpg, "result.ytgjs", new BigDecimal(count(bqpg.get("ytgj")) + count(bqpg.get("ytgjHand"))), t + "压痛关节数（自动计算）");
		}
		if (crpChanged || esrChanged) {
			BigDecimal sjc = num(VisitJson.path(bqpg, "result.zzgjs")), tjc = num(VisitJson.path(bqpg, "result.ytgjs"));
			BigDecimal pg = num(bqpg.get("ztScoreByPatient"));
			BigDecimal crp = checkedLabNumber(fzjc, "cfydb"), esr = checkedLabNumber(fzjc, "xc");
			if (sjc != null && tjc != null && pg != null) {
				double base = 0.56 * Math.sqrt(tjc.doubleValue()) + 0.28 * Math.sqrt(sjc.doubleValue()) + 0.014 * pg.doubleValue();
				if (crpChanged && crp != null && crp.doubleValue() >= 0) {
					any |= putNumber(bqpg, "result.crpScore", round2(base + 0.36 * Math.log(crp.doubleValue() + 1) + 0.96), t + "DAS28-CRP（自动计算）");
				}
				if (esrChanged && esr != null && esr.doubleValue() > 0) {
					any |= putNumber(bqpg, "result.esrScore", round2(base + 0.7 * Math.log(esr.doubleValue())), t + "DAS28-ESR（自动计算）");
				}
			}
			if (crpChanged && (sjc == null || tjc == null || pg == null || crp == null || crp.signum() < 0)) {
				any |= invalidate(bqpg, "result.crpScore", t + "DAS28-CRP（自动计算）");
			}
			if (esrChanged && (sjc == null || tjc == null || pg == null || esr == null || esr.signum() <= 0)) {
				any |= invalidate(bqpg, "result.esrScore", t + "DAS28-ESR（自动计算）");
			}
		}
		if (haq) {
			double sum = 0;
			boolean complete = true;
			for (int[] dim : HAQ_DIMS) {
				int max = -1;
				for (int q : dim) {
					int s = HAQ_OPTIONS.indexOf(VisitJson.text(bqpg.get("q" + q)));
					max = Math.max(max, s);
				}
				if (max < 0) {
					complete = false; // 有维度一题都没答，不算
					break;
				}
				sum += max;
			}
			if (complete) {
				BigDecimal score = round2(sum / HAQ_DIMS.length);
				any |= putNumber(bqpg, "hqaScore", score, t + "HAQ 健康评分（自动计算）");
				if (bqpg.get("result") instanceof JSONObject && bqpg.getJSONObject("result").containsKey("hqaScore")) {
					any |= putNumber(bqpg, "result.hqaScore", score, null); // 与 hqaScore 同一个值，不重复记修改记录
				}
			} else {
				any |= invalidate(bqpg, "hqaScore", t + "HAQ 健康评分（自动计算）");
				any |= invalidate(bqpg, "result.hqaScore", null);
			}
		}
		return any;
	}

	private static BigDecimal checkedLabNumber(JSONObject lab, String key) {
		if (lab == null) return null;
		Object wx = lab.get("xcgWx");
		return wx instanceof JSONObject && VisitJson.isTrue(((JSONObject) wx).get(key)) ? null : num(lab.get(key));
	}

	/** 失效保留既存键及其空值类型；不创建缺失的派生字段。 */
	private boolean invalidate(JSONObject obj, String key, String label) {
		int dot = key.indexOf('.');
		JSONObject holder = dot < 0 ? obj : (obj.get(key.substring(0, dot)) instanceof JSONObject ? obj.getJSONObject(key.substring(0, dot)) : null);
		String leaf = dot < 0 ? key : key.substring(dot + 1);
		if (holder == null || !holder.containsKey(leaf)) return false;
		Object cur = holder.get(leaf), empty = cur instanceof String ? "" : null;
		if (cur == null || "".equals(cur)) return false;
		holder.put(leaf, empty);
		if (label != null) changes.add(new FieldChange(key, label, VisitJson.text(cur), null));
		return true;
	}

	/** 写入数字，保持原值类型（原来是字符串就写字符串）；值没变返回 false */
	private boolean putNumber(JSONObject obj, String key, BigDecimal v, String label) {
		Object cur = VisitJson.path(obj, key);
		BigDecimal old = num(cur);
		if (old != null && old.compareTo(v) == 0) {
			return false;
		}
		VisitJson.setPath(obj, key, cur instanceof String ? v.toPlainString() : (Object) v);
		if (label != null) {
			changes.add(new FieldChange(key, label, VisitJson.text(cur), v.toPlainString()));
		}
		return true;
	}

	/** 普通字段写入：值没变不写；清空时写空串 / 空数组（不删字段） */
	private boolean put(JSONObject obj, String key, Object cur, Object val, String label) {
		String curText = VisitJson.text(cur), valText = VisitJson.text(val);
		if (eq(curText, valText) || (cur == null && valText == null)) {
			return false;
		}
		VisitJson.setPath(obj, key, valText == null ? (cur instanceof Collection ? new JSONArray() : "") : val);
		changes.add(new FieldChange(key, label, curText, valText));
		return true;
	}

	/** 表单值 → 老系统 JSON 值，保持原值类型；不合法时抛 IllegalArgumentException（提示给医生） */
	static Object convert(Object input, String type, Object cur, String label) {
		if ("list".equals(type)) {
			List<String> items = new ArrayList<String>();
			if (input instanceof Collection) {
				for (Object o : (Collection<?>) input) {
					String s = o == null ? null : VisitJson.blankToNull(String.valueOf(o));
					if (s != null) {
						items.add(s);
					}
				}
			} else if (input != null) {
				for (String s : LIST_SEP.split(String.valueOf(input))) {
					if (VisitJson.blankToNull(s) != null) {
						items.add(s.trim());
					}
				}
			}
			if (items.isEmpty()) {
				return null;
			}
			return cur instanceof String ? String.join("、", items) : new JSONArray(new ArrayList<Object>(items));
		}
		String s = input == null ? null : VisitJson.blankToNull(String.valueOf(input));
		if (s == null) {
			return null;
		}
		if ("date".equals(type) && !DATE.matcher(s).matches() && !s.equals(VisitJson.text(cur))) {
			throw new IllegalArgumentException(label + "：日期格式应为 yyyy-MM-dd");
		}
		if ("haq".equals(type) && !HAQ_OPTIONS.contains(s) && !s.equals(VisitJson.text(cur))) {
			throw new IllegalArgumentException(label + "：只能选 " + String.join(" / ", HAQ_OPTIONS));
		}
		if ("number".equals(type) || cur instanceof Number) {
			BigDecimal n = num(s);
			if (n == null) {
				if ("number".equals(type)) {
					throw new IllegalArgumentException(label + "：应为数字");
				}
				return s;
			}
			return cur instanceof String ? s : (Object) n;
		}
		return s;
	}

	private static int count(Object list) {
		int n = 0;
		if (list instanceof Collection) {
			for (Object o : (Collection<?>) list) {
				String s = o == null ? null : VisitJson.blankToNull(String.valueOf(o));
				if (s != null && !"无".equals(s)) {
					n++;
				}
			}
		}
		return n;
	}

	static BigDecimal num(Object v) {
		String s = v == null ? null : VisitJson.blankToNull(String.valueOf(v));
		if (s == null) {
			return null;
		}
		try {
			return new BigDecimal(s);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static BigDecimal round2(double d) {
		return new BigDecimal(d).setScale(2, RoundingMode.HALF_UP);
	}

	private static boolean eq(String a, String b) {
		return a == null ? b == null : a.equals(b);
	}
}
