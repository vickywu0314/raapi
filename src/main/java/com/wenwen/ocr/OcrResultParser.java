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
	private static final Pattern NUM = Pattern.compile("^[<>≤≥＜＞]?=?\\s*-?\\d+(\\.\\d+)?");
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

		List<String> warnings = new ArrayList<String>();
		if (basic.isEmpty() && fzjc.isEmpty()) warnings.add("没有识别出可用的信息，请换一张更清晰的图片，或手动录入");
		if (!basic.containsKey("name")) warnings.add("没有识别出姓名");
		if (!basic.containsKey("cardNo")) warnings.add("没有识别出身份证号，新建患者时无法查重，请手动补上");
		Map<String, Object> out = new LinkedHashMap<String, Object>();
		out.put("basic", basic);
		out.put("visitDate", visitDate);
		out.put("fzjc", fzjc);
		out.put("items", items);
		out.put("lines", lines);
		out.put("warnings", warnings);
		return out;
	}

	private static void addItem(Map<String, String> fzjc, List<Map<String, Object>> items, String name, String abbr, String result, String unit, Map<String, String> aliases) {
		String learned = aliases.get(alias(name, abbr));
		String key = learned != null ? learned : labKey(name, abbr);
		String value = null;
		if (key != null && result != null) {
			if (QUALITATIVE.contains(key)) {
				value = result.contains("阳") || result.trim().startsWith("+") ? "阳性" : result.contains("阴") || result.trim().startsWith("-") ? "阴性" : null;
			} else {
				Matcher m = NUM.matcher(result.trim());
				value = m.find() ? m.group().replaceAll("\\s", "") : null;
			}
		}
		Map<String, Object> it = new LinkedHashMap<String, Object>();
		it.put("name", name != null ? name : abbr);
		it.put("abbr", abbr);
		it.put("result", result);
		it.put("unit", unit);
		it.put("alias", alias(name, abbr));
		it.put("field", key);
		it.put("matchedBy", key == null ? null : learned != null ? "learned" : "builtin");
		it.put("value", value != null ? value : result == null ? null : result.trim());
		items.add(it);
		if (value != null && !fzjc.containsKey(key)) {
			fzjc.put(key, value);
		}
	}

	/** 叫法对照表的键：名称 + 缩写规范化（去空格、括号、连字符，转大写） */
	public static String alias(String name, String abbr) {
		String n = norm(name), a = norm(abbr);
		return (n == null ? "" : n) + (a == null || a.isEmpty() ? "" : "|" + a);
	}

	/** 质控 / 填表时字段的取值：数值字段取开头的数字（含 <、>），定性字段取阳性 / 阴性 */
	public static String value(String field, String raw) {
		if (field == null || raw == null) return null;
		if (QUALITATIVE.contains(field)) {
			return raw.contains("阳") || raw.trim().startsWith("+") ? "阳性" : raw.contains("阴") || raw.trim().startsWith("-") ? "阴性" : null;
		}
		Matcher m = NUM.matcher(raw.trim());
		return m.find() ? m.group().replaceAll("\\s", "") : null;
	}

	/**
	 * 名称 / 缩写 → 字段；对不上返回 null。
	 * 1. 有缩写（单独一列，或名称末尾方括号里的，如「★白细胞计数[WBC]」）且是已知缩写：按缩写定；
	 * 2. 否则按名称：名称里含同义词，且不是派生指标（平均、压积、分布宽度、百分率、网织…，如 MCV、HCT、RET 不能算成红细胞）。
	 */
	static String labKey(String name, String abbr) {
		String a = norm(abbr);
		String core = name;
		if (name != null) {
			Matcher m = BRACKET_ABBR.matcher(name.trim());
			if (m.find()) {
				if (a == null || a.isEmpty()) a = norm(m.group(1));
				core = name.trim().substring(0, m.start());
			}
			core = core.replaceFirst("^[\\s\\d.、★☆*※#]+", "");
		}
		if (a != null && !a.isEmpty()) {
			for (String[] row : LAB) {
				for (int i = 1; i < row.length; i++) {
					String p = norm(row[i]);
					if (p.matches("[A-Z0-9Γ\\-]+") && p.equals(a)) return row[0];
				}
			}
		}
		String n = norm(core);
		if (n == null || n.isEmpty()) return null;
		for (String w : DERIVED) {
			if (n.contains(w)) return null;
		}
		for (String[] row : LAB) {
			if (BLOOD_COUNT.contains(row[0]) && (n.contains("尿") || n.contains("粪") || n.contains("便") || n.contains("介素") || n.contains("胸水") || n.contains("腹水"))) continue;
			for (int i = 1; i < row.length; i++) {
				String p = norm(row[i]);
				boolean isAbbr = p.matches("[A-Z0-9Γ\\-]+");
				if (isAbbr) {
					if (Pattern.compile("(?<![A-Z])" + Pattern.quote(p) + "(?![A-Z0-9#%])").matcher(n).find()) return row[0];
				} else if (n.contains(p)) {
					return row[0];
				}
			}
		}
		return null;
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
