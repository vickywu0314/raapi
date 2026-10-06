package com.wenwen.vo;

import java.util.List;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value = "新建患者前按身份证号查重的结果")
public class PatientCheckVo {

	@ApiModelProperty("NEW 新患者 / OTHER_DISEASE 已在其他病种库（复用档案，新增 RA 关系）/ RA_OTHER_DOCTOR 已在 RA 库、不在本医生名下（可转到自己名下）/ MINE 已在本医生名下")
	private String result;
	@ApiModelProperty("已存在时的患者ID")
	private Long patientId;
	@ApiModelProperty("已存在时的姓名")
	private String name;
	@ApiModelProperty("RA_OTHER_DOCTOR 时当前所属医生")
	private String otherDoctorName;
	@ApiModelProperty("OTHER_DISEASE 时所在的其他病种（如 强直性脊柱炎），新建后记入其他病史")
	private List<String> otherDiseases;
	@ApiModelProperty("OTHER_DISEASE 时已有的基本信息（用于预填表单）")
	private PatientBasicForm basic;

	public String getResult() {
		return result;
	}
	public void setResult(String result) {
		this.result = result;
	}
	public Long getPatientId() {
		return patientId;
	}
	public void setPatientId(Long patientId) {
		this.patientId = patientId;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getOtherDoctorName() {
		return otherDoctorName;
	}
	public void setOtherDoctorName(String otherDoctorName) {
		this.otherDoctorName = otherDoctorName;
	}
	public List<String> getOtherDiseases() {
		return otherDiseases;
	}
	public void setOtherDiseases(List<String> otherDiseases) {
		this.otherDiseases = otherDiseases;
	}
	public PatientBasicForm getBasic() {
		return basic;
	}
	public void setBasic(PatientBasicForm basic) {
		this.basic = basic;
	}
}
