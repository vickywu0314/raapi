package com.wenwen.ocr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** 检验项目 → 辅助检查字段（用宣武医院血常规+网织报告单的实际项目名称） */
class OcrResultParserTest {
    @Test void completeNumericResultsRetainComparatorsAndNeverInferUnitsOrPolarity() {
        for (String value : new String[]{"<5", "≤5", ">5", "≥5", "1e2", "-1"})
            assertEquals(value, OcrResultParser.value("bxb", value));
        for (String value : new String[]{"4 mg/L", "4abc", "NaN", "Infinity", "1e309"})
            assertNull(OcrResultParser.value("bxb", value));
        assertEquals("-1", OcrResultParser.value("lfsyz", "-1"));
        assertEquals("30 IU/mL", OcrResultParser.value("lfsyz", "30 IU/mL"));
        assertEquals(">25 U/mL", OcrResultParser.value("kccpkt", ">25 U/mL"));
        assertEquals("阴性", OcrResultParser.value("ana", "阴性"));
        assertEquals("阳性", OcrResultParser.value("ssa", "++"));
    }
    @Test void numericPrefixCannotSilentlyDiscardUnrecognizedResultText() {
        com.alibaba.fastjson.JSONObject medical = com.alibaba.fastjson.JSONObject.parseObject("{\"words_result\":{\"Item\":[[{\"word_name\":\"项目名称\",\"word\":\"白细胞\"},{\"word_name\":\"英文缩写\",\"word\":\"WBC\"},{\"word_name\":\"结果\",\"word\":\"10000abc\"},{\"word_name\":\"单位\",\"word\":\"×10⁹/L\"}]]}}");
        java.util.Map<String,Object> parsed = OcrResultParser.parse(medical, null);
        org.junit.jupiter.api.Assertions.assertTrue(((java.util.Map<?,?>)parsed.get("fzjc")).isEmpty(), "OCR must not wash a malformed result into a number");
        java.util.Map<?,?> item = (java.util.Map<?,?>)((java.util.List<?>)parsed.get("items")).get(0);
        assertEquals("10000abc", item.get("result")); assertNull(item.get("value"));
        assertEquals(Boolean.TRUE, item.get("needsConfirmation"));
        assertNull(OcrResultParser.value("bxb", "10000abc"));
    }

	@Test
	void bloodRoutineMapsOnlyTheFourCountFields() {
		assertEquals("bxb", OcrResultParser.labKey("★白细胞计数[WBC]", null));
		assertEquals("rbc", OcrResultParser.labKey("★红细胞计数[RBC]", null));
		assertEquals("xhdb", OcrResultParser.labKey("★血红蛋白测定[HGB]", null));
		assertEquals("xxb", OcrResultParser.labKey("★血小板计数[PLT]", null));
		assertEquals("bxb", OcrResultParser.labKey("白细胞计数", "LEU"));
		assertEquals("bxb", OcrResultParser.labKey("白细胞", null));
	}

	@Test
	void derivedIndicesAreNotMistakenForCounts() {
		String[] none = { "淋巴细胞计数[LYMPH#]", "单核细胞计数[MONO#]", "中性粒细胞计数[NEUT#]", "嗜酸性粒细胞计数[EOS#]",
				"嗜碱性粒细胞计数[BASO#]", "淋巴细胞百分率[LYMPH%]", "中性粒细胞百分率[NEUT%]", "嗜酸性粒细胞比率[EOS%]",
				"★红细胞压积[HCT]", "红细胞平均体积[MCV]", "平均红细胞血红蛋白含量[MCH]", "平均红细胞血红蛋白浓度[MCHC]",
				"红细胞体积分布宽度[RDWCV]", "血小板压积[PCT]", "血小板平均体积[MPV]", "血小板体积分布宽度[PDW]",
				"网织红细胞计数[RET]", "尿白细胞", "尿红细胞", "白细胞介素6[IL-6]" };
		for (String n : none) {
			assertNull(OcrResultParser.labKey(n, null), n);
		}
	}

	@Test
	void otherLabsStillMatch() {
		assertEquals("cmcfydb", OcrResultParser.labKey("超敏C-反应蛋白", "hs-CRP"));
		assertEquals("cfydb", OcrResultParser.labKey("C反应蛋白(CRP)", null));
		assertEquals("kccpkt", OcrResultParser.labKey("抗环瓜氨酸肽抗体", "抗CCP"));
		assertEquals("ckmb", OcrResultParser.labKey("肌酸激酶同工酶[CK-MB]", null));
		assertEquals("ck", OcrResultParser.labKey("肌酸激酶[CK]", null));
		assertEquals("urea", OcrResultParser.labKey("尿素", null));
		assertEquals("d_ent", OcrResultParser.labKey("D - 二聚体", null));
	}

