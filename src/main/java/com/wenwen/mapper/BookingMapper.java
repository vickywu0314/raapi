package com.wenwen.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;

import com.wenwen.vo.BookingVo;

@Mapper
public interface BookingMapper {
	
	void saveBookingData(BookingVo vo);

	List<Map<String,Object>> queryBookingSchedule(Map<String,Object> map);
	
	List<Map<String,Object>> queryBookingByPhone(Map<String,String> map);
//	List<String> getInvitationCodes();
//	List<String> checkPhoneExist();
//	Long saveUser(User user);
//	User getUserByPhone(Map<String,Object> map);
//	User getUserById(Long id);
//
//	void delUserMedia(UserMedia um);
//	User getUserByCode(String code);
//	void updateIndustry(User user);
//	void updateUserQrcode(User user);
}
