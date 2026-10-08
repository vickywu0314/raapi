package com.wenwen.ai.cohort;

import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.AbstractDataSource;

/** 观察真实审计owner；组合原final observer，独立raw读取不计生产借用。 */
final class ParseAuditObservedDataSource extends AbstractDataSource {
    final DataSource delegate;
    int borrowed,returned,active,statements,results,inserts,commits,rollbacks,attempts,ownerSawInserted;
    String lastEventId;
    boolean failInsertSql;
    Runnable afterInsert;
    final List<Boolean> autoCommits=new ArrayList<>();
    final List<String> sqls=new ArrayList<>();
    ParseAuditObservedDataSource(DataSource delegate){this.delegate=delegate;}
    public Connection getConnection() throws SQLException{return observe(delegate.getConnection());}
    public Connection getConnection(String user,String password) throws SQLException{return observe(delegate.getConnection(user,password));}
    private static Object call(Object target,Method method,Object[] args)throws Throwable{try{return method.invoke(target,args);}catch(InvocationTargetException e){throw e.getCause();}}
    private Connection observe(Connection connection){borrowed++;active++;
        return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},new InvocationHandler(){
            boolean closed,touched;
            public Object invoke(Object proxy,Method method,Object[] args)throws Throwable{
                String name=method.getName();
                if(name.equals("close")){if(!closed){connection.close();closed=true;returned++;active--;}return null;}
                if(name.equals("commit")&&touched){Object result=call(connection,method,args);commits++;return result;}
                if(name.equals("rollback")&&touched){Object result=call(connection,method,args);rollbacks++;return result;}
                if(name.equals("prepareStatement")){
                    String sql=(String)args[0];sqls.add(sql);boolean insert=sql.trim().toUpperCase(Locale.ROOT).startsWith("INSERT INTO RA_AI_PARSE_AUDIT");
                    Object[] actual=args.clone();if(insert){touched=true;attempts++;autoCommits.add(connection.getAutoCommit());if(failInsertSql)actual[0]=sql.replace("ra_ai_parse_audit","p05a_missing_audit");}
                    PreparedStatement statement=(PreparedStatement)call(connection,method,actual);statements++;
                    return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),new Class<?>[]{PreparedStatement.class},new InvocationHandler(){
                        boolean closedStatement;
                        public Object invoke(Object proxy,Method method,Object[] args)throws Throwable{
                            String name=method.getName();if(name.equals("close")){if(!closedStatement){statement.close();closedStatement=true;statements--;}return null;}
                            if(insert&&name.equals("setString")&&Integer.valueOf(1).equals(args[0]))lastEventId=(String)args[1];
                            Object value=call(statement,method,args);
                            if(insert&&(name.equals("execute")||name.equals("executeUpdate"))){inserts++;if(afterInsert!=null){try(PreparedStatement read=connection.prepareStatement("SELECT COUNT(*) FROM ra_ai_parse_audit WHERE event_id=?")){read.setString(1,lastEventId);try(ResultSet rows=read.executeQuery()){rows.next();ownerSawInserted=rows.getInt(1);}}Runnable fault=afterInsert;afterInsert=null;fault.run();}}
                            if(value instanceof ResultSet)return observeResult((ResultSet)value);
                            return value;
                        }
                    });
                }
                return call(connection,method,args);
            }
        });
    }
    private ResultSet observeResult(ResultSet rows){results++;return (ResultSet)Proxy.newProxyInstance(ResultSet.class.getClassLoader(),new Class<?>[]{ResultSet.class},new InvocationHandler(){boolean closed;public Object invoke(Object proxy,Method method,Object[] args)throws Throwable{if(method.getName().equals("close")){if(!closed){rows.close();closed=true;results--;}return null;}return call(rows,method,args);}});}
    void reset(){if(active!=0||statements!=0||results!=0)throw new AssertionError("观察前owner未退出");borrowed=returned=active=statements=results=inserts=commits=rollbacks=attempts=ownerSawInserted=0;lastEventId=null;failInsertSql=false;afterInsert=null;autoCommits.clear();sqls.clear();}
}