	@Test
	void conflictingCrpNameAndAbbreviationNeedsConfirmationEvenWithLearnedAlias() {
		assertNull(OcrResultParser.labKey("超敏C反应蛋白", "CRP"));
		assertNull(OcrResultParser.labKey("高敏C反应蛋白[CRP]", null));
		com.alibaba.fastjson.JSONObject medical = com.alibaba.fastjson.JSONObject.parseObject("{\"words_result\":{\"Item\":[[{\"word_name\":\"项目名称\",\"word\":\"超敏C反应蛋白\"},{\"word_name\":\"英文缩写\",\"word\":\"CRP\"},{\"word_name\":\"结果\",\"word\":\"8.5\"}]]}}");
		java.util.Map<String,String> learned = new java.util.HashMap<>();
		learned.put(OcrResultParser.alias("超敏C反应蛋白", "CRP"), "cfydb");
		java.util.Map<String,Object> parsed=OcrResultParser.parse(medical,null,learned);
		org.junit.jupiter.api.Assertions.assertTrue(((java.util.Map<?,?>)parsed.get("fzjc")).isEmpty());
		java.util.Map<?,?> item=(java.util.Map<?,?>)((java.util.List<?>)parsed.get("items")).get(0);
		assertNull(item.get("field")); assertEquals(Boolean.TRUE,item.get("needsConfirmation"));
		org.junit.jupiter.api.Assertions.assertTrue(String.valueOf(item.get("mappingWarning")).contains("冲突"));
	}
	@Test void derivedNameCannotBeForcedIntoBloodCountByAbbreviation() {
		assertNull(OcrResultParser.labKey("平均红细胞体积", "RBC"));
		assertNull(OcrResultParser.labKey("尿白细胞", "WBC"));
	}

	@Test void prescriptionHerbsAndOnlyExplicitSyndromeOrFormula() {
		java.util.List<String> lines = java.util.Arrays.asList("中国中医科学院广安门医院 门诊病历", "诊断：类风湿关节炎 肝肾不足证", "中药处方：独活寄生汤加减",
				"墨旱莲15g 秦艽15g", "山药15g  绵萆薢15g", "黄芪 30 克", "共7剂 每日1剂");
		java.util.Map<String, Object> p = OcrResultParser.prescription(lines);
		java.util.List<?> herbs = (java.util.List<?>) p.get("herbs");
		assertEquals(5, herbs.size());
		assertEquals("墨旱莲15g", ((java.util.Map<?, ?>) herbs.get(0)).get("text"));
		assertEquals("黄芪30g", ((java.util.Map<?, ?>) herbs.get(4)).get("text"));
		assertEquals("肝肾不足证", p.get("zz"));
		assertEquals("独活寄生汤", p.get("fj"));
		// 只写方剂、方剂只属于一个证型：带出证型
		p = OcrResultParser.prescription(java.util.Arrays.asList("方药：身痛逐瘀汤", "桃仁10g 红花10g 当归15g"));
		assertEquals("瘀血阻络证", p.get("zz"));
		// 没写证型 / 方剂：不猜
		p = OcrResultParser.prescription(java.util.Arrays.asList("当归15g 川芎10g 白芍15g"));
		assertNull(p.get("zz")); assertNull(p.get("fj"));
		// 不足 3 味不算处方
		assertNull(OcrResultParser.prescription(java.util.Arrays.asList("葡萄糖 5.2", "体重50kg", "甲氨蝶呤10mg")));
	}

	@Test void laboratoryConcentrationsAreNotHerbDoses() {
		for (String unit : new String[]{"g/L", "G/L", "g／L", "g / L", "克/升", "克 ／ 升"}) {
			java.util.List<String> labs = java.util.Arrays.asList("总蛋白70 " + unit, "白蛋白40 " + unit, "球蛋白30 " + unit);
			assertNull(OcrResultParser.prescription(labs), unit);
			java.util.List<String> mixed = new java.util.ArrayList<>(labs);
			mixed.add("当归15g 川芎10g 白芍15克");
			java.util.Map<String, Object> prescription = OcrResultParser.prescription(mixed);
			java.util.List<?> herbs = (java.util.List<?>) prescription.get("herbs");
			assertEquals(3, herbs.size(), unit);
			assertEquals("当归15g", ((java.util.Map<?, ?>) herbs.get(0)).get("text"), unit);
			assertEquals("白芍15g", ((java.util.Map<?, ?>) herbs.get(2)).get("text"), unit);
		}
	}
}
