package com.wenwen.service;
import com.wenwen.ai.scope.TrustedDoctor;
import com.wenwen.ai.query.ParseQuery;
import java.io.IOException;
import java.util.Map;
public interface AiParseService {Map<String,Object> parse(TrustedDoctor doctor,ParseQuery query,String trace,long started) throws IOException;Map<String,Object> apply(TrustedDoctor doctor,ParseQuery query,String trace,long started) throws IOException;}
