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
