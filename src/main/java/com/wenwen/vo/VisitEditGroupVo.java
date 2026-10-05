package com.wenwen.vo;

import java.util.List;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value = "编辑随访：一组字段")
public class VisitEditGroupVo {

	@ApiModelProperty("分组名")
	private String title;
	@ApiModelProperty("字段")
	private List<VisitEditFieldVo> fields;

	public String getTitle() {
		return title;
	}
	public void setTitle(String title) {
		this.title = title;
	}
	public List<VisitEditFieldVo> getFields() {
		return fields;
	}
	public void setFields(List<VisitEditFieldVo> fields) {
		this.fields = fields;
	}
}
