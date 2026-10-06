package com.wenwen.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 疾病分型（血清学）：按 RF / 抗CCP 化验值判定阴阳性，分级参照 ACR/EULAR 2010：
 * 值 ≤ ULN 阴性；ULN &lt; 值 ≤ 3×ULN 低滴度阳性；值 &gt; 3×ULN 高滴度阳性。
 * 老数据只存数值、不存各化验室的参考范围，ULN 统一用配置值。
 */
public final class SerologyUtil {

	public static final String NEGATIVE = "negative";
	public static final String LOW_POSITIVE = "low_positive";
	public static final String HIGH_POSITIVE = "high_positive";
	public static final String UNTESTED = "untested";

	private static final Pattern NUMBER = Pattern.compile("^([<>＜＞≤≥]=?)?\\s*(\\d+(?:\\.\\d+)?)");

	private SerologyUtil() {
	}

	/**
	 * 一次化验原值 → 状态；空值、看不懂的文字返回 null（调用方接着看更早的随访）。
	 * 数字前带「&lt;」（低于检测下限）按阴性；只写「阳性 / +」没有数值的，分不出滴度，按低滴度阳性。
	 */
	public static String classify(String raw, double uln) {
		if (raw == null) {
			return null;
		}
		String s = raw.trim().replace(" ", "");
		if (s.isEmpty() || "null".equalsIgnoreCase(s)) {
			return null;
		}
		Matcher m = NUMBER.matcher(s);
		if (m.find()) {
			String op = m.group(1);
			double v = Double.parseDouble(m.group(2));
			if (op != null && (op.startsWith("<") || op.startsWith("＜") || op.startsWith("≤"))) {
				return NEGATIVE;
			}
			return v <= uln ? NEGATIVE : v <= 3 * uln ? LOW_POSITIVE : HIGH_POSITIVE;
		}
		if (s.contains("阴") || "-".equals(s) || "—".equals(s)) {
			return NEGATIVE;
		}
		if (s.contains("阳") || s.startsWith("+")) {
			return LOW_POSITIVE;
		}
		return null;
	}

	public static String label(String status) {
		if (NEGATIVE.equals(status)) {
			return "阴性";
		}
		if (LOW_POSITIVE.equals(status)) {
			return "低滴度阳性";
		}
		if (HIGH_POSITIVE.equals(status)) {
			return "高滴度阳性";
		}
		return "未检测";
	}

	public static boolean isPositive(String status) {
		return LOW_POSITIVE.equals(status) || HIGH_POSITIVE.equals(status);
	}

	/** 疾病分型：RF、抗CCP 任一阳性 → 血清阳性；做过且都不阳性 → 血清阴性；都未检测 → null */
	public static String subtype(String rfStatus, String ccpStatus) {
		if (isPositive(rfStatus) || isPositive(ccpStatus)) {
			return "血清阳性";
		}
		if (NEGATIVE.equals(rfStatus) || NEGATIVE.equals(ccpStatus)) {
			return "血清阴性";
		}
		return null;
	}
}
