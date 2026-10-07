package com.wenwen.ai.scope;

/** 真实登录 Adapter 的可信集成入口；默认实现始终拒绝。 */
public interface PrincipalProvider {
    TrustedDoctor requireCurrent();
}
