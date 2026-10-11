package com.wenwen.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value = "新增随访结果")
public class VisitCreateResultVo {

	@ApiModelProperty("新随访ID")
	private Long visitId;
	@ApiModelProperty("患者ID")
	private Long patientId;
	@ApiModelProperty("填写了几个模块")
	private int filledCount;

	public Long getVisitId() {
		return visitId;
	}
	public void setVisitId(Long visitId) {
		this.visitId = visitId;
	}
	public Long getPatientId() {
		return patientId;
	}
	public void setPatientId(Long patientId) {
		this.patientId = patientId;
	}
	public int getFilledCount() {
		return filledCount;
	}
	public void setFilledCount(int filledCount) {
		this.filledCount = filledCount;
	}
}
