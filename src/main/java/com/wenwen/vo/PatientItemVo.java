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
	@ApiModelProperty("出生年份：取身份证号里的出生日期；无有效身份证号时 = 建档年份 − 建档时年龄；都没有为 null")
	private Integer birthYear;
	@ApiModelProperty("当前年龄：有身份证号按出生日期算周岁，否则 = 今年 − 出生年份；无则 null")
	private Integer age;
	@ApiModelProperty("已随访次数（随访记录条数）")
	private int visitCount;
	@ApiModelProperty("随访周期（月）：3 / 6 / 12 / 24")
	private int followCycle;
	@ApiModelProperty("疾病分型（按 RF / 抗CCP 计算）：血清阳性 / 血清阴性；两项都未检测为 null（页面显示「分型未提供」）")
	private String subtype;
	@ApiModelProperty("类风湿因子 RF：随访辅助检查 fzjc.lfsyz，取最近一次有结果的随访")
	private AntibodyVo rf;
	@ApiModelProperty("抗CCP抗体：随访辅助检查 fzjc.kccpkt，取最近一次有结果的随访")
	private AntibodyVo ccp;
	@ApiModelProperty("最近有效存量 DAS28-CRP（bqpg.result.crpScore），原值非负、两位 HALF_UP；非法则继续历史查找，无有效值为 null")
	private BigDecimal latestDas28;
	@ApiModelProperty("DAS28-CRP 疾病活动度：按两位 canonical：remission 临床缓解（<2.3）/ low 低（2.3~2.7）/ moderate 中（>2.7 且 ≤4.1）/ high 高（>4.1）；无有效分值为 null")
	private String das28Activity;
	@ApiModelProperty("疾病活动度中文：临床缓解 / 低疾病活动度 / 中疾病活动度 / 高疾病活动度")
	private String das28ActivityLabel;
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
	public String getDas28Activity() {
		return das28Activity;
	}
	public void setDas28Activity(String das28Activity) {
		this.das28Activity = das28Activity;
	}
	public String getDas28ActivityLabel() {
		return das28ActivityLabel;
	}
	public void setDas28ActivityLabel(String das28ActivityLabel) {
		this.das28ActivityLabel = das28ActivityLabel;
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
	public AntibodyVo getRf() {
		return rf;
	}
	public void setRf(AntibodyVo rf) {
		this.rf = rf;
	}
	public AntibodyVo getCcp() {
		return ccp;
	}
	public void setCcp(AntibodyVo ccp) {
		this.ccp = ccp;
	}
	public List<ComorbidityVo> getComorbidities() {
		return comorbidities;
	}
	public void setComorbidities(List<ComorbidityVo> comorbidities) {
		this.comorbidities = comorbidities;
	}
}
