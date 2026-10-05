package com.wenwen.vo;

import java.util.List;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value = "编辑随访表单")
public class VisitEditFormVo {

	@ApiModelProperty("随访ID")
	private Long visitId;
	@ApiModelProperty("患者ID")
	private Long patientId;
	@ApiModelProperty("随访日期 yyyy-MM-dd")
	private String visitDate;
	@ApiModelProperty("基线访视 / 常规随访")
	private String visitType;
	@ApiModelProperty("数据版本，保存时原样传回；期间数据被别人改过则保存失败，提示刷新")
	private String version;
	@ApiModelProperty("7 个病历模块")
	private List<VisitEditModuleVo> modules;

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
	public String getVisitDate() {
		return visitDate;
	}
	public void setVisitDate(String visitDate) {
		this.visitDate = visitDate;
	}
	public String getVisitType() {
		return visitType;
	}
	public void setVisitType(String visitType) {
		this.visitType = visitType;
	}
	public String getVersion() {
		return version;
	}
	public void setVersion(String version) {
		this.version = version;
	}
	public List<VisitEditModuleVo> getModules() {
		return modules;
	}
	public void setModules(List<VisitEditModuleVo> modules) {
		this.modules = modules;
	}
}
