package com.wenwen.ai.scope;

/** 已完成可信凭证到内部医生 id 映射的身份；请求字段不能构造认证来源。 */
public final class TrustedDoctor {
    private final long id;
    public TrustedDoctor(long id) {
        if (id <= 0) throw new IllegalArgumentException("医生 id 必须为正数");
        this.id = id;
    }
    public long getId() { return id; }
}
