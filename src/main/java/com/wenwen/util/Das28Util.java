package com.wenwen.util;

import java.math.BigDecimal;

/**
 * DAS28 疾病活动度分级（EULAR 通用切点）：
 * &lt; 2.6 临床缓解；2.6 ~ 3.2 低疾病活动度；3.2 ~ 5.1（不含 3.2）中疾病活动度；&gt; 5.1 高疾病活动度。
 */
public final class Das28Util {

	public static final String REMISSION = "remission";
	public static final String LOW = "low";
	public static final String MODERATE = "moderate";
	public static final String HIGH = "high";

	private static final BigDecimal REMISSION_BELOW = new BigDecimal("2.6");
	private static final BigDecimal LOW_MAX = new BigDecimal("3.2");
	private static final BigDecimal MODERATE_MAX = new BigDecimal("5.1");

	private Das28Util() {
	}

	/** 分值 → 活动度编码；null 返回 null */
	public static String activity(BigDecimal score) {
		if (score == null) {
			return null;
		}
		if (score.compareTo(REMISSION_BELOW) < 0) {
			return REMISSION;
		}
		if (score.compareTo(LOW_MAX) <= 0) {
			return LOW;
		}
		return score.compareTo(MODERATE_MAX) <= 0 ? MODERATE : HIGH;
	}

	public static String label(String activity) {
		if (REMISSION.equals(activity)) {
			return "临床缓解";
		}
		if (LOW.equals(activity)) {
			return "低疾病活动度";
		}
		if (MODERATE.equals(activity)) {
			return "中疾病活动度";
		}
		if (HIGH.equals(activity)) {
			return "高疾病活动度";
		}
		return null;
	}
}
