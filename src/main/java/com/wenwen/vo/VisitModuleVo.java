package com.wenwen.vo;

import java.util.List;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value = "访视详情的一个病历模块")
public class VisitModuleVo {

	@ApiModelProperty("模块编码：history / exam / assessment / tcm / treatment / adverse / caseRecord")
	private String key;
	@ApiModelProperty("模块中文名：病史病情 / 辅助检查 / 病情评估 / 中医诊断 / 治疗方案 / 不良反应 / 随诊病例")
	private String title;
	@ApiModelProperty("对应随访表字段：bsbq / fzjc / bqpg / zyzd / zlfa / blsj / bblsj")
	private String column;
	@ApiModelProperty("是否有内容")
	private boolean filled;
	@ApiModelProperty("记录内容：字段存的是 {record, date} 时取 record；是普通文字时为原文")
	private String record;
	@ApiModelProperty("记录日期：取 {record, date} 中的 date；无则 null")
	private String recordDate;
	@ApiModelProperty("字段存的是其它结构的 JSON 时，按「名称：值」逐项列出（只用 label、after 两项）；否则为空数组")
	private List<FieldChange> items;

	public String getKey() {
		return key;
	}
	public void setKey(String key) {
		this.key = key;
	}
	public String getTitle() {
		return title;
	}
	public void setTitle(String title) {
		this.title = title;
	}
	public String getColumn() {
		return column;
	}
	public void setColumn(String column) {
		this.column = column;
	}
	public boolean isFilled() {
		return filled;
	}
	public void setFilled(boolean filled) {
		this.filled = filled;
	}
	public String getRecord() {
		return record;
	}
	public void setRecord(String record) {
		this.record = record;
	}
	public String getRecordDate() {
		return recordDate;
	}
	public void setRecordDate(String recordDate) {
		this.recordDate = recordDate;
	}
	public List<FieldChange> getItems() {
		return items;
	}
	public void setItems(List<FieldChange> items) {
		this.items = items;
	}
}
