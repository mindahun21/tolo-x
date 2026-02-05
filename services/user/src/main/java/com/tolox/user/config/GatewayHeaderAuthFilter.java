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
    private String gatewayToken;

    @Value("${internal.service.token}")
    private String serviceToken;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs") || path.equals("/swagger-ui.html") || path.equals("/api-docs") || path.equals("/actuator/health")) {
            filterChain.doFilter(request, response);
            return;
        }
        String incomingGatewayToken = request.getHeader("X-Internal-Token");
        String incomingServiceToken = request.getHeader("X-Service-Token");

        log.info("service token: {}, incoming service token: {}",serviceToken, incomingServiceToken);
        log.info("gateway token: {} incoming gateway token: {}",gatewayToken, incomingGatewayToken);

        if(serviceToken.equals(incomingServiceToken)){
            // This is for service-to-service communication
            List<SimpleGrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_INTERNAL"));
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken("internal-service", null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }else if (gatewayToken.equals(incomingGatewayToken)) {
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
        }else {
            log.error("Unauthorized request: invalid internal token");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

//        5)
        filterChain.doFilter(request, response);
    }
}
