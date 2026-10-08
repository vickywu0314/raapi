package com.wenwen.ai.cohort;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.AbstractDataSource;
/** 组合真实JDBC，故障仅在已执行SQL/实际提交边界触发。 */
final class ExportApplicationObservedDataSource extends AbstractDataSource {
    interface Cut {void at(Connection owner,String sql)throws Exception;}
    private final DataSource delegate;
    final AtomicInteger active=new AtomicInteger(),borrowed=new AtomicInteger(),returned=new AtomicInteger();
    int auditStatements,commits,rollbacks,mysqlFailures,faults;String lastSqlState;int lastMysqlError;String lastInsertedId,failSqlContains,failCommitBefore,failCommitAfter,afterSqlContains,afterCommitContains;Cut afterSql,afterCommit;
    final List<String> auditSql=Collections.synchronizedList(new ArrayList<>());final List<Boolean> auditAutoCommit=Collections.synchronizedList(new ArrayList<>());
    ExportApplicationObservedDataSource(DataSource d){delegate=d;}
    public Connection getConnection()throws SQLException{return wrap(delegate.getConnection());}
    public Connection getConnection(String u,String p)throws SQLException{return wrap(delegate.getConnection(u,p));}
    private Connection wrap(Connection c){active.incrementAndGet();borrowed.incrementAndGet();return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},new InvocationHandler(){boolean closed;String lastAudit;
        public Object invoke(Object p,Method m,Object[] a)throws Throwable{try{
            String name=m.getName();if("close".equals(name)){if(!closed){c.close();closed=true;active.decrementAndGet();returned.incrementAndGet();}return null;}
            if("commit".equals(name)&&lastAudit!=null){if(failCommitBefore!=null&&lastAudit.contains(failCommitBefore)){failCommitBefore=null;faults++;throw new SQLException("synthetic export commit-before failure","08006");}Object result=m.invoke(c,a);commits++;if(afterCommit!=null&&lastAudit.contains(afterCommitContains)){Cut cut=afterCommit;afterCommit=null;cut.at(c,lastAudit);}if(failCommitAfter!=null&&lastAudit.contains(failCommitAfter)){failCommitAfter=null;faults++;throw new SQLException("synthetic export commit-ack failure","08006");}return result;}
            if("rollback".equals(name)&&lastAudit!=null)rollbacks++;
            if("prepareStatement".equals(name)&&a[0] instanceof String){String sql=(String)a[0];boolean audit=(sql.contains("ra_ai_export_request")||sql.contains("export_application"));if(audit){if(sql.trim().startsWith("INSERT")||sql.trim().startsWith("UPDATE")||sql.trim().startsWith("DELETE"))lastAudit=sql;auditSql.add(sql);auditAutoCommit.add(c.getAutoCommit());}Object[] actual=a.clone();if(audit&&failSqlContains!=null&&sql.contains(failSqlContains))actual[0]=sql.replace("ra_ai_export_request","p06b_missing_request_table").replace("export_application","p06b_missing_application_table");PreparedStatement st=(PreparedStatement)m.invoke(c,actual);return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),new Class<?>[]{PreparedStatement.class},(sp,sm,sa)->{try{if(audit&&sql.trim().startsWith("INSERT")&&"setString".equals(sm.getName())&&Integer.valueOf(1).equals(sa[0]))lastInsertedId=String.valueOf(sa[1]);Object result=sm.invoke(st,sa);if(audit&&Arrays.asList("execute","executeUpdate","executeQuery").contains(sm.getName())){auditStatements++;if(afterSql!=null&&sql.contains(afterSqlContains)){Cut cut=afterSql;afterSql=null;faults++;cut.at(c,sql);}}return result;}catch(InvocationTargetException e){record(e.getCause());throw e.getCause();}});}
            return m.invoke(c,a);
        }catch(InvocationTargetException e){record(e.getCause());throw e.getCause();}}
    });}
    private void record(Throwable e){if(e instanceof SQLException){mysqlFailures++;lastSqlState=((SQLException)e).getSQLState();lastMysqlError=((SQLException)e).getErrorCode();}}
    void reset(){if(active.get()!=0)throw new AssertionError("导出owner未退出");borrowed.set(0);returned.set(0);auditStatements=commits=rollbacks=mysqlFailures=faults=0;lastSqlState=null;lastMysqlError=0;lastInsertedId=failSqlContains=failCommitBefore=failCommitAfter=afterSqlContains=afterCommitContains=null;afterSql=afterCommit=null;auditSql.clear();auditAutoCommit.clear();}
}
