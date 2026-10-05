package com.wenwen.vo;

/**
 * 修改记录中的一项字段变化：字段名、中文名、旧值、新值
 */
public class FieldChange {

	private String field;
	private String label;
	private String before;
	private String after;

	public FieldChange() {
	}

	public FieldChange(String field, String label, String before, String after) {
		this.field = field;
		this.label = label;
		this.before = before;
		this.after = after;
	}

	/** 页面显示格式：字段：旧值 → 新值（空值显示为「空」） */
	public String toText() {
		return label + "：" + show(before) + " → " + show(after);
	}

	private static String show(String v) {
		return v == null || v.isEmpty() ? "空" : v;
	}

	public String getField() {
		return field;
	}
	public void setField(String field) {
		this.field = field;
	}
	public String getLabel() {
		return label;
	}
	public void setLabel(String label) {
		this.label = label;
	}
	public String getBefore() {
		return before;
	}
	public void setBefore(String before) {
		this.before = before;
	}
	public String getAfter() {
		return after;
	}
	public void setAfter(String after) {
		this.after = after;
	}
}
