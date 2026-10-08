package com.wenwen.ai.cohort;

import java.io.IOException;
import javax.servlet.*;
import javax.servlet.http.*;

/** 在真实 HTTP 链外观察原始读取字节数，不替换 Controller 或查询解析。 */
final class CountingBodyFilter implements Filter {
    int bytes;Runnable onFirstRead;
    void firstRead(){if(onFirstRead!=null){Runnable action=onFirstRead;onFirstRead=null;action.run();}}
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        bytes=0;
        chain.doFilter(new HttpServletRequestWrapper((HttpServletRequest) request) {
            public ServletInputStream getInputStream() throws IOException {
                ServletInputStream stream = super.getInputStream();
                return new ServletInputStream() {
                    public int read() throws IOException { int value=stream.read(); firstRead(); if(value!=-1) bytes++; return value; }
                    public int read(byte[] b,int off,int len) throws IOException { int n=stream.read(b,off,len); firstRead(); if(n>0) bytes+=n; return n; }
                    public boolean isFinished() { return stream.isFinished(); }
                    public boolean isReady() { return stream.isReady(); }
                    public void setReadListener(ReadListener listener) { stream.setReadListener(listener); }
                };
            }
        },response);
    }
}
