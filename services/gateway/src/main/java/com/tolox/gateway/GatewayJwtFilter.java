package com.tolox.gateway;

import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Collection;

@Component
public class GatewayJwtFilter implements GlobalFilter, Ordered {
    private final JwtUtil jwtUtil;
    @Value("${internal.token}")
    private String internalToken;

    public GatewayJwtFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if(authHeader == null || !authHeader.startsWith("Bearer ")){
            return chain.filter(exchange);
        }

        String token = authHeader.substring(7);
        if(!jwtUtil.isTokenValid(token)){
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        Claims claims = jwtUtil.extractAllClaims(token);
        String email = claims.getSubject();
        Object rolesObj =  claims.get("roles");

        String rolesHeader;
        if(rolesObj instanceof Collection){
            rolesHeader = String.join(",",((Collection<?>) rolesObj).stream().map(Object::toString).toList());
        }else{
            rolesHeader = rolesObj == null ? "" : rolesObj.toString();
        }
        ServerHttpRequest mutate = request.mutate()
                .header("X-User-Email", email != null? email : "")
                .header("X-User-Roles", rolesHeader)
                .header("X-Internal-Token", internalToken)
                .build();

        return chain.filter(exchange.mutate().request(mutate).build());
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
