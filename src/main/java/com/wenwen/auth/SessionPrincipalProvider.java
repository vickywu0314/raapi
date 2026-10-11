package com.wenwen.auth;

import javax.servlet.http.HttpServletRequest;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.wenwen.ai.query.CohortException;
import com.wenwen.ai.scope.PrincipalProvider;
import com.wenwen.ai.scope.TrustedDoctor;

/** AI 队列分析的当前医生：取登录会话里的医生 ID（不信任请求里传的 doctorId）；没登录 401 */
public class SessionPrincipalProvider implements PrincipalProvider {
	@Override
	public TrustedDoctor requireCurrent() {
		ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
		HttpServletRequest request = attrs == null ? null : attrs.getRequest();
		Long doctorId = AuthService.currentDoctorId(request);
		if (doctorId == null) {
			throw new CohortException(401, "UNAUTHENTICATED", "请先登录");
		}
		return new TrustedDoctor(doctorId);
	}
}
