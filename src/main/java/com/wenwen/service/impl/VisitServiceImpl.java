package com.wenwen.service.impl;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.wenwen.mapper.VisitMapper;
import com.wenwen.service.AuditLogService;
import com.wenwen.service.VisitService;
import com.wenwen.util.BizException;
import com.wenwen.util.VisitFieldDict;
import com.wenwen.util.VisitJson;
import com.wenwen.vo.FieldChange;
import com.wenwen.vo.VisitCreateRequest;
import com.wenwen.vo.VisitCreateResultVo;
import com.wenwen.vo.VisitDetailVo;
import com.wenwen.vo.VisitEditFieldVo;
import com.wenwen.vo.VisitEditFormVo;
import com.wenwen.vo.VisitEditGroupVo;
import com.wenwen.vo.VisitEditModuleVo;
import com.wenwen.vo.VisitEditTableVo;
import com.wenwen.vo.VisitModuleUpdate;
import com.wenwen.vo.VisitUpdateRequest;
import com.wenwen.vo.VisitUpdateResultVo;
import com.wenwen.vo.VisitFieldGroupVo;
import com.wenwen.vo.VisitFieldVo;
import com.wenwen.vo.VisitImageGroupVo;
import com.wenwen.vo.VisitModuleVo;
import com.wenwen.vo.VisitTableVo;

@Service
public class VisitServiceImpl implements VisitService {

	private static final String NOT_CHECKED = "未查";

	@Autowired
	private VisitMapper visitMapper;

    @Autowired
    private com.wenwen.mapper.PatientMapper patientMapper;

	@Autowired
	private AuditLogService auditLogService;

