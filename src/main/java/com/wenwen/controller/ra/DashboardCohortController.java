package com.wenwen.controller.ra;

import java.io.IOException;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wenwen.ai.analysis.CohortResultComputer;
import com.wenwen.ai.query.CohortException;
import com.wenwen.ai.query.CohortQuery;
import com.wenwen.ai.result.AnalysisResultStore;
import com.wenwen.ai.scope.AiFeatureAvailability;
import com.wenwen.ai.scope.PrincipalProvider;
import com.wenwen.ai.scope.TrustedDoctor;
import com.wenwen.vo.DataResult;

/**
 * 数据看板「智能研究队列」「研究数据」预览：用 AI 队列分析同一套计算（同筛选条件、同治疗方案时长口径），
 * 只返回人数、资料完整度、当前方案持续 ≥3 / 6 / 12 个月人数；只读，不保存分析会话、不返回患者名单。
 */
@RestController
@RequestMapping("/api/ra/dashboard")
public class DashboardCohortController {
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(DashboardCohortController.class);
	private final PrincipalProvider principal;
	private final AiFeatureAvailability availability;
	private final AnalysisResultStore results;
	private final Clock clock;
	private final com.wenwen.config.ReadCache readCache;

	public DashboardCohortController(PrincipalProvider principal, AiFeatureAvailability availability, AnalysisResultStore results, Clock clock,
			org.springframework.beans.factory.ObjectProvider<com.wenwen.config.ReadCache> readCache) {
		this.principal = principal;
		this.availability = availability;
		this.results = results;
		this.clock = clock;
		this.readCache = readCache.getIfAvailable(() -> com.wenwen.config.ReadCache.DISABLED);
	}

	/** 入参与 /api/ra/ai/cohort 相同：{"filters":{...}}；{} 为当前医生名下全部 RA 患者 */
	@PostMapping("/cohortPreview")
	public ResponseEntity<DataResult<?>> cohortPreview(HttpServletRequest request) {
		String trace = UUID.randomUUID().toString();
		DataResult<Object> result = new DataResult<>();
		int status = 200;
		try {
			TrustedDoctor doctor = principal.requireCurrent();
			availability.requireApi();
			byte[] body = org.springframework.util.StreamUtils.copyToByteArray(request.getInputStream());
			CohortQuery query = CohortQuery.read(new java.io.ByteArrayInputStream(body), doctor);
			result.setData(readCache.get("cohortPreview:" + doctor.getId() + "|" + query.normalizedFilters(), () -> preview(doctor, query, trace)));
			result.setSuccess(true);
			result.setCode("200");
			result.setMessage("成功");
		} catch (CohortException e) {
			status = e.getStatus();
			if (status >= 500) {
				LOG.warn("cohort preview unavailable trace={} code={}", trace, e.getCode(), e);
			}
			result.setSuccess(false);
			result.setCode(e.getCode());
			result.setMessage(e.getMessage());
		} catch (IOException | RuntimeException e) {
			status = 503;
			LOG.error("cohort preview failed trace={}", trace, e);
			result.setSuccess(false);
			result.setCode("SERVICE_UNAVAILABLE");
			result.setMessage("分析暂不可用");
		}
		return ResponseEntity.status(status).header("X-Trace-Id", trace).body(result);
	}

	private Map<String, Object> preview(TrustedDoctor doctor, CohortQuery query, String trace) {
		CohortResultComputer.Computed computed = results.compute(doctor, query, trace, clock.instant(), null, 0, Integer.MAX_VALUE, 0);
		int scheme3 = 0, scheme6 = 0, scheme12 = 0;
		for (Map<String, Object> row : computed.rows) {
			Object tx = row.get("treatment");
			if (!(tx instanceof Map) || !"ACTIVE".equals(((Map<?, ?>) tx).get("state"))) {
				continue;
			}
			Object months = ((Map<?, ?>) tx).get("schemeDurationMonths");
			if (months instanceof Number) {
				long m = ((Number) months).longValue();
				scheme3 += m >= 3 ? 1 : 0;
				scheme6 += m >= 6 ? 1 : 0;
				scheme12 += m >= 12 ? 1 : 0;
			}
		}
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("n", computed.result.getN());
		data.put("completeRate", computed.result.getStats() == null ? null : computed.result.getStats().get("completeRate"));
		data.put("scheme3", scheme3);
		data.put("scheme6", scheme6);
		data.put("scheme12", scheme12);
		return data;
	}
}
