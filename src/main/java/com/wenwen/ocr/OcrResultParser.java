package com.wenwen.ocr;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

/**
 * 百度 OCR 结果 → 新建患者 / 基线访视的草稿（纯计算，不读写数据库）。
 * 来源：医疗检验报告单识别（words_result.CommonData 表头、words_result.Item 检验项目）+ 通用文字识别（逐行文字）。
 * 检验项目按名称 / 缩写对到辅助检查字段（fzjc）；表头和文字里按关键字取姓名、性别、年龄、身份证号、手机号等。
 * 识别不出的不猜，留空由医生补；所有结果都要医生在表单里核对后才保存。
 */
public final class OcrResultParser {

	/** 检验项目 → 辅助检查字段：字段、名称关键字（先写更具体的，如「超敏C反应蛋白」在「C反应蛋白」前） */
	private static final String[][] LAB = {
			{ "cmcfydb", "超敏C反应蛋白", "超敏c反应蛋白", "HSCRP", "HS-CRP", "高敏C反应蛋白" },
			{ "cfydb", "C反应蛋白", "c反应蛋白", "CRP" },
			{ "xc", "血沉", "红细胞沉降率", "ESR" },
			{ "bxb", "白细胞计数", "白细胞", "WBC", "LEU" },
			{ "xhdb", "血红蛋白", "HGB", "HB" },
			{ "xxb", "血小板计数", "血小板", "PLT" },
			{ "rbc", "红细胞计数", "红细胞", "RBC" },
			{ "lfsyz", "类风湿因子", "RF" },
			{ "kccpkt", "抗环瓜氨酸肽", "抗CCP", "CCP", "ACPA" },
			{ "myqdb", "免疫球蛋白G", "IGG" },
			{ "lga", "免疫球蛋白A", "IGA" },
			{ "lgm", "免疫球蛋白M", "IGM" },
			{ "alt", "谷丙转氨酶", "丙氨酸氨基转移酶", "ALT", "GPT" },
			{ "ast", "谷草转氨酶", "天门冬氨酸氨基转移酶", "天冬氨酸氨基转移酶", "AST", "GOT" },
			{ "ggt", "谷氨酰转移酶", "谷氨酰转肽酶", "谷氨酰转氨酶", "GGT", "Γ-GT" },
			{ "zjdhs", "直接胆红素", "结合胆红素", "DBIL" },
			{ "urea", "尿素氮", "尿素", "BUN", "UREA" },
			{ "crea", "肌酐", "CREA", "CR" },
			{ "tc", "总胆固醇", "TC", "CHOL" },
			{ "tg", "甘油三酯", "TG" },
			{ "hdl_c", "高密度脂蛋白", "HDL" },
			{ "ldl_c", "低密度脂蛋白", "LDL" },
			{ "xt", "葡萄糖", "血糖", "GLU" },
			{ "d_ent", "D-二聚体", "D二聚体", "D-DIMER", "DD" },
			{ "hcy", "同型半胱氨酸", "HCY" },
			{ "probnp", "NT-PROBNP", "N末端B型钠尿肽", "N端脑钠肽前体" },
			{ "bnp", "B型钠尿肽", "脑钠肽", "BNP" },
			{ "ctnt", "肌钙蛋白", "CTNT", "CTNI", "TNT", "TNI" },
			{ "ckmb", "肌酸激酶同工酶", "CK-MB", "CKMB" },
			{ "ck", "肌酸激酶", "CK" },
			{ "ldh", "乳酸脱氢酶", "LDH" },
			{ "ahbdh", "羟丁酸脱氢酶", "HBDH" },
			{ "ro52", "RO-52", "RO52" },
			{ "ssa", "抗SSA", "SS-A", "SSA" },
			{ "ssb", "抗SSB", "SS-B", "SSB" },
			{ "ana", "抗核抗体", "ANA" } };
	/** 定性结果（阳性 / 阴性）的字段：存「阳性」「阴性」 */
	private static final List<String> QUALITATIVE = java.util.Arrays.asList("ana", "ssa", "ssb", "ro52");

