package com.wenwen.vo;

import java.util.List;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value = "患者列表")
public class PatientsListVo {

	@ApiModelProperty("页头：该医生名下已建档患者总数（不受筛选影响）")
	private int totalPatients;
	@ApiModelProperty("页头：资料待补全人数（不受筛选影响）")
	private int incompleteCount;
	@ApiModelProperty("符合筛选条件的总条数（分页用）")
	private int total;
	@ApiModelProperty("当前页码")
	private int page;
	@ApiModelProperty("每页条数")
	private int size;
	@ApiModelProperty("当前页患者")
	private List<PatientItemVo> items;

	public int getTotalPatients() {
		return totalPatients;
	}
	public void setTotalPatients(int totalPatients) {
		this.totalPatients = totalPatients;
	}
	public int getIncompleteCount() {
		return incompleteCount;
	}
	public void setIncompleteCount(int incompleteCount) {
		this.incompleteCount = incompleteCount;
	}
	public int getTotal() {
		return total;
	}
	public void setTotal(int total) {
		this.total = total;
	}
	public int getPage() {
		return page;
	}
	public void setPage(int page) {
		this.page = page;
	}
	public int getSize() {
		return size;
	}
	public void setSize(int size) {
		this.size = size;
	}
	public List<PatientItemVo> getItems() {
		return items;
	}
	public void setItems(List<PatientItemVo> items) {
		this.items = items;
	}
}