	@Override
	public VisitDetailVo getVisitDetail(Long doctorId, Long visitId) {
		if (doctorId == null || visitId == null) {
			throw new IllegalArgumentException("缺少医生ID或随访ID");
		}
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("doctorId", doctorId);
		map.put("visitId", visitId);
		Map<String, Object> row = visitMapper.getVisit(map);
		if (row == null) {
			throw new BizException("403", "随访记录不存在，或患者不在您名下");
		}

		VisitDetailVo vo = new VisitDetailVo();
		vo.setVisitId(toLong(row.get("visitId")));
		vo.setPatientId(toLong(row.get("patientId")));
		vo.setVisitDate((String) row.get("visitDate"));
		vo.setDoctorId(toLong(row.get("doctorId")));
		vo.setDoctorName(VisitJson.blankToNull((String) row.get("doctorName")));
		map.put("patientId", vo.getPatientId());
		int number=com.wenwen.ai.patient.PatientFollowUpPolicy.numbers(patientMapper.listVisits(map)).get(vo.getVisitId());
        vo.setFollowUpNumber(number);
        vo.setBaseline(number==0);
        vo.setVisitType(com.wenwen.ai.patient.PatientFollowUpPolicy.visitType(number));

		List<VisitModuleVo> modules = new ArrayList<VisitModuleVo>();
		int filled = 0;
		for (VisitFieldDict.Module m : VisitFieldDict.MODULES) {
			VisitModuleVo mod = toModule(m, (String) row.get(m.column));
			if (mod.isFilled()) {
				filled++;
			}
			modules.add(mod);
		}
		vo.setModules(modules);
		vo.setFilledCount(filled);
		return vo;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public Long deleteVisit(Long doctorId, Long visitId, String reason) {
		reason = reason == null ? "" : reason.trim();
		if (doctorId == null || visitId == null) {
			throw new IllegalArgumentException("缺少医生ID或随访ID");
		}
		if (reason.isEmpty()) {
			throw new IllegalArgumentException("请填写删除原因");
		}
		if (reason.length() > 500) {
			throw new IllegalArgumentException("删除原因不能超过 500 字");
		}
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("doctorId", doctorId);
		map.put("visitId", visitId);
		Map<String, Object> row = visitMapper.getVisit(map);
		if (row == null) {
			throw new BizException("403", "随访记录不存在，或患者不在您名下");
		}
		Long patientId = toLong(row.get("patientId"));
		map.put("patientId", patientId);
		boolean baseline = visitId.equals(visitMapper.getBaselineVisitId(map));
		String visitDate = (String) row.get("visitDate");

		// 1. 接好「上一次随访」链条；2. 删除
		map.put("prevVisitId", toLong(row.get("lastFollowUpId")));
		visitMapper.relinkNextVisit(map);
		visitMapper.deleteVisit(map);
		// 3、4. 老系统的计数和日期按剩下的随访重新计算
		visitMapper.refreshPatientCounters(map);
		map.put("visitDoctorId", toLong(row.get("doctorId")));
		map.put("researchType", row.get("researchType"));
		visitMapper.refreshRelationCounters(map);
		// 5. 修改记录（只记删除原因，不备份数据）
		String operator = VisitJson.blankToNull(visitMapper.getUserName(map));
		auditLogService.record(patientId, visitId, "删除随访", null,
				(baseline ? "基线访视" : "常规随访") + " " + (visitDate == null ? "日期未填" : visitDate) + "；删除原因：" + reason,
				doctorId, operator == null ? "医生 ID " + doctorId : operator);
		return patientId;
	}

	// ===================== 编辑随访 =====================

	@Override
	public VisitEditFormVo getEditForm(Long doctorId, Long visitId) {
		Map<String, Object> row = loadVisit(doctorId, visitId);
		VisitEditFormVo form = new VisitEditFormVo();
		form.setVisitId(visitId);
		form.setPatientId(toLong(row.get("patientId")));
		form.setVisitDate((String) row.get("visitDate"));
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("patientId", form.getPatientId());
		form.setVisitType(visitId.equals(visitMapper.getBaselineVisitId(map)) ? "基线访视" : "常规随访");
		form.setVersion(version(row));
		List<VisitEditModuleVo> modules = new ArrayList<VisitEditModuleVo>();
		for (VisitFieldDict.Module m : VisitFieldDict.MODULES) {
			modules.add(editModule(m, (String) row.get(m.column)));
		}
		form.setModules(modules);
		form.setOptionSets(VisitFieldDict.OPTION_SETS);
		return form;
	}

	private VisitEditModuleVo editModule(VisitFieldDict.Module m, String raw) {
		VisitEditModuleVo mod = new VisitEditModuleVo();
		mod.setKey(m.key);
		mod.setTitle(m.title);
		mod.setColumn(m.column);
		mod.setGroups(new ArrayList<VisitEditGroupVo>());
		mod.setTables(new ArrayList<VisitEditTableVo>());
		JSONObject json = VisitJson.parse(raw);
		String text = VisitJson.blankToNull(raw);
		if (text != null && (json == null || json.containsKey("record"))) {
			// 普通文字 / {record, date}：不是老系统的结构化格式，只读显示
			mod.setEditable(false);
			mod.setRecord(json == null ? text : VisitJson.blankToNull(json.getString("record")));
			return mod;
		}
		mod.setEditable(true);
		if (json == null) {
			json = new JSONObject(true);
		}
		JSONObject wxMap = m.wxMap != null && json.get(m.wxMap) instanceof JSONObject ? json.getJSONObject(m.wxMap) : null;
		for (VisitFieldDict.Group g : m.groups) {
			List<VisitEditFieldVo> fields = new ArrayList<VisitEditFieldVo>();
			for (VisitFieldDict.Field f : g.fields) {
				VisitEditFieldVo fv = editField(f, f.key.startsWith("@") ? null : VisitJson.path(json, f.key));
				if (m.wx.containsKey(f.key)) {
					fv.setWx(VisitJson.isTrue(json.get(m.wx.get(f.key))));
				} else if (m.wxMap != null && !f.isList() && !f.key.startsWith("@")) {
					fv.setWx(wxMap != null && VisitJson.isTrue(wxMap.get(f.key)));
				}
				fields.add(fv);
			}
			VisitEditGroupVo gv = new VisitEditGroupVo();
			gv.setTitle(g.title);
			gv.setFields(fields);
			mod.getGroups().add(gv);
		}
		for (VisitFieldDict.Table t : m.tables) {
			VisitEditTableVo tv = new VisitEditTableVo();
			tv.setKey(t.key);
			tv.setTitle(t.title);
			List<VisitEditFieldVo> cols = new ArrayList<VisitEditFieldVo>();
			for (VisitFieldDict.Field c : t.editColumns) {
				cols.add(editField(c, null));
			}
			tv.setColumns(cols);
			List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
			JSONArray arr = json.get(t.key) instanceof JSONArray ? json.getJSONArray(t.key) : new JSONArray();
			for (int i = 0; i < arr.size(); i++) {
				if (!(arr.get(i) instanceof JSONObject)) {
					continue;
				}
				JSONObject r = arr.getJSONObject(i);
				Map<String, Object> out = new LinkedHashMap<String, Object>();
				out.put("_row", i);
				for (VisitFieldDict.Field c : t.editColumns) {
					out.put(c.key, editValue(c.type, r.get(c.key)));
				}
				rows.add(out);
			}
			tv.setRows(rows);
			mod.getTables().add(tv);
		}
		// 图片、字典外字段：只读，沿用访视详情的解析
		VisitModuleVo view = toModule(m, raw);
		mod.setImages(view.getImages());
		mod.setOthers(new ArrayList<VisitFieldVo>());
		for (VisitFieldGroupVo g : view.getGroups()) {
			if ("其他字段".equals(g.getTitle())) {
				mod.getOthers().addAll(g.getItems());
			}
		}
		return mod;
	}

	private static VisitEditFieldVo editField(VisitFieldDict.Field f, Object value) {
		VisitEditFieldVo fv = new VisitEditFieldVo();
		fv.setKey(f.key);
		fv.setLabel(f.label);
		fv.setUnit(f.unit);
		fv.setType(f.type);
		fv.setValue(editValue(f.type, value));
		fv.setOptions(f.options.isEmpty() ? null : f.options);
		fv.setShowIf(f.showIf);
		fv.setOptionsFrom(f.optionsFrom);
		fv.setOptionSet(f.optionSet);
		fv.setNotBaseline(f.notBaseline ? Boolean.TRUE : null);
		return fv;
	}

	/** 表单值：多选为字符串数组，其它为字符串 */
	private static Object editValue(String type, Object value) {
		if (VisitFieldDict.isList(type)) {
			List<String> items = new ArrayList<String>();
			if (value instanceof Collection) {
				for (Object o : (Collection<?>) value) {
					String s = VisitJson.text(o);
					if (s != null) {
						items.add(s);
					}
				}
			} else if (VisitJson.text(value) != null) {
				items.add(VisitJson.text(value));
			}
			return items;
		}
		return VisitJson.text(value);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public VisitUpdateResultVo updateVisit(VisitUpdateRequest req) {
		if (req == null || req.getVisitId() == null) {
			throw new IllegalArgumentException("缺少随访ID");
		}
		Map<String, Object> row = loadVisit(req.getDoctorId(), req.getVisitId());
		if (req.getVersion() == null || !req.getVersion().equals(version(row))) {
			throw new BizException("409", "这次随访在您打开后已被修改（可能是老系统或其他人），请刷新页面后重新编辑");
		}
		Long patientId = toLong(row.get("patientId"));
		Map<String, VisitModuleUpdate> ups = req.getModules() == null ? new HashMap<String, VisitModuleUpdate>() : req.getModules();
		VisitEditor editor = new VisitEditor();
		Map<String, Object> update = new HashMap<String, Object>();
		update.put("visitId", req.getVisitId());
		Map<String, Object> originals = new HashMap<String, Object>();
		update.put("originals", originals);

		// 随访日期
		String oldDate = (String) row.get("visitDate");
		String newDate = VisitJson.blankToNull(req.getVisitDate());
		boolean dateChanged = newDate != null && !newDate.equals(oldDate);
		if (dateChanged) {
			LocalDate d;
			try {
				d = LocalDate.parse(newDate);
			} catch (Exception e) {
				throw new IllegalArgumentException("随访日期格式应为 yyyy-MM-dd");
			}
			if (d.isAfter(LocalDate.now())) {
				throw new IllegalArgumentException("随访日期不能晚于今天");
			}
			update.put("visitDate", newDate);
			update.put("originalFollowUpDate", row.get("originalFollowUpDate"));
			update.put("originalCamelDate", row.get("originalCamelDate"));
			editor.changes.add(new FieldChange("follow_up_date", "随访日期", oldDate, newDate));
		}

		// 各模块合并（只处理结构化模块）
		Map<String, JSONObject> objs = new HashMap<String, JSONObject>();
		Map<String, Set<String>> changed = new HashMap<String, Set<String>>();
		for (VisitFieldDict.Module m : VisitFieldDict.MODULES) {
			String raw = (String) row.get(m.column);
			JSONObject json = VisitJson.parse(raw);
			boolean structured = VisitJson.blankToNull(raw) == null || (json != null && !json.containsKey("record"));
			if (!structured) {
				continue;
			}
			boolean isNew = json == null;
			JSONObject obj = isNew ? new JSONObject(true) : json;
			Set<String> keys = editor.apply(m, obj, ups.get(m.column));
			// 改了随访日期：病史病情里的随诊时间同步（原来有这个字段才改）
			if (dateChanged && "bsbq".equals(m.column) && obj.containsKey("followDate") && !newDate.equals(obj.getString("followDate"))) {
				obj.put("followDate", newDate);
				keys.add("followDate");
			}
			if (!keys.isEmpty() && isNew) {
				obj.put("finish", true); // 新填的模块，老系统前端按 finish 显示已填写
			}
			objs.put(m.column, obj);
			changed.put(m.column, keys);
		}
		if (editor.recompute(objs.get("fzjc"), objs.get("bqpg"),
				changed.containsKey("fzjc") ? changed.get("fzjc") : new HashSet<String>(),
				changed.containsKey("bqpg") ? changed.get("bqpg") : new HashSet<String>())) {
			changed.get("bqpg").add("result");
		}
		for (Map.Entry<String, Set<String>> e : changed.entrySet()) {
			if (!e.getValue().isEmpty()) {
				update.put(e.getKey(), VisitJson.write(objs.get(e.getKey())));
				originals.put(e.getKey(), row.get(e.getKey()));
			}
		}

		// 保护本次写回的DAS来源，即使化验未编辑、缺失或重评分数恰好不变。
		if (editor.dasReevaluated && update.containsKey("bqpg")) {
			originals.put("fzjc", row.get("fzjc"));
			originals.put("bqpg", row.get("bqpg"));
		}

		VisitUpdateResultVo result = new VisitUpdateResultVo();
		List<String> texts = new ArrayList<String>();
		for (FieldChange c : editor.changes) {
			texts.add(c.toText());
		}
		result.setChanges(texts);
		result.setChangedCount(editor.changes.size());
		if (editor.changes.isEmpty()) {
			return result;
		}
		if (visitMapper.updateVisit(update) == 0) {
			throw new BizException("409", "这次随访在保存前已被修改或删除，请刷新页面后重新编辑");
		}
		if (dateChanged) {
			// 老系统维护的随访次数 / 日期按新日期重算
			Map<String, Object> map = new HashMap<String, Object>();
			map.put("patientId", patientId);
			map.put("visitDoctorId", toLong(row.get("doctorId")));
			map.put("researchType", row.get("researchType"));
			visitMapper.refreshPatientCounters(map);
			visitMapper.refreshRelationCounters(map);
		}
		Map<String, Object> um = new HashMap<String, Object>();
		um.put("doctorId", req.getDoctorId());
		String operator = VisitJson.blankToNull(visitMapper.getUserName(um));
		um.put("patientId", patientId);
		boolean baseline = req.getVisitId().equals(visitMapper.getBaselineVisitId(um));
		auditLogService.record(patientId, req.getVisitId(), "编辑随访", editor.changes,
				null, req.getDoctorId(), operator == null ? "医生 ID " + req.getDoctorId() : operator);
		return result;
	}

	// ===================== 新增随访 =====================

	/** 各模块列的长度上限（老表 varchar；zlfa 是 text） */
	private static final Map<String, Integer> COLUMN_MAX = new HashMap<String, Integer>();
	static {
		for (String c : new String[] { "bsbq", "fzjc", "zyzd", "blsj", "bblsj" }) {
			COLUMN_MAX.put(c, 2000);
		}
		COLUMN_MAX.put("bqpg", 4000);
		COLUMN_MAX.put("zlfa", 65535 / 3);
	}

	@Override
	public VisitEditFormVo getCreateForm(Long doctorId, Long patientId) {
		Map<String, Object> rel = myRaRelation(doctorId, patientId);
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("patientId", patientId);
		List<Map<String, Object>> visits = visitMapper.listPatientVisits(map);
		Map<String, Object> basic = visitMapper.getPatientForVisit(map);
		String today = LocalDate.now().toString();

		VisitEditFormVo form = new VisitEditFormVo();
		form.setPatientId(patientId);
		form.setVisitDate(today);
		form.setVisitNo(visits.size() + 1);
		form.setVisitType(visits.isEmpty() ? "基线访视" : "常规随访");
		form.setOptionSets(VisitFieldDict.OPTION_SETS);
		List<VisitEditModuleVo> modules = new ArrayList<VisitEditModuleVo>();
		for (VisitFieldDict.Module m : VisitFieldDict.MODULES) {
			if ("bblsj".equals(m.column)) {
				continue; // 不良事件（bblsj）用途待确认，新增随访不写
			}
			VisitEditModuleVo mod = editModule(m, null);
			mod.setImages(new ArrayList<VisitImageGroupVo>());
			mod.setOthers(new ArrayList<VisitFieldVo>());
			modules.add(mod);
		}
		form.setModules(modules);

		// 病史病情：基本信息里已有的自动带入（医生可改）
		String firstDate = null;
		for (Map<String, Object> v : visits) {
			JSONObject b = VisitJson.parse((String) v.get("bsbq"));
			firstDate = b == null ? null : VisitJson.blankToNull(b.getString("firstDate"));
			if (firstDate != null) {
				break;
			}
		}
		if (firstDate == null && !visits.isEmpty()) {
			firstDate = (String) visits.get(0).get("visitDate");
		}
		prefill(modules, "bsbq", "firstDate", firstDate == null && visits.isEmpty() ? today : firstDate);
		prefill(modules, "bsbq", "followDate", today);
		if (basic != null) {
			prefill(modules, "bsbq", "happenDate", basic.get("happenDate"));
			prefill(modules, "bsbq", "confirmDate", basic.get("confirmDate"));
			if (basic.get("gender") != null && ((Number) basic.get("gender")).intValue() == 1) {
				List<String> male = new ArrayList<String>();
				male.add("男性");
				prefill(modules, "bsbq", "menstruation", male);
			}
		}
		return form;
	}

	private static void prefill(List<VisitEditModuleVo> modules, String column, String key, Object value) {
		if (value == null) {
			return;
		}
		for (VisitEditModuleVo m : modules) {
			if (!column.equals(m.getColumn())) {
				continue;
			}
			for (VisitEditGroupVo g : m.getGroups()) {
				for (VisitEditFieldVo f : g.getFields()) {
					if (key.equals(f.getKey())) {
						f.setValue(value);
					}
				}
			}
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public VisitCreateResultVo createVisit(VisitCreateRequest req) {
		if (req == null || req.getDoctorId() == null || req.getPatientId() == null) {
			throw new IllegalArgumentException("缺少医生ID或患者ID");
		}
		Long doctorId = req.getDoctorId(), patientId = req.getPatientId();
		Map<String, Object> rel = myRaRelation(doctorId, patientId);
		String visitDate = VisitJson.blankToNull(req.getVisitDate());
		if (visitDate == null) {
			throw new IllegalArgumentException("请填写随访日期");
		}
		LocalDate d;
		try {
			d = LocalDate.parse(visitDate);
		} catch (Exception e) {
			throw new IllegalArgumentException("随访日期格式应为 yyyy-MM-dd");
		}
		if (d.isAfter(LocalDate.now())) {
			throw new IllegalArgumentException("随访日期不能晚于今天");
		}
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("patientId", patientId);
		List<Map<String, Object>> visits = visitMapper.listPatientVisits(map);

		// 各模块：从空对象开始合并表单，没填的字段不写 key（与编辑随访同一套转换）
		Map<String, VisitModuleUpdate> ups = req.getModules() == null ? new HashMap<String, VisitModuleUpdate>() : req.getModules();
		VisitEditor editor = new VisitEditor();
		Map<String, JSONObject> objs = new HashMap<String, JSONObject>();
		Map<String, Set<String>> changed = new HashMap<String, Set<String>>();
		for (VisitFieldDict.Module m : VisitFieldDict.MODULES) {
			if ("bblsj".equals(m.column)) {
				continue;
			}
			JSONObject obj = new JSONObject(true);
			changed.put(m.column, editor.apply(m, obj, ups.get(m.column)));
			objs.put(m.column, obj);
		}
		editor.recompute(objs.get("fzjc"), objs.get("bqpg"), changed.get("fzjc"), changed.get("bqpg"));
		// 上次 DAS28：取上一次随访的本次结果（老系统同样记在 result.lastCrpScore / lastEsrScore）
		JSONObject bqpg = objs.get("bqpg");
		Map<String, Object> prev = latest(visits);
		if (!bqpg.isEmpty() && prev != null) {
			JSONObject pb = VisitJson.parse((String) prev.get("bqpg"));
			for (String[] k : new String[][] { { "crpScore", "lastCrpScore" }, { "esrScore", "lastEsrScore" } }) {
				Object v = pb == null ? null : VisitJson.path(pb, "result." + k[0]);
				if (VisitJson.text(v) != null) {
					VisitJson.setPath(bqpg, "result." + k[1], v);
				}
			}
		}

		Map<String, Object> ins = new HashMap<String, Object>();
		int filled = 0;
		for (VisitFieldDict.Module m : VisitFieldDict.MODULES) {
			JSONObject obj = objs.get(m.column);
			if (obj == null || obj.isEmpty()) {
				continue; // 没填的模块存 NULL（与老系统未填写一致）
			}
			obj = ordered(m, obj);
			obj.put("finish", true);
			String json = VisitJson.write(obj);
			if (json.length() > COLUMN_MAX.get(m.column)) {
				throw new IllegalArgumentException(m.title + "内容太长（" + json.length() + " 字，最多 " + COLUMN_MAX.get(m.column) + " 字），请精简文字后再保存");
			}
			ins.put(m.column, json);
			filled++;
		}
		ins.put("patientId", patientId);
		ins.put("doctorId", doctorId);
		ins.put("visitDate", visitDate);
		ins.put("researchType", rel.get("researchType"));
		ins.put("lastVisitId", prev == null ? null : prev.get("visitId"));
		visitMapper.insertVisit(ins);
		Long visitId = toLong(ins.get("id"));

		map.put("visitDoctorId", doctorId);
		map.put("researchType", rel.get("researchType"));
		visitMapper.refreshPatientCounters(map);
		visitMapper.refreshRelationCounters(map);

		Map<String, Object> um = new HashMap<String, Object>();
		um.put("doctorId", doctorId);
		String operator = VisitJson.blankToNull(visitMapper.getUserName(um));
		um.put("patientId", patientId);
		boolean baseline = visitId.equals(visitMapper.getBaselineVisitId(um));
		auditLogService.record(patientId, visitId, "新增随访", null,
				(baseline ? "基线访视" : "常规随访") + " " + visitDate + "；填写了 " + filled + " 个模块",
				doctorId, operator == null ? "医生 ID " + doctorId : operator);

		VisitCreateResultVo vo = new VisitCreateResultVo();
		vo.setVisitId(visitId);
		vo.setPatientId(patientId);
		vo.setFilledCount(filled);
		return vo;
	}

	/** 本医生名下该患者的 RA 医患关系；不在名下 403，已脱落 409 */
	private Map<String, Object> myRaRelation(Long doctorId, Long patientId) {
		if (doctorId == null || patientId == null) {
			throw new IllegalArgumentException("缺少医生ID或患者ID");
		}
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("doctorId", doctorId);
		map.put("patientId", patientId);
		List<Map<String, Object>> rels = visitMapper.listMyRaRelations(map);
		if (rels == null || rels.isEmpty()) {
			throw new BizException("403", "患者不存在，或不在您名下");
		}
		for (Map<String, Object> r : rels) {
			if (((Number) r.get("miss")).intValue() != 1) {
				return r;
			}
		}
		throw new BizException("409", "该患者已标记脱落，请先在患者详情点「恢复随访」");
	}

	/** 时间最晚的一次随访（列表按时间升序，取最后一条） */
	private static Map<String, Object> latest(List<Map<String, Object>> visits) {
		Map<String, Object> last = null;
		for (Map<String, Object> v : visits) {
			if (v.get("visitDate") != null || last == null) {
				last = v;
			}
		}
		return last;
	}

	/** 按模块的写入顺序（writeOrder，其次字典顺序）重排字段，对齐老系统 JSON 的字段顺序 */
	private static JSONObject ordered(VisitFieldDict.Module m, JSONObject obj) {
		List<String> order = new ArrayList<String>(m.writeOrder);
		for (VisitFieldDict.Group g : m.groups) {
			for (VisitFieldDict.Field f : g.fields) {
				String k = f.key.contains(".") ? f.key.substring(0, f.key.indexOf('.')) : f.key;
				if (!order.contains(k)) {
					order.add(k);
				}
				String wx = m.wx.get(f.key);
				if (wx != null && !order.contains(wx)) {
					order.add(wx);
				}
			}
		}
		JSONObject out = new JSONObject(true);
		for (String k : order) {
			if (obj.containsKey(k)) {
				out.put(k, obj.get(k));
			}
		}
		for (Map.Entry<String, Object> e : obj.entrySet()) {
			if (!out.containsKey(e.getKey())) {
				out.put(e.getKey(), e.getValue());
			}
		}
		return out;
	}

	/** 取随访并校验权限（只能是自己名下 RA 患者的随访） */
	private Map<String, Object> loadVisit(Long doctorId, Long visitId) {
		if (doctorId == null || visitId == null) {
			throw new IllegalArgumentException("缺少医生ID或随访ID");
		}
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("doctorId", doctorId);
		map.put("visitId", visitId);
		Map<String, Object> row = visitMapper.getVisit(map);
		if (row == null) {
			throw new BizException("403", "随访记录不存在，或患者不在您名下");
		}
		return row;
	}

	/** 数据版本：随访日期 + 7 个模块原文的 MD5，用于发现打开后被别人（含老系统）改过 */
	private static String version(Map<String, Object> row) {
		StringBuilder sb = new StringBuilder(String.valueOf(row.get("visitDate")));
		for (VisitFieldDict.Module m : VisitFieldDict.MODULES) {
			sb.append('\u0001').append(String.valueOf(row.get(m.column)));
		}
		try {
			byte[] d = MessageDigest.getInstance("MD5").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
			return String.format("%032x", new BigInteger(1, d));
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * 解析一个模块（只读，不改老数据）：
	 * 结构化 JSON → 按字段字典分组 / 清单 / 图片，字典外的字段放「其他字段」；
	 * {"record": "...", "date": "..."} 或普通文字 → record
	 */
	private static VisitModuleVo toModule(VisitFieldDict.Module dict, String raw) {
		VisitModuleVo mod = new VisitModuleVo();
		mod.setKey(dict.key);
		mod.setTitle(dict.title);
		mod.setColumn(dict.column);
		mod.setGroups(new ArrayList<VisitFieldGroupVo>());
		mod.setTables(new ArrayList<VisitTableVo>());
		mod.setImages(new ArrayList<VisitImageGroupVo>());
		String text = VisitJson.blankToNull(raw);
		if (text == null) {
			return mod;
		}
		JSONObject json = VisitJson.parse(text); // 保持原字段顺序
		if (json == null) {
			mod.setRecord(text);
		} else if (json.containsKey("record")) {
			mod.setRecord(VisitJson.blankToNull(json.getString("record")));
			mod.setRecordDate(VisitJson.blankToNull(json.getString("date")));
		} else {
			fillStructured(dict, json, mod);
		}
		mod.setFilled(mod.getRecord() != null || !mod.getGroups().isEmpty() || !mod.getTables().isEmpty() || !mod.getImages().isEmpty());
		return mod;
	}

	private static void fillStructured(VisitFieldDict.Module dict, JSONObject json, VisitModuleVo mod) {
		Set<String> used = new HashSet<String>(dict.hidden);
		JSONObject wxMap = dict.wxMap == null ? null : json.getJSONObject(dict.wxMap);

		for (VisitFieldDict.Group g : dict.groups) {
			List<VisitFieldVo> items = new ArrayList<VisitFieldVo>();
			for (VisitFieldDict.Field f : g.fields) {
				used.add(f.key);
				boolean notChecked = VisitJson.isTrue(dict.wx.containsKey(f.key) ? json.get(dict.wx.get(f.key)) : null)
						|| (wxMap != null && VisitJson.isTrue(wxMap.get(f.key)));
				String value = f.key.startsWith("@") ? null : VisitJson.text(VisitJson.path(json, f.key));
				if (value == null && !notChecked) {
					continue;
				}
				items.add(field(f.key, f.label, value == null ? NOT_CHECKED : value, value == null ? "" : f.unit, value == null));
			}
			if (!items.isEmpty()) {
				mod.getGroups().add(group(g.title, items));
			}
		}

		for (VisitFieldDict.Field img : dict.images) {
			used.add(img.key);
			List<String> urls = new ArrayList<String>();
			Object v = json.get(img.key);
			if (v instanceof Collection) {
				for (Object o : (Collection<?>) v) {
					String u = o == null ? null : VisitJson.blankToNull(String.valueOf(o));
					if (u != null) {
						urls.add(u);
					}
				}
			}
			if (!urls.isEmpty()) {
				VisitImageGroupVo ig = new VisitImageGroupVo();
				ig.setTitle(img.label);
				ig.setUrls(urls);
				mod.getImages().add(ig);
			}
		}

		for (VisitFieldDict.Table t : dict.tables) {
			used.add(t.key);
			JSONArray arr = json.get(t.key) instanceof JSONArray ? json.getJSONArray(t.key) : null;
			if (arr == null || arr.isEmpty()) {
				continue;
			}
			List<List<String>> cells = new ArrayList<List<String>>();
			for (Object o : arr) {
				if (!(o instanceof JSONObject)) {
					continue;
				}
				JSONObject r = (JSONObject) o;
				List<String> line = new ArrayList<String>();
				for (VisitFieldDict.Field c : t.columns) {
					line.add(cell(r, c));
				}
				cells.add(line);
			}
			// 只保留有内容的列
			List<String> columns = new ArrayList<String>();
			List<Integer> keep = new ArrayList<Integer>();
			for (int i = 0; i < t.columns.size(); i++) {
				for (List<String> line : cells) {
					if (line.get(i) != null) {
						keep.add(i);
						VisitFieldDict.Field c = t.columns.get(i);
						columns.add(c.unit.isEmpty() ? c.label : c.label + "（" + c.unit + "）");
						break;
					}
				}
			}
			if (keep.isEmpty()) {
				continue;
			}
			List<List<String>> rows = new ArrayList<List<String>>();
			for (List<String> line : cells) {
				List<String> out = new ArrayList<String>();
				for (int i : keep) {
					out.add(line.get(i) == null ? "" : line.get(i));
				}
				rows.add(out);
			}
			VisitTableVo tv = new VisitTableVo();
			tv.setTitle(t.title);
			tv.setColumns(columns);
			tv.setRows(rows);
			mod.getTables().add(tv);
		}

		// 字典外的字段：按原字段名放「其他字段」，不丢数据
		List<VisitFieldVo> others = new ArrayList<VisitFieldVo>();
		for (Map.Entry<String, Object> e : json.entrySet()) {
			String k = e.getKey();
			if (e.getValue() instanceof JSONObject && !(dict.wxMap != null && dict.wxMap.equals(k))) {
				for (Map.Entry<String, Object> sub : ((JSONObject) e.getValue()).entrySet()) {
					String sk = k + "." + sub.getKey();
					String v = VisitJson.text(sub.getValue());
					if (!used.contains(sk) && v != null) {
						others.add(field(sk, sk, v, "", false));
					}
				}
				continue;
			}
			String v = VisitJson.text(e.getValue());
			if (!used.contains(k) && v != null) {
				others.add(field(k, k, v, "", false));
			}
		}
		if (!others.isEmpty()) {
			mod.getGroups().add(group("其他字段", others));
		}
	}

	/** 清单单元格：a+b 拼接（剂量 + 单位），a~b 为区间（起 ~ 止） */
	private static String cell(JSONObject r, VisitFieldDict.Field c) {
		if (c.key.contains("+")) {
			String[] p = c.key.split("\\+");
			String a = VisitJson.text(r.get(p[0])), b = VisitJson.text(r.get(p[1]));
			return a == null ? null : (b == null ? a : a + " " + b);
		}
		if (c.key.contains("~")) {
			String[] p = c.key.split("~");
			String a = VisitJson.text(r.get(p[0])), b = VisitJson.text(r.get(p[1]));
			return a == null && b == null ? null : (a == null ? "" : a) + " ~ " + (b == null ? "" : b);
		}
		return VisitJson.text(r.get(c.key));
	}




	private static VisitFieldVo field(String key, String label, String value, String unit, boolean notChecked) {
		VisitFieldVo f = new VisitFieldVo();
		f.setKey(key);
		f.setLabel(label);
		f.setValue(value);
		f.setUnit(unit);
		f.setNotChecked(notChecked);
		return f;
	}

	private static VisitFieldGroupVo group(String title, List<VisitFieldVo> items) {
		VisitFieldGroupVo g = new VisitFieldGroupVo();
		g.setTitle(title);
		g.setItems(items);
		return g;
	}


	private static Long toLong(Object value) {
		return value == null ? null : ((Number) value).longValue();
	}
}
