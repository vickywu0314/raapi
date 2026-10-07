package com.wenwen.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

public class Das28UtilTest {

	private static String a(String score) {
		return Das28Util.activity(new BigDecimal(score));
	}

	@Test
	public void cutPoints() {
		assertEquals(Das28Util.REMISSION, a("0"));
		assertEquals(Das28Util.REMISSION, a("2.294"));
		assertEquals(Das28Util.LOW, a("2.295"));
		assertEquals(Das28Util.LOW, a("2.3"));
		assertEquals(Das28Util.LOW, a("2.7"));
		assertEquals(Das28Util.LOW, a("2.704"));
		assertEquals(Das28Util.MODERATE, a("2.705"));
		assertEquals(Das28Util.MODERATE, a("4.1"));
		assertEquals(Das28Util.MODERATE, a("4.104"));
		assertEquals(Das28Util.HIGH, a("4.105"));
		assertNull(Das28Util.activity(null));
	}

	@Test
	public void canonicalStoredValues() {
		String[][] values = {
			{"0", "0.00"}, {"2.294", "2.29"}, {"2.295", "2.30"},
			{"2.3", "2.30"}, {"2.7", "2.70"}, {"2.704", "2.70"},
			{"2.705", "2.71"}, {"4.1", "4.10"}, {"4.104", "4.10"},
			{"4.105", "4.11"}, {"  123456.789  ", "123456.79"}
		};
		for (String[] value : values) {
			assertEquals(new BigDecimal(value[1]), Das28Util.canonicalCrp(value[0]), value[0]);
		}
	}

	@Test
	public void invalidStoredValuesAreNotZero() {
		for (String raw : new String[] {"-0.001", "-1", null, "", "  ", "未查",
				"NaN", "Infinity", "{}", "[]", "1e2147483648"}) {
			assertNull(Das28Util.canonicalCrp(raw), String.valueOf(raw));
		}
	}

	@Test
	public void negativeActivityIsNotRemission() {
		assertNull(Das28Util.activity(new BigDecimal("-0.001")));
		assertNull(Das28Util.activity(new BigDecimal("-1")));
	}

	@Test
	public void labels() {
		assertEquals("高疾病活动度", Das28Util.label(Das28Util.HIGH));
		assertEquals("临床缓解", Das28Util.label(Das28Util.REMISSION));
		assertNull(Das28Util.label(null));
	}
}
