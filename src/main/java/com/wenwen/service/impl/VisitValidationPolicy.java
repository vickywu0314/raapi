package com.wenwen.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.wenwen.util.BizException;
import com.wenwen.util.VisitFieldDict;
import com.wenwen.util.VisitJson;
import com.wenwen.vo.VisitFieldValidationVo;
import com.wenwen.vo.VisitModuleUpdate;
import com.wenwen.vo.VisitValueConfirmation;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/** 随访录入校验；参考范围不从正常人群区间推断为绝对非法值。 */
final class VisitValidationPolicy {
    private static final Pattern LAB_NUMBER = Pattern.compile(
        "^(?:<=|>=|<|>|≤|≥|＜|＞|=)?\\s*[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?$");
    private final Map<String, JsonNode> rules = new LinkedHashMap<>();
    private final String version;

    VisitValidationPolicy(String configuration) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
            mapper.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
            JsonNode root;
            try (JsonParser parser = mapper.getFactory().createParser(
                    configuration == null || configuration.trim().isEmpty() ? "{}" : configuration)) {
                root = mapper.readTree(parser);
                if (root == null || !root.isObject() || parser.nextToken() != null) throw new IllegalArgumentException();
            }
            Iterator<Map.Entry<String, JsonNode>> entries = root.fields();
            while (entries.hasNext()) {
                Map.Entry<String, JsonNode> entry = entries.next();
                VisitFieldDict.Field field = configuredField(entry.getKey());
                JsonNode rule = entry.getValue();
                if (field == null || !entry.getKey().startsWith("fzjc.") || !field.laboratory || !field.editable()
                        || field.showIf != null || field.notBaseline || !rule.isObject()) throw new IllegalArgumentException();
                Iterator<String> names = rule.fieldNames();
                while (names.hasNext()) {
                    String name = names.next();
                    if (!Arrays.asList("required", "allowUnchecked", "referenceMin", "referenceMax", "unit").contains(name)) throw new IllegalArgumentException();
                }
                if (rule.has("required") && !rule.get("required").isBoolean()) throw new IllegalArgumentException();
                if (rule.has("allowUnchecked") && !rule.get("allowUnchecked").isBoolean()) throw new IllegalArgumentException();
                if (rule.has("unit") && (!rule.get("unit").isTextual() || !field.unit.equals(rule.get("unit").asText()))) throw new IllegalArgumentException();
                BigDecimal min = reference(rule, "referenceMin"), max = reference(rule, "referenceMax");
                if ((min != null || max != null) && !field.laboratory) throw new IllegalArgumentException();
                if (min != null && max != null && min.compareTo(max) > 0) throw new IllegalArgumentException();
                rules.put(entry.getKey(), rule);
            }
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(("ENTRY-20261019-v1|" + root.toString()).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(); for (byte b : digest) hex.append(String.format("%02x", b & 255));
            version = hex.toString();
        } catch (Exception error) {
            throw new BizException("503", "随访校验规则配置不可用，请联系管理员");
        }
    }

    private static BigDecimal reference(JsonNode rule, String key) {
        if (!rule.has(key)) return null;
        JsonNode value = rule.get(key);
        if (!value.isNumber() || !Double.isFinite(value.doubleValue())) throw new IllegalArgumentException();
        return value.decimalValue();
    }
    private static VisitFieldDict.Field configuredField(String path) {
        for (VisitFieldDict.Module module : VisitFieldDict.MODULES)
            for (VisitFieldDict.Group group : module.groups) for (VisitFieldDict.Field field : group.fields)
                if ((module.column + "." + field.key).equals(path)) return field;
        return null;
    }

    /** 已配置的参考范围 [min, max]（单侧时另一侧为 null）；没配置返回 null。访视详情只读显示用。 */
    BigDecimal[] referenceRange(String column, VisitFieldDict.Field field) {
        JsonNode configured = rules.get(column + "." + field.key);
        if (configured == null || !field.laboratory) return null;
        BigDecimal minimum = reference(configured, "referenceMin"), maximum = reference(configured, "referenceMax");
        return minimum == null && maximum == null ? null : new BigDecimal[] {minimum, maximum};
    }

    /**
     * 只读显示用的异常标记：high 高于上限 / low 低于下限 / null 在范围内或无法判断（非数值、阴阳性、单位不一致等）。
     * 带比较符时只在整个可能取值都越界时才标记（如上限 10 时「&gt;10」为 high、「&lt;5」不标记）。不影响保存校验。
     */
    static String abnormal(BigDecimal minimum, BigDecimal maximum, String field, String value, String unit) {
        if (value == null) return null;
        String text = value.trim();
        if ("lfsyz".equals(field) || "kccpkt".equals(field)) {
            String suffix = unitSuffix(text);
            if (suffix != null) {
                if (!suffix.equals(unit)) return null;
                text = text.substring(0, text.length() - suffix.length()).trim();
            }
        }
        Matcher match = Pattern.compile("^(<=|>=|<|>|≤|≥|＜|＞|=)?\\s*([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?)$").matcher(text);
        if (!match.matches()) return null;
        BigDecimal number = new BigDecimal(match.group(2));
        String operator = match.group(1) == null ? "=" : match.group(1);
        switch (operator) {
            case "<": case "＜":
                return minimum != null && number.compareTo(minimum) <= 0 ? "low" : null;
            case "<=": case "≤":
                return minimum != null && number.compareTo(minimum) < 0 ? "low" : null;
            case ">": case "＞":
                return maximum != null && number.compareTo(maximum) >= 0 ? "high" : null;
            case ">=": case "≥":
                return maximum != null && number.compareTo(maximum) > 0 ? "high" : null;
            default:
                if (minimum != null && number.compareTo(minimum) < 0) return "low";
                return maximum != null && number.compareTo(maximum) > 0 ? "high" : null;
        }
    }

    VisitFieldValidationVo describe(String column, VisitFieldDict.Field field, String target) {
        VisitFieldValidationVo rule = new VisitFieldValidationVo();
        rule.kind = field.laboratory ? "laboratory" : field.type;
        rule.min = field.min; rule.max = field.max;
        JsonNode configured = rules.get(column + "." + field.key);
        if (configured != null) {
            rule.required = configured.path("required").asBoolean(false);
            rule.allowUnchecked = configured.path("allowUnchecked").asBoolean(false);
            BigDecimal minimum = reference(configured, "referenceMin"), maximum = reference(configured, "referenceMax");
            rule.referenceMin = minimum == null ? null : minimum.toString(); rule.referenceMax = maximum == null ? null : maximum.toString();
            rule.referenceConfigured = rule.referenceMin != null || rule.referenceMax != null;
        }
        rule.qualitativeAllowed = "lfsyz".equals(field.key) || "kccpkt".equals(field.key);
        rule.unitSuffixAllowed = rule.qualitativeAllowed;
        rule.ruleVersion = version + ":" + column + "." + field.key + ":" + field.unit;
        rule.target = target;
        return rule;
    }

    List<String> validate(VisitFieldDict.Module module, JSONObject before, JSONObject candidate,
            VisitModuleUpdate update, boolean create, String target) {
        List<String> confirmations = new ArrayList<>();
        for (VisitFieldDict.Group group : module.groups) for (VisitFieldDict.Field field : group.fields) {
            if (!"fzjc".equals(module.column) || !field.laboratory || !field.editable()) continue;
            VisitFieldValidationVo rule = describe(module.column, field, target);
            String value = VisitJson.text(VisitJson.path(candidate, field.key));
            boolean changed = !Objects.equals(value, VisitJson.text(VisitJson.path(before, field.key)));
            boolean missingMarker = field.laboratory && "未查".equals(value);
            boolean unchecked = unchecked(module, candidate, field) || missingMarker;
            boolean previouslyUnchecked = unchecked(module, before, field)
                || (field.laboratory && "未查".equals(VisitJson.text(VisitJson.path(before, field.key))));
            boolean wxChanged = unchecked != previouslyUnchecked;
            if (missingMarker) value = null; // 既有显式缺失标记，不冒充数值或正常结果。
            if (field.laboratory && changed && value != null && unchecked)
                throw new IllegalArgumentException(module.title + "·" + field.label + "：新填写的结果与未查不能同时提交，请核对");
            if (rule.required && !(unchecked && rule.allowUnchecked) && (create || changed || wxChanged) && (value == null || unchecked))
                throw new IllegalArgumentException(module.title + "·" + field.label + "：请填写此必填项" + (rule.allowUnchecked ? "，未检查时可选择未查" : "（当前规则未允许以未查豁免）"));
            String suffix = rule.unitSuffixAllowed ? unitSuffix(value) : null;
            boolean unitMismatch = suffix != null && !field.unit.equals(suffix);
            String numericValue = suffix == null ? value : value.substring(0, value.length() - suffix.length()).trim();
            if (changed && value != null && !unchecked && (unitMismatch || (rule.referenceConfigured && needsReview(rule, numericValue)))) {
                String submitted = update == null || update.getFields() == null ? null : VisitJson.text(update.getFields().get(field.key));
                VisitValueConfirmation confirmation = update == null || update.getConfirmations() == null ? null : update.getConfirmations().get(field.key);
                if (confirmation == null || !Objects.equals(submitted, confirmation.getValue())
                        || !field.unit.equals(confirmation.getUnit()) || !rule.ruleVersion.equals(confirmation.getRuleVersion())
                        || !target.equals(confirmation.getTarget()))
                    throw new IllegalArgumentException(module.title + "·" + field.label + (unitMismatch ? "：单位不一致，请核对并逐项确认" : "：结果超出参考范围或尚不能证明在范围内，请核对并逐项确认"));
                confirmations.add("医生已核对并确认检验结果：" + field.label + "=" + submitted + " " + field.unit + "（规则 " + rule.ruleVersion + "）");
            }
        }
        return confirmations;
    }

    private static String unitSuffix(String value) {
        if (value == null) return null;
        Matcher match = Pattern.compile("(IU/mL|U/mL)$").matcher(value);
        return match.find() ? match.group(1) : null;
    }

    /** 比较符表示整个可能集合；不能丢弃比较符，把边界数冒充精确结果。 */
    static boolean needsReview(VisitFieldValidationVo rule, String value) {
        Matcher match = Pattern.compile("^(<=|>=|<|>|≤|≥|＜|＞|=)?\\s*([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?)$").matcher(value);
        if (!match.matches()) return true;
        BigDecimal number = new BigDecimal(match.group(2)); String operator = match.group(1);
        BigDecimal minimum = rule.referenceMin == null ? null : new BigDecimal(rule.referenceMin);
        BigDecimal maximum = rule.referenceMax == null ? null : new BigDecimal(rule.referenceMax);
        if (operator != null && Arrays.asList("<", "<=", "≤", "＜").contains(operator))
            return minimum != null || (maximum != null && number.compareTo(maximum) > 0);
        if (operator != null && Arrays.asList(">", ">=", "≥", "＞").contains(operator))
            return maximum != null || (minimum != null && number.compareTo(minimum) < 0);
        return (minimum != null && number.compareTo(minimum) < 0)
            || (maximum != null && number.compareTo(maximum) > 0);
    }

    private static boolean unchecked(VisitFieldDict.Module module, JSONObject data, VisitFieldDict.Field field) {
        String single = module.wx.get(field.key);
        if (single != null) return VisitJson.isTrue(data.get(single));
        Object map = module.wxMap == null ? null : data.get(module.wxMap);
        return map instanceof JSONObject && VisitJson.isTrue(((JSONObject) map).get(field.key));
    }
    static void validateValue(VisitFieldDict.Field field, Object input, Object current, String label) {
        if (!field.laboratory && !"date".equals(field.type) && !"number".equals(field.type)) return;
        if (input != null && !(input instanceof String) && !(input instanceof Number))
            throw new IllegalArgumentException(label + "：应填写一个数值，不能填写对象或数组");
        String value = VisitJson.blankToNull(input == null ? null : String.valueOf(input));
        if (value == null || value.equals(VisitJson.text(current))) return;
        if (field.laboratory && "未查".equals(value)) return;
        if ("date".equals(field.type)) {
            try {
                if (!value.matches("\\d{4}-\\d{2}-\\d{2}")) throw new IllegalArgumentException();
                LocalDate.parse(value);
            } catch (Exception error) {
                throw new IllegalArgumentException(label + "：日期应为真实的 yyyy-MM-dd 日期");
            }
            return;
        }
        if ("number".equals(field.type)) {
            BigDecimal number;
            try {
                number = new BigDecimal(value);
                if (!Double.isFinite(number.doubleValue())) throw new NumberFormatException();
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException(label + "：应为有限数值");
            }
            if ((field.min != null && number.compareTo(field.min) < 0)
                    || (field.max != null && number.compareTo(field.max) > 0))
                throw new IllegalArgumentException(label + "：应在 " + field.min + "～" + field.max + " 之间");
            return;
        }
        if (("lfsyz".equals(field.key) || "kccpkt".equals(field.key))
                && ("阴性".equals(value) || "阳性".equals(value) || "-".equals(value)
                    || "—".equals(value) || value.matches("\\+{1,4}"))) return;
        if ("lfsyz".equals(field.key) || "kccpkt".equals(field.key)) {
            String suffix = unitSuffix(value);
            if (suffix != null) value = value.substring(0, value.length() - suffix.length()).trim();
        }
        if (!LAB_NUMBER.matcher(value).matches())
            throw new IllegalArgumentException(label + "：应为完整数值，可保留报告中的比较符");
        try {
            BigDecimal number = new BigDecimal(value.replaceFirst("^(?:<=|>=|<|>|≤|≥|＜|＞|=)\\s*", ""));
            if (!Double.isFinite(number.doubleValue())) throw new NumberFormatException();
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(label + "：应为有限数值");
        }
    }
}
