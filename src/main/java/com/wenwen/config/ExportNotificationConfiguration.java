package com.wenwen.config;
import com.wenwen.ai.export.mail.*;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
/** 默认关闭；只读取本适配命名空间，不复制旧JavaMail杂项。 */
@Configuration public class ExportNotificationConfiguration {
 @Bean public ExportNotificationSettings exportNotificationSettings(Environment e){return new ExportNotificationSettings(e.getProperty("ra.export.smtp.enabled","false"),e.getProperty("ra.export.smtp.host",""),e.getProperty("ra.export.smtp.port",""),e.getProperty("ra.export.smtp.from",""),e.getProperty("ra.export.apply-mail-to",""),e.getProperty("ra.export.smtp.username",""),e.getProperty("ra.export.smtp.password",""),e.getProperty("ra.export.smtp.mode","STARTTLS"));}
 @Bean(destroyMethod="close") public ExportNotificationSender exportNotificationSender(){return new ExportNotificationSender();}
}
