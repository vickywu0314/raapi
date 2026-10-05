package com.wenwen.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 随访 7 个病历模块的字段字典：老系统 JSON 字段名 → 中文名、单位、分组。
 * 来源：docs/待确认清单-v1.xlsx（业务已确认 2026-10-05），说明见 docs/随访字段字典.md。
 * 本文件由字典数据生成，改字段请同时更新 docs/随访字段字典.md。
 *
 * 字段名里的「.」表示嵌套（如 result.crpScore）；以 @ 开头的是只有「未查」标记、没有值的虚拟字段（如 @csxd 超声心动）。
 * 表格列名里 a+b 表示两个字段拼接（剂量 + 单位），a~b 表示区间（起 ~ 止）。
 */
public final class VisitFieldDict {

	public static final class Field {
		public final String key;
		public final String label;
		public final String unit;

		Field(String key, String label, String unit) {
			this.key = key;
			this.label = label;
			this.unit = unit;
		}
	}

	public static final class Group {
		public final String title;
		public final List<Field> fields;

		Group(String title, Field... fields) {
			this.title = title;
			this.fields = Collections.unmodifiableList(Arrays.asList(fields));
		}
	}

	public static final class Table {
		public final String key;
		public final String title;
		public final List<Field> columns;

		Table(String key, String title, Field... columns) {
			this.key = key;
			this.title = title;
			this.columns = Collections.unmodifiableList(Arrays.asList(columns));
		}
	}

	public static final class Module {
		/** 页面模块编码 */
		public final String key;
		/** 页面模块中文名 */
		public final String title;
		/** 随访表 patient_follow_up_history 的字段 */
		public final String column;
		public final List<Group> groups = new ArrayList<Group>();
		/** 图片字段：key → 中文名 */
		public final List<Field> images = new ArrayList<Field>();
		public final List<Table> tables = new ArrayList<Table>();
		/** 字段 → 它的「未查」标记字段（true = 未查） */
		public final Map<String, String> wx = new LinkedHashMap<String, String>();
		/** 「未查」标记表字段（如 xcgWx：{化验字段: true/false}），没有为 null */
		public String wxMap;
		/** 不显示的字段（重复值、内部标记等） */
		public final Set<String> hidden = new HashSet<String>(Arrays.asList("finish", "id"));

		Module(String key, String title, String column) {
			this.key = key;
			this.title = title;
			this.column = column;
		}
	}

	/** 7 个模块，顺序即页面顺序 */
	public static final List<Module> MODULES;

	private static Field f(String key, String label, String unit) {
		return new Field(key, label, unit);
	}