	/** 名称末尾方括号 / 圆括号里的英文缩写，如「★白细胞计数[WBC]」「淋巴细胞百分率[LYMPH%]」 */
	private static final Pattern BRACKET_ABBR = Pattern.compile("[\\[［【(（]\\s*([A-Za-z][A-Za-z0-9#%\\-.]*)\\s*[\\]］】)）]\\s*$");
	/** 派生指标的字样：名称含这些的不按名称对应（平均红细胞体积、红细胞压积、网织红细胞、淋巴细胞百分率…） */
	private static final String[] DERIVED = { "平均", "压积", "分布", "宽度", "百分", "比率", "比例", "网织", "未成熟", "有核" };
	/** 血常规计数字段：尿常规、便常规、积液里的同名项目不算 */
	private static final List<String> BLOOD_COUNT = java.util.Arrays.asList("bxb", "xhdb", "xxb", "rbc");
	/** 比较名称时忽略的字符：空格、括号、连字符 */
	private static final Pattern STRIP = Pattern.compile("[\\s（）()\\-－—·]");
	private static final Pattern NUM = Pattern.compile("^(?:<=|>=|<|>|≤|≥|＜|＞|=)?\\s*[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?$");
	private static final Pattern DATE = Pattern.compile("(\\d{4})\\s*[-年./]\\s*(\\d{1,2})\\s*[-月./]\\s*(\\d{1,2})");
	private static final Pattern CARD = Pattern.compile("(?<!\\d)(\\d{17}[\\dXx])(?!\\d)");
	private static final Pattern MOBILE = Pattern.compile("(?<!\\d)(1[3-9]\\d{9})(?!\\d)");

	private OcrResultParser() {
	}

	/**
	 * @param medical 医疗检验报告单识别的原始结果，可为 null
	 * @param text 通用文字识别的原始结果，可为 null
	 */
	public static Map<String, Object> parse(JSONObject medical, JSONObject text) {
		return parse(medical, text, new java.util.HashMap<String, String>());
	}

