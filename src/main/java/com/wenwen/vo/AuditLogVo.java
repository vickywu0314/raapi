package com.wenwen.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value = "修改记录一条")
public class AuditLogVo {

	@ApiModelProperty("记录ID")
	private Long id;
	@ApiModelProperty("操作时间 yyyy-MM-dd HH:mm")
	private String time;
	@ApiModelProperty("动作：新建档案 / 修改档案 / 新增随访 / 编辑随访 / 删除随访 / 标记脱落 / 质控处理")
	private String action;
	@ApiModelProperty("涉及的随访ID，档案类操作为 null")
	private Long visitId;
	@ApiModelProperty("修改内容；多项用「；」分隔，字段修改为「字段：旧值 → 新值」")
	private String detail;
	@ApiModelProperty("操作人显示名")
	private String operator;

	public Long getId() {
		return id;
	}
	public void setId(Long id) {
		this.id = id;
	}
	public String getTime() {
		return time;
	}
	public void setTime(String time) {
		this.time = time;
	}
	public String getAction() {
		return action;
	}
	public void setAction(String action) {
		this.action = action;
	}
	public Long getVisitId() {
		return visitId;
	}
	public void setVisitId(Long visitId) {
		this.visitId = visitId;
	}
	public String getDetail() {
		return detail;
	}
	public void setDetail(String detail) {
		this.detail = detail;
	}
	public String getOperator() {
		return operator;
	}
	public void setOperator(String operator) {
		this.operator = operator;
	}
}
