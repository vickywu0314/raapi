package com.wenwen.vo;

import java.util.Map;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value = "编辑随访：保存请求（JSON）")
public class VisitUpdateRequest {

	@ApiModelProperty("医生ID")
	private Long doctorId;
	@ApiModelProperty("随访ID")
	private Long visitId;
	@ApiModelProperty("打开表单时拿到的 version")
	private String version;
	@ApiModelProperty("随访日期 yyyy-MM-dd")
	private String visitDate;
	@ApiModelProperty("随访表字段（bsbq / fzjc / …）→ 该模块提交的内容")
	private Map<String, VisitModuleUpdate> modules;

	public Long getDoctorId() {
		return doctorId;
	}
	public void setDoctorId(Long doctorId) {
		this.doctorId = doctorId;
	}
	public Long getVisitId() {
		return visitId;
	}
	public void setVisitId(Long visitId) {
		this.visitId = visitId;
	}
	public String getVersion() {
		return version;
	}
	public void setVersion(String version) {
		this.version = version;
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
