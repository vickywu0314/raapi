package com.wenwen.util;

/**
 * 业务异常：带返回码和可直接给前端显示的中文信息（如 403 无权查看）
 */
public class BizException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final String code;

	public BizException(String code, String message) {
		super(message);
		this.code = code;
	}

	public String getCode() {
		return code;
	}
}