	/**
	 * @param aliases 医生对照过的叫法（规范化后的名称 → 字段，见 alias()），优先于内置同义词
	 */
	public static Map<String, Object> parse(JSONObject medical, JSONObject text, Map<String, String> aliases) {
		Map<String, String> basic = new LinkedHashMap<String, String>();
		Map<String, String> fzjc = new LinkedHashMap<String, String>();
		List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
		List<String> lines = new ArrayList<String>();
		String visitDate = null;

		// 1. 检验报告单：表头（姓名、性别、年龄、采样 / 报告时间…）和检验项目
		JSONObject wr = medical == null ? null : medical.getJSONObject("words_result");
		if (wr != null) {
			JSONArray common = wr.getJSONArray("CommonData");
			for (int i = 0; common != null && i < common.size(); i++) {
				JSONObject c = common.getJSONObject(i);
				String k = str(c.getString("word_name")), v = str(c.getString("word"));
				if (k == null || v == null) {
					continue;
				}
				lines.add(k + "：" + v);
				if (k.contains("姓名")) basic.putIfAbsent("name", v);
				else if (k.contains("性别")) putGender(basic, v);
				else if (k.contains("年龄")) putAge(basic, v);
				else if (visitDate == null && (k.contains("时间") || k.contains("日期"))) visitDate = date(v);
			}
			JSONArray rows = wr.getJSONArray("Item");
			for (int i = 0; rows != null && i < rows.size(); i++) {
				Object row = rows.get(i);
				String name = null, abbr = null, result = null, unit = null;
				JSONArray cells = row instanceof JSONArray ? (JSONArray) row : null;
				for (int j = 0; cells != null && j < cells.size(); j++) {
					JSONObject c = cells.getJSONObject(j);
					String k = str(c.getString("word_name")), v = str(c.getString("word"));
					if (k == null || v == null) continue;
					if (k.contains("名称") || k.equals("项目")) name = v;
					else if (k.contains("缩写") || k.contains("代号") || k.contains("英文")) abbr = v;
					else if (k.contains("结果") && !k.contains("提示")) result = v;
					else if (k.contains("单位")) unit = v;
				}
				if (name == null && abbr == null) continue;
				lines.add((name == null ? "" : name) + (abbr == null ? "" : "(" + abbr + ")") + " " + (result == null ? "" : result) + (unit == null ? "" : " " + unit));
				addItem(fzjc, items, name, abbr, result, unit, aliases);
			}
		}

		// 2. 通用文字识别：逐行文字
		JSONArray words = text == null ? null : text.getJSONArray("words_result");
		List<String> plain = new ArrayList<String>();
		for (int i = 0; words != null && i < words.size(); i++) {
			String w = str(words.getJSONObject(i).getString("words"));
			if (w != null) {
				plain.add(w);
			}
		}
		lines.addAll(plain);
		// 检验报告单没识别出来的项目，从逐行文字里找「名称 数值」
		for (String w : plain) {
			String key = labKey(w, null);
			if (key == null || fzjc.containsKey(key)) continue;
			int end = labelEnd(w, key);
			String name = end > 0 ? w.substring(0, end) : w;
			String rest = end > 0 ? w.substring(end).replaceFirst("^[^\\d<>≤≥＜＞阴阳+-]*", "") : "";
			addItem(fzjc, items, name, null, rest, null, aliases);
		}

		// 3. 姓名、性别、年龄、身份证号、手机号等：表头没有的从文字里找
		String all = String.join("\n", lines);
		find(basic, "name", all, "姓\\s*名[:：]?\\s*([\\u4e00-\\u9fa5·]{2,6})");
		if (!basic.containsKey("gender")) {
			Matcher m = Pattern.compile("性\\s*别[:：]?\\s*([男女])").matcher(all);
			if (m.find()) putGender(basic, m.group(1));
		}
		if (!basic.containsKey("age")) {
			Matcher m = Pattern.compile("年\\s*龄[:：]?\\s*(\\d{1,3})").matcher(all);
			if (m.find()) basic.put("age", m.group(1));
		}
		Matcher card = CARD.matcher(all);
		if (card.find()) basic.put("cardNo", card.group(1).toUpperCase());
		Matcher mobile = MOBILE.matcher(all.replace(card.find(0) ? card.group(1) : "\u0000", ""));
		if (mobile.find()) basic.put("mobile", mobile.group(1));
		find(basic, "nation", all, "民\\s*族[:：]?\\s*([\\u4e00-\\u9fa5]{1,6}族)");
		Matcher marry = Pattern.compile("婚\\s*(?:姻|史|否)[^\\n:：]*[:：]?\\s*(未婚|已婚|离异|离婚|丧偶)").matcher(all);
		if (marry.find()) {
			String v = marry.group(1);
			basic.put("marry", "未婚".equals(v) ? "0" : "已婚".equals(v) ? "1" : "丧偶".equals(v) ? "3" : "2");
		}
		find(basic, "height", all, "身\\s*高[:：]?\\s*(\\d{2,3}(?:\\.\\d)?)\\s*(?:cm|CM|厘米)?");
		find(basic, "weight", all, "体\\s*重[:：]?\\s*(\\d{2,3}(?:\\.\\d)?)\\s*(?:kg|KG|公斤)?");
		Matcher happen = Pattern.compile("(?:发病|起病)(?:时间|日期)?[:：]?\\s*(\\d{4}\\s*[-年./]\\s*\\d{1,2}\\s*[-月./]\\s*\\d{1,2})").matcher(all);
		if (happen.find()) basic.put("happenDate", date(happen.group(1)));
		Matcher confirm = Pattern.compile("确诊(?:时间|日期)?[:：]?\\s*(\\d{4}\\s*[-年./]\\s*\\d{1,2}\\s*[-月./]\\s*\\d{1,2})").matcher(all);
		if (confirm.find()) basic.put("confirmDate", date(confirm.group(1)));
		if (visitDate == null) {
			Matcher d = Pattern.compile("(?:报告|采样|采集|送检|检验|就诊|门诊)(?:时间|日期)[:：]?\\s*(\\d{4}\\s*[-年./]\\s*\\d{1,2}\\s*[-月./]\\s*\\d{1,2})").matcher(all);
			if (d.find()) visitDate = date(d.group(1));
		}

		// 4. 中药处方：「药名 + 剂量g」≥ 3 味时当作处方，填治疗方案 · 中药饮片；处方行不再当检验项目显示
		Map<String, Object> prescription = prescription(lines);
		if (prescription != null) {
			for (java.util.Iterator<Map<String, Object>> it = items.iterator(); it.hasNext();) {
				Map<String, Object> item = it.next();
				if (item.get("field") == null && (HERB.matcher(String.valueOf(item.get("name"))).find() || HERB.matcher(String.valueOf(item.get("result"))).find())) it.remove();
			}
		}

		List<String> warnings = new ArrayList<String>();
		if (basic.isEmpty() && fzjc.isEmpty() && prescription == null) warnings.add("没有识别出可用的信息，请换一张更清晰的图片，或手动录入");
		if (!basic.containsKey("name")) warnings.add("没有识别出姓名");
		if (!basic.containsKey("cardNo")) warnings.add("没有识别出身份证号，新建患者时无法查重，请手动补上");
		Map<String, Object> out = new LinkedHashMap<String, Object>();
		out.put("basic", basic);
		out.put("visitDate", visitDate);
		out.put("fzjc", fzjc);
		out.put("items", items);
		out.put("prescription", prescription);
		out.put("lines", lines);
		out.put("warnings", warnings);
		return out;
	}

