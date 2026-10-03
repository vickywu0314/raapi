package com.wenwen.util;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 研究编号：病种字母-建档日期yyyyMMdd-5位序号，如 RA-20261003-00001。
 * 序号 1~99999 用 5 位数字；用完后由高位起逐位以字母替换数字位：
 * A0000~Z9999，AA000~ZZ999，AAA00~ZZZ99，AAAA0~ZZZZ9，AAAAA~ZZZZZ。
 */
public final class StudyNoUtil {

	private static final int LENGTH = 5;

	private StudyNoUtil() {
	}

	/**
	 * @param disease 病种字母，如 RA
	 * @param createDate 建档日期
	 * @param seq 当天序号，从 1 开始
	 */
	public static String build(String disease, Date createDate, long seq) {
		return disease + "-" + new SimpleDateFormat("yyyyMMdd").format(createDate) + "-" + encodeSeq(seq);
	}

	/** 当天序号 → 5 位编码 */
	public static String encodeSeq(long seq) {
		if (seq < 1) {
			throw new IllegalArgumentException("序号从 1 开始：" + seq);
		}
		if (seq <= 99999) {
			return pad(seq, LENGTH);
		}
		long n = seq - 100000;
		for (int letters = 1; letters <= LENGTH; letters++) {
			int digits = LENGTH - letters;
			long digitCap = pow(10, digits);
			long capacity = pow(26, letters) * digitCap;
			if (n < capacity) {
				return letters(n / digitCap, letters) + (digits == 0 ? "" : pad(n % digitCap, digits));
			}
			n -= capacity;
		}
		throw new IllegalStateException("当天研究编号已用完：" + seq);
	}

	private static String letters(long value, int width) {
		char[] chars = new char[width];
		for (int i = width - 1; i >= 0; i--) {
			chars[i] = (char) ('A' + value % 26);
			value /= 26;
		}
		return new String(chars);
	}

	private static String pad(long value, int width) {
		return String.format("%0" + width + "d", value);
	}

	private static long pow(long base, int exp) {
		long r = 1;
		for (int i = 0; i < exp; i++) {
			r *= base;
		}
		return r;
	}
}
