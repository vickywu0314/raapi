package com.wenwen.ocr;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.wenwen.util.BizException;

/**
 * 百度 OCR 调用：API Key / Secret Key 换 access_token（缓存，提前 1 天刷新），图片按 base64 提交。
 * 图片存在我们自己的服务器上，百度访问不到内网 URL，所以不传 url 参数，而是读文件内容转 base64。
 * 配置：ra.ocr.baidu.api-key、secret-key（密钥只放服务器配置，不进 git）；base-url 默认 https://aip.baidubce.com（测试时可指向本地模拟服务）。
 */
@Component
public class BaiduOcrClient {

	@Value("${ra.ocr.baidu.api-key:}")
	private String apiKey;
	@Value("${ra.ocr.baidu.secret-key:}")
	private String secretKey;
	@Value("${ra.ocr.baidu.base-url:https://aip.baidubce.com}")
	private String baseUrl;

	private String token;
	private long tokenExpiresAt;

	public boolean configured() {
		return !blank(apiKey) && !blank(secretKey);
	}

	/**
	 * 调一个 OCR 接口，返回百度的原始 JSON。
	 *
	 * @param api 接口名，如 medical_report_detection、accurate_basic
	 */
	public JSONObject call(String api, byte[] image) {
		if (!configured()) {
			throw new BizException("400", "OCR 还没配置：请在 application.properties 填写 ra.ocr.baidu.api-key 和 ra.ocr.baidu.secret-key");
		}
		String body = "image=" + enc(Base64.getEncoder().encodeToString(image));
		JSONObject r = post(baseUrl + "/rest/2.0/ocr/v1/" + api + "?access_token=" + enc(token()), body);
		if (r.containsKey("error_code")) {
			String code = r.getString("error_code");
			if ("110".equals(code) || "111".equals(code)) { // token 失效：清掉重取一次
				synchronized (this) {
					token = null;
				}
				r = post(baseUrl + "/rest/2.0/ocr/v1/" + api + "?access_token=" + enc(token()), body);
			}
		}
		if (r.containsKey("error_code")) {
			throw new BizException("502", "百度 OCR（" + api + "）返回错误：" + r.getString("error_msg") + "（" + r.getString("error_code") + "）");
		}
		return r;
	}

	private synchronized String token() {
		if (token != null && System.currentTimeMillis() < tokenExpiresAt) {
			return token;
		}
		JSONObject r = post(baseUrl + "/oauth/2.0/token?grant_type=client_credentials&client_id=" + enc(apiKey.trim()) + "&client_secret=" + enc(secretKey.trim()), "");
		if (blank(r.getString("access_token"))) {
			throw new BizException("502", "百度 OCR 鉴权失败：" + (r.containsKey("error_description") ? r.getString("error_description") : r.toJSONString()) + "，请检查 API Key / Secret Key");
		}
		token = r.getString("access_token");
		long seconds = r.getLongValue("expires_in") > 0 ? r.getLongValue("expires_in") : 2592000L;
		tokenExpiresAt = System.currentTimeMillis() + Math.max(60, seconds - 86400) * 1000L;
		return token;
	}

	private static JSONObject post(String url, String body) {
		HttpURLConnection c = null;
		try {
			c = (HttpURLConnection) new URL(url).openConnection();
			c.setRequestMethod("POST");
			c.setConnectTimeout(10000);
			c.setReadTimeout(60000);
			c.setDoOutput(true);
			c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			c.setRequestProperty("Accept", "application/json");
			try (OutputStream out = c.getOutputStream()) {
				out.write(body.getBytes(StandardCharsets.UTF_8));
			}
			int status = c.getResponseCode();
			InputStream in = status >= 400 ? c.getErrorStream() : c.getInputStream();
			String text = in == null ? "" : read(in);
			JSONObject json;
			try {
				json = JSON.parseObject(text);
			} catch (RuntimeException e) {
				json = null;
			}
			if (json == null) {
				throw new BizException("502", "百度 OCR 返回了无法识别的内容（HTTP " + status + "）");
			}
			return json;
		} catch (IOException e) {
			throw new BizException("502", "连不上百度 OCR（" + e.getMessage() + "），请确认服务器能访问外网 aip.baidubce.com");
		} finally {
			if (c != null) {
				c.disconnect();
			}
		}
	}

	private static String read(InputStream in) throws IOException {
		try (InputStream i = in; ByteArrayOutputStream b = new ByteArrayOutputStream()) {
			byte[] buf = new byte[8192];
			int n;
			while ((n = i.read(buf)) > 0) {
				b.write(buf, 0, n);
			}
			return new String(b.toByteArray(), StandardCharsets.UTF_8);
		}
	}

	private static String enc(String s) {
		try {
			return URLEncoder.encode(s, "UTF-8");
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private static boolean blank(String s) {
		return s == null || s.trim().isEmpty();
	}
}
