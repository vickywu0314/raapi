package com.wenwen.ai.scope;

import com.wenwen.ai.query.CohortException;

/** 一个应用context构造时固定的新增请求准入；不改变已持久事实或在途调用。 */
public final class AiFeatureAvailability {
    private final boolean apiEnabled;
    private final boolean xlsxEnabled;
    public AiFeatureAvailability(String api, String xlsx) {
        apiEnabled = "true".equals(api);
        xlsxEnabled = "true".equals(xlsx);
    }
    public void requireApi() {
        if (!apiEnabled) throw new CohortException(503, "SERVICE_UNAVAILABLE", "队列功能暂不可用");
    }
    public void requireXlsx() {
        requireApi();
        if (!xlsxEnabled) throw new CohortException(503, "SERVICE_UNAVAILABLE", "文件导出暂不可用");
    }
}
