package com.wenwen.ai.export.mail;
import com.sun.mail.smtp.SMTPTransport;
import javax.mail.*;
import java.io.*;
/** 使用原库协议，DATA开始与最终250由真实方法里程碑取得。 */
public final class TrackedSmtpTransport extends SMTPTransport {
 private final ExportNotificationSender.Flight flight;
 public TrackedSmtpTransport(Session session,ExportNotificationSender.Flight flight){super(session,new URLName("smtp",null,-1,null,null,null));this.flight=flight;}
 @Override protected OutputStream data()throws MessagingException {flight.check();flight.dataEntered=true;return super.data();}
 @Override protected void finishData()throws IOException,MessagingException {super.finishData();flight.terminal.compareAndSet(null,"ACCEPTED");}
}
