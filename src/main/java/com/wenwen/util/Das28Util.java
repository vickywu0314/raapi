package com.wenwen.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * DAS28-CRP 疾病活动度分级（两位 HALF_UP canonical 后分层）：
 * &lt; 2.3 临床缓解；2.3 ~ 2.7 低疾病活动度；&gt; 2.7 且 ≤ 4.1 中疾病活动度；&gt; 4.1 高疾病活动度。
 */
public final class Das28Util {

	public static final String REMISSION = "remission";
	public static final String LOW = "low";
	public static final String MODERATE = "moderate";
	public static final String HIGH = "high";

	private static final BigDecimal REMISSION_BELOW = new BigDecimal("2.3");
	private static final BigDecimal LOW_MAX = new BigDecimal("2.7");
	private static final BigDecimal MODERATE_MAX = new BigDecimal("4.1");

	private Das28Util() {
	}

	/** 存量 CRP：原值非负才接纳，再按 HALF_UP 保留两位；缺失或非法值为 null。 */
	public static BigDecimal canonicalCrp(String raw) {
		if (raw == null || raw.trim().isEmpty()) {
			return null;
		}
		try {
			BigDecimal score = new BigDecimal(raw.trim());
			return score.signum() < 0 ? null : score.setScale(2, RoundingMode.HALF_UP);
		} catch (NumberFormatException | ArithmeticException e) {
			return null;
		}
	}

	/** 分值 → 活动度编码；使用同一 canonical 政策，null / 负值返回 null。 */
	public static String activity(BigDecimal score) {
		score = canonicalCrp(score == null ? null : score.toString());
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

	/** 病情变化阈值：首次与最近一次 DAS28-CRP 相差 ≥ 0.6 才算改善 / 加重（与看板「活动度回升」同一阈值） */
	public static final BigDecimal TREND_DELTA = new BigDecimal("0.6");
	public static final String BETTER = "better";
	public static final String STABLE = "stable";
	public static final String WORSE = "worse";

	/**
	 * 病情变化：最近一次 − 首次（均为 canonical 两位）≤ −0.6 改善 better，≥ +0.6 加重 worse，其余 stable；
	 * 任一缺失返回 null（需至少两次有效评分，由调用方保证 first 与 latest 不是同一次随访）。
	 */
	public static String trend(BigDecimal first, BigDecimal latest) {
		if (first == null || latest == null) {
			return null;
		}
		BigDecimal delta = latest.subtract(first);
		if (delta.compareTo(TREND_DELTA) >= 0) {
			return WORSE;
		}
		return delta.compareTo(TREND_DELTA.negate()) <= 0 ? BETTER : STABLE;
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
