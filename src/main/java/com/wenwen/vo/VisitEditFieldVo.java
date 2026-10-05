package com.wenwen.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value = "编辑随访：一个字段（或清单的一列）")
public class VisitEditFieldVo {

	@ApiModelProperty("老系统 JSON 字段名")
	private String key;
	@ApiModelProperty("中文名")
	private String label;
	@ApiModelProperty("单位")
	private String unit;
	@ApiModelProperty("text 文本 / date 日期 / list 多选（数组）/ number 数字 / haq HAQ 选项 / computed 保存时自动计算（只读）/ readonly 只读 / wxonly 只有「未查」")
	private String type;
	@ApiModelProperty("当前值：list 为字符串数组，其它为字符串；无则 null")
	private Object value;
	@ApiModelProperty("「未查」勾选状态；该字段没有未查标记时为 null")
	private Boolean wx;

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
	public String getUnit() {
		return unit;
	}
	public void setUnit(String unit) {
		this.unit = unit;
	}
	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
	}
	public Object getValue() {
		return value;
	}
	public void setValue(Object value) {
		this.value = value;
	}
	public Boolean getWx() {
		return wx;
	}
	public void setWx(Boolean wx) {
		this.wx = wx;
	}
}
