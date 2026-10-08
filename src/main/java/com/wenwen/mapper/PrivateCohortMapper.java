package com.wenwen.mapper;
import com.wenwen.ai.cohort.*;
import org.apache.ibatis.annotations.Mapper;
@Mapper public interface PrivateCohortMapper {
    PrivateCohortDefinition find(@org.apache.ibatis.annotations.Param("owner") long owner,@org.apache.ibatis.annotations.Param("id") String id);
    long count(@org.apache.ibatis.annotations.Param("owner") long owner);
    java.util.List<PrivateCohortDefinition> list(@org.apache.ibatis.annotations.Param("owner") long owner,@org.apache.ibatis.annotations.Param("offset") long offset,@org.apache.ibatis.annotations.Param("size") int size);
    PrivateCohortDefinition findForUpdate(@org.apache.ibatis.annotations.Param("owner") long owner,@org.apache.ibatis.annotations.Param("id") String id);
    int delete(@org.apache.ibatis.annotations.Param("d") PrivateCohortDefinition definition,@org.apache.ibatis.annotations.Param("expected") long expected);
    int rename(@org.apache.ibatis.annotations.Param("d") PrivateCohortDefinition definition,@org.apache.ibatis.annotations.Param("expected") long expected);
    int insertDefinition(PrivateCohortDefinition definition);
    PrivateCohortReceipt findReceipt(@org.apache.ibatis.annotations.Param("owner") long owner,@org.apache.ibatis.annotations.Param("action") String action,@org.apache.ibatis.annotations.Param("requestId") String requestId);
    int insertReceipt(PrivateCohortReceipt receipt);
}
