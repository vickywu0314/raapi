package com.wenwen.controller.ra;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.wenwen.auth.AuthService;
import com.wenwen.ocr.OcrService;
import com.wenwen.util.BizException;
import com.wenwen.vo.DataResult;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

@Api(description = "OCR 识别病历")
@RestController
public class OcrController {

	@Autowired
	private OcrService ocrService;

	@ApiOperation(value = "上传病历图片", notes = "multipart 字段 file；jpg / png / bmp，≤15MB。返回 fileId 和预览 url", httpMethod = "POST")
	@PostMapping("/api/ra/ocr/upload")
	public DataResult<Map<String, Object>> upload(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
		DataResult<Map<String, Object>> r = new DataResult<Map<String, Object>>();
		try {
			ok(r, ocrService.upload(file, AuthService.currentDoctorId(request)));
		} catch (Exception e) {
			fail(r, e, "上传失败");
		}
		return r;
	}

	@ApiOperation(value = "识别病历图片", notes = "调用百度 OCR（ra.ocr.baidu.*），返回新建患者 / 基线访视草稿：basic、visitDate、fzjc、items（每个检验项目及对应的系统字段）、fieldOptions、warnings", httpMethod = "POST")
	@PostMapping("/api/ra/ocr/recognize")
	public DataResult<Map<String, Object>> recognize(@RequestParam("fileId") String fileId) {
		DataResult<Map<String, Object>> r = new DataResult<Map<String, Object>>();
		try {
			ok(r, ocrService.recognize(fileId));
		} catch (Exception e) {
			fail(r, e, "识别失败");
		}
		return r;
	}

	@ApiOperation(value = "保存检验项目叫法对照", notes = "请求体 JSON {doctorId, mappings:[{alias, field, name}]}；医生手动对应过的叫法，下次自动对上", httpMethod = "POST")
	@PostMapping("/api/ra/ocr/saveAliases")
	public DataResult<Void> saveAliases(@RequestBody Map<String, Object> body) {
		DataResult<Void> r = new DataResult<Void>();
		try {
			@SuppressWarnings("unchecked")
			List<Map<String, Object>> mappings = (List<Map<String, Object>>) body.get("mappings");
			Object d = body.get("doctorId");
			ocrService.saveAliases(mappings, d == null ? null : Long.valueOf(String.valueOf(d)));
			ok(r, null);
		} catch (Exception e) {
			fail(r, e, "保存对照失败");
		}
		return r;
	}

	@ApiOperation(value = "某次随访的 OCR 原图", notes = "用 OCR 识别结果保存的随访，返回当时上传的病历图片 [{fileId, url, uploadTime, recognizeTime}]；患者须在本医生名下", httpMethod = "POST")
	@PostMapping("/api/ra/ocr/visitImages")
	public DataResult<List<Map<String, Object>>> visitImages(@RequestParam("doctorId") Long doctorId, @RequestParam("patientId") Long patientId,
			@RequestParam("visitId") Long visitId) {
		DataResult<List<Map<String, Object>>> r = new DataResult<List<Map<String, Object>>>();
		try {
			ok(r, ocrService.visitImages(doctorId, patientId, visitId));
		} catch (Exception e) {
			fail(r, e, "查询 OCR 原图失败");
		}
		return r;
	}

	/** 病历图片预览：需要登录，且是本人上传的或已关联到本人名下的患者 */
	@GetMapping("/api/ra/files/ocr/{day}/{name:.+}")
	public ResponseEntity<byte[]> file(@PathVariable String day, @PathVariable String name, HttpServletRequest request) throws Exception {
		Long doctorId = AuthService.currentDoctorId(request);
		if (doctorId == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		Path p;
		try {
			p = ocrService.existing(day + "/" + name);
			if (!ocrService.canView(day + "/" + name, doctorId)) {
				return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
			}
		} catch (RuntimeException e) {
			return ResponseEntity.notFound().build();
		}
		String n = name.toLowerCase();
		MediaType type = n.endsWith(".png") ? MediaType.IMAGE_PNG : n.endsWith(".bmp") ? MediaType.parseMediaType("image/bmp") : MediaType.IMAGE_JPEG;
		return ResponseEntity.ok().contentType(type).header("Cache-Control", "private, max-age=3600").body(Files.readAllBytes(p));
	}

	private static <T> void ok(DataResult<T> r, T data) {
		r.setCode("200");
		r.setMessage("成功");
		r.setSuccess(true);
		r.setData(data);
	}

	private static void fail(DataResult<?> r, Exception e, String message) {
		r.setSuccess(false);
		if (e instanceof BizException) {
			r.setCode(((BizException) e).getCode());
			r.setMessage(e.getMessage());
		} else if (e instanceof IllegalArgumentException) {
			r.setCode("400");
			r.setMessage(e.getMessage());
		} else {
			e.printStackTrace();
			r.setCode("500");
			r.setMessage(message);
		}
	}
}
