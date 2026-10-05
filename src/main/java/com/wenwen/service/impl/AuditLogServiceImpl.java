package com.wenwen.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson.JSON;
import com.wenwen.mapper.AuditLogMapper;
import com.wenwen.service.AuditLogService;
import com.wenwen.vo.AuditLogVo;
import com.wenwen.vo.FieldChange;

@Service
public class AuditLogServiceImpl implements AuditLogService {

	@Autowired
	private AuditLogMapper auditLogMapper;

	@Override
	public List<FieldChange> diff(Map<String, ?> before, Map<String, ?> after, Map<String, String> labels) {
		List<FieldChange> changes = new ArrayList<FieldChange>();
		for (Map.Entry<String, String> e : labels.entrySet()) {
			String b = text(before.get(e.getKey()));
			String a = text(after.get(e.getKey()));
			if (!b.equals(a)) {
				changes.add(new FieldChange(e.getKey(), e.getValue(), b, a));
			}
		}
		return changes;
	}

	@Override
	public void record(Long patientId, Long visitId, String action, List<FieldChange> changes, String note, Long operatorId, String operatorName) {
		List<String> parts = new ArrayList<String>();
		if (changes != null) {
			for (FieldChange c : changes) {
				parts.add(c.toText());
			}
		}
		if (note != null && !note.isEmpty()) {
			parts.add(note);
		}
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("patientId", patientId);
		map.put("visitId", visitId);
		map.put("action", action);
		map.put("detail", String.join("；", parts));
		map.put("changes", changes == null || changes.isEmpty() ? null : JSON.toJSONString(changes));
		map.put("operatorId", operatorId);
		map.put("operatorName", operatorName);
		auditLogMapper.insert(map);
	}

	@Override
	public List<AuditLogVo> listByPatient(Long patientId) {
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("patientId", patientId);
		List<AuditLogVo> list = new ArrayList<AuditLogVo>();
		for (Map<String, Object> row : auditLogMapper.listByPatient(map)) {
			AuditLogVo vo = new AuditLogVo();
			vo.setId(row.get("id") == null ? null : ((Number) row.get("id")).longValue());
			vo.setTime((String) row.get("time"));
			vo.setAction((String) row.get("action"));
			vo.setVisitId(row.get("visitId") == null ? null : ((Number) row.get("visitId")).longValue());
			vo.setDetail((String) row.get("detail"));
			vo.setOperator((String) row.get("operator"));
			list.add(vo);
		}
		return list;
	}

	private static String text(Object v) {
		return v == null ? "" : String.valueOf(v).trim();
	}
}
