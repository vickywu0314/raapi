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
		/** text 文本 / date 日期 / list 多选（存数组）/ number 数字 / haq HAQ 选项 / computed 系统计算（只读）/ readonly 只读 / wxonly 只有「未查」 */
		public final String type;

		Field(String key, String label, String unit, String type) {
			this.key = key;
			this.label = label;
			this.unit = unit;
			this.type = type;
		}

		public boolean editable() {
			return !"computed".equals(type) && !"readonly".equals(type) && !"wxonly".equals(type);
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
		/** 显示用的列（可拼接） */
		public final List<Field> columns;
		/** 编辑用的列（每列一个字段） */
		public final List<Field> editColumns = new ArrayList<Field>();

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

	private static Field f(String key, String label, String unit, String type) {
		return new Field(key, label, unit, type);
	}

	static {
		List<Module> list = new ArrayList<Module>();
		Module m;

		// 病史病情（bsbq）
		m = new Module("history", "病史病情", "bsbq");
		m.groups.add(new Group("病程",
				f("firstDate", "首诊时间", "", "date"),
				f("followDate", "随诊时间", "", "date"),
				f("happenDate", "发病时间", "", "date"),
				f("confirmDate", "确诊时间", "", "date")));
		m.groups.add(new Group("症状",
				f("cause", "加重诱因", "", "list"),
				f("extraCause", "其他诱因", "", "text"),
				f("ache", "关节疼痛", "", "text"),
				f("stiffness", "关节晨僵", "", "text"),
				f("swell", "关节肿胀", "", "text"),
				f("appetite", "胃口（食欲）", "", "text"),
				f("insomnia", "失眠多梦", "", "text"),
				f("distracted", "心烦不安", "", "text"),
				f("hypodynamic", "神疲乏力", "", "text"),
				f("wind", "怕风怕凉", "", "text"),
				f("menstruation", "月经", "", "list"),
				f("pee", "小便", "", "list"),
				f("shit", "大便", "", "list")));
		m.groups.add(new Group("治疗史",
				f("zls", "治疗史", "", "list"),
				f("qbxs", "起坐形式", "", "text"),
				f("yjqzlyw", "研究前治疗药物及用量", "", "text"),
				f("gjwqtbz", "关节外其他症状或疾病", "", "list")));
		list.add(m);

		// 辅助检查（fzjc）
		m = new Module("exam", "辅助检查", "fzjc");
		m.groups.add(new Group("血常规",
				f("bxb", "白细胞 WBC", "×10⁹/L", "text"),
				f("xhdb", "血红蛋白 HGB", "g/L", "text"),
				f("xxb", "血小板 PLT", "×10⁹/L", "text")));
		m.groups.add(new Group("炎症指标",
				f("xc", "血沉 ESR", "mm/h", "text"),
				f("cfydb", "C反应蛋白 CRP", "mg/L", "text"),
				f("cmcfydb", "超敏C反应蛋白 hsCRP", "mg/L", "text")));
		m.groups.add(new Group("免疫指标",
				f("lfsyz", "类风湿因子 RF", "IU/mL", "text"),
				f("kccpkt", "抗CCP抗体", "U/mL", "text"),
				f("myqdb", "免疫球蛋白 IgG", "g/L", "text"),
				f("lga", "免疫球蛋白 IgA", "g/L", "text"),
				f("lgm", "免疫球蛋白 IgM", "g/L", "text"),
				f("bt3", "补体 C3", "g/L", "text"),
				f("bt4", "补体 C4", "g/L", "text")));
		m.groups.add(new Group("自身抗体",
				f("ana", "ANA", "", "list"),
				f("ssa", "抗SS-A抗体", "", "list"),
				f("ssb", "抗SS-B抗体", "", "list"),
				f("ro52", "抗Ro-52抗体", "", "list")));
		m.groups.add(new Group("肝肾功能",
				f("alt", "谷丙转氨酶 ALT", "U/L", "text"),
				f("ast", "谷草转氨酶 AST", "U/L", "text"),
				f("ggt", "谷氨酰转氨酶 GGT", "U/L", "text"),
				f("zjdhs", "直接胆红素", "μmol/L", "text"),
				f("urea", "尿素", "mmol/L", "text"),
				f("crea", "肌酐", "μmol/L", "text")));
		m.groups.add(new Group("血脂 / 血糖 / 其他",
				f("tc", "总胆固醇 TC", "mmol/L", "text"),
				f("tg", "甘油三酯 TG", "mmol/L", "text"),
				f("hdl_c", "高密度脂蛋白胆固醇 HDL-C", "mmol/L", "text"),
				f("ldl_c", "低密度脂蛋白胆固醇 LDL-C", "mmol/L", "text"),
				f("xt", "血糖", "mmol/L", "text"),
				f("d_ent", "D-二聚体", "ng/ml", "text"),
				f("hcy", "同型半胱氨酸", "μmol/L", "text")));
		m.groups.add(new Group("生命体征",
				f("hx", "呼吸", "", "text"),
				f("xl", "心率", "次/分", "text"),
				f("xyssy", "收缩压", "mmHg", "text"),
				f("szy", "舒张压", "mmHg", "text")));
		m.groups.add(new Group("关节影像（双手正位片）",
				f("checkDate", "检查时间", "", "date"),
				f("checkNo", "检查号", "", "text")));
		m.groups.add(new Group("心电图 / 超声心动",
				f("xdt", "心电图", "", "list"),
				f("@csxd", "超声心动", "", "wxonly")));
		m.images.add(f("xcgImgs", "化验检查报告", "", "readonly"));
		m.images.add(f("gjxxImgs", "双手正位片 / 关节X线", "", "readonly"));
		m.images.add(f("xdtImgs", "心电图", "", "readonly"));
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
				f("zzgj", "肿胀关节", "", "list"),
				f("ytgj", "压痛关节", "", "list"),
				f("zzgjHand", "手部肿胀关节（编号）", "", "list"),
				f("ytgjHand", "手部压痛关节（编号）", "", "list"),
				f("result.zzgjs", "肿胀关节数", "个", "computed"),
				f("result.ytgjs", "压痛关节数", "个", "computed")));
		m.groups.add(new Group("评分",
				f("result.crpScore", "DAS28-CRP", "", "computed"),
				f("result.esrScore", "DAS28-ESR", "", "computed"),
				f("result.lastCrpScore", "上次 DAS28-CRP", "", "readonly"),
				f("result.lastEsrScore", "上次 DAS28-ESR", "", "readonly"),
				f("tjScore", "患者疼痛 VAS", "分", "number"),
				f("ztScoreByPatient", "疾病总体状况 VAS（患者）", "分", "number"),
				f("ztScoreByDoctor", "疾病总体状况 VAS（医生）", "分", "number"),
				f("hqaScore", "HAQ 健康评分", "", "computed")));
		m.groups.add(new Group("治疗反应（相对上次随访）",
				f("acr20", "ACR20", "", "readonly"),
				f("acr50", "ACR50", "", "readonly"),
				f("acr70", "ACR70", "", "readonly")));
		m.groups.add(new Group("脏器受累",
				f("zqsl", "脏器受累", "", "text"),
				f("zqslItems", "受累脏器", "", "list"),
				f("zqslOtherItem", "其他受累", "", "text")));
		m.groups.add(new Group("HAQ 健康评估问卷",
				f("q1", "1. 穿衣能力", "", "haq"),
				f("q2", "2. 自己能洗头吗", "", "haq"),
				f("q3", "3. 能从椅子上不用手站起来吗", "", "haq"),
				f("q4", "4. 能上下床吗", "", "haq"),
				f("q5", "5. 能自己使用筷子吗", "", "haq"),
				f("q6", "6. 能端起盛满的杯子送到嘴边吗", "", "haq"),
				f("q7", "7. 能开启未启封的易拉罐吗", "", "haq"),
				f("q8", "8. 能在户外走平路吗", "", "haq"),
				f("q9", "9. 能上5级台阶吗", "", "haq"),
				f("q10", "10. 能自己洗澡并擦干吗", "", "haq"),
				f("q11", "11. 能洗盆浴吗", "", "haq"),
				f("q12", "12. 能自己上厕所吗", "", "haq"),
				f("q13", "13. 能伸手摘下衣架上的衣帽吗", "", "haq"),
				f("q14", "14. 能弯腰从地上拾起衣物吗", "", "haq"),
				f("q15", "15. 能用钥匙开门吗", "", "haq"),
				f("q16", "16. 能打开已经开启的罐头吗", "", "haq"),
				f("q17", "17. 能开关水龙头吗", "", "haq"),
				f("q18", "18. 能出门到商店购物吗", "", "haq"),
				f("q19", "19. 能上下出租车或公交车吗", "", "haq"),
				f("q20", "20. 能做家务吗（如吸尘、扫地）", "", "haq")));
		m.hidden.add("result.acr20");
		m.hidden.add("result.acr50");
		m.hidden.add("result.acr70");
		m.hidden.add("result.hqaScore");
		list.add(m);

		// 中医诊断（zyzd）
		m = new Module("tcm", "中医诊断", "zyzd");
		m.groups.add(new Group("证型",
				f("zz", "主证", "", "text"),
				f("zzzz", "主证 · 主症", "", "list"),
				f("zzcz", "主证 · 次症", "", "list"),
				f("jz", "兼证", "", "text"),
				f("jzzz", "兼证 · 主症", "", "list"),
				f("jzcz", "兼证 · 次症", "", "list")));
		m.groups.add(new Group("舌脉",
				f("ss", "舌色", "", "list"),
				f("sx", "舌形", "", "list"),
				f("tz", "苔质", "", "list"),
				f("ts", "苔色", "", "text"),
				f("mx", "脉象", "", "list")));
		m.images.add(f("mbImgs", "面部", "", "readonly"));
		m.images.add(f("smImgs", "舌面", "", "readonly"));
		m.images.add(f("sdImgs", "舌底", "", "readonly"));
		list.add(m);

		// 治疗方案（zlfa）
		m = new Module("treatment", "治疗方案", "zlfa");
		m.groups.add(new Group("花费合计",
				f("xyCostTotal", "西药总花费", "元", "text"),
				f("zcyCostTotal", "中成药总花费", "元", "text"),
				f("cyCostTotal", "草药总花费", "元", "text")));
		m.tables.add(new Table("xyList", "西药",
				f("drugName", "药品名称", "", "text"),
				f("goodsName", "商品名称", "", "text"),
				f("category", "药品类别", "", "text"),
				f("company", "生产厂家", "", "text"),
				f("dosis+dosisUnit", "剂量", "", "text"),
				f("drugDelivery", "给药方式", "", "text"),
				f("drugFreq", "给药频次", "", "text"),
				f("startTime~endTime", "用药起止", "", "text"),
				f("tygc", "调药过程", "", "text"),
				f("tyyy", "调药原因", "", "text"),
				f("cost", "花费", "元", "text")));
		m.tables.get(m.tables.size() - 1).editColumns.add(f("drugName", "药品名称", "", "text"));
		m.tables.get(m.tables.size() - 1).editColumns.add(f("goodsName", "商品名称", "", "text"));
		m.tables.get(m.tables.size() - 1).editColumns.add(f("category", "药品类别", "", "text"));
		m.tables.get(m.tables.size() - 1).editColumns.add(f("company", "生产厂家", "", "text"));
		m.tables.get(m.tables.size() - 1).editColumns.add(f("dosis", "剂量", "", "text"));
		m.tables.get(m.tables.size() - 1).editColumns.add(f("dosisUnit", "剂量单位", "", "text"));
		m.tables.get(m.tables.size() - 1).editColumns.add(f("drugDelivery", "给药方式", "", "text"));
		m.tables.get(m.tables.size() - 1).editColumns.add(f("drugFreq", "给药频次", "", "text"));
		m.tables.get(m.tables.size() - 1).editColumns.add(f("startTime", "起始时间", "", "date"));
		m.tables.get(m.tables.size() - 1).editColumns.add(f("endTime", "结束时间", "", "date"));
		m.tables.get(m.tables.size() - 1).editColumns.add(f("tygc", "调药过程", "", "text"));
		m.tables.get(m.tables.size() - 1).editColumns.add(f("tyyy", "调药原因", "", "text"));
		m.tables.get(m.tables.size() - 1).editColumns.add(f("cost", "花费（元）", "", "text"));
		m.tables.add(new Table("zcyList", "中成药",
				f("drugName", "药品名称", "", "text"),
				f("goodsName", "商品名称", "", "text"),
				f("category", "药品类别", "", "text"),
				f("company", "生产厂家", "", "text"),
				f("dosis+dosisUnit", "剂量", "", "text"),
				f("drugDelivery", "给药方式", "", "text"),
				f("drugFreq", "给药频次", "", "text"),
				f("startTime~endTime", "用药起止", "", "text"),
				f("tygc", "调药过程", "", "text"),
				f("tyyy", "调药原因", "", "text"),
				f("cost", "花费", "元", "text")));
		m.tables.get(m.tables.size() - 1).editColumns.add(f("drugName", "药品名称", "", "text"));
		m.tables.get(m.tables.size() - 1).editColumns.add(f("goodsName", "商品名称", "", "text"));
		m.tables.get(m.tables.size() - 1).editColumns.add(f("category", "药品类别", "", "text"));
		m.tables.get(m.tables.size() - 1).editColumns.add(f("company", "生产厂家", "", "text"));
		m.tables.get(m.tables.size() - 1).editColumns.add(f("dosis", "剂量", "", "text"));
		m.tables.get(m.tables.size() - 1).editColumns.add(f("dosisUnit", "剂量单位", "", "text"));
		m.tables.get(m.tables.size() - 1).editColumns.add(f("drugDelivery", "给药方式", "", "text"));
		m.tables.get(m.tables.size() - 1).editColumns.add(f("drugFreq", "给药频次", "", "text"));
		m.tables.get(m.tables.size() - 1).editColumns.add(f("startTime", "起始时间", "", "date"));
		m.tables.get(m.tables.size() - 1).editColumns.add(f("endTime", "结束时间", "", "date"));
		m.tables.get(m.tables.size() - 1).editColumns.add(f("tygc", "调药过程", "", "text"));
		m.tables.get(m.tables.size() - 1).editColumns.add(f("tyyy", "调药原因", "", "text"));
		m.tables.get(m.tables.size() - 1).editColumns.add(f("cost", "花费（元）", "", "text"));
		m.tables.add(new Table("cyList", "中药饮片",
				f("zz", "证型", "", "text"),
				f("fj", "方剂", "", "text"),
				f("bccy", "草药", "", "text"),
				f("qtbccy", "补充草药", "", "text")));
		m.tables.get(m.tables.size() - 1).editColumns.add(f("zz", "证型", "", "text"));
		m.tables.get(m.tables.size() - 1).editColumns.add(f("fj", "方剂", "", "text"));
		m.tables.get(m.tables.size() - 1).editColumns.add(f("bccy", "草药", "", "list"));
		m.tables.get(m.tables.size() - 1).editColumns.add(f("qtbccy", "补充草药", "", "text"));
		m.tables.add(new Table("zywzList", "中医外治",
				f("name", "方法", "", "text"),
				f("part", "部位", "", "text"),
				f("qt", "其他", "", "text")));
		m.tables.get(m.tables.size() - 1).editColumns.add(f("name", "方法", "", "text"));
		m.tables.get(m.tables.size() - 1).editColumns.add(f("part", "部位", "", "text"));
		m.tables.get(m.tables.size() - 1).editColumns.add(f("qt", "其他", "", "text"));
		list.add(m);

		// 不良反应（blsj）
		m = new Module("adverse", "不良反应", "blsj");
		m.groups.add(new Group("",
				f("event", "本次是否发生不良反应", "", "text"),
				f("startDate", "发生日期", "", "date"),
				f("endDate", "结束日期", "", "date"),
				f("badCaseList", "不良反应名称", "", "list"),
				f("action", "采取与药物的相关措施", "", "list"),
				// 以下 3 个老系统没有存过，本系统自定义（2026-10-06 与业务确认）
				f("saeList", "SAE类别", "", "list"),
				f("otherAction", "其他措施", "", "text"),
				f("eventDetail", "不良事件详情", "", "text")));
		list.add(m);

		// 不良事件（bblsj）
		m = new Module("adverseEvent", "不良事件", "bblsj");
		list.add(m);
		MODULES = Collections.unmodifiableList(list);
	}

	private VisitFieldDict() {
	}
}
