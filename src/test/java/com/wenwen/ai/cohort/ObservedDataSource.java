package com.wenwen.ai.cohort;

import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.AbstractDataSource;

/** 只观察生产借用连接；fixture/writer 直接用独立 raw DataSource。 */
final class ObservedDataSource extends AbstractDataSource {
    final DataSource delegate;
    final AtomicInteger active = new AtomicInteger();
    int borrowed, returned, selects, mysqlFailures;
    final List<Integer> queryConnections = new ArrayList<>();
    final List<Integer> isolations = new ArrayList<>();
    final List<Boolean> autoCommits = new ArrayList<>();
    String lastInsertedId;int ownerSawInserted;
    int inserts,deletes,writeCommits,writeRollbacks,scopeReads,displayReads;
    boolean failInsertSql,failRunReadSql,failDisplaySql,omitDisplayRow;
    Runnable afterInsert,afterScopeQuery;
    final List<Integer> writeConnections=new ArrayList<>();final List<Boolean> writeAutoCommits=new ArrayList<>();
    Runnable afterFirstQuery;
    boolean failSecondSql, failQcSql;
    String lastSqlState;int lastMysqlError;
    String failClinicalTable;
    ObservedDataSource(DataSource delegate) { this.delegate = delegate; }
    public Connection getConnection() throws SQLException { return observe(delegate.getConnection()); }
    public Connection getConnection(String user, String password) throws SQLException { return observe(delegate.getConnection(user,password)); }
    private Connection observe(Connection connection) {
        active.incrementAndGet(); borrowed++;
        int identity = System.identityHashCode(connection);
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, new InvocationHandler() {
            boolean closed,writeTouched;
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                try {
                    if ("close".equals(method.getName())) {
                        if (!closed) { connection.close(); closed=true; active.decrementAndGet(); returned++; }
                        return null;
                    }
                    if("commit".equals(method.getName())&&writeTouched)writeCommits++;
                    if("rollback".equals(method.getName())&&writeTouched)writeRollbacks++;
                    if("prepareStatement".equals(method.getName())&&args[0] instanceof String&&(((String)args[0]).trim().startsWith("INSERT")||((String)args[0]).trim().startsWith("DELETE"))&&((String)args[0]).contains("ra_ai_analysis_run")) {
                        writeTouched=true;String sql=(String)args[0];boolean insert=sql.trim().startsWith("INSERT");Object[] actual=args.clone();
                        if(insert&&failInsertSql)actual[0]=sql.replace("ra_ai_analysis_run","p03c_missing_run_table");
                        PreparedStatement statement=(PreparedStatement)method.invoke(connection,actual);
                        writeConnections.add(identity);writeAutoCommits.add(connection.getAutoCommit());
                        return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),new Class<?>[]{PreparedStatement.class},(p,m,a)->{
                            try{if(insert&&"setString".equals(m.getName())&&Integer.valueOf(1).equals(a[0]))lastInsertedId=(String)a[1];Object result=m.invoke(statement,a);if("execute".equals(m.getName())||"executeUpdate".equals(m.getName())){if(insert){inserts++;if(afterInsert!=null){try(PreparedStatement read=connection.prepareStatement("SELECT COUNT(*) FROM ra_ai_analysis_run WHERE id=?")){read.setString(1,lastInsertedId);try(ResultSet rows=read.executeQuery()){rows.next();ownerSawInserted=rows.getInt(1);}}Runnable fault=afterInsert;afterInsert=null;fault.run();}}else deletes++;}return result;}
                            catch(InvocationTargetException e){if(e.getCause() instanceof SQLException){mysqlFailures++;lastSqlState=((SQLException)e.getCause()).getSQLState();lastMysqlError=((SQLException)e.getCause()).getErrorCode();}throw e.getCause();}
                        });
                    }
                    if ("prepareStatement".equals(method.getName()) && args[0] instanceof String && ((String)args[0]).trim().startsWith("SELECT")) {
                        selects++; queryConnections.add(identity); isolations.add(connection.getTransactionIsolation()); autoCommits.add(connection.getAutoCommit());
                        boolean scope=((String)args[0]).trim().startsWith("SELECT p.id FROM patient_basic_info");
                        boolean display=((String)args[0]).trim().startsWith("SELECT p.id,p.name,p.study_no");if(scope)scopeReads++;if(display)displayReads++;
                        boolean second = ((String)args[0]).contains("patient_follow_up_history");
                        Object[] actual = args.clone();
                        if ((second && failSecondSql) || (failClinicalTable!=null && ((String)args[0]).contains(failClinicalTable))) actual[0] = "SELECT ? FROM p01c_missing_source_table";
                        if(failQcSql && ((String)args[0]).contains("\'M_BASELINE_LAB\'")) actual[0]=((String)args[0]).replace("patient_basic_info", "p02e_missing_qc_source");
                        if(failRunReadSql&&((String)args[0]).contains("FROM ra_ai_analysis_run"))actual[0]=((String)args[0]).replace("ra_ai_analysis_run","p03c_missing_run_read");
                        if(omitDisplayRow&&display)actual[0]=((String)actual[0])+" LIMIT 19";
                        if(failDisplaySql&&display)actual[0]=((String)args[0]).replace("patient_basic_info","p03c_missing_display");
                        PreparedStatement statement = (PreparedStatement) method.invoke(connection,actual);
                        return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),new Class<?>[]{PreparedStatement.class},(p,m,a) -> {
                            try {
                                Object value = m.invoke(statement,a);
                                if ("execute".equals(m.getName()) && !second && afterFirstQuery != null) {
                                    Runnable barrier = afterFirstQuery; afterFirstQuery=null; barrier.run();
                                }
                                if("execute".equals(m.getName())&&scope&&afterScopeQuery!=null){Runnable barrier=afterScopeQuery;afterScopeQuery=null;barrier.run();}
                                return value;
                            } catch (InvocationTargetException e) { if (e.getCause() instanceof SQLException) {mysqlFailures++;lastSqlState=((SQLException)e.getCause()).getSQLState();lastMysqlError=((SQLException)e.getCause()).getErrorCode();} throw e.getCause(); }
                        });
                    }
                    return method.invoke(connection,args);
                } catch (InvocationTargetException e) { if (e.getCause() instanceof SQLException) {mysqlFailures++;lastSqlState=((SQLException)e.getCause()).getSQLState();lastMysqlError=((SQLException)e.getCause()).getErrorCode();} throw e.getCause(); }
            }
        });
    }
    void reset() {
        if (active.get()!=0) throw new AssertionError("请求前存在未释放连接");
        lastInsertedId=null;ownerSawInserted=0;borrowed=returned=selects=mysqlFailures=inserts=deletes=writeCommits=writeRollbacks=scopeReads=displayReads=0;failInsertSql=failRunReadSql=failDisplaySql=omitDisplayRow=false;afterInsert=afterScopeQuery=null;writeConnections.clear();writeAutoCommits.clear(); queryConnections.clear(); isolations.clear(); autoCommits.clear(); afterFirstQuery=null; failSecondSql=false; failClinicalTable=null;failQcSql=false;lastSqlState=null;lastMysqlError=0;
    }
}
