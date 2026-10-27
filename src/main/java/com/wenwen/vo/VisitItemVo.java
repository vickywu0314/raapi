package com.wenwen.vo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value = "随访时间线一条")
public class VisitItemVo {

	@ApiModelProperty("随访ID（patient_follow_up_history.id）")
	private Long visitId;
	@ApiModelProperty("随访日期 yyyy-MM-dd；无则 null")
	private String visitDate;
	@ApiModelProperty("访视类型：时间最早的一次为「基线访视」，其余为「第 N 次随访」")
	private String visitType;
	@ApiModelProperty("是否基线访视")
	private boolean baseline;
    @ApiModelProperty("基线为0；后续按完整时间与小ID顺序编号")
    private int followUpNumber;
    public int getFollowUpNumber() { return followUpNumber; }
    public void setFollowUpNumber(int value) { followUpNumber=value; }
	@ApiModelProperty("本次 DAS28-CRP（病情评估 bqpg.result.crpScore，与 latestDas28 同一读取与两位 HALF_UP 口径）；无有效值为 null")
	private java.math.BigDecimal das28Crp;
	@ApiModelProperty("本次 DAS28-ESR（bqpg.result.esrScore，非负、两位 HALF_UP）；无有效值为 null")
	private java.math.BigDecimal das28Esr;
	@ApiModelProperty("与上次相比治疗方案是否调整（治疗方案 zlfa.tz：是 true / 否 false）；未填写（含基线）为 null")
	private Boolean regimenChanged;
	@ApiModelProperty("加重诱因（病史病情 bsbq.cause，多选用「、」连接，「其他」带上 extraCause；去掉「无」）；未填写或只有「无」为 null")
	private String cause;
	public java.math.BigDecimal getDas28Crp() { return das28Crp; }
	public void setDas28Crp(java.math.BigDecimal value) { das28Crp = value; }
	public java.math.BigDecimal getDas28Esr() { return das28Esr; }
	public void setDas28Esr(java.math.BigDecimal value) { das28Esr = value; }
	public Boolean getRegimenChanged() { return regimenChanged; }
	public void setRegimenChanged(Boolean value) { regimenChanged = value; }
	public String getCause() { return cause; }
	public void setCause(String value) { cause = value; }

	@ApiModelProperty("记录医生ID")
	private Long doctorId;
	@ApiModelProperty("记录医生姓名（user.name）；无则 null")
	private String doctorName;

	@JsonSerialize(using = ToStringSerializer.class)
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
	@JsonSerialize(using = ToStringSerializer.class)
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
}
