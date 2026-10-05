package com.wenwen.vo;

import java.util.List;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value = "病历模块中的一组字段")
public class VisitFieldGroupVo {

	@ApiModelProperty("分组名，如 血常规；字典外的字段为「其他字段」")
	private String title;
	@ApiModelProperty("字段")
	private List<VisitFieldVo> items;

	public String getTitle() {
		return title;
	}
	public void setTitle(String title) {
		this.title = title;
	}
	public List<VisitFieldVo> getItems() {
		return items;
	}
	public void setItems(List<VisitFieldVo> items) {
		this.items = items;
	}
}
