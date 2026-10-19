package com.wenwen.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.wenwen.util.VisitFieldDict;
import com.wenwen.vo.VisitModuleUpdate;
import com.wenwen.vo.VisitCreateRequest;
import com.wenwen.vo.VisitValueConfirmation;
import com.wenwen.vo.VisitFieldValidationVo;
import com.wenwen.util.BizException;
import com.wenwen.service.AuditLogService;
import com.wenwen.mapper.VisitMapper;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import org.springframework.test.util.ReflectionTestUtils;

class VisitEntryValidationTest {
    static VisitServiceImpl createService(VisitMapper mapper) {
        Map<String, Object> relation = new HashMap<>(); relation.put("miss", 0); relation.put("researchType", 0);
        when(mapper.listMyRaRelations(anyMap())).thenReturn(Collections.singletonList(relation));
        when(mapper.listPatientVisits(anyMap())).thenReturn(Collections.emptyList());
        VisitServiceImpl service = new VisitServiceImpl(); ReflectionTestUtils.setField(service, "visitMapper", mapper);
        ReflectionTestUtils.setField(service, "auditLogService", mock(AuditLogService.class));
        when(mapper.insertVisit(anyMap())).thenAnswer(call -> { ((Map<String, Object>) call.getArgument(0)).put("id", 10L); return 1; });
        return service;
    }
    static JsonNode field(JsonNode form, String column, String key) {
        for (JsonNode module : form.path("modules")) if (column.equals(module.path("column").asText()))
            for (JsonNode group : module.path("groups")) for (JsonNode field : group.path("fields"))
                if (key.equals(field.path("key").asText())) return field;
        throw new AssertionError(column + "." + key);
    }
    static VisitFieldDict.Module module(String column) {
        for (VisitFieldDict.Module m : VisitFieldDict.MODULES) if (column.equals(m.column)) return m;
        throw new AssertionError(column);
    }
    static VisitModuleUpdate fields(String key, Object value) {
        VisitModuleUpdate update = new VisitModuleUpdate();
        update.setFields(Collections.singletonMap(key, value));
        return update;
    }
    @Test void newLaboratoryValuesRejectMalformedAndNonScalarInput() {
        for (Object value : Arrays.asList("10000abc", "NaN", "Infinity", true,
                Collections.singletonMap("value", 4), Arrays.asList("4", "5"))) {
            assertThrows(IllegalArgumentException.class,
                () -> new VisitEditor().apply(module("fzjc"), new JSONObject(true), fields("bxb", value)),
                "new WBC input must reject " + value);
        }
    }
    @Test void calendarDatesRejectNonexistentDaysAndStructuredInput() {
        for (Object value : Arrays.asList("2026-02-30", "2025-02-29", "2026-13-01", Arrays.asList("2026-10-01"))) {
            assertThrows(IllegalArgumentException.class,
                () -> new VisitEditor().apply(module("bsbq"), new JSONObject(true), fields("happenDate", value)),
                "new date must be a real calendar day: " + value);
        }
    }
    @Test void vasUsesTheAlreadyConfirmedZeroToHundredScale() {
        for (Object value : Arrays.asList("101", "-1", "1e309")) {
            assertThrows(IllegalArgumentException.class,
                () -> new VisitEditor().apply(module("bqpg"), new JSONObject(true), fields("ztScoreByPatient", value)),
                "VAS must stay in the confirmed 0–100 scale: " + value);
        }
    }
    @Test void createFormSuppliesSharedUnconfiguredValidationMetadata() {
        JsonNode form = new ObjectMapper().valueToTree(createService(mock(VisitMapper.class)).getCreateForm(101L, 1L));
        JsonNode rule = field(form, "fzjc", "bxb").path("validation");
        assertTrue(rule.isObject(), "the live API must expose the same validation contract used for saving");
        assertEquals("laboratory", rule.path("kind").asText());
        assertFalse(rule.path("required").asBoolean()); assertFalse(rule.path("referenceConfigured").asBoolean());
        assertTrue(rule.path("referenceMin").isNull()); assertTrue(rule.path("referenceMax").isNull());
        assertEquals("patient:1:new", rule.path("target").asText());
        assertFalse(rule.path("ruleVersion").asText().isEmpty());
    }
    static VisitCreateRequest createRequest() {
        VisitCreateRequest request = new VisitCreateRequest(); request.setDoctorId(101L); request.setPatientId(1L);
        request.setVisitDate("2026-10-01"); return request;
    }
    @Test void configuredRequiredFieldBlocksMissingWithoutInventingOtherRequirements() {
        VisitMapper mapper = mock(VisitMapper.class); VisitServiceImpl service = createService(mapper);
        ReflectionTestUtils.setField(service, "validationRules", "{\"fzjc.bxb\":{\"required\":true}}");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.createVisit(createRequest()));
        assertTrue(error.getMessage().contains("白细胞")); verify(mapper, never()).insertVisit(anyMap());
    }
    static final String WBC_RULE = "{\"fzjc.bxb\":{\"referenceMin\":3,\"referenceMax\":10,\"unit\":\"×10⁹/L\"}}";
    @Test void configuredReferenceIntervalRequiresAnExplicitConfirmation() {
        VisitMapper mapper = mock(VisitMapper.class); VisitServiceImpl service = createService(mapper);
        ReflectionTestUtils.setField(service, "validationRules", WBC_RULE);
        VisitCreateRequest request = createRequest(); request.setModules(Collections.singletonMap("fzjc", fields("bxb", "11")));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.createVisit(request));
        assertTrue(error.getMessage().contains("确认")); verify(mapper, never()).insertVisit(anyMap());
    }
    @Test void newNonemptyLabCannotAlsoBeSubmittedAsUnchecked() {
        VisitMapper mapper = mock(VisitMapper.class); VisitServiceImpl service = createService(mapper);
        VisitModuleUpdate update = fields("bxb", "4"); update.setWx(Collections.singletonMap("bxb", true));
        VisitCreateRequest request = createRequest(); request.setModules(Collections.singletonMap("fzjc", update));
        assertThrows(IllegalArgumentException.class, () -> service.createVisit(request));
        verify(mapper, never()).insertVisit(anyMap());
    }
    @Test void confirmationIsBoundToValueUnitRuleAndTargetAndLeavesAuditEvidence() {
        VisitMapper mapper = mock(VisitMapper.class); VisitServiceImpl service = createService(mapper);
        ReflectionTestUtils.setField(service, "validationRules", WBC_RULE);
        JsonNode rule = field(new ObjectMapper().valueToTree(service.getCreateForm(101L, 1L)), "fzjc", "bxb").path("validation");
        VisitModuleUpdate update = fields("bxb", "11"); VisitCreateRequest request = createRequest();
        request.setModules(Collections.singletonMap("fzjc", update));
        for (String mismatch : Arrays.asList("value", "unit", "ruleVersion", "target")) {
            VisitValueConfirmation confirmation = confirmation("11", "×10⁹/L", rule.path("ruleVersion").asText(), "patient:1:new");
            if (mismatch.equals("value")) confirmation.setValue("12");
            if (mismatch.equals("unit")) confirmation.setUnit("/μL");
            if (mismatch.equals("ruleVersion")) confirmation.setRuleVersion("old-rule");
            if (mismatch.equals("target")) confirmation.setTarget("visit:22");
            update.setConfirmations(Collections.singletonMap("bxb", confirmation));
            assertThrows(IllegalArgumentException.class, () -> service.createVisit(request), mismatch);
        }
        verify(mapper, never()).insertVisit(anyMap());
        update.setConfirmations(Collections.singletonMap("bxb", confirmation("11", "×10⁹/L", rule.path("ruleVersion").asText(), "patient:1:new")));
        assertEquals(Long.valueOf(10), service.createVisit(request).getVisitId());
        AuditLogService audit = (AuditLogService) ReflectionTestUtils.getField(service, "auditLogService");
        verify(audit).record(eq(1L), eq(10L), eq("新增随访"), isNull(), contains("白细胞 WBC=11 ×10⁹/L"), eq(101L), anyString());
    }
    static VisitValueConfirmation confirmation(String value, String unit, String version, String target) {
        VisitValueConfirmation confirmation = new VisitValueConfirmation(); confirmation.setValue(value); confirmation.setUnit(unit);
        confirmation.setRuleVersion(version); confirmation.setTarget(target); return confirmation;
    }
    @Test void boundedNumbersAreNeverReducedToTheirNumericEndpoint() {
        VisitValidationPolicy policy = new VisitValidationPolicy(WBC_RULE);
        VisitFieldDict.Field wbc = module("fzjc").groups.get(0).fields.get(0);
        VisitFieldValidationVo rule = policy.describe("fzjc", wbc, "visit:10");
        for (String value : Arrays.asList("<9", "≤9", ">4", "≥4", "＜9", "＞4", "2.999", "10.001"))
            assertTrue(VisitValidationPolicy.needsReview(rule, value), value);
        for (String value : Arrays.asList("3", "10", "=9")) assertFalse(VisitValidationPolicy.needsReview(rule, value), value);
        VisitFieldValidationVo upperOnly = new VisitValidationPolicy("{\"fzjc.bxb\":{\"referenceMax\":10}}") .describe("fzjc", wbc, "visit:10");
        assertFalse(VisitValidationPolicy.needsReview(upperOnly, "≤9"));
        assertTrue(VisitValidationPolicy.needsReview(upperOnly, ">4"));
    }
    @Test void invalidConfigurationCannotSilentlyDisableAnyRule() {
        for (String config : Arrays.asList("{", "[]", "{\"fzjc.unknown\":{}}", "{\"fzjc.bxb\":{\"required\":\"yes\"}}",
                "{\"fzjc.bxb\":{\"unit\":\"wrong\"}}", "{\"fzjc.bxb\":{\"referenceMin\":10,\"referenceMax\":3}}",
                "{\"fzjc.bxb\":{\"required\":true,\"required\":false}}", "{\"fzjc.bxb\":{\"ref\":1}}", "{} {}"))
            assertEquals("503", assertThrows(BizException.class, () -> new VisitValidationPolicy(config)).getCode(), config);
    }
    @Test void missingOptionalOrExplicitlyUncheckedLabFieldsRemainSavable() {
        VisitMapper mapper = mock(VisitMapper.class); VisitServiceImpl service = createService(mapper);
        assertNotNull(service.createVisit(createRequest()));
        ReflectionTestUtils.setField(service, "validationRules", "{\"fzjc.bxb\":{\"required\":true,\"allowUnchecked\":true}}");
        VisitCreateRequest request = createRequest(); VisitModuleUpdate unchecked = new VisitModuleUpdate();
        unchecked.setWx(Collections.singletonMap("bxb", true)); request.setModules(Collections.singletonMap("fzjc", unchecked));
        assertNotNull(service.createVisit(request));

    }
    @Test void unconfiguredAndUnchangedLegacyValuesAndSerologyRemainCompatible() {
        for (String value : Arrays.asList("10000", "-1", "0", "<5", "≤5", ">5", "≥5", "1e2")) {
            JSONObject data = new JSONObject(true); new VisitEditor().apply(module("fzjc"), data, fields("bxb", value)); assertEquals(value, data.get("bxb"));
        }
        JSONObject legacy = new JSONObject(true); legacy.put("bxb", "old text");
        assertTrue(new VisitEditor().apply(module("fzjc"), legacy, fields("bxb", "old text")).isEmpty());
        for (String value : Arrays.asList("阳性", "阴性", "+", "+++", "—"))
            assertDoesNotThrow(() -> new VisitEditor().apply(module("fzjc"), new JSONObject(true), fields("lfsyz", value)));
    }
    @Test void requiredUncheckedWaiverMustBeExplicitlyConfigured() {
        VisitMapper mapper = mock(VisitMapper.class); VisitServiceImpl service = createService(mapper);
        ReflectionTestUtils.setField(service, "validationRules", "{\"fzjc.bxb\":{\"required\":true}}");
        VisitCreateRequest request = createRequest(); request.setModules(Collections.singletonMap("fzjc", fields("bxb", "未查")));
        assertThrows(IllegalArgumentException.class, () -> service.createVisit(request));
    }
    @Test void untouchedLegacyMissingMarkerDoesNotBecomeANewRequiredFieldEdit() {
        VisitValidationPolicy policy = new VisitValidationPolicy("{\"fzjc.bxb\":{\"required\":true}}");
        JSONObject legacy = new JSONObject(true); legacy.put("bxb", "未查");
        assertDoesNotThrow(() -> policy.validate(module("fzjc"), legacy, com.wenwen.util.VisitJson.copy(legacy), null, false, "visit:10"));
    }
    @Test void configuredDecimalReferenceBoundRetainsExactDigits() {
        VisitValidationPolicy policy = new VisitValidationPolicy("{\"fzjc.bxb\":{\"referenceMax\":1.00000000000000000001}}");
        VisitFieldValidationVo rule = policy.describe("fzjc", module("fzjc").groups.get(0).fields.get(0), "visit:10");
        assertEquals("1.00000000000000000001", rule.referenceMax);
        assertTrue(VisitValidationPolicy.needsReview(rule, "1.00000000000000000002"));
    }
    @Test void serologyKeepsExistingExplicitUnitSuffixAndRequiresReviewForMismatch() {
        for (String key : Arrays.asList("lfsyz", "kccpkt")) {
            String unit = "lfsyz".equals(key) ? "IU/mL" : "U/mL";
            String value = "lfsyz".equals(key) ? "30 IU/mL" : ">25 U/mL";
            assertDoesNotThrow(() -> new VisitEditor().apply(module("fzjc"), new JSONObject(true), fields(key, value)));
            VisitValidationPolicy policy = new VisitValidationPolicy("{}");
            JSONObject candidate = new JSONObject(true);candidate.put(key,value);
            assertDoesNotThrow(() -> policy.validate(module("fzjc"), new JSONObject(true), candidate, fields(key,value), true,"visit:10"));
            String mismatch = "30 " + (unit.equals("IU/mL") ? "U/mL" : "IU/mL");candidate.put(key,mismatch);
            VisitModuleUpdate update = fields(key,mismatch);
            assertThrows(IllegalArgumentException.class, () -> policy.validate(module("fzjc"), new JSONObject(true), candidate, update, true,"visit:10"));
            VisitFieldDict.Field field = module("fzjc").groups.stream().flatMap(g -> g.fields.stream()).filter(f -> f.key.equals(key)).findFirst().get();
            update.setConfirmations(Collections.singletonMap(key,confirmation(mismatch,unit,policy.describe("fzjc",field,"visit:10").ruleVersion,"visit:10")));
            assertEquals(1,policy.validate(module("fzjc"),new JSONObject(true),candidate,update,true,"visit:10").size());
        }
        assertThrows(IllegalArgumentException.class, () -> new VisitEditor().apply(module("fzjc"),new JSONObject(true),fields("bxb","30 IU/mL")));
    }
    @Test void configurationRejectsUnsupportedNonLaboratoryAndConditionalFields() {
        for (String path : Arrays.asList("blsj.eventDetail", "zlfa.tz", "zlfa.tz_reason_qt", "bsbq.happenDate", "bqpg.ztScoreByPatient", "fzjc.ana"))
            assertEquals("503", assertThrows(BizException.class, () -> new VisitValidationPolicy("{\""+path+"\":{\"required\":true}}")).getCode(),path);
        VisitFieldDict.Field lab=module("fzjc").groups.get(0).fields.get(0);
        String old=lab.showIf;boolean prior=lab.notBaseline;
        try {
            lab.showIf="bxb:是";
            assertEquals("503",assertThrows(BizException.class,()->new VisitValidationPolicy(WBC_RULE)).getCode());
            lab.showIf=null;lab.notBaseline=true;
            assertEquals("503",assertThrows(BizException.class,()->new VisitValidationPolicy(WBC_RULE)).getCode());
        } finally {lab.showIf=old;lab.notBaseline=prior;}
    }
}
