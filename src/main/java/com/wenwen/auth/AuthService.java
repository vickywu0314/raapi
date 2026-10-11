package com.wenwen.auth;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.wenwen.util.BizException;

/**
 * 登录（服务端会话）：账号在 ra.auth.accounts 配置，格式「用户名:密码:医生ID」，多个用逗号分隔。
 * 测试期只配内部测试账号（如 yanwenjing → 医生 82394 言文静）；正式账号体系确定后（user 表账号 / 单点登录）替换 check()。
 * 登录后会话里存医生 ID，AI 队列分析等接口按它鉴权（SessionPrincipalProvider）。
 */
@Service
public class AuthService {

	public static final String SESSION_DOCTOR = "ra.doctorId";
	public static final String SESSION_USER = "ra.username";
	/** 不勾「记住我」：会话 12 小时无操作失效；勾了：30 天 */
	private static final int SHORT_SECONDS = 12 * 3600, LONG_SECONDS = 30 * 24 * 3600;

	@Value("${ra.auth.accounts:}")
	private String accounts;

	@Autowired
	private JdbcTemplate jdbc;

	public Map<String, Object> login(String username, String password, boolean remember, HttpServletRequest request) {
		username = username == null ? "" : username.trim();
		if (username.isEmpty() || password == null || password.isEmpty()) {
			throw new BizException("400", "请输入用户名和密码");
		}
		Long doctorId = check(username, password);
		if (doctorId == null) {
			throw new BizException("401", "用户名或密码错误");
		}
		HttpSession old = request.getSession(false);
		if (old != null) {
			old.invalidate(); // 换新会话，防会话固定
		}
		HttpSession s = request.getSession(true);
		s.setAttribute(SESSION_DOCTOR, doctorId);
		s.setAttribute(SESSION_USER, username);
		s.setMaxInactiveInterval(remember ? LONG_SECONDS : SHORT_SECONDS);
		return user(username, doctorId);
	}

	/** 当前登录的医生；没登录或会话过期返回 null */
	public Map<String, Object> current(HttpServletRequest request) {
		Long doctorId = currentDoctorId(request);
		return doctorId == null ? null : user((String) request.getSession(false).getAttribute(SESSION_USER), doctorId);
	}

	public static Long currentDoctorId(HttpServletRequest request) {
		HttpSession s = request == null ? null : request.getSession(false);
		Object v = s == null ? null : s.getAttribute(SESSION_DOCTOR);
		return v instanceof Long ? (Long) v : null;
	}

	public void logout(HttpServletRequest request) {
		HttpSession s = request.getSession(false);
		if (s != null) {
			s.invalidate();
		}
	}

	private Long check(String username, String password) {
		for (String a : (accounts == null ? "" : accounts).split(",")) {
			String[] p = a.trim().split(":");
			if (p.length == 3 && p[0].trim().equals(username) && p[1].equals(password)) {
				try {
					return Long.valueOf(p[2].trim());
				} catch (NumberFormatException e) {
					return null;
				}
			}
		}
		return null;
	}

	private Map<String, Object> user(String username, Long doctorId) {
		String name = null;
		try {
			name = jdbc.query("SELECT name FROM `user` WHERE id = ?", (org.springframework.jdbc.core.ResultSetExtractor<String>) rs -> rs.next() ? rs.getString(1) : null, doctorId);
		} catch (RuntimeException e) {
			name = null;
		}
		Map<String, Object> u = new LinkedHashMap<String, Object>();
		u.put("username", username);
		u.put("doctorId", doctorId);
		u.put("name", name == null || name.trim().isEmpty() ? "医生 " + doctorId : name.trim());
		u.put("role", "研究者");
		return u;
	}
}
