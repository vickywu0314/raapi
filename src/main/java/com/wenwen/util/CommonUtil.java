package com.wenwen.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;

public class CommonUtil {

	public static String[] chars = new String[] { "a", "b", "c", "d", "e", "f",  
            "g", "h", "i", "j", "k", "l", "m", "n", "o", "p", "q", "r", "s",  
            "t", "u", "v", "w", "x", "y", "z", "0", "1", "2", "3", "4", "5",  
            "6", "7", "8", "9", "A", "B", "C", "D", "E", "F", "G", "H", "I",  
            "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V",  
            "W", "X", "Y", "Z" };  
	
	public static String[] chars2 = new String[] { "0", "1", "2", "3", "4", "5",  
            "6", "7", "8", "9" };  
	
	//生成邀请码&其他随机码
	public static String generateShortUuid(int num) {  
		StringBuffer shortBuffer = new StringBuffer();  
		String uuid = UUID.randomUUID().toString().replace("-", "");  
	    for (int i = 0; i < num; i++) {  
	        String str = uuid.substring(i * 4, i * 4 + 4);  
	        int x = Integer.parseInt(str, 16);  
	        shortBuffer.append(chars[x % 0x3E]);  
	    }  
	    return shortBuffer.toString();  
	} 
	
	public static String generateCode() {  
		StringBuffer shortBuffer = new StringBuffer();  
		String uuid = UUID.randomUUID().toString().replace("-", "");  
	    for (int i = 0; i < 4; i++) {  
	        String str = uuid.substring(i * 4, i * 4 + 4);  
	        int x = Integer.parseInt(str, 16);  
	        shortBuffer.append(chars2[x % 0xA]);  
	    }  
	    return shortBuffer.toString();  
	}
	public static String md5(String str) {    
		MessageDigest messageDigest = null;    
        try {    
            messageDigest = MessageDigest.getInstance("MD5");    
            messageDigest.reset();    
            messageDigest.update(str.getBytes("UTF-8"));    
        } catch (NoSuchAlgorithmException e) {    
            System.out.println("NoSuchAlgorithmException caught!");    
            System.exit(-1);    
        } catch (UnsupportedEncodingException e) {    
            e.printStackTrace();    
        }    
        byte[] byteArray = messageDigest.digest();    
        StringBuffer md5StrBuff = new StringBuffer();    
        for (int i = 0; i < byteArray.length; i++) {                
            if (Integer.toHexString(0xFF & byteArray[i]).length() == 1)    
            	md5StrBuff.append("0").append(Integer.toHexString(0xFF & byteArray[i]));    
            else    
                md5StrBuff.append(Integer.toHexString(0xFF & byteArray[i]));    
        }    
        return md5StrBuff.toString();    
    }    
    
    public static String sendToUrl(String sendtext, String urll, String encoding, Integer seconds,String type) throws Exception {
		URL url = new URL(urll);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		int httpTimeOut = seconds * 1000;
		conn.setConnectTimeout(httpTimeOut);
		conn.setReadTimeout(httpTimeOut);
		conn.setDoInput(true);
		conn.setDoOutput(true);
		if(StringUtils.isNotBlank(type)){
			conn.setRequestProperty("Content-Type", type);						
		}else{
			conn.setRequestProperty("Content-Type", "text/xml");			
		}
		conn.setRequestProperty("Accept-Charset", encoding);
		conn.setRequestMethod("POST");
		conn.connect();
		BufferedWriter output = null;
		BufferedReader br = null;
		StringBuffer sb = new StringBuffer();
		try {
			output = new BufferedWriter(new OutputStreamWriter(conn.getOutputStream(), encoding));
			output.write(sendtext);
			try {
				br = new BufferedReader(new InputStreamReader(conn.getInputStream(), encoding));
				String s = "";
				while ((s = br.readLine()) != null) {
					sb.append(s);
				}
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				br.close();
			}
		
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (output != null) {
				output.flush();
				output.close();
			}
		}
		return sb.toString();
	}
}
