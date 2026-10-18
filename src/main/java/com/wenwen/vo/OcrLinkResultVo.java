package com.wenwen.vo;

import java.util.ArrayList;
import java.util.List;

/** 临床记录保存后的原图关联结果；linked与pending互斥且并集等于请求集合。 */
public class OcrLinkResultVo {
    private String status = "NOT_REQUESTED";
    private final List<String> linkedFileIds = new ArrayList<String>();
    private final List<String> pendingFileIds = new ArrayList<String>();
    private final List<String> retryableFileIds = new ArrayList<String>();
    private String message;
    public String getStatus() { return status; }
    public List<String> getLinkedFileIds() { return linkedFileIds; }
    public List<String> getPendingFileIds() { return pendingFileIds; }
    /** 可重试集合始终是pending的子集；兼容boolean表示至少有一张可重试。 */
    public List<String> getRetryableFileIds() { return retryableFileIds; }
    public boolean isRetryable() { return !retryableFileIds.isEmpty(); }
    public void addPending(String fileId, boolean retryable) {
        pendingFileIds.add(fileId);
        if (retryable) retryableFileIds.add(fileId);
    }
    public String getMessage() { return message; }
    public void finish() {
        status = pendingFileIds.isEmpty() ? linkedFileIds.isEmpty() ? "NOT_REQUESTED" : "LINKED" : "PENDING";
        int blocked = pendingFileIds.size() - retryableFileIds.size();
        message = pendingFileIds.isEmpty() ? null : "病历已保存，" + retryableFileIds.size() + "张原图可重试关联，" + blocked + "张需人工核对，勿重复创建病历";
    }
}
