package com.wenwen.vo;

import java.util.List;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value = "编辑档案请求（JSON）")
public class PatientUpdateRequest {

	@ApiModelProperty("当前医生ID（必填）")
	private Long doctorId;
	@ApiModelProperty("患者ID（必填）")
	private Long patientId;
	@ApiModelProperty("基本信息（字段同新建患者）")
	private PatientBasicForm basic;
	@ApiModelProperty("医生实际修改过的字段名（basic 里的字段名，如 mobile、weight、acrEular、comorbidities）；只更新这些字段，其余原样不动")
	private List<String> changed;

	public Long getDoctorId() {
		return doctorId;
	}
	public void setDoctorId(Long doctorId) {
		this.doctorId = doctorId;
	}
	public Long getPatientId() {
		return patientId;
	}
	public void setPatientId(Long patientId) {
		this.patientId = patientId;
	}
	public PatientBasicForm getBasic() {
		return basic;
	}
	public void setBasic(PatientBasicForm basic) {
		this.basic = basic;
	}
	public List<String> getChanged() {
		return changed;
	}
	public void setChanged(List<String> changed) {
		this.changed = changed;
	}
}
