package com.wenwen.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value = "血清抗体结果（RF / 抗CCP），截至本次日期最新可分类的有日期随访；可能不同于曾经阳性分型")
public class AntibodyVo {

	@ApiModelProperty("状态：negative 阴性 / low_positive 低滴度阳性 / high_positive 高滴度阳性 / positive 阳性（滴度未定） / untested 未检测")
	private String status;
	@ApiModelProperty("状态中文：阴性 / 低滴度阳性 / 高滴度阳性 / 阳性（滴度未定） / 未检测")
	private String statusLabel;
	@ApiModelProperty("化验原值（随访 fzjc 里的原文，如 960、<20）；未检测为 null")
	private String value;
	@ApiModelProperty("判定用的参考范围上限 ULN（配置 ra.serology.*-uln）")
	private Double uln;
	@ApiModelProperty("取自哪次随访的日期 yyyy-MM-dd；未检测为 null")
	private String visitDate;

	public String getStatus() {
		return status;
	}
	public void setStatus(String status) {
		this.status = status;
	}
	public String getStatusLabel() {
		return statusLabel;
	}
	public void setStatusLabel(String statusLabel) {
		this.statusLabel = statusLabel;
	}
	public String getValue() {
		return value;
	}
	public void setValue(String value) {
		this.value = value;
	}
	public Double getUln() {
		return uln;
	}
	public void setUln(Double uln) {
		this.uln = uln;
	}
	public String getVisitDate() {
		return visitDate;
	}
	public void setVisitDate(String visitDate) {
		this.visitDate = visitDate;
	}
}
