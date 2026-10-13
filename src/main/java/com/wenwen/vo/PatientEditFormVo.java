package com.wenwen.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value = "编辑档案表单的当前值")
public class PatientEditFormVo {

	@ApiModelProperty("患者ID")
	private Long patientId;
	@ApiModelProperty("研究编号（不可修改）")
	private String studyNo;
	@ApiModelProperty("基本信息当前值；acrEular 为最近一次评估的各部分（新系统评估过才有），comorbidities 含 source")
	private PatientBasicForm basic;
	@ApiModelProperty("ACR/EULAR 2010 总分（老数据可能只有总分、没有各部分）；未评估为 null")
	private Integer acrEularScore;

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
	public PatientBasicForm getBasic() {
		return basic;
	}
	public void setBasic(PatientBasicForm basic) {
		this.basic = basic;
	}
	public Integer getAcrEularScore() {
		return acrEularScore;
	}
	public void setAcrEularScore(Integer acrEularScore) {
		this.acrEularScore = acrEularScore;
	}
}
