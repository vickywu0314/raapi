package com.wenwen.vo;

import java.util.List;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value = "病历模块中的清单（如西药、中药饮片）")
public class VisitTableVo {

	@ApiModelProperty("清单名")
	private String title;
	@ApiModelProperty("列名（只保留有内容的列）")
	private List<String> columns;
	@ApiModelProperty("每行的值，与 columns 对应")
	private List<List<String>> rows;

	public String getTitle() {
		return title;
	}
	public void setTitle(String title) {
		this.title = title;
	}
	public List<String> getColumns() {
		return columns;
	}
	public void setColumns(List<String> columns) {
		this.columns = columns;
	}
	public List<List<String>> getRows() {
		return rows;
	}
	public void setRows(List<List<String>> rows) {
		this.rows = rows;
	}
}
