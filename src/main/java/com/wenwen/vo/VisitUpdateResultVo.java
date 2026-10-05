package com.wenwen.vo;

import java.util.List;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value = "编辑随访：保存结果")
public class VisitUpdateResultVo {

	@ApiModelProperty("实际修改的项数（含自动计算项）；0 表示没有修改，未写数据库")
	private int changedCount;
	@ApiModelProperty("修改内容，与修改记录一致")
	private List<String> changes;

	public int getChangedCount() {
		return changedCount;
	}
	public void setChangedCount(int changedCount) {
		this.changedCount = changedCount;
	}
	public List<String> getChanges() {
		return changes;
	}
	public void setChanges(List<String> changes) {
		this.changes = changes;
	}
}
