package com.mindahun.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
public class RequestResponseLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        log.info("➡️ Request: {} {}", request.getMethod(), request.getRequestURI());

        try{
            chain.doFilter(request, response);

        }catch (Exception e){
            log.error("Exception during request processing :{} ", e.getMessage(),e);
            throw e;
        }

        log.info("⬅️ Response: {}", response.getStatus());
    }
}