	private static void addItem(Map<String, String> fzjc, List<Map<String, Object>> items, String name, String abbr, String result, String unit, Map<String, String> aliases) {
		String learned = aliases.get(alias(name, abbr));
		boolean conflict = mappingConflict(name, abbr);
		String key = conflict || "blocked".equals(matchParts(name, abbr)[2]) ? null : learned != null ? learned : labKey(name, abbr);
		String value = null;
		if (key != null && result != null) value = value(key, result);
		Map<String, Object> it = new LinkedHashMap<String, Object>();
		it.put("name", name != null ? name : abbr);
		it.put("abbr", abbr);
		it.put("result", result);
		it.put("unit", unit);
		it.put("alias", alias(name, abbr));
		it.put("field", key);
		it.put("matchedBy", key == null ? null : learned != null ? "learned" : "builtin");
		boolean invalidResult = key != null && result != null && value == null;
		it.put("needsConfirmation", conflict || invalidResult);
		if (conflict) it.put("mappingWarning", "项目名称与缩写冲突，请对照原图手动确认对应字段");
		if (invalidResult) it.put("mappingWarning", "识别结果不是完整可用值，请对照原图后手动填写；未自动截取数字");
		it.put("value", value);
		items.add(it);
		if (value != null && !fzjc.containsKey(key)) {
			fzjc.put(key, value);
		}
	}

	/** 处方里的一味药：2～6 个汉字的药名 + 剂量（g / 克），如「墨旱莲15g」「山药 15 克」 */
	private static final Pattern HERB = Pattern.compile("([\\u4e00-\\u9fa5]{2,6})\\s*(\\d+(?:\\.\\d+)?)\\s*(?:g|G|克)(?![A-Za-z])");
	/** 药名前常带的字样（「处方黄芪30g」「中药：当归15g」），去掉 */
	private static final Pattern HERB_PREFIX = Pattern.compile("^(?:中药处方|中药饮片|中药|处方|方药|药物|饮片|用药|组成|加减|加|减)");

