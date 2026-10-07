package com.wenwen.ai.source;

import lombok.Getter;
import lombok.Setter;

/** 敏感源行仅用于局部转换，不生成包含身份证的 toString。 */
@Getter @Setter
public final class PatientRow {
    private long id;
    private Integer gender;
    private String cardNo;
    private String confirmDate;
}
