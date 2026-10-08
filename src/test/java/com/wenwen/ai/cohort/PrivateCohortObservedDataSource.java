package com.wenwen.ai.cohort;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.AbstractDataSource;
/** 新表真实JDBC观察；raw writer不经过本层，退出断言先于fixture清理。 */
final class PrivateCohortObservedDataSource extends AbstractDataSource {
    interface Cut {
        void at(Connection owner) throws Exception;
    }
    final DataSource delegate;
    final AtomicInteger maxActive=new AtomicInteger(),active=new AtomicInteger(),borrowed=new AtomicInteger(),returned=new AtomicInteger(),clinicalReads=new AtomicInteger(),privateReads=new AtomicInteger(),definitionWrites=new AtomicInteger(),receiptWrites=new AtomicInteger(),commits=new AtomicInteger(),rollbacks=new AtomicInteger(),mysqlFailures=new AtomicInteger();
    final List<String> sqls=Collections.synchronizedList(new ArrayList<>());
    final List<Integer> connections=Collections.synchronizedList(new ArrayList<>());
    final List<Boolean> autoCommits=Collections.synchronizedList(new ArrayList<>());
    volatile String failSqlContains,lastSqlState;
    volatile int lastMysqlError;
    volatile Cut afterCompleteReceipt,afterCount;
    volatile Runnable beforeCommand;
    PrivateCohortObservedDataSource(DataSource delegate){
        this.delegate=delegate;
    }
    public Connection getConnection()throws SQLException{
        return observe(delegate.getConnection());
    }
    public Connection getConnection(String u,String p)throws SQLException{
        return observe(delegate.getConnection(u,p));
    }
    private Connection observe(Connection c){
        int count=active.incrementAndGet();
        maxActive.accumulateAndGet(count,Math::max);
        borrowed.incrementAndGet();
        int id=System.identityHashCode(c);
        return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{
            Connection.class
        },new InvocationHandler(){
            boolean closed,touched;
            public Object invoke(Object proxy,Method method,Object[] args)throws Throwable{
                try{
                    String methodName=method.getName();
                    if("close".equals(methodName)){
                        if(!closed){
                            c.close();
                            closed=true;
                            active.decrementAndGet();
                            returned.incrementAndGet();
                        }return null;
                    }
                    if("commit".equals(methodName)&&touched)commits.incrementAndGet();
                    if("rollback".equals(methodName)&&touched)rollbacks.incrementAndGet();
                    if("prepareStatement".equals(methodName)&&args[0] instanceof String){
                        String sql=((String)args[0]).trim();
                        boolean priv=sql.contains("ra_private_cohort_");
                        boolean read=sql.startsWith("SELECT");
                        boolean defWrite=priv&&!read&&sql.contains("ra_private_cohort_definition"),receiptWrite=priv&&!read&&sql.contains("ra_private_cohort_command");
                        if(read){
                            if(priv)privateReads.incrementAndGet();
                            else if(sql.contains("patient_"))clinicalReads.incrementAndGet();
                        }
                        if(priv){
                            sqls.add(sql);
                            connections.add(id);
                            autoCommits.add(c.getAutoCommit());
                        }
                        if(defWrite||receiptWrite)touched=true;
                        Object[] actual=args.clone();
                        if(failSqlContains!=null&&sql.contains(failSqlContains))actual[0]=sql.replace("ra_private_cohort_definition","p04a_missing_definition").replace("ra_private_cohort_command","p04a_missing_command");
                        PreparedStatement statement=(PreparedStatement)method.invoke(c,actual);
                        return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),new Class<?>[]{
                            PreparedStatement.class
                        },(p,m,a)->{
                            try{
                                boolean execute=m.getName().equals("execute")||m.getName().equals("executeUpdate")||m.getName().equals("executeQuery");
                                if(execute&&priv&&beforeCommand!=null){
                                    Runnable cut=beforeCommand;
                                    beforeCommand=null;
                                    cut.run();
                                }
                                Object result=m.invoke(statement,a);
                                if(execute){
                                    if(defWrite)definitionWrites.incrementAndGet();
                                    if(receiptWrite)receiptWrites.incrementAndGet();
                                    if(receiptWrite&&(sql.startsWith("INSERT")||sql.startsWith("UPDATE"))&&afterCompleteReceipt!=null){
                                        Cut cut=afterCompleteReceipt;
                                        afterCompleteReceipt=null;
                                        cut.at(c);
                                    }
                                    if(read&&sql.startsWith("SELECT COUNT(*)")&&sql.contains("ra_private_cohort_definition")&&afterCount!=null){
                                        Cut cut=afterCount;
                                        afterCount=null;
                                        cut.at(c);
                                    }
                                }return result;
                            }catch(InvocationTargetException e){
                                record(e.getCause());
                                throw e.getCause();
                            }
                        });
                    }return method.invoke(c,args);
                }catch(InvocationTargetException e){
                    record(e.getCause());
                    throw e.getCause();
                }
            }
        });
    }
    private void record(Throwable e){
        if(e instanceof SQLException){
            mysqlFailures.incrementAndGet();
            lastSqlState=((SQLException)e).getSQLState();
            lastMysqlError=((SQLException)e).getErrorCode();
        }
    }
    void reset(){
        if(active.get()!=0)throw new AssertionError("请求未退出");
        for(AtomicInteger x:Arrays.asList(maxActive,borrowed,returned,clinicalReads,privateReads,definitionWrites,receiptWrites,commits,rollbacks,mysqlFailures))x.set(0);
        sqls.clear();
        connections.clear();
        autoCommits.clear();
        failSqlContains=lastSqlState=null;
        lastMysqlError=0;
        afterCompleteReceipt=afterCount=null;
        beforeCommand=null;
    }
}
