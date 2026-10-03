package com.wenwen.vo;

import java.util.List;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value = "患者其他病史（常见相关疾病）")
public class ComorbidityVo {

	@ApiModelProperty("病种编码：FM / AS / SS …")
	private String code;
	@ApiModelProperty("病名，如 纤维肌痛")
	private String name;
	@ApiModelProperty("起病年份，无则 null")
	private Integer sinceYear;
	@ApiModelProperty("当前情况（弹窗用），一期无数据来源，返回 null")
	private String status;
	@ApiModelProperty("核心指标（弹窗用），一期返回 null")
	private List<String> coreItems;
	@ApiModelProperty("治疗（弹窗用），一期返回 null")
	private String treatment;
	@ApiModelProperty("对应病种研究库是否已接入（决定弹窗能否跳转），一期均为 false")
	private boolean linkedStudyReady;

	public String getCode() {
		return code;
	}
	public void setCode(String code) {
		this.code = code;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public Integer getSinceYear() {
		return sinceYear;
	}
	public void setSinceYear(Integer sinceYear) {
		this.sinceYear = sinceYear;
	}
	public String getStatus() {
		return status;
	}
	public void setStatus(String status) {
		this.status = status;
	}
	public List<String> getCoreItems() {
		return coreItems;
	}
	public void setCoreItems(List<String> coreItems) {
		this.coreItems = coreItems;
	}
	public String getTreatment() {
		return treatment;
	}
	public void setTreatment(String treatment) {
		this.treatment = treatment;
	}
	public boolean isLinkedStudyReady() {
		return linkedStudyReady;
	}
	public void setLinkedStudyReady(boolean linkedStudyReady) {
		this.linkedStudyReady = linkedStudyReady;
	}
}
