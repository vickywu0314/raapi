package com.wenwen.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.Feature;
import com.wenwen.mapper.VisitMapper;
import com.wenwen.service.VisitService;
import com.wenwen.util.BizException;
import com.wenwen.vo.FieldChange;
import com.wenwen.vo.VisitDetailVo;
import com.wenwen.vo.VisitModuleVo;

@Service
public class VisitServiceImpl implements VisitService {

	/** 页面 7 个模块：编码、中文名、随访表字段（顺序即页面顺序） */
	private static final String[][] MODULES = {
		{ "history", "病史病情", "bsbq" },
		{ "exam", "辅助检查", "fzjc" },
		{ "assessment", "病情评估", "bqpg" },
		{ "tcm", "中医诊断", "zyzd" },
		{ "treatment", "治疗方案", "zlfa" },
		{ "adverse", "不良反应", "blsj" },
		{ "caseRecord", "随诊病例", "bblsj" },
	};

	@Autowired
	private VisitMapper visitMapper;

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
		vo.setDoctorName(blankToNull((String) row.get("doctorName")));
		map.put("patientId", vo.getPatientId());
		boolean baseline = vo.getVisitId().equals(visitMapper.getBaselineVisitId(map));
		vo.setBaseline(baseline);
		vo.setVisitType(baseline ? "基线访视" : "常规随访");

		List<VisitModuleVo> modules = new ArrayList<VisitModuleVo>();
		int filled = 0;
		for (String[] m : MODULES) {
			VisitModuleVo mod = toModule(m[0], m[1], m[2], (String) row.get(m[2]));
			if (mod.isFilled()) {
				filled++;
			}
			modules.add(mod);
		}
		vo.setModules(modules);
		vo.setFilledCount(filled);
		return vo;
	}

	/**
	 * 解析模块内容：
	 * {"record": "...", "date": "..."} → record + recordDate；
	 * 其它 JSON 对象 → 逐项「名称：值」；
	 * 不是 JSON → 原文作为 record
	 */
	private static VisitModuleVo toModule(String key, String title, String column, String raw) {
		VisitModuleVo mod = new VisitModuleVo();
		mod.setKey(key);
		mod.setTitle(title);
		mod.setColumn(column);
		mod.setItems(new ArrayList<FieldChange>());
		String text = blankToNull(raw);
		if (text == null) {
			return mod;
		}
		JSONObject json = null;
		if (text.startsWith("{")) {
			try {
				json = JSON.parseObject(text, Feature.OrderedField); // 保持原字段顺序
			} catch (Exception e) {
				json = null;
			}
		}
		if (json == null) {
			mod.setRecord(text);
		} else if (json.containsKey("record")) {
			mod.setRecord(blankToNull(json.getString("record")));
			mod.setRecordDate(blankToNull(json.getString("date")));
		} else {
			for (Map.Entry<String, Object> e : json.entrySet()) {
				String v = e.getValue() == null ? null : blankToNull(e.getValue() instanceof String ? (String) e.getValue() : JSON.toJSONString(e.getValue()));
				if (v != null) {
					mod.getItems().add(new FieldChange(e.getKey(), e.getKey(), null, v));
				}
			}
		}
		mod.setFilled(mod.getRecord() != null || !mod.getItems().isEmpty());
		return mod;
	}

	/** 空串、空 JSON（{} / [] / null）都视为未填写 */
	private static String blankToNull(String s) {
		if (s == null) {
			return null;
		}
		String t = s.trim();
		return t.isEmpty() || "{}".equals(t) || "[]".equals(t) || "null".equalsIgnoreCase(t) ? null : t;
	}

	private static Long toLong(Object value) {
		return value == null ? null : ((Number) value).longValue();
	}
}
