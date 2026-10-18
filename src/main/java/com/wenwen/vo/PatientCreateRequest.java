package com.wenwen.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value = "新建患者请求（JSON）")
public class PatientCreateRequest {

	@ApiModelProperty("兼容字段：忽略客户端值，以服务端登录会话为准")
	private Long doctorId;
	@ApiModelProperty("身份证号已在 RA 研究库且不在本医生名下时，医生确认「转到自己名下」后传 true")
	private boolean transfer;
	@ApiModelProperty("用 OCR 识别结果填表时的图片编号（/api/ra/ocr/upload 返回的 fileId），保存后把图片关联到这里；可不传")
	private String ocrFileId;
	private java.util.List<String> ocrFileIds;
	public java.util.List<String> getOcrFileIds() { return ocrFileIds; }
	public void setOcrFileIds(java.util.List<String> value) { ocrFileIds = value; }
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
	public String getOcrFileId() {
		return ocrFileId;
	}
	public void setOcrFileId(String ocrFileId) {
		this.ocrFileId = ocrFileId;
	}
}
