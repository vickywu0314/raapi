package com.wenwen.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value = "新建患者结果")
public class PatientCreateResultVo {

	@ApiModelProperty("患者ID")
	private Long patientId;
	@ApiModelProperty("研究编号")
	private String studyNo;
	@ApiModelProperty("created 新建 / linked 复用其他病种的患者档案 / transferred 转到本医生名下")
	private String action;

	public Long getPatientId() {
		return patientId;
	}
	public void setPatientId(Long patientId) {
		this.patientId = patientId;
	}
	public String getStudyNo() {
		return studyNo;
	}
	public void setStudyNo(String studyNo) {
		this.studyNo = studyNo;
	}
	public String getAction() {
		return action;
	}
	public void setAction(String action) {
		this.action = action;
	}
}
