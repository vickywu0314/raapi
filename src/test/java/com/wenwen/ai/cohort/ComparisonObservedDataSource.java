package com.wenwen.ai.cohort;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.AbstractDataSource;
/** 组合既有observer，观察实际新表/源SQL切点；raw/writer完全独立。 */
final class ComparisonObservedDataSource extends AbstractDataSource {
    interface Cut {void at(Connection owner,String id)throws Exception;}
    final DataSource delegate;
    final AtomicInteger active=new AtomicInteger(),borrowed=new AtomicInteger(),returned=new AtomicInteger(),maxActive=new AtomicInteger();
    int cleanup,insert,read,commits,rollbacks,sourceStatements,mysqlFailures,lastMysqlError;
    String lastInsertedId,lastSqlState,failSqlContains;
    int failSourceOrdinal;
    Cut afterInsert;
    Cut afterPatients;
    final List<String> statements=new ArrayList<>();
    final List<Integer> identities=new ArrayList<>();
    final List<Boolean> autoCommits=new ArrayList<>();
    final List<Integer> isolations=new ArrayList<>();
    ComparisonObservedDataSource(DataSource delegate){this.delegate=delegate;}
    public Connection getConnection()throws SQLException{return observe(delegate.getConnection());}
    public Connection getConnection(String user,String password)throws SQLException{return observe(delegate.getConnection(user,password));}
    private Connection observe(Connection c){
        int current=active.incrementAndGet();maxActive.accumulateAndGet(current,Math::max);borrowed.incrementAndGet();int identity=System.identityHashCode(c);
        return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},new InvocationHandler(){
            boolean closed,touched;
            public Object invoke(Object proxy,Method method,Object[] args)throws Throwable {
                try{
                    String name=method.getName();if("close".equals(name)){if(!closed){c.close();closed=true;active.decrementAndGet();returned.incrementAndGet();}return null;}
                    if("commit".equals(name)&&touched)commits++;if("rollback".equals(name)&&touched)rollbacks++;
                    if("prepareStatement".equals(name)&&args[0] instanceof String){
                        String sql=((String)args[0]).trim();boolean comparison=sql.contains("ra_ai_compare_run"),select=sql.startsWith("SELECT"),clinical=select&&sql.contains("patient_");boolean insertSql=comparison&&sql.startsWith("INSERT"),cleanupSql=comparison&&sql.startsWith("DELETE");
                        if(insertSql||cleanupSql)touched=true;if(comparison||clinical){statements.add(sql);identities.add(identity);autoCommits.add(c.getAutoCommit());isolations.add(c.getTransactionIsolation());}
                        Object[] actual=args.clone();if(clinical){sourceStatements++;if(sourceStatements==failSourceOrdinal)actual[0]=sql.replace("patient_basic_info","p04b_missing_clinical_source");}
                        if(failSqlContains!=null&&sql.contains(failSqlContains))actual[0]=sql.replace("ra_ai_compare_run","p04b_missing_compare_run").replace("patient_basic_info","p04b_missing_scope_source");
                        PreparedStatement statement=(PreparedStatement)method.invoke(c,actual);
                        return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),new Class<?>[]{PreparedStatement.class},(p,m,a)->{
                            try{
                                if(insertSql&&"setString".equals(m.getName())&&Integer.valueOf(1).equals(a[0]))lastInsertedId=(String)a[1];Object result=m.invoke(statement,a);
                                boolean execute=Arrays.asList("execute","executeUpdate","executeQuery").contains(m.getName());
                                if(execute){if(insertSql){insert++;if(afterInsert!=null){Cut cut=afterInsert;afterInsert=null;cut.at(c,lastInsertedId);}}if(cleanupSql)cleanup++;if(comparison&&select)read++;
                                    if(clinical&&sql.startsWith("SELECT p.id, p.gender")&&afterPatients!=null){Cut cut=afterPatients;afterPatients=null;cut.at(c,null);}}
                                return result;
                            }catch(InvocationTargetException e){record(e.getCause());throw e.getCause();}
                        });
                    }
                    return method.invoke(c,args);
                }catch(InvocationTargetException e){record(e.getCause());throw e.getCause();}
            }
        });
    }
    private void record(Throwable e){if(e instanceof SQLException){mysqlFailures++;lastMysqlError=((SQLException)e).getErrorCode();lastSqlState=((SQLException)e).getSQLState();}}
    void reset(){if(active.get()!=0)throw new AssertionError("比较owner未退出");borrowed.set(0);returned.set(0);maxActive.set(0);cleanup=insert=read=commits=rollbacks=sourceStatements=mysqlFailures=lastMysqlError=failSourceOrdinal=0;lastInsertedId=lastSqlState=failSqlContains=null;afterInsert=null;afterPatients=null;statements.clear();identities.clear();autoCommits.clear();isolations.clear();}
}
