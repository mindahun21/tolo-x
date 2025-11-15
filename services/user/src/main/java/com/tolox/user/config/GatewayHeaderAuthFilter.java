package com.tolox.user.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class GatewayHeaderAuthFilter extends OncePerRequestFilter {

    @Value("${internal.token}")
    private String internalToken;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
//      1) verify the request form gateway (internal token)
        String incomingToken = request.getHeader("X-Internal-Token");
        if(!internalToken.equals(incomingToken)){
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
//      2) extract identity headers
        String rolesHeader = request.getHeader("X-User-Roles");
        String email = request.getHeader("X-User-Email");

//      3) build authorities from roles header
        if(email != null && !email.isEmpty()){
            List<SimpleGrantedAuthority> grantedAuthorities = Collections.emptyList();
            if(rolesHeader != null && !rolesHeader.toString().isEmpty()){
                grantedAuthorities = Arrays.stream(rolesHeader.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(role-> new SimpleGrantedAuthority("ROLE_" + role))
                        .toList();
            }
//        4) build auth principal
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(email, null, grantedAuthorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
//        5)
        filterChain.doFilter(request, response);
    }
}
