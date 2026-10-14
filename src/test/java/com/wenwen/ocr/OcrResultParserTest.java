package com.wenwen.ocr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** 检验项目 → 辅助检查字段（用宣武医院血常规+网织报告单的实际项目名称） */
class OcrResultParserTest {

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
}
