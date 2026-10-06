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
		assertEquals(Das28Util.REMISSION, a("2.59"));
		assertEquals(Das28Util.LOW, a("2.60"));
		assertEquals(Das28Util.LOW, a("3.20"));
		assertEquals(Das28Util.MODERATE, a("3.21"));
		assertEquals(Das28Util.MODERATE, a("5.10"));
		assertEquals(Das28Util.HIGH, a("5.11"));
		assertEquals(Das28Util.HIGH, a("6.68"));
		assertNull(Das28Util.activity(null));
	}

	@Test
	public void labels() {
		assertEquals("高疾病活动度", Das28Util.label(Das28Util.HIGH));
		assertEquals("临床缓解", Das28Util.label(Das28Util.REMISSION));
		assertNull(Das28Util.label(null));
	}
}
