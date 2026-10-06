package com.wenwen.vo;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value = "患者详情（基本信息 + 随访时间线）")
public class PatientDetailVo {

	@ApiModelProperty("ID号")
	private Long patientId;
	@ApiModelProperty("研究编号")
	private String studyNo;
	@ApiModelProperty("姓名")
	private String name;
	@ApiModelProperty("性别编码：1 男 / 2 女")
	private Integer gender;
	@ApiModelProperty("性别中文；其他值为 null")
	private String sex;
	@ApiModelProperty("出生年份：取身份证号里的出生日期；无有效身份证号时 = 建档年份 − 建档时年龄")
	private Integer birthYear;
	@ApiModelProperty("当前年龄")
	private Integer age;
	@ApiModelProperty("随访状态编码，同患者列表")
	private String followStatus;
	@ApiModelProperty("随访状态中文")
	private String followStatusLabel;
	@ApiModelProperty("脱落原因（已脱落时），取医患关系的 reason / other_miss_reason / note")
	private String withdrawReason;
	@ApiModelProperty("疾病分型（按 RF / 抗CCP 计算）：血清阳性 / 血清阴性；两项都未检测为 null（页面显示「待补充」）")
	private String subtype;
	@ApiModelProperty("类风湿因子 RF：随访辅助检查 fzjc.lfsyz，取最近一次有结果的随访")
	private AntibodyVo rf;
	@ApiModelProperty("抗CCP抗体：随访辅助检查 fzjc.kccpkt，取最近一次有结果的随访")
	private AntibodyVo ccp;
	@ApiModelProperty("手机号")
	private String mobile;
	@ApiModelProperty("身份证号（脱敏，后 4 位为 *）；明文另调 patientSensitive")
	private String cardNoMasked;
	@ApiModelProperty("是否有身份证号（决定是否显示「眼睛」按钮）")
	private boolean hasCardNo;
	@ApiModelProperty("民族")
	private String nation;
	@ApiModelProperty("婚史编码（原值）")
	private Integer marry;
	@ApiModelProperty("婚史中文：0 未婚 / 1 已婚 / 2 离异 / 3 丧偶；其它编码为 null（页面显示「代码 N」）")
	private String marryLabel;
	@ApiModelProperty("建档日期 yyyy-MM-dd")
	private String createDate;
	@ApiModelProperty("随访观察起始：基线访视日期，无随访取建档日期")
	private String followStartDate;
	@ApiModelProperty("确诊日期")
	private String confirmDate;
	@ApiModelProperty("发病时间")
	private String happenDate;
	@ApiModelProperty("随访周期（月）")
	private int followCycle;
	@ApiModelProperty("下次应随访日期；已脱落或无随访为 null")
	private String nextDueDate;
	@ApiModelProperty("距下次应随访天数，负数为已逾期")
	private Integer nextDueDays;
	@ApiModelProperty("最近一次 DAS28-CRP（同患者列表）")
	private BigDecimal latestDas28;
	@ApiModelProperty("DAS28-CRP 疾病活动度：remission 临床缓解（<2.6）/ low 低（2.6~3.2）/ moderate 中（3.2~5.1）/ high 高（>5.1）；无分值为 null")
	private String das28Activity;
	@ApiModelProperty("疾病活动度中文：临床缓解 / 低疾病活动度 / 中疾病活动度 / 高疾病活动度")
	private String das28ActivityLabel;
	@ApiModelProperty("身高 cm")
	private String height;
	@ApiModelProperty("体重 kg")
	private String weight;
	@ApiModelProperty("BMI，1 位小数；身高体重任一缺失为 null")
	private BigDecimal bmi;
	@ApiModelProperty("腰围（cm），patient_basic_info.waistline")
	private String waistline;
	@ApiModelProperty("心率（次/分），patient_basic_info.xl")
	private String heartRate;
	@ApiModelProperty("收缩压（mmHg），patient_basic_info.xy_h；为空时取 xy 里「收缩压/舒张压」的前一个")
	private String systolic;
	@ApiModelProperty("舒张压（mmHg），patient_basic_info.xy_l；为空时取 xy 里「收缩压/舒张压」的后一个")
	private String diastolic;
	@ApiModelProperty("吸烟史（文字）")
	private String smoking;
	@ApiModelProperty("过敏史（文字）")
	private String allergy;
	@ApiModelProperty("家族史（文字）")
	private String familyHistory;
	@ApiModelProperty("既往史 / 其他病史（文字）")
	private String pastHistory;
	@ApiModelProperty("是否资料待补全")
	private boolean incomplete;
	@ApiModelProperty("缺失项")
	private List<String> missingItems;
	@ApiModelProperty("常见相关疾病")
	private List<ComorbidityVo> comorbidities;
	@ApiModelProperty("访视次数")
	private int visitCount;
	@ApiModelProperty("随访时间线，最近的在前")
	private List<VisitItemVo> visits;

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
	public String getWithdrawReason() {
		return withdrawReason;
	}
	public void setWithdrawReason(String withdrawReason) {
		this.withdrawReason = withdrawReason;
	}
	public String getSubtype() {
		return subtype;
	}
	public void setSubtype(String subtype) {
		this.subtype = subtype;
	}
	public String getMobile() {
		return mobile;
	}
	public void setMobile(String mobile) {
		this.mobile = mobile;
	}
	public String getCardNoMasked() {
		return cardNoMasked;
	}
	public void setCardNoMasked(String cardNoMasked) {
		this.cardNoMasked = cardNoMasked;
	}
	public boolean isHasCardNo() {
		return hasCardNo;
	}
	public void setHasCardNo(boolean hasCardNo) {
		this.hasCardNo = hasCardNo;
	}
	public String getNation() {
		return nation;
	}
	public void setNation(String nation) {
		this.nation = nation;
	}
	public Integer getMarry() {
		return marry;
	}
	public void setMarry(Integer marry) {
		this.marry = marry;
	}
	public String getMarryLabel() {
		return marryLabel;
	}
	public void setMarryLabel(String marryLabel) {
		this.marryLabel = marryLabel;
	}
	public String getCreateDate() {
		return createDate;
	}
	public void setCreateDate(String createDate) {
		this.createDate = createDate;
	}
	public String getFollowStartDate() {
		return followStartDate;
	}
	public void setFollowStartDate(String followStartDate) {
		this.followStartDate = followStartDate;
	}
	public String getConfirmDate() {
		return confirmDate;
	}
	public void setConfirmDate(String confirmDate) {
		this.confirmDate = confirmDate;
	}
	public String getHappenDate() {
		return happenDate;
	}
	public void setHappenDate(String happenDate) {
		this.happenDate = happenDate;
	}
	public int getFollowCycle() {
		return followCycle;
	}
	public void setFollowCycle(int followCycle) {
		this.followCycle = followCycle;
	}
	public String getNextDueDate() {
		return nextDueDate;
	}
	public void setNextDueDate(String nextDueDate) {
		this.nextDueDate = nextDueDate;
	}
	public Integer getNextDueDays() {
		return nextDueDays;
	}
	public void setNextDueDays(Integer nextDueDays) {
		this.nextDueDays = nextDueDays;
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
	public String getWaistline() {
		return waistline;
	}
	public void setWaistline(String waistline) {
		this.waistline = waistline;
	}
	public String getHeartRate() {
		return heartRate;
	}
	public void setHeartRate(String heartRate) {
		this.heartRate = heartRate;
	}
	public String getSystolic() {
		return systolic;
	}
	public void setSystolic(String systolic) {
		this.systolic = systolic;
	}
	public String getDiastolic() {
		return diastolic;
	}
	public void setDiastolic(String diastolic) {
		this.diastolic = diastolic;
	}
	public String getHeight() {
		return height;
	}
	public void setHeight(String height) {
		this.height = height;
	}
	public String getWeight() {
		return weight;
	}
	public void setWeight(String weight) {
		this.weight = weight;
	}
	public BigDecimal getBmi() {
		return bmi;
	}
	public void setBmi(BigDecimal bmi) {
		this.bmi = bmi;
	}
	public String getSmoking() {
		return smoking;
	}
	public void setSmoking(String smoking) {
		this.smoking = smoking;
	}
	public String getAllergy() {
		return allergy;
	}
	public void setAllergy(String allergy) {
		this.allergy = allergy;
	}
	public String getFamilyHistory() {
		return familyHistory;
	}
	public void setFamilyHistory(String familyHistory) {
		this.familyHistory = familyHistory;
	}
	public String getPastHistory() {
		return pastHistory;
	}
	public void setPastHistory(String pastHistory) {
		this.pastHistory = pastHistory;
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
	public int getVisitCount() {
		return visitCount;
	}
	public void setVisitCount(int visitCount) {
		this.visitCount = visitCount;
	}
	public List<VisitItemVo> getVisits() {
		return visits;
	}
	public void setVisits(List<VisitItemVo> visits) {
		this.visits = visits;
	}
}
