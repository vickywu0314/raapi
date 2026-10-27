package com.wenwen.ai.patient;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** 患者列表扩展筛选 / 排序：真实 MVC + 合成 MySQL5.7；所有条件都在 total 计数和分页之前生效。 */
class PatientListFilterHttpTest extends TrustedPatientHttpFixture {
    private static final String DOCTOR = "303";

    /** 医生 303 名下 5 个 RA 患者 + 1 个他人患者；建档日期相对数据库当天，与上海 asOf 至少差几天，避开边界 */
    private void seedList() throws Exception {
        seed();
        sql("INSERT INTO patient_basic_info (id,name,study_no,gender,card_no,mobile,create_date) VALUES "
            + "(30001,'甲一','RA-C',1,'110105196001011234','13800001111',DATE_SUB(CURDATE(),INTERVAL 10 DAY)),"
            + "(30002,'乙二','RA-A',2,'110105198505051234','13900002222',DATE_SUB(CURDATE(),INTERVAL 100 DAY)),"
            + "(30003,'丙三','RA-B',2,'110105195001011234',NULL,DATE_SUB(CURDATE(),INTERVAL 400 DAY)),"
            + "(30004,'丁四',NULL,1,NULL,NULL,NULL),"
            + "(30005,'戊五','RA-D',2,'110105199006061234','13700003333',DATE_SUB(CURDATE(),INTERVAL 5 DAY)),"
            + "(30009,'他人','RA-Z',2,'110105198505051234','13800001111',DATE_SUB(CURDATE(),INTERVAL 10 DAY))");
        sql("INSERT INTO patient_relation_doctor (doctor_id,patient_id,research_type) VALUES (303,30001,0),(303,30002,7),(303,30003,0),(303,30004,0),(303,30005,1),(404,30009,0)");
        sql("INSERT INTO patient_follow_up_history (id,patient_basic_info_id,research_type,doctor_id,follow_up_date,bqpg,fzjc) VALUES "
            + "(31001,30001,0,303,'2025-01-01','{\"result\":{\"crpScore\":5.0}}','{\"lfsyz\":\"50\"}'),"
            + "(31002,30001,0,303,'2025-06-01','{\"result\":{\"crpScore\":3.0}}',NULL),"
            + "(31003,30001,0,303,'2026-01-01','{\"result\":{\"crpScore\":2.0}}',NULL),"
            + "(31004,30002,7,303,'2025-02-01','{\"result\":{\"crpScore\":3.0}}','{\"lfsyz\":\"5\",\"kccpkt\":\"10\"}'),"
            + "(31005,30002,7,303,'2026-02-01','{\"result\":{\"crpScore\":\"3.5\"}}',NULL),"
            + "(31006,30003,0,303,'2025-03-01','{\"result\":{\"crpScore\":2.5}}',NULL),"
            + "(31007,30003,0,303,'2026-03-01','{\"result\":{\"crpScore\":4.5}}',NULL),"
            + "(31008,30005,1,303,'2026-04-01','{\"result\":{\"crpScore\":2.5}}',NULL),"
            + "(31009,30005,1,303,'2026-04-02','{\"result\":{\"esrScore\":3.1}}',NULL)");
        sql("INSERT INTO patient_comorbidity (id,patient_id,disease_code,since_year) VALUES (32001,30001,'FM',2019),(32002,30003,'AS',2020)");
    }

    private JsonNode list(String... params) throws Exception {
        MockHttpServletRequestBuilder q = post("/api/ra/patient/patientsList").param("doctorId", DOCTOR);
        for (int i = 0; i < params.length; i += 2) q.param(params[i], params[i + 1]);
        return json.readTree(http.perform(q).andReturn().getResponse().getContentAsByteArray());
    }

    private List<String> ids(String... params) throws Exception {
        JsonNode r = list(params);
        assertEquals("200", r.path("code").textValue(), r.toString());
        List<String> ids = new ArrayList<>();
        for (JsonNode item : r.path("data").path("items")) ids.add(item.path("patientId").textValue());
        assertEquals(ids.size(), r.path("data").path("total").intValue(), "total 与筛选后条数一致（size=200 一页装下）");
        assertEquals(5, r.path("data").path("totalPatients").intValue(), "页头总数不受筛选影响");
        return ids;
    }

    private static List<String> of(String... ids) { return java.util.Arrays.asList(ids); }

    @Test void defaultOrderAndSorts() throws Exception {
        seedList();
        assertEquals(of("30005", "30001", "30002", "30003", "30004"), ids("size", "200"));
        assertEquals(of("30005", "30001", "30002", "30003", "30004"), ids("size", "200", "sort", "created"));
        assertEquals(of("30005", "30003", "30002", "30001", "30004"), ids("size", "200", "sort", "last"));
        // das-desc：4.5 / 3.5 / 2.5 / 2.0，无评分最后
        assertEquals(of("30003", "30002", "30005", "30001", "30004"), ids("size", "200", "sort", "das-desc"));
        assertEquals(of("30001", "30005", "30002", "30003", "30004"), ids("size", "200", "sort", "das-asc"));
        assertEquals(of("30002", "30003", "30001", "30005", "30004"), ids("size", "200", "sort", "code"));
        // 排序后再分页：第二页从第 3 个开始，total 仍为全部
        JsonNode page2 = list("sort", "das-desc", "page", "2", "size", "2").path("data");
        assertEquals(5, page2.path("total").intValue());
        assertEquals("30005", page2.path("items").get(0).path("patientId").textValue());
    }

