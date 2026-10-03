package com.wenwen.vo;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value = "患者列表一行")
public class PatientItemVo {

	@ApiModelProperty("ID号（患者主键）")
	private Long patientId;
	@ApiModelProperty("研究编号，如 RA-20261003-00001")
	private String studyNo;
	@ApiModelProperty("姓名")
	private String name;
	@ApiModelProperty("性别编码：1 男 / 2 女")
	private Integer gender;
	@ApiModelProperty("性别中文：男 / 女；其他值为 null")
	private String sex;
	@ApiModelProperty("出生年份 = 建档年份 − 建档时年龄；无年龄则 null")
	private Integer birthYear;
	@ApiModelProperty("当前年龄 = 今年 − 出生年份；无则 null")
	private Integer age;
	@ApiModelProperty("已随访次数（随访记录条数）")
	private int visitCount;
	@ApiModelProperty("随访周期（月）：3 / 6 / 12 / 24")
	private int followCycle;
	@ApiModelProperty("疾病分型；一期无结构化数据，返回 null（页面显示「分型未提供」）")
	private String subtype;
	@ApiModelProperty("最近一次 DAS28-CRP；一期无结构化数据，返回 null")
	private BigDecimal latestDas28;
	@ApiModelProperty("最近随访日期 yyyy-MM-dd；无随访则 null（页面显示「暂无访视」）")
	private String lastVisitDate;
	@ApiModelProperty("下次应随访日期 yyyy-MM-dd = 最近随访 + 随访周期；已脱落或无随访则 null")
	private String nextDueDate;
	@ApiModelProperty("随访状态：active 随访中 / soon 近期需随访 / overdue 随访逾期 / pending_first 待首次随访 / withdrawn 已脱落")
	private String followStatus;
	@ApiModelProperty("随访状态中文")
	private String followStatusLabel;
	@ApiModelProperty("是否资料待补全（命中任一「缺失」类质控规则）")
	private boolean incomplete;
	@ApiModelProperty("缺失项中文，如 [缺 DAS28 评分]")
	private List<String> missingItems;
	@ApiModelProperty("其他病史；空数组时页面显示「无」")
	private List<ComorbidityVo> comorbidities;

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
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public Integer getGender() {
		return gender;
	}
	public void setGender(Integer gender) {
		this.gender = gender;
	}
	public String getSex() {
		return sex;
	}
	public void setSex(String sex) {
		this.sex = sex;
	}
	public Integer getBirthYear() {
		return birthYear;
	}
	public void setBirthYear(Integer birthYear) {
		this.birthYear = birthYear;
	}
	public Integer getAge() {
		return age;
	}
	public void setAge(Integer age) {
		this.age = age;
	}
	public int getVisitCount() {
		return visitCount;
	}
	public void setVisitCount(int visitCount) {
		this.visitCount = visitCount;
	}
	public int getFollowCycle() {
		return followCycle;
	}
	public void setFollowCycle(int followCycle) {
		this.followCycle = followCycle;
	}
	public String getSubtype() {
		return subtype;
	}
	public void setSubtype(String subtype) {
		this.subtype = subtype;
	}
	public BigDecimal getLatestDas28() {
		return latestDas28;
	}
	public void setLatestDas28(BigDecimal latestDas28) {
		this.latestDas28 = latestDas28;
	}
	public String getLastVisitDate() {
		return lastVisitDate;
	}
	public void setLastVisitDate(String lastVisitDate) {
		this.lastVisitDate = lastVisitDate;
	}
	public String getNextDueDate() {
		return nextDueDate;
	}
	public void setNextDueDate(String nextDueDate) {
		this.nextDueDate = nextDueDate;
	}
	public String getFollowStatus() {
		return followStatus;
	}
	public void setFollowStatus(String followStatus) {
		this.followStatus = followStatus;
	}
	public String getFollowStatusLabel() {
		return followStatusLabel;
	}
	public void setFollowStatusLabel(String followStatusLabel) {
		this.followStatusLabel = followStatusLabel;
	}
	public boolean isIncomplete() {
		return incomplete;
	}
	public void setIncomplete(boolean incomplete) {
		this.incomplete = incomplete;
	}
	public List<String> getMissingItems() {
		return missingItems;
	}
	public void setMissingItems(List<String> missingItems) {
		this.missingItems = missingItems;
	}
	public List<ComorbidityVo> getComorbidities() {
		return comorbidities;
	}
	public void setComorbidities(List<ComorbidityVo> comorbidities) {
		this.comorbidities = comorbidities;
	}
}
