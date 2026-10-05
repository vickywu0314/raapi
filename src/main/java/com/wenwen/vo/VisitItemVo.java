package com.wenwen.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value = "随访时间线一条")
public class VisitItemVo {

	@ApiModelProperty("随访ID（patient_follow_up_history.id）")
	private Long visitId;
	@ApiModelProperty("随访日期 yyyy-MM-dd；无则 null")
	private String visitDate;
	@ApiModelProperty("访视类型：时间最早的一次为「基线访视」，其余为「常规随访」")
	private String visitType;
	@ApiModelProperty("是否基线访视")
	private boolean baseline;
	@ApiModelProperty("记录医生ID")
	private Long doctorId;

	public Long getVisitId() {
		return visitId;
	}
	public void setVisitId(Long visitId) {
		this.visitId = visitId;
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
}
