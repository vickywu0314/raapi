package com.wenwen.vo;

/** 医生逐项核对后提交；仅对当前目标、本次值、字典单位和有效规则版本生效。 */
public class VisitValueConfirmation {
    private String value, unit, ruleVersion, target;
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    public String getUnit() { return unit; }
    public void setUnit(String value) { unit = value; }
    public String getRuleVersion() { return ruleVersion; }
    public void setRuleVersion(String value) { ruleVersion = value; }
    public String getTarget() { return target; }
    public void setTarget(String value) { target = value; }
}
