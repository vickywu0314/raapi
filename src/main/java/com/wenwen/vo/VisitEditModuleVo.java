package com.wenwen.vo;

import java.util.List;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value = "编辑随访：一个病历模块")
public class VisitEditModuleVo {

	@ApiModelProperty("模块编码")
	private String key;
	@ApiModelProperty("模块中文名")
	private String title;
	@ApiModelProperty("随访表字段")
	private String column;
	@ApiModelProperty("是否可编辑；字段里是普通文字等非结构化内容时为 false，只读显示 record")
	private boolean editable;
	@ApiModelProperty("非结构化内容（只读）")
	private String record;
	@ApiModelProperty("按字段字典分组的字段")
	private List<VisitEditGroupVo> groups;
	@ApiModelProperty("清单")
	private List<VisitEditTableVo> tables;
	@ApiModelProperty("图片（只读）")
	private List<VisitImageGroupVo> images;
	@ApiModelProperty("字典外的字段（只读，保存时原样保留）")
	private List<VisitFieldVo> others;

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
	public boolean isEditable() {
		return editable;
	}
	public void setEditable(boolean editable) {
		this.editable = editable;
	}
	public String getRecord() {
		return record;
	}
	public void setRecord(String record) {
		this.record = record;
	}
	public List<VisitEditGroupVo> getGroups() {
		return groups;
	}
	public void setGroups(List<VisitEditGroupVo> groups) {
		this.groups = groups;
	}
	public List<VisitEditTableVo> getTables() {
		return tables;
	}
	public void setTables(List<VisitEditTableVo> tables) {
		this.tables = tables;
	}
	public List<VisitImageGroupVo> getImages() {
		return images;
	}
	public void setImages(List<VisitImageGroupVo> images) {
		this.images = images;
	}
	public List<VisitFieldVo> getOthers() {
		return others;
	}
	public void setOthers(List<VisitFieldVo> others) {
		this.others = others;
	}
}
