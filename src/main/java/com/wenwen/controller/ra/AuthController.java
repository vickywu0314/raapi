package com.wenwen.controller.ra;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wenwen.auth.AuthService;
import com.wenwen.util.BizException;
import com.wenwen.vo.DataResult;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

@Api(description = "登录")
@RestController
@RequestMapping(value = "/api/ra/auth")
public class AuthController {

	@Autowired
	private AuthService authService;

	@ApiOperation(value = "登录", notes = "账号见 ra.auth.accounts；成功后建立服务端会话（Cookie），返回 username / doctorId / name / role", httpMethod = "POST")
	@PostMapping("/login")
	public DataResult<Map<String, Object>> login(@RequestParam(value = "username", required = false) String username,
			@RequestParam(value = "password", required = false) String password,
			@RequestParam(value = "remember", required = false, defaultValue = "false") boolean remember, HttpServletRequest request) {
		DataResult<Map<String, Object>> r = new DataResult<Map<String, Object>>();
		try {
			ok(r, authService.login(username, password, remember, request));
		} catch (BizException e) {
			r.setCode(e.getCode());
			r.setMessage(e.getMessage());
			r.setSuccess(false);
		}
		return r;
	}

	@ApiOperation(value = "当前登录用户", notes = "没登录或会话过期：success=false、code=401", httpMethod = "POST")
	@PostMapping("/me")
	public DataResult<Map<String, Object>> me(HttpServletRequest request) {
		DataResult<Map<String, Object>> r = new DataResult<Map<String, Object>>();
		Map<String, Object> u = authService.current(request);
		if (u == null) {
			r.setCode("401");
			r.setMessage("请先登录");
			r.setSuccess(false);
		} else {
			ok(r, u);
		}
		return r;
	}

	@ApiOperation(value = "退出登录", httpMethod = "POST")
	@PostMapping("/logout")
	public DataResult<Void> logout(HttpServletRequest request) {
		authService.logout(request);
		DataResult<Void> r = new DataResult<Void>();
		ok(r, null);
		return r;
	}

	private static <T> void ok(DataResult<T> r, T data) {
		r.setCode("200");
		r.setMessage("成功");
		r.setSuccess(true);
		r.setData(data);
	}
}