	/**
	 * 中药处方：从全部识别文字里取「药名 + 剂量」，≥ 3 味才算处方；
	 * 证型 / 方剂只在文字里明确写了系统选项（8 个证型、各证型下的方剂）时才填，不按药味猜；
	 * 只识别出方剂、且该方剂只属于一个证型时，带出证型。返回 null = 不是处方。
	 */
	static Map<String, Object> prescription(List<String> lines) {
		String all = String.join("\n", lines);
		List<Map<String, Object>> herbs = new ArrayList<Map<String, Object>>();
		java.util.Set<String> seen = new java.util.HashSet<String>();
		Matcher m = HERB.matcher(all);
		while (m.find()) {
			String name = HERB_PREFIX.matcher(m.group(1)).replaceFirst("");
			if (name.length() < 1 || !seen.add(name)) continue;
			Map<String, Object> h = new LinkedHashMap<String, Object>();
			h.put("name", name);
			h.put("dose", m.group(2) + "g");
			h.put("text", name + m.group(2) + "g");
			herbs.add(h);
		}
		if (herbs.size() < 3) return null;
		String zz = null, fj = null;
		for (String syndrome : com.wenwen.util.VisitFieldDict.SYNDROMES) {
			if (all.contains(syndrome) || all.contains(syndrome.substring(0, syndrome.length() - 1))) { zz = syndrome; break; }
		}
		Map<String, List<String>> fangji = com.wenwen.util.VisitFieldDict.OPTION_SETS.get("fangji");
		java.util.Set<String> owners = new java.util.LinkedHashSet<String>();
		if (fangji != null) {
			for (Map.Entry<String, List<String>> e : fangji.entrySet()) {
				for (String f : e.getValue()) {
					if (all.contains(f) && (fj == null || f.length() > fj.length())) fj = f;
				}
			}
			for (Map.Entry<String, List<String>> e : fangji.entrySet()) if (fj != null && e.getValue().contains(fj)) owners.add(e.getKey());
		}
		List<String> notes = new ArrayList<String>();
		if (zz == null && owners.size() == 1) zz = owners.iterator().next();
		else if (zz != null && fj != null && !owners.contains(zz)) { notes.add("识别出的方剂「" + fj + "」不在证型「" + zz + "」的方剂选项里，方剂未填，请手动选择"); fj = null; }
		if (zz == null) notes.add("没有识别出证型，请手动选择");
		Map<String, Object> out = new LinkedHashMap<String, Object>();
		out.put("herbs", herbs);
		out.put("zz", zz);
		out.put("fj", fj);
		out.put("notes", notes);
		return out;
	}

	/** 叫法对照表的键：名称 + 缩写规范化（去空格、括号、连字符，转大写） */
	public static String alias(String name, String abbr) {
		String n = norm(name), a = norm(abbr);
		return (n == null ? "" : n) + (a == null || a.isEmpty() ? "" : "|" + a);
	}

	/** 只有完整有限数值或明确的定性结果才能自动填表；保比较符，不丢弃尾随文字。 */
	public static String value(String field, String raw) {
		if (field == null || raw == null) return null;
		if (QUALITATIVE.contains(field)) {
			String text = raw.trim();
			return text.matches("阳性|\\+{1,4}") ? "阳性" : text.matches("阴性|[-—]") ? "阴性" : "未做".equals(text) ? "未做" : null;
		}
		String text = raw.trim();
		if ("lfsyz".equals(field) || "kccpkt".equals(field)) text = text.replaceFirst("(?:IU/mL|U/mL)$", "").trim();
		Matcher m = NUM.matcher(text);
		if (!m.matches()) return null;
		String value = m.group().replaceAll("\\s", "");
		try {
			String number = value.replaceFirst("^(?:<=|>=|<|>|≤|≥|＜|＞|=)", "");
			return Double.isFinite(new java.math.BigDecimal(number).doubleValue()) ? (text.equals(raw.trim()) ? value : raw.trim()) : null;
		} catch (NumberFormatException error) { return null; }
	}

