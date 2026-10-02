package com.wenwen.controller.ra;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wenwen.mapper.BookingMapper;
import com.wenwen.vo.BookingVo;
import com.wenwen.vo.DataResult;

//import doraemon.gaoke.util.LocalLock;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

@Api(description = "预约模块")
@CrossOrigin
@RestController
@RequestMapping(value="/booking")
public class BookingController {
	
	@Autowired
	private BookingMapper bookingMapper;
	
	@ApiOperation(value = "预约模块", notes = "预约模块" ,response=DataResult.class,httpMethod = "POST")
//	@LocalLock(key = "localLock:arg[4]")		//第四个参数，会当成key传进来，防刷新的； 
	@PostMapping("/saveBookingData")
	public DataResult<List<Map<String, Object>>> saveBookingData(
			@RequestParam(value = "bookingDate", required = true) @ApiParam(value = "预约时间", required = true) String bookingDate,
			@RequestParam(value = "accountNum", required = true) @ApiParam(value = "预约的客户人数", required = true) int accountNum,
			@RequestParam(value = "address", required = true) @ApiParam(value = "预约的地址", required = true) String address,
			@RequestParam(value = "memo", required = true) @ApiParam(value = "备注", required = true) String memo,
			@RequestParam(value = "token", required = true) @ApiParam(value = "Token", required = true) String token,
			@RequestParam(value = "phone", required = true) @ApiParam(value = "预约人手机号", required = true) String phone,
			@RequestParam(value = "activityName", required = true) @ApiParam(value = "预约人活动的名称", required = true) String activityName,
			@RequestParam(value = "activityId", required = true) @ApiParam(value = "预约人活动的ID", required = true) int activityId,
			@RequestParam(value = "totalCount", required = false) @ApiParam(value = "该预约活动的总人数", required = true) int totalCount,
			HttpServletRequest request) {
		DataResult<List<Map<String, Object>>> result = new DataResult<List<Map<String, Object>>>();
		//需要查一下这个活动当前日期 可不可以预约，如果不可以，则回传不行； 
		Map<String,Object> map = new HashMap<String, Object>();
		map.put("activityId", activityId);
		map.put("bookingDate", bookingDate);
		List<Map<String, Object>> bookActivitiesList = bookingMapper.queryBookingSchedule(map);
		if(null == bookActivitiesList || bookActivitiesList.size() == 0) {
			result.setCode("500");
			result.setMessage("当前日期不可预约");
			result.setSuccess(false);
		}else {
			//查询一下是否满了
			Map<String, Object> one = bookActivitiesList.get(0);
			Integer currentBookingCount = Integer.parseInt(one.get("bookingCount").toString());
			currentBookingCount +=1;
			int tc = Integer.parseInt(one.get("totalCount").toString());
			if(currentBookingCount > tc)
			{
				result.setCode("500");
				result.setMessage("人数已满，不可预约");
				result.setSuccess(false);
			}
			try {
				BookingVo booking = new BookingVo();
				booking.setBookingDate(bookingDate);
				booking.setAccountNum(accountNum);
				booking.setAddress(address);
				booking.setMemo(memo);
				booking.setPhone(phone);
				booking.setActivityId(activityId);
				booking.setActivityName(activityName);
				booking.setTotalCount(totalCount);
				bookingMapper.saveBookingData(booking);
				result.setCode("200");
				result.setMessage("成功");
				result.setSuccess(true);
			}catch(Exception e) {
				e.printStackTrace();
				result.setCode("500");
				result.setMessage("参数错误获取失败");
				result.setSuccess(false);
			}
		}
		return result;
	}
	
	//查询预约的活动情况
	@PostMapping("/queryBookingSchedule")
	public DataResult<List<Map<String, Object>>>  queryBookingSchedule(
			@RequestParam(value = "activityName", required = false) @ApiParam(value = "活动名称", required = true) String activityName,
			@RequestParam(value = "activityId", required = false) @ApiParam(value = "活动ID", required = true) String activityId,
			@RequestParam(value = "month", required = false) @ApiParam(value = "活动时间的月数", required = true) String month,
			HttpServletRequest request)
	{
		DataResult<List<Map<String, Object>>> result = new DataResult<List<Map<String, Object>>>();
		Map<String,Object> map = new HashMap<String, Object>();
		map.put("activityId", activityId);
		map.put("activityName", activityName);
		
		map.put("month", month.substring(0, 7));
		try {
			List<Map<String, Object>> bookActivitiesList = bookingMapper.queryBookingSchedule(map);
			result.setCode("200");
			result.setData(bookActivitiesList);
			result.setMessage("成功");
			result.setSuccess(true);
		}catch(Exception e) {
			e.printStackTrace();
			result.setCode("500");
			result.setMessage("参数错误获取失败");
			result.setSuccess(false);
		}
		return result;
	}
	
	//根据phone查询预约情况
	@PostMapping("/queryBookingByPhone")
	public DataResult<List<Map<String, Object>>>  queryBookingByPhone(
			@RequestParam(value = "phone", required = true) @ApiParam(value = "用户手机",required = true) String phone,
			HttpServletRequest request)
	{
		DataResult<List<Map<String, Object>>> result = new DataResult<List<Map<String, Object>>>();
		try {
			Map<String,String> map = new HashMap<String, String>();
			map.put("phone", phone);
			List<Map<String, Object>> userBookingList = bookingMapper.queryBookingByPhone(map);
			result.setCode("200");
			result.setData(userBookingList);
			result.setMessage("成功");
			result.setSuccess(true);
		}catch(Exception e) {
			result.setCode("500");
			result.setMessage("参数错误获取失败");
			result.setSuccess(false);
			e.printStackTrace();
		}
		return result;
	}
	
//
//	//提供客户导EXCEL功能
//	@PostMapping("/getUserReportByCreateTime")
//	public void getUserReportByCreateTime(HttpServletRequest request,HttpServletResponse response){
//		String templatePath = dir+"jl_template.xls";
//		JxlsUtils jxl = new JxlsUtils();
//		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
//		Calendar calendar = new GregorianCalendar();
//		calendar.setTime(new Date());
//		calendar.add(calendar.DATE,-1);
//		String yesterday= sdf.format(calendar.getTime());
//		Map<String,Object> map = new HashMap<String, Object>();
//		map.put("create_time", yesterday);
//		List<Map<String,Object>> list = userMapper.getUserByCreateTime(map);
//		
//		String fileName = "jl_report_"+yesterday+".xls";
//		String outPath = dir+"jl_report.xls";
//		try {
//			jxl.writeExcelFromTemplate(outPath, templatePath,list,response);
//			response.setContentType("application/x-msdownload");
//			response.addHeader("Content-Disposition","attachment;filename=" + fileName);
//			ServletOutputStream output  = response.getOutputStream();
//			FileInputStream fis = new FileInputStream(outPath);
//	
//			byte[] b = new byte[1024];
//			int i = 0;
//			while((i = fis.read(b)) > 0){
//				output.write(b, 0, i);
//			}
//			output.flush();
//			fis.close();
//			output.close();
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//	}
	
}
