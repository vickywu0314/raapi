package com.wenwen.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel("随访管理 · 一位患者的下次随访计划")
public class FollowupPlanVo {

	@ApiModelProperty("患者ID（页面显示为 ID号）")
	private Long patientId;
	@ApiModelProperty("研究编号")
	private String studyNo;
	@ApiModelProperty("姓名")
	private String name;
	@ApiModelProperty("性别：男 / 女 / null")
	private String sex;
	@ApiModelProperty("出生年份（按身份证号）")
	private Integer birthYear;
	@ApiModelProperty("已随访次数")
	private int visitCount;
	@ApiModelProperty("下次是第几次随访 = visitCount + 1")
	private int nth;
	@ApiModelProperty("是否首次（基线）随访：还没有随访记录")
	private boolean first;
	@ApiModelProperty("随访周期（月）")
	private int followCycle;
	@ApiModelProperty("最近随访日期 yyyy-MM-dd；没有随访为 null")
	private String lastVisitDate;
	@ApiModelProperty("预计日期 yyyy-MM-dd：最近随访 + 随访周期；没有随访取建档日期")
	private String dueDate;
	@ApiModelProperty("距今天数，负数 = 已逾期天数")
	private Long days;
	@ApiModelProperty("状态：overdue 已逾期 / soon 14 天内到期 / later 待随访")
	private String status;
	@ApiModelProperty("状态 · 标签文字")
	private String statusLabel;

	public Long getPatientId() { return patientId; }
	public void setPatientId(Long patientId) { this.patientId = patientId; }
	public String getStudyNo() { return studyNo; }
	public void setStudyNo(String studyNo) { this.studyNo = studyNo; }
	public String getName() { return name; }
	public void setName(String name) { this.name = name; }
	public String getSex() { return sex; }
	public void setSex(String sex) { this.sex = sex; }
	public Integer getBirthYear() { return birthYear; }
	public void setBirthYear(Integer birthYear) { this.birthYear = birthYear; }
	public int getVisitCount() { return visitCount; }
	public void setVisitCount(int visitCount) { this.visitCount = visitCount; }
	public int getNth() { return nth; }
	public void setNth(int nth) { this.nth = nth; }
	public boolean isFirst() { return first; }
	public void setFirst(boolean first) { this.first = first; }
	public int getFollowCycle() { return followCycle; }
	public void setFollowCycle(int followCycle) { this.followCycle = followCycle; }
	public String getLastVisitDate() { return lastVisitDate; }
	public void setLastVisitDate(String lastVisitDate) { this.lastVisitDate = lastVisitDate; }
	public String getDueDate() { return dueDate; }
	public void setDueDate(String dueDate) { this.dueDate = dueDate; }
	public Long getDays() { return days; }
	public void setDays(Long days) { this.days = days; }
	public String getStatus() { return status; }
	public void setStatus(String status) { this.status = status; }
	public String getStatusLabel() { return statusLabel; }
	public void setStatusLabel(String statusLabel) { this.statusLabel = statusLabel; }
}
