package com.wenwen.vo;

import java.util.Map;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value = "新增随访请求（JSON）")
public class VisitCreateRequest {

	@ApiModelProperty("用 OCR 识别结果填表时的图片编号（/api/ra/ocr/upload 返回的 fileId），保存后把图片关联到这里；可不传")
	private String ocrFileId;
	private java.util.List<String> ocrFileIds;
	public java.util.List<String> getOcrFileIds() { return ocrFileIds; }
	public void setOcrFileIds(java.util.List<String> value) { ocrFileIds = value; }
	@ApiModelProperty("兼容字段：忽略客户端值，以服务端登录会话为准")
	private Long doctorId;
	@ApiModelProperty("患者ID（必填）")
	private Long patientId;
	@ApiModelProperty("随访日期 yyyy-MM-dd（必填，不能晚于今天）")
	private String visitDate;
	@ApiModelProperty("各模块填写的内容：随访表字段名（bsbq / fzjc / bqpg / zyzd / zlfa / blsj）→ 字段 / 未查 / 清单，结构同编辑随访")
	private Map<String, VisitModuleUpdate> modules;

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
	public String getVisitDate() {
		return visitDate;
	}
	public void setVisitDate(String visitDate) {
		this.visitDate = visitDate;
	}
	public Map<String, VisitModuleUpdate> getModules() {
		return modules;
	}
	public void setModules(Map<String, VisitModuleUpdate> modules) {
		this.modules = modules;
	}
	public String getOcrFileId() {
		return ocrFileId;
	}
	public void setOcrFileId(String ocrFileId) {
		this.ocrFileId = ocrFileId;
	}
}
