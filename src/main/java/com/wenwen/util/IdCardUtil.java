package com.wenwen.util;

import java.time.DateTimeException;
import java.time.LocalDate;

/**
 * 身份证号工具：取出生日期（只读，不校验真伪）。
 * 18 位：第 7–14 位为 yyyyMMdd；15 位（老身份证）：第 7–12 位为 yyMMdd，年份补 19。
 */
public final class IdCardUtil {

	private IdCardUtil() {
	}

	/** 出生日期；不是有效的 15 / 18 位身份证号或日期不合法时返回 null */
	public static LocalDate birthDate(String cardNo) {
        return birthDate(cardNo, LocalDate.now());
    }

    public static LocalDate birthDate(String cardNo, LocalDate asOf) {
		if (cardNo == null) {
			return null;
		}
		String s = cardNo.trim();
		String ymd;
		if (s.matches("\\d{17}[\\dXx]")) {
			ymd = s.substring(6, 14);
		} else if (s.matches("\\d{15}")) {
			ymd = "19" + s.substring(6, 12);
		} else {
			return null;
		}
		try {
			LocalDate d = LocalDate.of(Integer.parseInt(ymd.substring(0, 4)), Integer.parseInt(ymd.substring(4, 6)), Integer.parseInt(ymd.substring(6, 8)));
			if (d.getYear() < 1900 || d.isAfter(asOf)) {
				return null;
			}
			return d;
		} catch (DateTimeException | NumberFormatException e) {
			return null;
		}
	}
}
