package com.wenwen.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value = "常见相关疾病（勾选项）")
public class ComorbidityInput {

	@ApiModelProperty("病种编码：FM / AS / SS / RA-ILD / RA-MS")
	private String code;
	@ApiModelProperty("起病年份，可空")
	private Integer sinceYear;

	public String getCode() {
		return code;
	}
	public void setCode(String code) {
		this.code = code;
	}
	public Integer getSinceYear() {
		return sinceYear;
	}
	public void setSinceYear(Integer sinceYear) {
		this.sinceYear = sinceYear;
	}
}
