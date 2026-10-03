package com.wenwen.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.text.SimpleDateFormat;

import org.junit.jupiter.api.Test;

public class StudyNoUtilTest {

	@Test
	public void digits() {
		assertEquals("00001", StudyNoUtil.encodeSeq(1));
		assertEquals("99999", StudyNoUtil.encodeSeq(99999));
	}

	@Test
	public void lettersReplaceDigitsFromHighPosition() {
		assertEquals("A0000", StudyNoUtil.encodeSeq(100000));
		assertEquals("A9999", StudyNoUtil.encodeSeq(109999));
		assertEquals("B0000", StudyNoUtil.encodeSeq(110000));
		assertEquals("Z9999", StudyNoUtil.encodeSeq(359999));
		assertEquals("AA000", StudyNoUtil.encodeSeq(360000));
		assertEquals("ZZZZZ", StudyNoUtil.encodeSeq(99999 + 260000 + 676000 + 1757600 + 4569760 + 11881376));
	}

	@Test
	public void exhausted() {
		assertThrows(IllegalStateException.class, () -> StudyNoUtil.encodeSeq(99999 + 260000 + 676000 + 1757600 + 4569760 + 11881376 + 1));
	}

	@Test
	public void build() throws Exception {
		assertEquals("RA-20261003-00001", StudyNoUtil.build("RA", new SimpleDateFormat("yyyy-MM-dd").parse("2026-10-03"), 1));
	}
}
