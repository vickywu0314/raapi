package com.wenwen.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

public class SerologyUtilTest {

	@Test
	public void numbersByUln() {
		assertEquals(SerologyUtil.NEGATIVE, SerologyUtil.classify("20", 20));
		assertEquals(SerologyUtil.NEGATIVE, SerologyUtil.classify("0.5", 25));
		assertEquals(SerologyUtil.LOW_POSITIVE, SerologyUtil.classify("35", 20));
		assertEquals(SerologyUtil.LOW_POSITIVE, SerologyUtil.classify("60", 20));
		assertEquals(SerologyUtil.HIGH_POSITIVE, SerologyUtil.classify("60.1", 20));
		assertEquals(SerologyUtil.HIGH_POSITIVE, SerologyUtil.classify("960", 20));
		assertEquals(SerologyUtil.HIGH_POSITIVE, SerologyUtil.classify("35 IU/mL", 10));
	}

	@Test
	public void operatorsAndText() {
		assertEquals(SerologyUtil.NEGATIVE, SerologyUtil.classify("<20", 20));
		assertEquals(SerologyUtil.NEGATIVE, SerologyUtil.classify("＜ 25", 25));
		assertEquals(SerologyUtil.HIGH_POSITIVE, SerologyUtil.classify(">3200", 25));
		assertEquals(SerologyUtil.NEGATIVE, SerologyUtil.classify("阴性", 20));
		assertEquals(SerologyUtil.NEGATIVE, SerologyUtil.classify("-", 20));
		assertEquals(SerologyUtil.LOW_POSITIVE, SerologyUtil.classify("阳性", 20));
		assertEquals(SerologyUtil.LOW_POSITIVE, SerologyUtil.classify("+", 20));
	}

	@Test
	public void unknownIsNull() {
		assertNull(SerologyUtil.classify(null, 20));
		assertNull(SerologyUtil.classify(" ", 20));
		assertNull(SerologyUtil.classify("false", 20));
		assertNull(SerologyUtil.classify("见化验单", 20));
	}

	@Test
	public void subtype() {
		assertEquals("血清阳性", SerologyUtil.subtype(SerologyUtil.NEGATIVE, SerologyUtil.HIGH_POSITIVE));
		assertEquals("血清阳性", SerologyUtil.subtype(SerologyUtil.LOW_POSITIVE, SerologyUtil.UNTESTED));
		assertEquals("血清阴性", SerologyUtil.subtype(SerologyUtil.NEGATIVE, SerologyUtil.UNTESTED));
		assertNull(SerologyUtil.subtype(SerologyUtil.UNTESTED, SerologyUtil.UNTESTED));
	}
}
