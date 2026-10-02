package com.wenwen.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.util.List;
import java.util.Map;
@ApiModel(value = "返回数据集的实体类")
public class DataResult<T>{

    @ApiModelProperty("返回状态 true 成功 false 失败")
    private Boolean success;
    @ApiModelProperty("状态码 200 成功")
    private String code;
    @ApiModelProperty("返回的信息")
    private String message;
    @ApiModelProperty("返回的分页信息")
    private NewPage page;
    @ApiModelProperty("返回的数据List内容")
    private List<T> dataList;
    @ApiModelProperty("返回的数据实体内容")
    private T data;
    @ApiModelProperty("参数")
    private Object params;
    @ApiModelProperty("返回的数据Map内容")
    private Map<String,List<Map<String,Object>>> dataMap;
    
    public Map<String,List<Map<String,Object>>> getDataMap() {
		return dataMap;
	}

	public void setDataMap(Map<String,List<Map<String,Object>>> dataMap) {
		this.dataMap = dataMap;
	}

	public static class NewPage{
        @ApiModelProperty("当前页码")
        private int currPageIndex;
        @ApiModelProperty("每页条数")
        private int pageSize;
        @ApiModelProperty("数据总条数")
        private int recordCount;
        @ApiModelProperty("总页数")
        private int pageCount;

        public NewPage(int currPageIndex, int pageSize, int recordCount, int pageCount) {
            this.currPageIndex = currPageIndex;
            this.pageSize = pageSize;
            this.recordCount = recordCount;
            this.pageCount = pageCount;
        }

        public int getCurrPageIndex() {
            return currPageIndex;
        }
        public void setCurrPageIndex(int currPageIndex) {
            this.currPageIndex = currPageIndex;
        }
        public int getPageSize() {
            return pageSize;
        }
        public void setPageSize(int pageSize) {
            this.pageSize = pageSize;
        }
        public int getRecordCount() {
            return recordCount;
        }
        public void setRecordCount(int recordCount) {
            this.recordCount = recordCount;
        }
        public int getPageCount() {
            return pageCount;
        }
        public void setPageCount(int pageCount) {
            this.pageCount = pageCount;
        }

    }


    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public NewPage getPage() {
        return page;
    }

    public void setPage(NewPage page) {
        this.page = page;
    }

    public List<T> getDataList() {
        return dataList;
    }

    public void setDataList(List<T> dataList) {
        this.dataList = dataList;
    }

    public T getData() {
		return data;
	}

	public void setData(T data) {
		this.data = data;
	}

	public Object getParams() {
        return params;
    }

    public void setParams(Object params) {
        this.params = params;
    }

}
