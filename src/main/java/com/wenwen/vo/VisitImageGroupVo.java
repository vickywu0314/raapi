package com.wenwen.vo;

import java.util.List;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value = "病历模块中的一组图片")
public class VisitImageGroupVo {

	@ApiModelProperty("图片类别，如 化验检查报告")
	private String title;
	@ApiModelProperty("图片地址")
	private List<String> urls;

	public String getTitle() {
		return title;
	}
	public void setTitle(String title) {
		this.title = title;
	}
	public List<String> getUrls() {
		return urls;
	}
	public void setUrls(List<String> urls) {
		this.urls = urls;
	}
}
