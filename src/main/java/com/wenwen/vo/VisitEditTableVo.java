package com.wenwen.vo;

import java.util.List;
import java.util.Map;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value = "编辑随访：一个清单（西药等）")
public class VisitEditTableVo {

	@ApiModelProperty("老系统 JSON 字段名，如 xyList")
	private String key;
	@ApiModelProperty("清单名")
	private String title;
	@ApiModelProperty("可编辑的列（value 为空）")
	private List<VisitEditFieldVo> columns;
	@ApiModelProperty("每行：列 key → 值；_row 为该行在原数组中的位置，保存时原样传回，用于保留该行未显示的字段")
	private List<Map<String, Object>> rows;

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
	public List<VisitEditFieldVo> getColumns() {
		return columns;
	}
	public void setColumns(List<VisitEditFieldVo> columns) {
		this.columns = columns;
	}
	public List<Map<String, Object>> getRows() {
		return rows;
	}
	public void setRows(List<Map<String, Object>> rows) {
		this.rows = rows;
	}
}
