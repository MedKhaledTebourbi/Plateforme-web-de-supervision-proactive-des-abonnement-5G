package com.example.micro_reclamation.Service;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.io.*;

@Component
@Order(1)
public class DebugBodyFilter implements Filter {

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) req;

        System.out.println("=============================");
        System.out.println("METHOD      : " + request.getMethod());
        System.out.println("URI         : " + request.getRequestURI());
        System.out.println("CONTENT-TYPE: " + request.getContentType());
        System.out.println("CONTENT-LEN : " + request.getContentLength());
        System.out.println("=============================");

        chain.doFilter(req, res);
    }
}
