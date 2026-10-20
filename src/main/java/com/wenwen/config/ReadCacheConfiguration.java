package com.wenwen.config;

import java.util.Arrays;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 改患者 / 随访 / 质控处理的接口执行完后清空 ReadCache，保证本平台上的修改马上能在列表和统计里看到 */
@Configuration
public class ReadCacheConfiguration implements WebMvcConfigurer {

	private static final List<String> WRITES = Arrays.asList(
			"/api/ra/patient/createPatient", "/api/ra/patient/updatePatient", "/api/ra/patient/withdraw", "/api/ra/patient/reactivate",
			"/api/ra/visit/createVisit", "/api/ra/visit/updateVisit", "/api/ra/visit/deleteVisit",
			"/api/ra/quality/resolve", "/api/ra/ocr/linkImages");

	private final ReadCache cache;

	public ReadCacheConfiguration(ReadCache cache) {
		this.cache = cache;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(new HandlerInterceptor() {
			@Override
			public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
				if (WRITES.contains(request.getRequestURI().substring(request.getContextPath().length()))) {
					cache.clear();
				}
			}
		}).addPathPatterns("/api/ra/**");
	}
}
