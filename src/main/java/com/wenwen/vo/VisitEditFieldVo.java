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
	@ApiModelProperty("text 文本 / textarea 多行文本 / date 日期 / number 数字 / list 多选（数组）/ radio 单选（文字）/ radioList 单选（存数组）/ haq HAQ 选项 / computed 保存时自动计算（只读）/ readonly 只读")
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

	@ApiModelProperty("选项（单选 / 多选）；「存库值|显示文字」时页面显示后者；为空时多选用文本框")
	private java.util.List<String> options;
	@ApiModelProperty("显示条件「字段:值」：该字段等于 / 包含该值时才显示")
	private String showIf;
	@ApiModelProperty("选项随该字段的值变化（如主症随主证），选项取 optionSets[optionSet][该字段的值]")
	private String optionsFrom;
	private String optionSet;
	@ApiModelProperty("true = 只在第 2 次及以后的随访显示")
	private Boolean notBaseline;

	public java.util.List<String> getOptions() {
		return options;
	}
	public void setOptions(java.util.List<String> options) {
		this.options = options;
	}
	public String getShowIf() {
		return showIf;
	}
	public void setShowIf(String showIf) {
		this.showIf = showIf;
	}
	public String getOptionsFrom() {
		return optionsFrom;
	}
	public void setOptionsFrom(String optionsFrom) {
		this.optionsFrom = optionsFrom;
	}
	public String getOptionSet() {
		return optionSet;
	}
	public void setOptionSet(String optionSet) {
		this.optionSet = optionSet;
	}
	public Boolean getNotBaseline() {
		return notBaseline;
	}
	public void setNotBaseline(Boolean notBaseline) {
		this.notBaseline = notBaseline;
	}
}
