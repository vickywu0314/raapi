package com.wenwen.vo;

import java.math.BigDecimal;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value = "项目总览数据")
public class ProjectsDataVo {

	@ApiModelProperty("已入组患者（全部患者，含已脱落）")
	private int enrolledPatients;
	@ApiModelProperty("计划随访完成率 %，保留1位小数")
	private BigDecimal followUpCompletionRate;
	@ApiModelProperty("本周期已随访患者数（完成率分子）")
	private int followUpDoneCount;
	@ApiModelProperty("本周期应随访患者数（完成率分母）")
	private int followUpDueCount;
	@ApiModelProperty("整体数据质量 %（无待处理质控问题的患者占比），保留1位小数")
	private BigDecimal dataQualityRate;
	@ApiModelProperty("参与研究中心数")
	private int centerCount;
	@ApiModelProperty("跟踪患者：目前有效的患者数")
	private int trackingPatients;
	@ApiModelProperty("跟踪患者：全部患者数")
	private int totalPatients;
	@ApiModelProperty("跟踪患者占比 %，保留1位小数")
	private BigDecimal trackingRate;
	@ApiModelProperty("待随访患者数")
	private int pendingFollowUpPatients;
	@ApiModelProperty("待处理质控问题条数")
	private int pendingQcIssues;
	@ApiModelProperty("有待处理质控问题的患者数")
	private int qcIssuePatients;
	@ApiModelProperty("研究级可用记录数")
	private int researchUsableRecords;

	public int getEnrolledPatients() {
		return enrolledPatients;
	}
	public void setEnrolledPatients(int enrolledPatients) {
		this.enrolledPatients = enrolledPatients;
	}
	public BigDecimal getFollowUpCompletionRate() {
		return followUpCompletionRate;
	}
	public void setFollowUpCompletionRate(BigDecimal followUpCompletionRate) {
		this.followUpCompletionRate = followUpCompletionRate;
	}
	public int getFollowUpDoneCount() {
		return followUpDoneCount;
	}
	public void setFollowUpDoneCount(int followUpDoneCount) {
		this.followUpDoneCount = followUpDoneCount;
	}
	public int getFollowUpDueCount() {
		return followUpDueCount;
	}
	public void setFollowUpDueCount(int followUpDueCount) {
		this.followUpDueCount = followUpDueCount;
	}
	public BigDecimal getDataQualityRate() {
		return dataQualityRate;
	}
	public void setDataQualityRate(BigDecimal dataQualityRate) {
		this.dataQualityRate = dataQualityRate;
	}
	public int getCenterCount() {
		return centerCount;
	}
	public void setCenterCount(int centerCount) {
		this.centerCount = centerCount;
	}
	public int getTrackingPatients() {
		return trackingPatients;
	}
	public void setTrackingPatients(int trackingPatients) {
		this.trackingPatients = trackingPatients;
	}
	public int getTotalPatients() {
		return totalPatients;
	}
	public void setTotalPatients(int totalPatients) {
		this.totalPatients = totalPatients;
	}
	public BigDecimal getTrackingRate() {
		return trackingRate;
	}
	public void setTrackingRate(BigDecimal trackingRate) {
		this.trackingRate = trackingRate;
	}
	public int getPendingFollowUpPatients() {
		return pendingFollowUpPatients;
	}
	public void setPendingFollowUpPatients(int pendingFollowUpPatients) {
		this.pendingFollowUpPatients = pendingFollowUpPatients;
	}
	public int getPendingQcIssues() {
		return pendingQcIssues;
	}
	public void setPendingQcIssues(int pendingQcIssues) {
		this.pendingQcIssues = pendingQcIssues;
	}
	public int getQcIssuePatients() {
		return qcIssuePatients;
	}
	public void setQcIssuePatients(int qcIssuePatients) {
		this.qcIssuePatients = qcIssuePatients;
	}
	public int getResearchUsableRecords() {
		return researchUsableRecords;
	}
	public void setResearchUsableRecords(int researchUsableRecords) {
		this.researchUsableRecords = researchUsableRecords;
	}
}
