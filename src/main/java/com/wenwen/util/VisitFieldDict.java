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
 * 随访 7 个病历模块的字段字典：老系统 JSON 字段名 → 中文名、单位、控件、选项、分组。
 * 2~7 组以《RA 数据库字段》思维导图为准（2026-10-06 业务确认），定稿清单见 docs/新增随访字段清单.md，字段说明见 docs/随访字段字典.md。
 * 字典里没有的老字段不删不改：访视详情放「其他字段」显示，编辑保存时原样保留。
 *
 * 字段名里的「.」表示嵌套（如 result.crpScore）。表格列名里 a+b 表示两个字段拼接（剂量 + 单位），a~b 表示区间（起 ~ 止）。
 * 选项写「存库值|显示文字」时，存库用老数据里的写法（含老系统的错别字），页面显示正确写法。
 */
public final class VisitFieldDict {

	public static final class Field {
		public final String key;
		public final String label;
		public final String unit;
		/**
		 * text 文本 / textarea 多行文本 / date 日期 / number 数字 / list 多选（存数组）/ radio 单选（存文字）/
		 * radioList 单选（老数据存数组，如 ["阴性"]）/ haq HAQ 选项 / computed 系统计算（只读）/ readonly 只读
		 */
		public final String type;
		/** 选项（单选 / 多选）；为空时多选用文本框（「、」分隔） */
		public final List<String> options = new ArrayList<String>();
		/** 显示条件：「字段:值」，该字段（单选等于 / 多选包含）该值时才显示，如 cause:其他 */
		public String showIf;
		/** 选项随另一个字段变化（如主症随主证）：取 OPTION_SETS.get(optionSet).get(那个字段的值) */
		public String optionsFrom;
		public String optionSet;
		/** 只在第 2 次及以后的随访显示（如「与上次相比治疗方案是否调整」） */
		public boolean notBaseline;

		Field(String key, String label, String unit, String type) {
			this.key = key;
			this.label = label;
			this.unit = unit;
			this.type = type;
		}

		Field opts(String... o) {
			options.addAll(Arrays.asList(o));
			return this;
		}

		Field opts(List<String> o) {
			options.addAll(o);
			return this;
		}

		Field when(String cond) {
			showIf = cond;
			return this;
		}

		Field from(String field, String set) {
			optionsFrom = field;
			optionSet = set;
			return this;
		}

		public boolean editable() {
			return !"computed".equals(type) && !"readonly".equals(type);
		}

		/** 存数组的类型 */
		public boolean isList() {
			return VisitFieldDict.isList(type);
		}
	}

