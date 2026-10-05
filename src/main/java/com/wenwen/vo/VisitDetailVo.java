package com.wenwen.vo;

import java.util.List;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value = "访视详情（visit-detail.html）")
public class VisitDetailVo {

	@ApiModelProperty("随访ID")
	private Long visitId;
	@ApiModelProperty("患者ID")
	private Long patientId;
	@ApiModelProperty("随访日期 yyyy-MM-dd")
	private String visitDate;
	@ApiModelProperty("访视类型：该患者时间最早的一次为「基线访视」，其余为「常规随访」")
	private String visitType;
	@ApiModelProperty("是否基线访视")
	private boolean baseline;
	@ApiModelProperty("记录医生ID")
	private Long doctorId;
	@ApiModelProperty("记录医生姓名（user.name）")
	private String doctorName;
	@ApiModelProperty("有内容的模块数（共 7 个）")
	private int filledCount;
	@ApiModelProperty("7 个病历模块，按页面顺序")
	private List<VisitModuleVo> modules;

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
	public boolean isBaseline() {
		return baseline;
	}
	public void setBaseline(boolean baseline) {
		this.baseline = baseline;
	}
	public Long getDoctorId() {
		return doctorId;
	}
	public void setDoctorId(Long doctorId) {
		this.doctorId = doctorId;
	}
	public String getDoctorName() {
		return doctorName;
	}
	public void setDoctorName(String doctorName) {
		this.doctorName = doctorName;
	}
	public int getFilledCount() {
		return filledCount;
	}
	public void setFilledCount(int filledCount) {
		this.filledCount = filledCount;
	}
	public List<VisitModuleVo> getModules() {
		return modules;
	}
	public void setModules(List<VisitModuleVo> modules) {
		this.modules = modules;
	}
}
