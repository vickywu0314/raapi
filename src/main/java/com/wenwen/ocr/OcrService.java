package com.wenwen.ocr;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.alibaba.fastjson.JSONObject;
import com.wenwen.mapper.OcrMapper;
import com.wenwen.util.BizException;
import com.wenwen.util.VisitFieldDict;

/**
 * OCR 识别病历：上传图片存服务器（ra.upload.dir/ocr/yyyyMMdd/），识别时读文件转 base64 调百度，结果解析成新建患者 / 基线访视的草稿。
 * 草稿只用于填表，医生核对后才保存；检验项目对不上的由医生手动对应，并记进叫法对照表。
 */
@Service
public class OcrService {

	private static final List<String> TYPES = Arrays.asList("jpg", "jpeg", "png", "bmp");
	private static final Pattern FILE_ID = Pattern.compile("\\d{8}/[0-9a-f]{32}\\.(jpg|jpeg|png|bmp)");
	/** 超过这个大小或边长的图片先压缩再发给百度（百度对 base64 后的大小有限制） */
	private static final long MAX_SEND_BYTES = 3L * 1024 * 1024;
	private static final int MAX_SIDE = 3000;

	@Value("${ra.upload.dir:upload}")
	private String uploadDir;
	@Value("${ra.ocr.baidu.apis:medical_report_detection,accurate_basic}")
	private String apis;

	@Autowired
	private BaiduOcrClient baidu;
	@Autowired
	private OcrMapper mapper;