	public static boolean isList(String type) {
		return "list".equals(type) || "radioList".equals(type);
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

		Table edit(Field... cols) {
			editColumns.addAll(Arrays.asList(cols));
			return this;
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
		/** 新增随访写 JSON 时的字段顺序（对齐老系统；没列的按字典顺序接在后面） */
		public final List<String> writeOrder = new ArrayList<String>();

		Module(String key, String title, String column) {
			this.key = key;
			this.title = title;
			this.column = column;
		}
	}

	/** 7 个模块，顺序即页面顺序 */
	public static final List<Module> MODULES;

	/** 联动选项：集合名 → 上级字段的值 → 选项 */
	public static final Map<String, Map<String, List<String>>> OPTION_SETS = new LinkedHashMap<String, Map<String, List<String>>>();

	public static final List<String> SYNDROMES = Arrays.asList("风湿痹阻证", "寒湿痹阻证", "湿热痹阻证", "痰瘀痹阻证", "瘀血阻络证", "气血两虚证", "肝肾不足证", "气阴两虚证");

	private static Field f(String key, String label, String unit, String type) {
		return new Field(key, label, unit, type);
	}

	private static Field lab(String key, String label, String unit) {
		return new Field(key, label, unit, "text");
	}

	private static void set(String name, String parent, String... options) {
		Map<String, List<String>> m = OPTION_SETS.get(name);
		if (m == null) {
			m = new LinkedHashMap<String, List<String>>();
			OPTION_SETS.put(name, m);
		}
		m.put(parent, Arrays.asList(options));
	}

	static {
		// 中医证型 → 主症 / 次症（存库用老数据写法：停顿用全角逗号；「通减」「痠痛」「倦态」「晕眩」老数据就是这么存的）
		set("zhuzheng", "风湿痹阻证", "关节疼痛，肿胀，游走不定", "关节疼痛，肿胀，时发不止");
		set("cizheng", "风湿痹阻证", "恶风，或出汗", "头痛", "肢体沉重");
		set("zhuzheng", "寒湿痹阻证", "关节冷痛，触之不温，皮色不红", "疼痛遇寒加重，得热通减|疼痛遇寒加重，得热痛减");
		set("cizheng", "寒湿痹阻证", "关节拘急，屈伸不利", "肢冷，或畏寒喜暖", "口淡不渴");
		set("zhuzheng", "湿热痹阻证", "关节肿热疼痛", "关节触之热感或自觉热感");
		set("cizheng", "湿热痹阻证", "关节局部皮色发红", "发热", "心烦", "口渴或渴不欲饮", "小便黄");
		set("zhuzheng", "痰瘀痹阻证", "关节肿痛久不消", "关节局部肤色晦暗，或有皮下结节");
		set("cizheng", "痰瘀痹阻证", "关节肌肉刺痛", "关节僵硬变形", "面色黯黧", "唇暗");
		set("zhuzheng", "瘀血阻络证", "关节刺痛，疼痛部位固定不移", "疼痛夜甚");
		set("cizheng", "瘀血阻络证", "肢体麻木", "关节局部色暗", "肌肤甲错或干燥无泽");
		set("zhuzheng", "气血两虚证", "关节痠痛或隐痛，伴倦态乏力|关节酸痛或隐痛，伴倦怠乏力", "面色不华");
		set("cizheng", "气血两虚证", "心悸气短", "头晕", "爪甲色淡", "食少纳差");
		set("zhuzheng", "肝肾不足证", "关节疼痛，肿大或僵硬变形", "腰膝酸软或腰背酸痛");
		set("cizheng", "肝肾不足证", "足跟痛", "晕眩耳鸣|眩晕耳鸣", "潮热盗汗", "尿频，夜尿多");
		set("zhuzheng", "气阴两虚证", "关节肿大伴气短乏力", "肌肉酸痛，口干眼涩");
		set("cizheng", "气阴两虚证", "自汗或盗汗", "手足心热", "形体瘦弱，肌肤无泽", "虚烦多梦");
		// 中药饮片：证型 → 方剂
		set("fangji", "风湿痹阻证", "羌活胜湿汤", "蠲痹汤", "大秦艽汤", "羌活地黄汤", "健脾化湿通络方");
		set("fangji", "寒湿痹阻证", "乌头汤", "桂枝芍药知母汤", "麻黄附子细辛汤", "补肾祛寒治尪汤", "羌活地黄汤", "健脾化湿通络方", "肾着汤", "薏苡仁汤");
		set("fangji", "湿热痹阻证", "二妙散", "清热活血方", "四妙消痹汤", "痹速清合剂", "清络饮", "羌活地黄汤", "健脾化湿通络方", "四妙丸", "当归拈痛汤", "宣痹汤", "清热强脊汤", "木防己汤");
		set("fangji", "痰瘀痹阻证", "双合汤", "清热活血方", "益气养血通络方", "羌活地黄汤", "健脾化湿通络方");
		set("fangji", "瘀血阻络证", "身痛逐瘀汤", "桃红饮", "清热活血方", "益气养血通络方", "羌活地黄汤", "健脾化湿通络方");
		set("fangji", "气血两虚证", "黄芪桂枝五物汤", "十全大补汤", "归脾汤", "益气养血通络方", "补肾祛寒治尪汤", "羌活地黄汤", "健脾化湿通络方");
		set("fangji", "肝肾不足证", "独活寄生汤", "三痹汤", "虎潜丸", "益气养血通络方", "补肾祛寒治尪汤", "羌活地黄汤", "健脾化湿通络方", "左归丸", "一贯煎");
		set("fangji", "气阴两虚证", "四神煎", "益气养血通络方", "补肾祛寒治尪汤", "羌活地黄汤", "健脾化湿通络方", "生脉饮合沙参麦冬汤", "四君子合益胃汤", "路氏润燥汤");

		List<Module> list = new ArrayList<Module>();
		Module m;

		// 病史病情（bsbq）
		m = new Module("history", "病史病情", "bsbq");
		m.groups.add(new Group("病程",
				f("firstDate", "首诊时间", "", "date"),
				f("followDate", "随访时间", "", "date"),
				f("happenDate", "发病时间", "", "date"),
				f("confirmDate", "确诊时间", "", "date")));
		m.groups.add(new Group("症状",
				f("cause", "加重诱因", "", "list").opts("无", "停药", "减药", "换药", "间断服药", "劳累", "感冒", "感染", "情绪刺激", "分娩或小产", "饮食不当", "创伤", "手术", "天气变化", "其他"),
				f("extraCause", "其他诱因", "", "text").when("cause:其他"),
				f("ache", "关节疼痛", "", "radio").opts("无", "疼痛较轻", "疼痛较重，可以忍受", "疼痛较重，难以忍受"),
				f("stiffness", "关节晨僵", "", "radio").opts("无", "<30分钟", "30分钟~1小时", "1小时~2小时", ">2小时"),
				f("swell", "关节肿胀", "", "radio").opts("无", "很轻", "较重", "极重"),
				f("touchHot", "疼痛关节是否发热", "", "radio").opts("不热", "触摸热，且关节局部红热", "触摸有热感，但不觉发热", "触摸热，且自觉发热"),
				f("appetite", "食欲如何", "", "radio").opts("很好", "有点差", "很差", "一点胃口都没有"),
				f("insomnia", "失眠多梦", "", "radio").opts("无", "偶尔", "时常", "总是"),
				f("distracted", "烦躁不安或情绪低落", "", "radio").opts("几乎没有", "偶尔", "常常", "几乎总是"),
				f("hypodynamic", "疲劳", "", "radio").opts("无", "有疲乏", "很疲乏", "非常疲乏，不能干任何事"),
				f("rcshorgzxx", "日常生活或学习", "", "radio").opts("无困难", "有困难", "很困难", "完全不能"),
				f("wind", "怕风怕凉", "", "radio").opts("无", "稍感，无需加盖衣被", "加盖衣被可缓解", "加盖衣被不缓解"),
				f("menstruation", "月经", "", "list").opts("正常", "停经", "延期", "提前", "血块", "白带", "量多", "量少", "男性"),
				f("pee", "小便", "", "list").opts("黄", "热", "清长", "夜尿频", "无力", "正常"),
				f("shit", "大便", "", "list").opts("稀溏", "便秘", "粘滞", "无力", "正常")));
		list.add(m);

		// 辅助检查（fzjc）
		m = new Module("exam", "辅助检查", "fzjc");
		m.groups.add(new Group("血常规",
				lab("bxb", "白细胞 WBC", "×10⁹/L"),
				lab("xhdb", "血红蛋白 HGB", "g/L"),
				lab("xxb", "血小板 PLT", "×10⁹/L"),
				lab("rbc", "红细胞 RBC", "×10¹²/L")));
		m.groups.add(new Group("炎症指标",
				lab("xc", "血沉 ESR", "mm/h"),
				lab("cfydb", "C反应蛋白 CRP", "mg/L"),
				lab("cmcfydb", "超敏C反应蛋白 hsCRP", "mg/L")));
		m.groups.add(new Group("免疫指标",
				lab("lfsyz", "类风湿因子 RF", "IU/mL"),
				lab("kccpkt", "抗CCP抗体", "U/mL"),
				lab("myqdb", "免疫球蛋白 IgG", "g/L"),
				lab("lga", "免疫球蛋白 IgA", "g/L"),
				lab("lgm", "免疫球蛋白 IgM", "g/L")));
		m.groups.add(new Group("肝功能",
				lab("alt", "谷丙转氨酶 ALT", "U/L"),
				lab("ast", "谷草转氨酶 AST", "U/L"),
				lab("ggt", "谷氨酰转氨酶 GGT", "U/L")));
		m.groups.add(new Group("肾功能",
				lab("urea", "尿素", "mmol/L"),
				lab("crea", "肌酐", "μmol/L")));
		m.groups.add(new Group("心脏检查",
				lab("ev", "E 值", "m/s"),
				lab("av", "A 值", "m/s"),
				lab("eav", "E/A 值", ""),
				lab("fs", "左室短轴缩短率 FS", "%"),
				lab("ef", "左室射血分数 EF", "%"),
				lab("lvd", "左室舒张末期内径", "mm"),
				f("xdtresult", "心电图结果", "", "textarea"),
				lab("bnp", "B 型钠尿肽 BNP", "pg/mL"),
				lab("probnp", "NT-proBNP", "pg/mL"),
				lab("ctnt", "心肌肌钙蛋白", "ng/mL"),
				lab("ckmb", "肌酸激酶同工酶 CK-MB", "U/L"),
				lab("ck", "肌酸激酶 CK", "U/L"),
				lab("ldh", "乳酸脱氢酶 LDH", "U/L"),
				lab("ahbdh", "α-羟丁酸脱氢酶", "U/L")));
		m.groups.add(new Group("自身抗体",
				f("ana", "ANA", "", "radioList").opts("阳性", "阴性", "未做"),
				f("ssa", "抗SS-A抗体", "", "radioList").opts("阳性", "阴性", "未做"),
				f("ssb", "抗SS-B抗体", "", "radioList").opts("阳性", "阴性", "未做"),
				f("ro52", "抗Ro-52抗体", "", "radioList").opts("阳性", "阴性", "未做")));
		m.groups.add(new Group("其他化验",
				lab("d_ent", "D-二聚体", "ng/ml"),
				lab("hcy", "同型半胱氨酸", "μmol/L"),
				lab("zjdhs", "直接胆红素", "μmol/L")));
		m.groups.add(new Group("血脂",
				lab("tc", "总胆固醇 TC", "mmol/L"),
				lab("tg", "甘油三酯 TG", "mmol/L"),
				lab("hdl_c", "高密度脂蛋白胆固醇 HDL-C", "mmol/L"),
				lab("ldl_c", "低密度脂蛋白胆固醇 LDL-C", "mmol/L")));
		m.groups.add(new Group("血糖",
				lab("xt", "葡萄糖", "mmol/L")));
		m.groups.add(new Group("双手正位片检查",
				f("checkDate", "检查日期", "", "date"),
				f("checkNo", "检查号", "", "text")));
		m.groups.add(new Group("心电图",
				f("xdt", "心电图", "", "list").opts("正常", "T波改变", "ST改变", "传导阻滞", "心律失常", "其他")));
		m.groups.add(new Group("关节超声",
				f("gjcs", "关节超声", "", "radio").opts("正常", "异常")));
		m.groups.add(new Group("超声心动",
				f("csxd", "超声心动", "", "radio").opts("正常", "异常")));
		m.images.add(f("xcgImgs", "检验报告", "", "readonly"));
		m.images.add(f("gjxxImgs", "双手正位片报告", "", "readonly"));
		m.images.add(f("xdtImgs", "心电图报告", "", "readonly"));
		m.images.add(f("gjcsImgs", "关节超声报告", "", "readonly"));
		m.images.add(f("csxdImgs", "超声心动报告", "", "readonly"));
		m.wx.put("checkDate", "gjjcWx");
		m.wx.put("checkNo", "gjjcWx");
		m.wx.put("xdt", "xdtWx");
		m.wx.put("gjcs", "gjcsWx");
		m.wx.put("csxd", "csxdWx");
		m.wxMap = "xcgWx";
		m.hidden.addAll(Arrays.asList("csxdWx", "gjjcWx", "xcgWx", "xdtWx", "gjcsWx"));
		list.add(m);

		// 病情评估（bqpg）
		List<String> joints = Arrays.asList("左肩", "左肘", "左腕", "右肩", "右肘", "右腕", "左膝", "右膝", "无");
		m = new Module("assessment", "病情评估", "bqpg");
		m.groups.add(new Group("HAQ 健康评估问卷",
				f("q1", "1. 能自己穿衣吗（包括系鞋带、纽扣）", "", "haq"),
				f("q2", "2. 能自己梳头吗", "", "haq"),
				f("q3", "3. 能从无扶手椅子中直接站起来吗", "", "haq"),
				f("q4", "4. 能自己上床 / 下床吗", "", "haq"),
				f("q5", "5. 能拿筷子吗", "", "haq"),
				f("q6", "6. 能将装满水的杯子送到嘴边吗", "", "haq"),
				f("q7", "7. 能开启未启封的易拉罐吗", "", "haq"),
				f("q8", "8. 能出门在路上行走吗", "", "haq"),
				f("q9", "9. 能上 5 个楼梯台阶吗", "", "haq"),
				f("q10", "10. 能自己洗澡并擦干身体吗", "", "haq"),
				f("q11", "11. 能洗盆浴吗", "", "haq"),
				f("q12", "12. 能自己上厕所吗", "", "haq"),
				f("q13", "13. 能伸手摘下衣架上的衣帽吗", "", "haq"),
				f("q14", "14. 能弯腰拾起地上东西吗", "", "haq"),
				f("q15", "15. 能用钥匙开门吗", "", "haq"),
				f("q16", "16. 能打开已打开过的罐头瓶吗", "", "haq"),
				f("q17", "17. 能开关水龙头吗", "", "haq"),
				f("q18", "18. 能出门到商店购物吗", "", "haq"),
				f("q19", "19. 能上下公共汽车或出租车吗", "", "haq"),
				f("q20", "20. 能做家务吗（如扫地、收拾房间、洗菜）", "", "haq")));
		m.groups.add(new Group("关节评估",
				f("zzgj", "肿胀关节", "", "list").opts(joints),
				f("zzgjHand", "肿胀关节 · 手部（编号）", "", "list"),
				f("ytgj", "压痛关节", "", "list").opts(joints),
				f("ytgjHand", "压痛关节 · 手部（编号）", "", "list")));
		m.groups.add(new Group("VAS 评分（0~100）",
				f("tjScore", "患者疼痛 VAS", "分", "number"),
				f("ztScoreByPatient", "疾病总体状况 VAS（患者）", "分", "number"),
				f("ztScoreByDoctor", "疾病总体状况 VAS（医生）", "分", "number")));
		m.groups.add(new Group("脏器受累",
				f("zqsl", "脏器受累", "", "radio").opts("是", "否"),
				f("zqslItems", "受累脏器", "", "list").opts("肺、胸膜病变", "心脏病变", "肾脏病变", "神经病变", "类风湿血管炎", "其他系统").when("zqsl:是"),
				f("xfmbbItems", "肺、胸膜病变 · 明细", "", "list").when("zqslItems:肺、胸膜病变"),
				f("xfmbbOtherItem", "肺、胸膜病变 · 其他", "", "text").when("zqslItems:肺、胸膜病变"),
				f("xzbjItems", "心脏病变 · 明细", "", "list").when("zqslItems:心脏病变"),
				f("xzbjOtherItem", "心脏病变 · 其他", "", "text").when("zqslItems:心脏病变"),
				f("szbjItems", "肾脏病变 · 明细", "", "list").when("zqslItems:肾脏病变"),
				f("szbjOtherItem", "肾脏病变 · 其他", "", "text").when("zqslItems:肾脏病变"),
				f("sjbjItems", "神经病变 · 明细", "", "list").when("zqslItems:神经病变"),
				f("sjbjOtherItem", "神经病变 · 其他", "", "text").when("zqslItems:神经病变"),
				f("lfsxgyItems", "类风湿血管炎 · 明细", "", "list").when("zqslItems:类风湿血管炎"),
				f("lfsxgyOtherItem", "类风湿血管炎 · 其他", "", "text").when("zqslItems:类风湿血管炎"),
				f("qtbjItems", "其他系统 · 明细", "", "list").when("zqslItems:其他系统"),
				f("qtbjOtherItem", "其他系统 · 其他", "", "text").when("zqslItems:其他系统"),
				f("zqslOtherItem", "其他受累", "", "text").when("zqsl:是")));
		m.groups.add(new Group("本次评估结果",
				f("result.zzgjs", "肿胀关节数", "个", "computed"),
				f("result.ytgjs", "压痛关节数", "个", "computed"),
				f("result.crpScore", "DAS28-CRP", "", "computed"),
				f("result.esrScore", "DAS28-ESR", "", "computed"),
				f("hqaScore", "HAQ 健康评分", "", "computed"),
				f("result.lastCrpScore", "上次 DAS28-CRP", "", "readonly"),
				f("result.lastEsrScore", "上次 DAS28-ESR", "", "readonly"),
				f("acr20", "ACR20", "", "readonly"),
				f("acr50", "ACR50", "", "readonly"),
				f("acr70", "ACR70", "", "readonly")));
		m.hidden.addAll(Arrays.asList("result.acr20", "result.acr50", "result.acr70", "result.hqaScore"));
		list.add(m);

		// 中医诊断（zyzd）
		List<String> syndromes = new ArrayList<String>(SYNDROMES);
		syndromes.add("暂无");
		m = new Module("tcm", "中医诊断", "zyzd");
		m.groups.add(new Group("证型",
				f("zz", "主证", "", "radio").opts(syndromes),
				f("zzzz", "主证 · 主症", "", "list").from("zz", "zhuzheng"),
				f("zzcz", "主证 · 次症", "", "list").from("zz", "cizheng"),
				f("jz", "兼证", "", "radio").opts(syndromes),
				f("jzzz", "兼证 · 主症", "", "list").from("jz", "zhuzheng"),
				f("jzcz", "兼证 · 次症", "", "list").from("jz", "cizheng")));
		m.groups.add(new Group("舌脉",
				f("ss", "舌色", "", "radioList").opts("淡红", "淡白", "红", "绛", "紫", "暗"),
				f("sx", "舌形", "", "list").opts("苍老", "娇嫩", "胖大", "瘦薄", "芒刺", "裂纹", "齿痕", "正常"),
				f("tz", "苔质", "", "list").opts("厚", "薄", "润", "滑", "燥", "腐", "腻", "剥"),
				f("ts", "苔色", "", "radio").opts("白", "黄", "灰", "黑"),
				f("mx", "脉象", "", "list").opts("浮", "沉", "弦", "滑", "细", "数", "缓", "结", "代", "迟", "其他")));
		m.images.add(f("smImgs", "舌面", "", "readonly"));
		m.images.add(f("sdImgs", "舌底", "", "readonly"));
		m.images.add(f("mbImgs", "面部", "", "readonly"));
		list.add(m);

		// 治疗方案（zlfa）
		m = new Module("treatment", "治疗方案", "zlfa");
		Field tz = f("tz", "与上次相比治疗方案是否调整", "", "radio").opts("是", "否");
		tz.notBaseline = true;
		m.groups.add(new Group("方案调整",
				tz,
				f("tz_reason", "调整原因", "", "radio").opts("疗效不佳", "不良事件", "经济原因", "其他").when("tz:是"),
				f("tz_reason_qt", "其他调整原因", "", "text").when("tz_reason:其他")));
		for (String[] t : new String[][] { { "xyList", "西药" }, { "zcyList", "中成药" } }) {
			m.tables.add(new Table(t[0], t[1],
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
					f("cost", "花费", "元", "text")).edit(
					f("drugName", "药品名称", "", "text"),
					f("goodsName", "商品名称", "", "text"),
					f("category", "药品类别", "", "text"),
					f("company", "生产厂家", "", "text"),
					f("dosis", "剂量", "", "text"),
					f("dosisUnit", "剂量单位", "", "text"),
					f("drugDelivery", "给药方式", "", "text"),
					f("drugFreq", "给药频次", "", "text"),
					f("startTime", "起始时间", "", "date"),
					f("endTime", "结束时间", "", "date"),
					f("tygc", "调药过程", "", "text"),
					f("tyyy", "调药原因", "", "text"),
					f("cost", "花费（元）", "", "text")));
		}
		m.tables.add(new Table("cyList", "中药饮片",
				f("zz", "证型", "", "text"),
				f("fj", "方剂", "", "text"),
				f("bccy", "草药", "", "text"),
				f("qtbccy", "补充草药", "", "text")).edit(
				f("zz", "证型", "", "radio").opts(SYNDROMES),
				f("fj", "方剂", "", "radio").from("zz", "fangji"),
				f("bccy", "草药", "", "list"),
				f("qtbccy", "补充草药", "", "text")));
		list.add(m);

		// 不良反应（blsj）：没填的不写 key；写入顺序对齐老数据 event → startDate → endDate → action → badCaseList
		m = new Module("adverse", "不良反应", "blsj");
		m.groups.add(new Group("",
				f("event", "不良反应", "", "radio").opts("无", "有"),
				f("badCaseList", "不良反应名称", "", "list").opts("白细胞下降", "血小板下降", "中性粒细胞下降", "贫血", "全血细胞下降", "淋巴细胞下降", "口腔溃疡",
						"口/舌疼痛", "恶心、呕吐", "腹泻", "腹痛", "肝功异常", "消化不良", "咳嗽", "咳痰", "哮喘", "呼吸困难", "血尿", "蛋白尿", "肾功异常", "肾衰竭", "少尿",
						"心梗", "头晕", "头痛", "失眠", "皮疹", "皮肤瘙痒", "色素沉着", "月经延迟", "肿瘤", "体重减轻", "脱发", "结节", "感染").when("event:有"),
				f("startDate", "发生日期", "", "date").when("event:有"),
				f("endDate", "结束日期", "", "date").when("event:有"),
				f("sae", "SAE类别", "", "list").opts("致命", "危及生命", "需要住院治疗或延长住院时间", "导致永久或严重的残疾", "能力丧失", "母亲使用研究药物导致新生儿不良事件", "由医生判断为重大医疗事件").when("event:有"),
				f("action", "采取与药物的相关措施", "", "list").opts("剂量不变", "减小剂量", "停止用药", "对症治疗").when("event:有"),
				f("otherAction", "其他措施", "", "textarea").when("event:有"),
				f("eventDetail", "不良事件详情", "", "textarea").when("event:有")));
		m.writeOrder.addAll(Arrays.asList("event", "startDate", "endDate", "action", "badCaseList", "sae", "otherAction", "eventDetail"));
		list.add(m);

		// 不良事件（bblsj）：用途待确认，只显示老数据，新增随访不写
		m = new Module("adverseEvent", "不良事件", "bblsj");
		list.add(m);
		MODULES = Collections.unmodifiableList(list);
	}

	private VisitFieldDict() {
	}
}
