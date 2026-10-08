package com.wenwen.ai.export.mail;
import com.wenwen.ai.export.*;
import com.fasterxml.jackson.databind.JsonNode;
import javax.mail.*;
import javax.mail.internet.*;
import java.io.*;
import java.time.Instant;
import java.util.*;
/** SMTP仅消费固定有限模板。自由说明与条件从未进入正文。 */
public final class ExportMailMessage {
 private ExportMailMessage(){}
 public static MimeMessage build(Session session,ExportApplicationRecord record,ExportNotificationSettings config,Instant at)throws IOException,MessagingException {
  JsonNode summary=ExportApplicationSummary.read(record.text("summary"));String id=String.valueOf(record.number("applicationId"));String purpose="RESEARCH".equals(record.text("purposeCode"))?"研究分析":"DATA_CHECK".equals(record.text("purposeCode"))?"数据核对":"其他用途";
  String counts="COHORT".equals(record.text("source"))?summary.path("counts").path("cohortN").asText():"A="+summary.path("counts").path("aN").asText()+"，B="+summary.path("counts").path("bN").asText();List<String> items=new ArrayList<>();for(JsonNode n:summary.get("selectedItems"))items.add(n.asText());
  String body="申请编号："+id+"\n申请医生ID："+record.number("owner")+"\n用途："+purpose+"\n申请日期范围："+record.text("startDate")+" 至 "+record.text("endDate")+"\n结果类型："+record.text("source")+"\n结果引用："+record.text("referenceId")+"\n引用人数："+counts+"\n申请字段："+String.join(",",items)+"\n通知说明：此邮件仅记录导出申请，不表示审批通过或历史文件已生成。\n";
  if(body.getBytes("UTF-8").length>4096)throw new IOException("notification template limit");MimeMessage message=new MimeMessage(session);message.setFrom(ExportNotificationSettings.address(config.from));message.setRecipients(Message.RecipientType.TO,new InternetAddress[]{ExportNotificationSettings.address(config.to)});message.setSubject("RA导出申请 #"+id,"UTF-8");message.setText(body,"UTF-8");message.setSentDate(Date.from(at));message.saveChanges();message.setHeader("Message-ID","<ra-export-"+id+"@notification.invalid>");ByteArrayOutputStream serialized=new ByteArrayOutputStream();message.writeTo(serialized);if(serialized.size()>16384)throw new IOException("notification MIME limit");return message;
 }
}
