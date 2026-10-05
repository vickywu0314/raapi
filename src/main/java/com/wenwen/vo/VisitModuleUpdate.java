package com.wenwen.vo;

import java.util.List;
import java.util.Map;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value = "编辑随访：一个模块提交的内容（只需提交表单里的值，后端只写有变化的字段）")
public class VisitModuleUpdate {

	@ApiModelProperty("字段 key → 新值（list 传字符串数组，其它传字符串；清空传空串）")
	private Map<String, Object> fields;
	@ApiModelProperty("字段 key → 是否未查")
	private Map<String, Boolean> wx;
	@ApiModelProperty("清单 key → 全部行（原有行带 _row，新增行不带）")
	private Map<String, List<Map<String, Object>>> tables;

	public Map<String, Object> getFields() {
		return fields;
	}
	public void setFields(Map<String, Object> fields) {
		this.fields = fields;
	}
	public Map<String, Boolean> getWx() {
		return wx;
	}
	public void setWx(Map<String, Boolean> wx) {
		this.wx = wx;
	}
	public Map<String, List<Map<String, Object>>> getTables() {
		return tables;
	}
	public void setTables(Map<String, List<Map<String, Object>>> tables) {
		this.tables = tables;
	}
}
