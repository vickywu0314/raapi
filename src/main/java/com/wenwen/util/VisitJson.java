package com.wenwen.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.Feature;
import com.alibaba.fastjson.serializer.SerializerFeature;

/**
 * 随访模块 JSON（老系统格式）的读写小工具：保持字段顺序、支持 result.crpScore 这样的嵌套路径。
 */
public final class VisitJson {

	private VisitJson() {
	}

	/** 解析为对象（保持原字段顺序）；不是 JSON 对象返回 null */
	public static JSONObject parse(String raw) {
		String text = blankToNull(raw);
		if (text == null || !text.startsWith("{")) {
			return null;
		}
		try {
			return JSON.parseObject(text, Feature.OrderedField);
		} catch (Exception e) {
			return null;
		}
	}

	/** 深拷贝（保持顺序和 null 值） */
	public static JSONObject copy(JSONObject o) {
		return o == null ? null : JSON.parseObject(write(o), Feature.OrderedField);
	}

	/** 写回字符串：保留值为 null 的字段，避免改动老数据结构 */
	public static String write(Object o) {
		return JSON.toJSONString(o, SerializerFeature.WriteMapNullValue, SerializerFeature.DisableCircularReferenceDetect);
	}

	/** 取值，支持一层嵌套（result.crpScore） */
	public static Object path(JSONObject json, String key) {
		int dot = key.indexOf('.');
		if (dot < 0) {
			return json.get(key);
		}
		Object parent = json.get(key.substring(0, dot));
		return parent instanceof JSONObject ? ((JSONObject) parent).get(key.substring(dot + 1)) : null;
	}

	/** 写值，支持一层嵌套；父对象不存在时新建 */
	public static void setPath(JSONObject json, String key, Object value) {
		int dot = key.indexOf('.');
		if (dot < 0) {
			json.put(key, value);
			return;
		}
		String parentKey = key.substring(0, dot);
		JSONObject parent = json.get(parentKey) instanceof JSONObject ? json.getJSONObject(parentKey) : null;
		if (parent == null) {
			parent = new JSONObject(true);
			json.put(parentKey, parent);
		}
		parent.put(key.substring(dot + 1), value);
	}

	/** 显示文字：数组用「、」连接，布尔转 是 / 否，对象转 JSON；空值返回 null */
	public static String text(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof Collection) {
			List<String> parts = new ArrayList<String>();
			for (Object o : (Collection<?>) value) {
				String t = text(o);
				if (t != null) {
					parts.add(t);
				}
			}
			return parts.isEmpty() ? null : String.join("、", parts);
		}
		if (value instanceof Boolean) {
			return (Boolean) value ? "是" : "否";
		}
		if (value instanceof JSONObject) {
			return ((JSONObject) value).isEmpty() ? null : JSON.toJSONString(value);
		}
		return blankToNull(String.valueOf(value));
	}

	/** 「未查」标记可能是布尔，也可能是字符串 "true" */
	public static boolean isTrue(Object v) {
		return Boolean.TRUE.equals(v) || "true".equalsIgnoreCase(String.valueOf(v));
	}

	/** 空串、空 JSON（{} / [] / null）都视为未填写 */
	public static String blankToNull(String s) {
		if (s == null) {
			return null;
		}
		String t = s.trim();
		return t.isEmpty() || "{}".equals(t) || "[]".equals(t) || "null".equalsIgnoreCase(t) ? null : t;
	}
}
