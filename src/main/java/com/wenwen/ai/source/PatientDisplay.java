package com.wenwen.ai.source;

import lombok.Value;

/** 仅医生授权名单显示；不携带身份证、生日或原患者行。 */
@Value
public class PatientDisplay {
    String name;
    String studyNo;
}
