package com.mindahun.auth.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindahun.auth.mapper.OAuth2RequestConverter;
import com.mindahun.auth.mapper.SerializableOAuth2AuthorizationRequest;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
public class CookieUtils {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static String getCookie(HttpServletRequest request, String cookieName){
        Cookie[] cookies = request.getCookies();
        if(cookies != null){
            for(Cookie cookie : cookies){
                if(cookie.getName().equals(cookieName)){
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    public static void addCookie(HttpServletResponse response, String name, String value, int maxAge) {
        Cookie cookie = new Cookie(name, value);
        cookie.setMaxAge(maxAge);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        response.addCookie(cookie);
    }

    public static void deleteCookie(HttpServletResponse response, HttpServletRequest request, String cookieName){
        Cookie[] cookies = request.getCookies();
        if(cookies != null){
            for(Cookie cookie : cookies){
                if(cookie.getName().equals(cookieName)){
                    cookie.setValue("");
                    cookie.setPath("/");
                    cookie.setMaxAge(0);
                    response.addCookie(cookie);
                }
            }
        }
    }

    public static String serializeOAuth2AuthorizationRequest(OAuth2AuthorizationRequest request){
        try {
            SerializableOAuth2AuthorizationRequest dto = OAuth2RequestConverter.toSerializable(request);
            String json = objectMapper.writeValueAsString(dto);
            return Base64.getUrlEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        }catch (Exception e){
            throw new IllegalStateException(e);
        }
    }

    public static OAuth2AuthorizationRequest deserializeOAuth2AuthorizationRequest(String cookieValue){
        if(cookieValue == null || cookieValue.isEmpty()) return null;
        try{
            byte[] bytes = Base64.getUrlDecoder().decode(cookieValue);
            String json = new String(bytes, StandardCharsets.UTF_8);

            SerializableOAuth2AuthorizationRequest dto = objectMapper.readValue(json, SerializableOAuth2AuthorizationRequest.class);
            return OAuth2RequestConverter.fromSerializable(dto);
        }catch (Exception e){
            throw new IllegalStateException(e);
        }
    }
}