	/** 保存上传的图片，返回 fileId（日期/文件名） */
	public Map<String, Object> upload(MultipartFile file) throws IOException {
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("请选择图片");
		}
		String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
		String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
		if (!TYPES.contains(ext)) {
			throw new IllegalArgumentException("只支持 jpg、png、bmp 图片");
		}
		if (file.getSize() > 15L * 1024 * 1024) {
			throw new IllegalArgumentException("图片不能超过 15MB");
		}
		byte[] bytes = file.getBytes();
		if (ImageIO.read(new ByteArrayInputStream(bytes)) == null) {
			throw new IllegalArgumentException("文件不是有效的图片");
		}
		String fileId = new SimpleDateFormat("yyyyMMdd").format(new Date()) + "/" + UUID.randomUUID().toString().replace("-", "") + "." + ext;
		Path path = path(fileId);
		Files.createDirectories(path.getParent());
		Files.write(path, bytes);
		Map<String, Object> out = new LinkedHashMap<String, Object>();
		out.put("fileId", fileId);
		out.put("url", "/api/ra/files/ocr/" + fileId);
		out.put("size", bytes.length);
		return out;
	}

	/** 识别：依次调配置的百度接口，合并解析 */
	public Map<String, Object> recognize(String fileId) throws IOException {
		byte[] image = forSending(Files.readAllBytes(existing(fileId)));
		JSONObject medical = null, text = null;
		List<String> failures = new ArrayList<String>();
		for (String api : apis.split(",")) {
			api = api.trim();
			if (api.isEmpty()) continue;
			try {
				JSONObject r = baidu.call(api, image);
				if (api.startsWith("medical")) medical = r;
				else text = r;
			} catch (BizException e) {
				if (!baidu.configured() || "400".equals(e.getCode())) throw e;
				failures.add(e.getMessage());
			}
		}
		if (medical == null && text == null) {
			throw new BizException("502", failures.isEmpty() ? "识别失败" : String.join("；", failures));
		}
		Map<String, Object> draft = OcrResultParser.parse(medical, text, aliases());
		decorate(draft);
		@SuppressWarnings("unchecked")
		List<String> warnings = (List<String>) draft.get("warnings");
		for (String f : failures) warnings.add("部分识别没有成功：" + f);
		draft.put("fileId", fileId);
		draft.put("url", "/api/ra/files/ocr/" + fileId);
		return draft;
	}

	/** 医生把某个叫法对应到系统字段（field 为空 = 不对应，不保存） */
	public void saveAliases(List<Map<String, Object>> mappings, Long doctorId) {
		Map<String, String> fields = labFields();
		for (Map<String, Object> m : mappings == null ? new ArrayList<Map<String, Object>>() : mappings) {
			String alias = m.get("alias") == null ? null : String.valueOf(m.get("alias")).trim();
			String field = m.get("field") == null ? null : String.valueOf(m.get("field")).trim();
			if (alias == null || alias.isEmpty() || alias.length() > 200 || field == null || !fields.containsKey(field)) {
				continue;
			}
			String sample = m.get("name") == null ? null : String.valueOf(m.get("name"));
			try {
				mapper.upsertAlias(alias, field, sample == null || sample.length() <= 200 ? sample : sample.substring(0, 200), doctorId);
			} catch (DataAccessException e) {
				throw new BizException("500", "叫法对照表还没建，请先执行 sql/20261012_ocr_lab_alias.sql");
			}
		}
	}

	/** 已保存的图片（供页面预览） */
	public Path existing(String fileId) {
		if (fileId == null || !FILE_ID.matcher(fileId).matches()) {
			throw new IllegalArgumentException("图片编号不正确");
		}
		Path p = path(fileId);
		if (!Files.isRegularFile(p)) {
			throw new BizException("404", "图片不存在，请重新上传");
		}
		return p;
	}

	/** 辅助检查里可以对应的字段：字段 → 中文名、单位、类型（下拉框用） */
	public static Map<String, String[]> labOptions() {
		Map<String, String[]> out = new LinkedHashMap<String, String[]>();
		for (VisitFieldDict.Module m : VisitFieldDict.MODULES) {
			if (!"fzjc".equals(m.column)) continue;
			for (VisitFieldDict.Group g : m.groups) {
				for (VisitFieldDict.Field f : g.fields) {
					if (("text".equals(f.type) && !"checkNo".equals(f.key)) || "radioList".equals(f.type)) {
						out.put(f.key, new String[] { g.title + " · " + f.label, f.unit, f.type });
					}
				}
			}
		}
		return out;
	}

	private static Map<String, String> labFields() {
		Map<String, String> m = new HashMap<String, String>();
		for (Map.Entry<String, String[]> e : labOptions().entrySet()) m.put(e.getKey(), e.getValue()[0]);
		return m;
	}

	/** 给每个检验项目补上系统字段的中文名、单位；单位不一致时提醒 */
	@SuppressWarnings("unchecked")
	private static void decorate(Map<String, Object> draft) {
		Map<String, String[]> opts = labOptions();
		for (Map<String, Object> it : (List<Map<String, Object>>) draft.get("items")) {
			String field = (String) it.get("field");
			if (field == null || !opts.containsKey(field)) {
				it.put("field", null);
				continue;
			}
			String[] o = opts.get(field);
			it.put("fieldLabel", o[0]);
			it.put("fieldUnit", o[1]);
			String unit = (String) it.get("unit");
			if (unit != null && o[1] != null && !o[1].isEmpty() && !sameUnit(unit, o[1])) {
				it.put("unitWarning", "报告单位 " + unit + "，系统单位 " + o[1] + "，请核对是否需要换算");
			}
		}
		List<Map<String, Object>> options = new ArrayList<Map<String, Object>>();
		for (Map.Entry<String, String[]> e : opts.entrySet()) {
			Map<String, Object> o = new LinkedHashMap<String, Object>();
			o.put("field", e.getKey());
			o.put("label", e.getValue()[0]);
			o.put("unit", e.getValue()[1]);
			o.put("type", e.getValue()[2]);
			options.add(o);
		}
		draft.put("fieldOptions", options);
	}

	private static boolean sameUnit(String a, String b) {
		String x = a.replaceAll("[\\s^*]", "").replace("×", "x").replace("μ", "u").replace("µ", "u").toLowerCase(Locale.ROOT).replace("10e", "10").replace("⁹", "9").replace("¹²", "12");
		String y = b.replaceAll("[\\s^*]", "").replace("×", "x").replace("μ", "u").replace("µ", "u").toLowerCase(Locale.ROOT).replace("10e", "10").replace("⁹", "9").replace("¹²", "12");
		return x.equals(y) || x.replace("x", "").equals(y.replace("x", ""));
	}

	private Map<String, String> aliases() {
		Map<String, String> m = new HashMap<String, String>();
		try {
			for (Map<String, Object> r : mapper.listAliases()) m.put(String.valueOf(r.get("alias")), String.valueOf(r.get("field")));
		} catch (DataAccessException e) {
			// 对照表没建：只用内置同义词
		}
		return m;
	}

	private Path path(String fileId) {
		return Paths.get(uploadDir, "ocr").resolve(fileId).normalize();
	}

	/** 太大的图片按长边 3000 像素、JPEG 质量 0.85 压缩后再发 */
	private static byte[] forSending(byte[] bytes) throws IOException {
		BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
		if (img == null || (bytes.length <= MAX_SEND_BYTES && Math.max(img.getWidth(), img.getHeight()) <= MAX_SIDE)) {
			return bytes;
		}
		double scale = Math.min(1.0, (double) MAX_SIDE / Math.max(img.getWidth(), img.getHeight()));
		int w = (int) Math.round(img.getWidth() * scale), h = (int) Math.round(img.getHeight() * scale);
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.drawImage(img, 0, 0, w, h, java.awt.Color.WHITE, null);
		g.dispose();
		Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
		ImageWriter writer = writers.next();
		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		try (ImageOutputStream ios = ImageIO.createImageOutputStream(buf)) {
			writer.setOutput(ios);
			ImageWriteParam p = writer.getDefaultWriteParam();
			p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			p.setCompressionQuality(0.85f);
			writer.write(null, new IIOImage(out, null, null), p);
		} finally {
			writer.dispose();
		}
		return buf.toByteArray();
	}
}
