package com.wenwen.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value = "ACR/EULAR 2010 类风湿关节炎分类标准：4 部分各选一项的得分")
public class AcrEularForm {

	@ApiModelProperty("受累关节数量：0 1个中大关节 / 1 2-10个中大关节 / 2 1-3个小关节 / 3 4-10个小关节 / 5 >10个小关节")
	private Integer jointScore;
	@ApiModelProperty("血清学抗体检测：0 均阴性 / 2 至少一项低滴度阳性 / 3 至少一项高滴度阳性")
	private Integer serologyScore;
	@ApiModelProperty("滑膜炎持续时间：0 <6周 / 1 ≥6周")
	private Integer durationScore;
	@ApiModelProperty("急性时相反应物：0 CRP或ESR均正常 / 1 增高")
	private Integer acuteScore;
	@ApiModelProperty("总分（后端按 4 部分重算，前端传的忽略）")
	private Integer totalScore;

	public Integer getJointScore() {
		return jointScore;
	}
	public void setJointScore(Integer jointScore) {
		this.jointScore = jointScore;
	}
	public Integer getSerologyScore() {
		return serologyScore;
	}
	public void setSerologyScore(Integer serologyScore) {
		this.serologyScore = serologyScore;
	}
	public Integer getDurationScore() {
		return durationScore;
	}
	public void setDurationScore(Integer durationScore) {
		this.durationScore = durationScore;
	}
	public Integer getAcuteScore() {
		return acuteScore;
	}
	public void setAcuteScore(Integer acuteScore) {
		this.acuteScore = acuteScore;
	}
	public Integer getTotalScore() {
		return totalScore;
	}
	public void setTotalScore(Integer totalScore) {
		this.totalScore = totalScore;
	}
}
