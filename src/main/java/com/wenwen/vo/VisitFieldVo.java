package com.wenwen.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value = "病历模块中的一个字段")
public class VisitFieldVo {

	@ApiModelProperty("老系统 JSON 字段名（如 xhdb、result.crpScore）")
	private String key;
	@ApiModelProperty("中文名")
	private String label;
	@ApiModelProperty("显示值：多选用「、」连接；未查时为「未查」")
	private String value;
	@ApiModelProperty("单位，无则空串")
	private String unit;
	@ApiModelProperty("是否未查（Wx 标记为 true）")
	private boolean notChecked;

	@ApiModelProperty("参考范围下限（检验项且配置了 ra.visit.validation.rules 时才有，见 API.md §4.7）；未配置为 null")
	private java.math.BigDecimal referenceMin;
	@ApiModelProperty("参考范围上限；未配置为 null")
	private java.math.BigDecimal referenceMax;
	@ApiModelProperty("只读显示：high 高于上限 / low 低于下限 / null 在范围内、未配置或无法判断")
	private String abnormal;
	public java.math.BigDecimal getReferenceMin() { return referenceMin; }
	public void setReferenceMin(java.math.BigDecimal value) { referenceMin = value; }
	public java.math.BigDecimal getReferenceMax() { return referenceMax; }
	public void setReferenceMax(java.math.BigDecimal value) { referenceMax = value; }
	public String getAbnormal() { return abnormal; }
	public void setAbnormal(String value) { abnormal = value; }

	public String getKey() {
		return key;
	}
	public void setKey(String key) {
		this.key = key;
	}
	public String getLabel() {
		return label;
	}
	public void setLabel(String label) {
		this.label = label;
	}
	public String getValue() {
		return value;
	}
	public void setValue(String value) {
		this.value = value;
	}
	public String getUnit() {
		return unit;
	}
	public void setUnit(String unit) {
		this.unit = unit;
	}
	public boolean isNotChecked() {
		return notChecked;
	}
	public void setNotChecked(boolean notChecked) {
		this.notChecked = notChecked;
	}
}
