package com.wenwen.vo;

import java.util.Map;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value = "新增随访请求（JSON）")
public class VisitCreateRequest {

	@ApiModelProperty("当前医生ID（必填）")
	private Long doctorId;
	@ApiModelProperty("患者ID（必填）")
	private Long patientId;
	@ApiModelProperty("随访日期 yyyy-MM-dd（必填，不能晚于今天）")
	private String visitDate;
	@ApiModelProperty("各模块填写的内容：随访表字段名（bsbq / fzjc / bqpg / zyzd / zlfa / blsj）→ 字段 / 未查 / 清单，结构同编辑随访")
	private Map<String, VisitModuleUpdate> modules;

	public Long getDoctorId() {
		return doctorId;
	}
	public void setDoctorId(Long doctorId) {
		this.doctorId = doctorId;
	}
	public Long getPatientId() {
		return patientId;
	}
	public void setPatientId(Long patientId) {
		this.patientId = patientId;
	}
	public String getVisitDate() {
		return visitDate;
	}
	public void setVisitDate(String visitDate) {
		this.visitDate = visitDate;
	}
	public Map<String, VisitModuleUpdate> getModules() {
		return modules;
	}
	public void setModules(Map<String, VisitModuleUpdate> modules) {
		this.modules = modules;
	}
}
