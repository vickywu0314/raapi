package com.wenwen.service.impl;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.wenwen.mapper.FollowupMapper;
import com.wenwen.service.FollowupService;
import com.wenwen.util.IdCardUtil;
import com.wenwen.vo.FollowupPlanVo;

@Service
public class FollowupServiceImpl implements FollowupService {

	private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
	private static final java.util.Map<String, String> STATUS_LABELS = new java.util.HashMap<String, String>();
	static {
		// 与患者列表（PatientServiceImpl.FOLLOW_STATUS）一致
		STATUS_LABELS.put("pending_first", "待首次随访");
		STATUS_LABELS.put("overdue", "随访逾期");
		STATUS_LABELS.put("soon", "近期需随访");
		STATUS_LABELS.put("active", "随访中");
		STATUS_LABELS.put("lost", "疑似失访");
	}

	@Autowired
	private FollowupMapper mapper;

	/** 疑似失访：最近一次随访（没有随访取建档日期）距今超过这么多个月，不再算进随访计划和逾期 */
	@Value("${ra.followup.lost-months:24}")
	private int lostMonths;

	@Override
	public List<FollowupPlanVo> listPlans(Long doctorId) {
		if (doctorId == null) {
			throw new IllegalArgumentException("缺少医生ID");
		}
		LocalDate today = LocalDate.now(SHANGHAI);
		List<FollowupPlanVo> list = new ArrayList<FollowupPlanVo>();
		for (Map<String, Object> r : mapper.listPlanRows(doctorId)) {
			FollowupPlanVo v = new FollowupPlanVo();
			v.setPatientId(((Number) r.get("patientId")).longValue());
			v.setStudyNo((String) r.get("studyNo"));
			v.setName((String) r.get("name"));
			Object g = r.get("gender");
			int gender = g == null ? 0 : ((Number) g).intValue();
			v.setSex(gender == 1 ? "男" : gender == 2 ? "女" : null);
			LocalDate birth = IdCardUtil.birthDate((String) r.get("cardNo"), today);
			v.setBirthYear(birth == null ? null : birth.getYear());
			int count = r.get("visitCount") == null ? 0 : ((Number) r.get("visitCount")).intValue();
			v.setVisitCount(count);
			// 第一条记录是基线访视，之后的才算随访：下次是基线时 nth = 0，否则为第 count 次随访
			v.setNth(count);
			v.setFirst(count == 0);
			int cycle = r.get("followCycle") == null ? 12 : ((Number) r.get("followCycle")).intValue();
			v.setFollowCycle(cycle);
			String last = (String) r.get("lastVisitDate");
			v.setLastVisitDate(last);
			// 有随访：最近随访 + 周期；还没有随访：基线随访，预计日期取建档日期
			LocalDate due = last != null ? date(last) : date((String) r.get("createDate"));
			if (due != null && last != null) {
				due = due.plusMonths(cycle);
			}
			LocalDate ref = last != null ? date(last) : date((String) r.get("createDate"));
			if (due != null) {
				long days = ChronoUnit.DAYS.between(today, due);
				v.setDueDate(due.toString());
				v.setDays(days);
				v.setStatus(days < 0 ? "overdue" : days <= 14 ? "soon" : "active");
			}
			// 状态与患者列表同一规则：还没有随访 → 待首次随访；再按预计日期 逾期 / 14 天内 / 随访中
			if (count == 0 || due == null) {
				v.setStatus("pending_first");
			}
			if (ref != null && ref.isBefore(today.minusMonths(lostMonths))) {
				v.setStatus("lost");
			}
			v.setStatusLabel(STATUS_LABELS.get(v.getStatus()));
			list.add(v);
		}
		list.sort(Comparator.comparing(FollowupPlanVo::getDays, Comparator.nullsLast(Comparator.naturalOrder()))
				.thenComparing(FollowupPlanVo::getPatientId, Comparator.reverseOrder()));
		return list;
	}

	private static LocalDate date(String s) {
		try {
			return s == null ? null : LocalDate.parse(s);
		} catch (DateTimeParseException e) {
			return null;
		}
	}
}
