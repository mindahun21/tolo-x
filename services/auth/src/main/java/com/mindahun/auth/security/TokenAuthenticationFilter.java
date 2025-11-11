package com.mindahun.auth.security;

import com.mindahun.auth.repository.UserRepository;
import com.mindahun.auth.service.CustomUserDetails;
import com.mindahun.auth.utils.JwtUtil;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class TokenAuthenticationFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,@NonNull HttpServletResponse response,@NonNull FilterChain filterChain) throws ServletException, IOException {

        String token = getTokenFromHeaderOrCookie(request);
        if (token != null) {
            try{
                String userEmail = jwtUtil.extractUsername(token);

                if(userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    UserDetails userDetails = userRepository.findByEmail(userEmail).map(CustomUserDetails::new).orElse(null);
                    if(userDetails != null && jwtUtil.validateToken(token,userDetails)) {
                        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                }
            }catch(ExpiredJwtException ex){
                log.info("================== hear in TokenAuthenticationFilter ===============");
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"error\":\"Token expired\"}");
                return;
            }catch(Exception ex){
                log.info("================== hear in TokenAuthenticationFilter2 ===============");

                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"error\":\"Token expired\"}");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private String getTokenFromHeaderOrCookie(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");
        if (headerAuth != null && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }
        if(request.getCookies()!=null){
            for(Cookie cookie: request.getCookies()){
                if(cookie.getName().equals("access_token")){
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