	static {
		List<Module> list = new ArrayList<Module>();
		Module m;

		// 病史病情（bsbq）
		m = new Module("history", "病史病情", "bsbq");
		m.groups.add(new Group("病程",
				f("firstDate", "首诊时间", ""),
				f("followDate", "随诊时间", ""),
				f("happenDate", "发病时间", ""),
				f("confirmDate", "确诊时间", "")));
		m.groups.add(new Group("症状",
				f("cause", "加重诱因", ""),
				f("extraCause", "其他诱因", ""),
				f("ache", "关节疼痛", ""),
				f("stiffness", "关节晨僵", ""),
				f("swell", "关节肿胀", ""),
				f("appetite", "胃口（食欲）", ""),
				f("insomnia", "失眠多梦", ""),
				f("distracted", "心烦不安", ""),
				f("hypodynamic", "神疲乏力", ""),
				f("wind", "怕风怕凉", ""),
				f("menstruation", "月经", ""),
				f("pee", "小便", ""),
				f("shit", "大便", "")));
		m.groups.add(new Group("治疗史",
				f("zls", "治疗史", ""),
				f("qbxs", "起坐形式", ""),
				f("yjqzlyw", "研究前治疗药物及用量", ""),
				f("gjwqtbz", "关节外其他症状或疾病", "")));
		list.add(m);

		// 辅助检查（fzjc）
		m = new Module("exam", "辅助检查", "fzjc");
		m.groups.add(new Group("血常规",
				f("bxb", "白细胞 WBC", "×10⁹/L"),
				f("xhdb", "血红蛋白 HGB", "g/L"),
				f("xxb", "血小板 PLT", "×10⁹/L")));
		m.groups.add(new Group("炎症指标",
				f("xc", "血沉 ESR", "mm/h"),
				f("cfydb", "C反应蛋白 CRP", "mg/L"),
				f("cmcfydb", "超敏C反应蛋白 hsCRP", "mg/L")));
		m.groups.add(new Group("免疫指标",
				f("lfsyz", "类风湿因子 RF", "IU/mL"),
				f("kccpkt", "抗CCP抗体", "U/mL"),
				f("myqdb", "免疫球蛋白 IgG", "g/L"),
				f("lga", "免疫球蛋白 IgA", "g/L"),
				f("lgm", "免疫球蛋白 IgM", "g/L"),
				f("bt3", "补体 C3", "g/L"),
				f("bt4", "补体 C4", "g/L")));
		m.groups.add(new Group("自身抗体",
				f("ana", "ANA", ""),
				f("ssa", "抗SS-A抗体", ""),
				f("ssb", "抗SS-B抗体", ""),
				f("ro52", "抗Ro-52抗体", "")));
		m.groups.add(new Group("肝肾功能",
				f("alt", "谷丙转氨酶 ALT", "U/L"),
				f("ast", "谷草转氨酶 AST", "U/L"),
				f("ggt", "谷氨酰转氨酶 GGT", "U/L"),
				f("zjdhs", "直接胆红素", "μmol/L"),
				f("urea", "尿素", "mmol/L"),
				f("crea", "肌酐", "μmol/L")));
		m.groups.add(new Group("血脂 / 血糖 / 其他",
				f("tc", "总胆固醇 TC", "mmol/L"),
				f("tg", "甘油三酯 TG", "mmol/L"),
				f("hdl_c", "高密度脂蛋白胆固醇 HDL-C", "mmol/L"),
				f("ldl_c", "低密度脂蛋白胆固醇 LDL-C", "mmol/L"),
				f("xt", "血糖", "mmol/L"),
				f("d_ent", "D-二聚体", "ng/ml"),
				f("hcy", "同型半胱氨酸", "μmol/L")));
		m.groups.add(new Group("生命体征",
				f("hx", "呼吸", ""),
				f("xl", "心率", "次/分"),
				f("xyssy", "收缩压", "mmHg"),
				f("szy", "舒张压", "mmHg")));
		m.groups.add(new Group("关节影像（双手正位片）",
				f("checkDate", "检查时间", ""),
				f("checkNo", "检查号", "")));
		m.groups.add(new Group("心电图 / 超声心动",
				f("xdt", "心电图", ""),
				f("@csxd", "超声心动", "")));
		m.images.add(f("xcgImgs", "化验检查报告", ""));
		m.images.add(f("gjxxImgs", "双手正位片 / 关节X线", ""));
		m.images.add(f("xdtImgs", "心电图", ""));
		m.wx.put("checkDate", "gjjcWx");
		m.wx.put("checkNo", "gjjcWx");
		m.wx.put("xdt", "xdtWx");
		m.wx.put("@csxd", "csxdWx");
		m.wxMap = "xcgWx";
		m.hidden.add("csxdWx");
		m.hidden.add("gjjcWx");
		m.hidden.add("xcgWx");
		m.hidden.add("xdtWx");
		list.add(m);

		// 病情评估（bqpg）
		m = new Module("assessment", "病情评估", "bqpg");
		m.groups.add(new Group("关节评估",
				f("zzgj", "肿胀关节", ""),
				f("ytgj", "压痛关节", ""),
				f("zzgjHand", "手部肿胀关节（编号）", ""),
				f("ytgjHand", "手部压痛关节（编号）", ""),
				f("result.zzgjs", "肿胀关节数", "个"),
				f("result.ytgjs", "压痛关节数", "个")));
		m.groups.add(new Group("评分",
				f("result.crpScore", "DAS28-CRP", ""),
				f("result.esrScore", "DAS28-ESR", ""),
				f("result.lastCrpScore", "上次 DAS28-CRP", ""),
				f("result.lastEsrScore", "上次 DAS28-ESR", ""),
				f("tjScore", "患者疼痛 VAS", "分"),
				f("ztScoreByPatient", "疾病总体状况 VAS（患者）", "分"),
				f("ztScoreByDoctor", "疾病总体状况 VAS（医生）", "分"),
				f("hqaScore", "HAQ 健康评分", "")));
		m.groups.add(new Group("治疗反应（相对上次随访）",
				f("acr20", "ACR20", ""),
				f("acr50", "ACR50", ""),
				f("acr70", "ACR70", "")));
		m.groups.add(new Group("脏器受累",
				f("zqsl", "脏器受累", ""),
				f("zqslItems", "受累脏器", ""),
				f("zqslOtherItem", "其他受累", "")));
		m.groups.add(new Group("HAQ 健康评估问卷",
				f("q1", "1. 穿衣能力", ""),
				f("q2", "2. 自己能洗头吗", ""),
				f("q3", "3. 能从椅子上不用手站起来吗", ""),
				f("q4", "4. 能上下床吗", ""),
				f("q5", "5. 能自己使用筷子吗", ""),
				f("q6", "6. 能端起盛满的杯子送到嘴边吗", ""),
				f("q7", "7. 能开启未启封的易拉罐吗", ""),
				f("q8", "8. 能在户外走平路吗", ""),
				f("q9", "9. 能上5级台阶吗", ""),
				f("q10", "10. 能自己洗澡并擦干吗", ""),
				f("q11", "11. 能洗盆浴吗", ""),
				f("q12", "12. 能自己上厕所吗", ""),
				f("q13", "13. 能伸手摘下衣架上的衣帽吗", ""),
				f("q14", "14. 能弯腰从地上拾起衣物吗", ""),
				f("q15", "15. 能用钥匙开门吗", ""),
				f("q16", "16. 能打开已经开启的罐头吗", ""),
				f("q17", "17. 能开关水龙头吗", ""),
				f("q18", "18. 能出门到商店购物吗", ""),
				f("q19", "19. 能上下出租车或公交车吗", ""),
				f("q20", "20. 能做家务吗（如吸尘、扫地）", "")));
		m.hidden.add("result.acr20");
		m.hidden.add("result.acr50");
		m.hidden.add("result.acr70");
		m.hidden.add("result.hqaScore");
		list.add(m);

		// 中医诊断（zyzd）
		m = new Module("tcm", "中医诊断", "zyzd");
		m.groups.add(new Group("证型",
				f("zz", "主证", ""),
				f("zzzz", "主证 · 主症", ""),
				f("zzcz", "主证 · 次症", ""),
				f("jz", "兼证", ""),
				f("jzzz", "兼证 · 主症", ""),
				f("jzcz", "兼证 · 次症", "")));
		m.groups.add(new Group("舌脉",
				f("ss", "舌色", ""),
				f("sx", "舌形", ""),
				f("tz", "苔质", ""),
				f("ts", "苔色", ""),
				f("mx", "脉象", "")));
		m.images.add(f("mbImgs", "面部", ""));
		m.images.add(f("smImgs", "舌面", ""));
		m.images.add(f("sdImgs", "舌底", ""));
		list.add(m);

		// 治疗方案（zlfa）
		m = new Module("treatment", "治疗方案", "zlfa");
		m.groups.add(new Group("花费合计",
				f("xyCostTotal", "西药总花费", "元"),
				f("zcyCostTotal", "中成药总花费", "元"),
				f("cyCostTotal", "草药总花费", "元")));
		m.tables.add(new Table("xyList", "西药",
				f("drugName", "药品名称", ""),
				f("goodsName", "商品名称", ""),
				f("category", "药品类别", ""),
				f("company", "生产厂家", ""),
				f("dosis+dosisUnit", "剂量", ""),
				f("drugDelivery", "给药方式", ""),
				f("drugFreq", "给药频次", ""),
				f("startTime~endTime", "用药起止", ""),
				f("tygc", "调药过程", ""),
				f("tyyy", "调药原因", ""),
				f("cost", "花费", "元")));
		m.tables.add(new Table("zcyList", "中成药",
				f("drugName", "药品名称", ""),
				f("goodsName", "商品名称", ""),
				f("category", "药品类别", ""),
				f("company", "生产厂家", ""),
				f("dosis+dosisUnit", "剂量", ""),
				f("drugDelivery", "给药方式", ""),
				f("drugFreq", "给药频次", ""),
				f("startTime~endTime", "用药起止", ""),
				f("tygc", "调药过程", ""),
				f("tyyy", "调药原因", ""),
				f("cost", "花费", "元")));
		m.tables.add(new Table("cyList", "中药饮片",
				f("zz", "证型", ""),
				f("fj", "方剂", ""),
				f("bccy", "草药", ""),
				f("qtbccy", "补充草药", "")));
		m.tables.add(new Table("zywzList", "中医外治",
				f("name", "方法", ""),
				f("part", "部位", ""),
				f("qt", "其他", "")));
		list.add(m);

		// 不良反应（blsj）
		m = new Module("adverse", "不良反应", "blsj");
		m.groups.add(new Group("",
				f("event", "本次是否发生不良反应", ""),
				f("startDate", "发生日期", ""),
				f("endDate", "结束日期", ""),
				f("badCaseList", "不良反应名称", "")));
		list.add(m);

		// 不良事件（bblsj）
		m = new Module("adverseEvent", "不良事件", "bblsj");
		list.add(m);
		MODULES = Collections.unmodifiableList(list);
	}

	private VisitFieldDict() {
	}
}
