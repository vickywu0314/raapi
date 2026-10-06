package com.wenwen.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value = "数据导出：可直接下载的时间范围")
public class ExportRangeVo {

	@ApiModelProperty("可直接下载的最早随访日期（当前月份往前 freeMonths 个月的 1 号）")
	private String freeStartDate;
	@ApiModelProperty("今天（导出截止日期最晚为今天）")
	private String today;
	@ApiModelProperty("可直接下载的月数")
	private int freeMonths;
	@ApiModelProperty("是否已配置申请接收邮箱")
	private boolean mailConfigured;

	public String getFreeStartDate() {
		return freeStartDate;
	}
	public void setFreeStartDate(String freeStartDate) {
		this.freeStartDate = freeStartDate;
	}
	public String getToday() {
		return today;
	}
	public void setToday(String today) {
		this.today = today;
	}
	public int getFreeMonths() {
		return freeMonths;
	}
	public void setFreeMonths(int freeMonths) {
		this.freeMonths = freeMonths;
	}
	public boolean isMailConfigured() {
		return mailConfigured;
	}
	public void setMailConfigured(boolean mailConfigured) {
		this.mailConfigured = mailConfigured;
	}
}
