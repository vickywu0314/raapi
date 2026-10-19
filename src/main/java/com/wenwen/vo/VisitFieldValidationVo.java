package com.wenwen.vo;

import java.math.BigDecimal;

/** 服务端有效输入合同；referenceConfigured=false 不表示结果正常。 */
public class VisitFieldValidationVo {
    public String kind;
    public boolean required;
    public boolean allowUnchecked;
    public BigDecimal min, max;
    /** 十进制字符串，避免浏览器 JSON number 舍入参考边界。 */
    public String referenceMin, referenceMax;
    public boolean referenceConfigured;
    public boolean qualitativeAllowed;
    public boolean unitSuffixAllowed;
    public String ruleVersion;
    public String target;
}
