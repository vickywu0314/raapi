package com.wenwen.ai.export.mail;
import javax.mail.internet.*;
import java.util.*;
/** 一次尝试的服务端配置快照；无请求参数或全局JavaMail属性。 */
public final class ExportNotificationSettings {
 public final String enabled,host,port,from,to,username,password,mode;
 public ExportNotificationSettings(String enabled,String host,String port,String from,String to,String user,String password,String mode){this.enabled=enabled;this.host=host;this.port=port;this.from=from;this.to=to;username=user;this.password=password;this.mode=mode;}
 public String failure(){if("false".equals(enabled))return "NOT_CONFIGURED";if(!"true".equals(enabled))return "INVALID_MAIL_CONFIG";if(host.isEmpty()||port.isEmpty()||from.isEmpty()||to.isEmpty())return "NOT_CONFIGURED";try{if(!port.matches("[0-9]+")||Integer.parseInt(port)<1||Integer.parseInt(port)>65535)throw new IllegalArgumentException();address(from);address(to);if(username.isEmpty()!=password.isEmpty())throw new IllegalArgumentException();if("LOOPBACK_PLAIN".equals(mode)){if(!Arrays.asList("127.0.0.1","::1").contains(host)||!username.isEmpty())throw new IllegalArgumentException();}else if(!"STARTTLS".equals(mode))throw new IllegalArgumentException();if(host.contains("\r")||host.contains("\n"))throw new IllegalArgumentException();return null;}catch(RuntimeException|AddressException e){return "INVALID_MAIL_CONFIG";}}
 public static InternetAddress address(String value)throws AddressException{if(value==null||value.length()>255||value.indexOf('\r')>=0||value.indexOf('\n')>=0)throw new AddressException("invalid notification address");InternetAddress[] addresses=InternetAddress.parse(value,true);if(addresses.length!=1||addresses[0].isGroup()||addresses[0].getPersonal()!=null||!value.equals(addresses[0].getAddress()))throw new AddressException("invalid notification address");addresses[0].validate();return addresses[0];}
}
