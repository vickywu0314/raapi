package com.wenwen.vo;

import java.util.List;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value = "访视详情的一个病历模块")
public class VisitModuleVo {

	@ApiModelProperty("模块编码：history / exam / assessment / tcm / treatment / adverse / adverseEvent")
	private String key;
	@ApiModelProperty("模块中文名：病史病情 / 辅助检查 / 病情评估 / 中医诊断 / 治疗方案 / 不良反应 / 不良事件")
	private String title;
	@ApiModelProperty("对应随访表字段：bsbq / fzjc / bqpg / zyzd / zlfa / blsj / bblsj")
	private String column;
	@ApiModelProperty("是否有内容")
	private boolean filled;
	@ApiModelProperty("字段存的是普通文字或 {record, date} 时的记录内容；结构化 JSON 时为 null")
	private String record;
	@ApiModelProperty("{record, date} 中的 date")
	private String recordDate;
	@ApiModelProperty("按字段字典分组的字段；字典外的字段在最后的「其他字段」组")
	private List<VisitFieldGroupVo> groups;
	@ApiModelProperty("清单（西药、中成药、中药饮片、中医外治）")
	private List<VisitTableVo> tables;
	@ApiModelProperty("图片（化验单、关节X线、心电图、舌面等）")
	private List<VisitImageGroupVo> images;

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
	public List<VisitFieldGroupVo> getGroups() {
		return groups;
	}
	public void setGroups(List<VisitFieldGroupVo> groups) {
		this.groups = groups;
	}
	public List<VisitTableVo> getTables() {
		return tables;
	}
	public void setTables(List<VisitTableVo> tables) {
		this.tables = tables;
	}
	public List<VisitImageGroupVo> getImages() {
		return images;
	}
	public void setImages(List<VisitImageGroupVo> images) {
		this.images = images;
	}
}
