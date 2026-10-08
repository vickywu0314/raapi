package com.wenwen.service;
import com.wenwen.ai.query.PrivateCohortQuery;
import com.wenwen.ai.scope.TrustedDoctor;
import java.util.Map;
public interface PrivateCohortService {
    Map<String,Object> execute(TrustedDoctor doctor,PrivateCohortQuery query,String trace);
}