	/**
	 * 名称 / 缩写 → 字段；对不上返回 null。
	 * 1. 有缩写（单独一列，或名称末尾方括号里的，如「★白细胞计数[WBC]」）且是已知缩写：按缩写定；
	 * 2. 否则按名称：名称里含同义词，且不是派生指标（平均、压积、分布宽度、百分率、网织…，如 MCV、HCT、RET 不能算成红细胞）。
	 */
	static String labKey(String name, String abbr) {
		String[] parts = matchParts(name, abbr);
		if ("blocked".equals(parts[2]) || conflict(parts)) return null;
		return parts[1] != null ? parts[1] : parts[0];
	}

	private static boolean mappingConflict(String name, String abbr) { return conflict(matchParts(name, abbr)); }
	private static boolean conflict(String[] p) { return p[0] != null && p[1] != null && !p[0].equals(p[1]); }

	private static String[] matchParts(String name, String abbr) {
		String a = norm(abbr), core = name;
		if (name != null) {
			Matcher m = BRACKET_ABBR.matcher(name.trim());
			if (m.find()) {
				if (a == null || a.isEmpty()) a = norm(m.group(1));
				core = name.trim().substring(0, m.start());
			}
			core = core.replaceFirst("^[\\s\\d.、★☆*※#]+", "");
		}
		String n = norm(core), byName = null, byAbbr = null;
		if (n != null) for (String w : DERIVED) if (n.contains(w)) return new String[] { null, null, "blocked" };
		for (String[] row : LAB) {
			for (int i = 1; i < row.length; i++) {
				String p = norm(row[i]);
				boolean isAbbr = p.matches("[A-Z0-9Γ\\-]+");
				if (byAbbr == null && isAbbr && p.equals(a)) byAbbr = row[0];
				if (byName == null && n != null && !n.isEmpty()) {
					if (isAbbr ? Pattern.compile("(?<![A-Z])" + Pattern.quote(p) + "(?![A-Z0-9#%])").matcher(n).find() : n.contains(p)) byName = row[0];
				}
			}
		}
		boolean otherSample = n != null && (n.contains("尿") || n.contains("粪") || n.contains("便") || n.contains("介素") || n.contains("胸水") || n.contains("腹水"));
		if (otherSample && (BLOOD_COUNT.contains(byName) || BLOOD_COUNT.contains(byAbbr))) return new String[] { null, null, "blocked" };
		return new String[] { byName, byAbbr, null };
	}

	/** 一行文字里项目名称结束的位置（原文下标）；名称里的空格、括号、连字符不影响 */
	private static int labelEnd(String line, String key) {
		String n = norm(line);
		for (String[] row : LAB) {
			if (!row[0].equals(key)) continue;
			for (int i = 1; i < row.length; i++) {
				String p = norm(row[i]);
				int at = n.indexOf(p);
				if (at < 0) continue;
				int want = at + p.length(), kept = 0;
				for (int k = 0; k < line.length(); k++) {
					if (!STRIP.matcher(String.valueOf(line.charAt(k))).matches() && ++kept == want) return k + 1;
				}
			}
		}
		return -1;
	}

	private static String norm(String s) {
		return s == null ? null : STRIP.matcher(s).replaceAll("").replace('＿', '_').toUpperCase();
	}

	private static void find(Map<String, String> basic, String key, String text, String regex) {
		if (basic.containsKey(key)) return;
		Matcher m = Pattern.compile(regex).matcher(text);
		if (m.find()) basic.put(key, m.group(1));
	}

	private static void putGender(Map<String, String> basic, String v) {
		if (v.contains("男")) basic.putIfAbsent("gender", "1");
		else if (v.contains("女")) basic.putIfAbsent("gender", "2");
	}

	private static void putAge(Map<String, String> basic, String v) {
		Matcher m = Pattern.compile("(\\d{1,3})").matcher(v);
		if (m.find()) basic.putIfAbsent("age", m.group(1));
	}

	static String date(String v) {
		Matcher m = v == null ? null : DATE.matcher(v);
		if (m == null || !m.find()) return null;
		return String.format("%s-%02d-%02d", m.group(1), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
	}

	private static String str(String s) {
		return s == null || s.trim().isEmpty() ? null : s.trim();
	}
}