    @Test void filtersApplyBeforeCountAndPaging() throws Exception {
        seedList();
        assertEquals(of("30001", "30004"), ids("size", "200", "sex", "男"));
        assertEquals(of("30005", "30002", "30003"), ids("size", "200", "sex", "女"));
        // 年龄：66 / 41 / 76 / 无 / 36（按身份证生日、上海今日）
        assertEquals(of("30002"), ids("size", "200", "ageBand", "40-49"));
        assertEquals(of("30001", "30003"), ids("size", "200", "ageMin", "60"));
        assertEquals(of("30005", "30002"), ids("size", "200", "ageMax", "59"));
        assertEquals(of("30001"), ids("size", "200", "ageBand", "60-69", "ageMax", "70"));
        // 活动度与 das28Activity 同一阈值
        assertEquals(of("30001"), ids("size", "200", "activity", "remission"));
        assertEquals(of("30005"), ids("size", "200", "activity", "low"));
        assertEquals(of("30002"), ids("size", "200", "activity", "moderate"));
        assertEquals(of("30003"), ids("size", "200", "activity", "high"));
        assertEquals(of("30004"), ids("size", "200", "activity", "none"));
        // 病情变化：首次 vs 最近，±0.6
        assertEquals(of("30001"), ids("size", "200", "trend", "better"));
        assertEquals(of("30002"), ids("size", "200", "trend", "stable"));
        assertEquals(of("30003"), ids("size", "200", "trend", "worse"));
        assertEquals(of("30005", "30004"), ids("size", "200", "trend", "none"));
        // 其他病史 / 分型
        assertEquals(of("30001", "30003"), ids("size", "200", "comorbidity", "any"));
        assertEquals(of("30005", "30002", "30004"), ids("size", "200", "comorbidity", "none"));
        assertEquals(of("30003"), ids("size", "200", "comorbidity", "as"));
        assertEquals(of("30001"), ids("size", "200", "serology", "pos"));
        assertEquals(of("30002"), ids("size", "200", "serology", "neg"));
        assertEquals(of("30005", "30003", "30004"), ids("size", "200", "serology", "unknown"));
        // 建档时间：10 / 100 / 400 天前 / 无 / 5 天前
        assertEquals(of("30005", "30001"), ids("size", "200", "created", "30"));
        assertEquals(of("30005", "30001", "30002"), ids("size", "200", "created", "365"));
        assertEquals(of("30003"), ids("size", "200", "created", "old"));
        // 组合 + 分页：total 是组合筛选后的条数
        JsonNode d = list("sex", "女", "activity", "moderate", "size", "1").path("data");
        assertEquals(1, d.path("total").intValue());
        d = list("sex", "女", "created", "365", "size", "1", "page", "2").path("data");
        assertEquals(2, d.path("total").intValue());
        assertEquals("30002", d.path("items").get(0).path("patientId").textValue());
    }

    @Test void keywordMatchesMobileAndAllWords() throws Exception {
        seedList();
        assertEquals(of("30001"), ids("size", "200", "keyword", "0000111"));
        assertEquals(of("30002"), ids("size", "200", "keyword", "139 乙"));
        assertEquals(of(), ids("size", "200", "keyword", "139　甲"));
        assertEquals(of("30005", "30001", "30002", "30003"), ids("size", "200", "keyword", " RA- "));
        assertEquals(of("30001"), ids("size", "200", "keyword", "RA-C 1380"));
    }

    @Test void itemsCarryCreateDateAndDasHistory() throws Exception {
        seedList();
        JsonNode items = list("size", "200").path("data").path("items");
        JsonNode p1 = null, p5 = null, p4 = null;
        for (JsonNode item : items) {
            if ("30001".equals(item.path("patientId").textValue())) p1 = item;
            if ("30005".equals(item.path("patientId").textValue())) p5 = item;
            if ("30004".equals(item.path("patientId").textValue())) p4 = item;
        }
        assertNotNull(p1);
        assertTrue(p1.path("createDate").textValue().matches("\\d{4}-\\d{2}-\\d{2}"));
        assertEquals(2.0, p1.path("latestDas28").doubleValue());
        assertEquals(3.0, p1.path("das28Prev").doubleValue());
        assertEquals(5.0, p1.path("das28First").doubleValue());
        assertEquals("better", p1.path("trend").textValue());
        assertFalse(p1.has("mobile"), "手机号只用于匹配，不在列表返回");
        assertTrue(p5.path("das28Prev").isNull());
        assertTrue(p5.path("das28First").isNull());
        assertTrue(p5.path("trend").isNull());
        assertTrue(p4.path("createDate").isNull());
    }

    @Test void invalidValuesReturn400() throws Exception {
        seedList();
        String[][] bad = {{"sort", "name"}, {"sex", "x"}, {"ageBand", "40"}, {"ageMin", "200"}, {"activity", "mild"},
            {"comorbidity", "XYZ"}, {"serology", "positive"}, {"created", "7"}, {"trend", "up"}};
        for (String[] p : bad) {
            JsonNode r = list(p[0], p[1]);
            assertEquals("400", r.path("code").textValue(), p[0]);
            assertFalse(r.path("success").asBoolean());
        }
        JsonNode r = list("ageMin", "50", "ageMax", "40");
        assertEquals("400", r.path("code").textValue());
    }
}
