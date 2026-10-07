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
	public static final String POSITIVE = "positive";
	public static final String UNTESTED = "untested";

    private static final Pattern NUMBER = Pattern.compile("^(<=|>=|<|>|≤|≥)?\\s*(\\d+(?:\\.\\d+)?)(?:\\s*(?:IU/mL|U/mL))?$");
    private SerologyUtil() { }

    /** 全匹配；有界文本仅在整个可能区间足以证明类别时接纳。 */
    public static String classify(String raw, double uln) {
        if (raw == null || !Double.isFinite(uln) || uln <= 0) return null;
        String s=raw.trim().replace('＜','<').replace('＞','>');
        if ("阴性".equals(s) || "-".equals(s) || "—".equals(s)) return NEGATIVE;
        if ("阳性".equals(s)) return LOW_POSITIVE; // 历史明确文本兼容
        if (s.matches("\\+{1,4}")) return POSITIVE;
        Matcher m=NUMBER.matcher(s);
        if (!m.matches()) return null;
        java.math.BigDecimal v=new java.math.BigDecimal(m.group(2)), limit=java.math.BigDecimal.valueOf(uln), high=limit.multiply(new java.math.BigDecimal("3"));
        String op=m.group(1);
        if (op == null) return v.compareTo(limit)<=0 ? NEGATIVE : v.compareTo(high)<=0 ? LOW_POSITIVE : HIGH_POSITIVE;
        if ("<".equals(op) || "<=".equals(op) || "≤".equals(op)) return v.compareTo(limit)<=0 ? NEGATIVE : null;
        boolean strict=">".equals(op);
        if (v.compareTo(high)>0 || (strict && v.compareTo(high)==0)) return HIGH_POSITIVE;
        if (v.compareTo(limit)>0 || (strict && v.compareTo(limit)==0)) return POSITIVE;
        return null;
    }

	public static String label(String status) {
		if (POSITIVE.equals(status)) return "阳性（滴度未定）";
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
		return POSITIVE.equals(status) || LOW_POSITIVE.equals(status) || HIGH_POSITIVE.equals(status);
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
