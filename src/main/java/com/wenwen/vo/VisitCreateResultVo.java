package com.wenwen.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value = "新增随访结果")
public class VisitCreateResultVo {
	private OcrLinkResultVo ocrLink;
	public OcrLinkResultVo getOcrLink() { return ocrLink; }
	public void setOcrLink(OcrLinkResultVo value) { ocrLink = value; }

	@ApiModelProperty("新随访ID")
	private Long visitId;
	@ApiModelProperty("患者ID")
	private Long patientId;
	@ApiModelProperty("填写了几个模块")
	private int filledCount;

	@JsonSerialize(using = ToStringSerializer.class)
	public Long getVisitId() {
		return visitId;
	}
	public void setVisitId(Long visitId) {
		this.visitId = visitId;
	}
	@JsonSerialize(using = ToStringSerializer.class)
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
