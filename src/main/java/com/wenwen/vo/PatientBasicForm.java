package com.wenwen.vo;

import java.util.List;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value = "新建患者 / 编辑档案的基本信息表单（预填与提交共用）")
public class PatientBasicForm {

	@ApiModelProperty("姓名（必填）")
	private String name;
	@ApiModelProperty("身份证号（可空；填了校验格式）")
	private String cardNo;
	@ApiModelProperty("性别：1 男 / 2 女（必填）")
	private Integer gender;
	@ApiModelProperty("手机号")
	private String mobile;
	@ApiModelProperty("民族，如 汉族")
	private String nation;
	@ApiModelProperty("婚史：0 未婚 / 1 已婚 / 2 离异 / 3 丧偶")
	private Integer marry;
	@ApiModelProperty("确诊日期 yyyy-MM-dd")
	private String confirmDate;
	@ApiModelProperty("发病时间 yyyy-MM-dd")
	private String happenDate;
	@ApiModelProperty("身高 cm")
	private String height;
	@ApiModelProperty("体重 kg")
	private String weight;
	@ApiModelProperty("腰围 cm")
	private String waistline;
	@ApiModelProperty("心率 次/分")
	private String heartRate;
	@ApiModelProperty("收缩压 mmHg")
	private String systolic;
	@ApiModelProperty("舒张压 mmHg")
	private String diastolic;
	@ApiModelProperty("吸烟史：0 无 / 1 有")
	private Integer smoke;
	@ApiModelProperty("吸烟年数（smoke=1 时）")
	private Integer smokeYears;
	@ApiModelProperty("每天支数（smoke=1 时）")
	private Integer smokeCountByDay;
	@ApiModelProperty("是否已戒烟：0 否 / 1 是（smoke=1 时）")
	private Integer smokeStop;
	@ApiModelProperty("过敏史")
	private String allergyHistory;
	@ApiModelProperty("家族史")
	private String familyHistory;
	@ApiModelProperty("既往史")
	private String pastHistory;
	@ApiModelProperty("随访周期（月）：3 / 6 / 12 / 24，默认 12")
	private Integer followCycle;
	@ApiModelProperty("ACR/EULAR 2010 评估；未评估为 null")
	private AcrEularForm acrEular;
	@ApiModelProperty("常见相关疾病（医生勾选）")
	private List<ComorbidityInput> comorbidities;

	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getCardNo() {
		return cardNo;
	}
	public void setCardNo(String cardNo) {
		this.cardNo = cardNo;
	}
	public Integer getGender() {
		return gender;
	}
	public void setGender(Integer gender) {
		this.gender = gender;
	}
	public String getMobile() {
		return mobile;
	}
	public void setMobile(String mobile) {
		this.mobile = mobile;
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
	public Integer getSmoke() {
		return smoke;
	}
	public void setSmoke(Integer smoke) {
		this.smoke = smoke;
	}
	public Integer getSmokeYears() {
		return smokeYears;
	}
	public void setSmokeYears(Integer smokeYears) {
		this.smokeYears = smokeYears;
	}
	public Integer getSmokeCountByDay() {
		return smokeCountByDay;
	}
	public void setSmokeCountByDay(Integer smokeCountByDay) {
		this.smokeCountByDay = smokeCountByDay;
	}
	public Integer getSmokeStop() {
		return smokeStop;
	}
	public void setSmokeStop(Integer smokeStop) {
		this.smokeStop = smokeStop;
	}
	public String getAllergyHistory() {
		return allergyHistory;
	}
	public void setAllergyHistory(String allergyHistory) {
		this.allergyHistory = allergyHistory;
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
	public Integer getFollowCycle() {
		return followCycle;
	}
	public void setFollowCycle(Integer followCycle) {
		this.followCycle = followCycle;
	}
	public AcrEularForm getAcrEular() {
		return acrEular;
	}
	public void setAcrEular(AcrEularForm acrEular) {
		this.acrEular = acrEular;
	}
	public List<ComorbidityInput> getComorbidities() {
		return comorbidities;
	}
	public void setComorbidities(List<ComorbidityInput> comorbidities) {
		this.comorbidities = comorbidities;
	}
}
