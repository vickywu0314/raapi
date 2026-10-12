package com.wenwen.ai.query;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.*;

/** 首页概览没有可选范围：只接收有界 UTF-8 空对象。 */
public final class OverviewQuery {
    private OverviewQuery() { }
    public static OverviewQuery read(InputStream input) throws IOException {
        JsonNode node=PatientDetailQuery.readObject(input);
        if(node==null||!node.isObject()||node.size()!=0)
            throw new CohortException(400,"INVALID_REQUEST","概览输入无效");
        return new OverviewQuery();
    }
}
