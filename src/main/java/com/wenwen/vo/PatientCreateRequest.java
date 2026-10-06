package com.wenwen.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value = "新建患者请求（JSON）")
public class PatientCreateRequest {

	@ApiModelProperty("当前医生ID（必填）")
	private Long doctorId;
	@ApiModelProperty("身份证号已在 RA 研究库且不在本医生名下时，医生确认「转到自己名下」后传 true")
	private boolean transfer;
	@ApiModelProperty("基本信息")
	private PatientBasicForm basic;

	public Long getDoctorId() {
		return doctorId;
	}
	public void setDoctorId(Long doctorId) {
		this.doctorId = doctorId;
	}
	public boolean isTransfer() {
		return transfer;
	}
	public void setTransfer(boolean transfer) {
		this.transfer = transfer;
	}
	public PatientBasicForm getBasic() {
		return basic;
	}
	public void setBasic(PatientBasicForm basic) {
		this.basic = basic;
	}
}
