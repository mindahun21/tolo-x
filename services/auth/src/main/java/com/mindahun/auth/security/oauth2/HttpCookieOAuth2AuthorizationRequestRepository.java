package com.mindahun.auth.security.oauth2;

import com.mindahun.auth.utils.CookieUtils;
import com.nimbusds.oauth2.sdk.util.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import org.springframework.stereotype.Component;

@Slf4j
@Component
public class HttpCookieOAuth2AuthorizationRequestRepository implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {
    public static final String OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME = "OAUTH2_AUTHORIZATION_REQUEST_COOKIE";
    public static final String REDIRECT_URI_PARAM_COOKIE_NAME = "redirect_uri";
    public static final String CLIENT_TYPE = "client_type";
    public static final int cookieExpirationSeconds = 3600;


    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        String cookieValue = CookieUtils.getCookie(request,OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);
        if(cookieValue == null) return null;
        return CookieUtils.deserializeOAuth2AuthorizationRequest(cookieValue);
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest, HttpServletRequest request, HttpServletResponse response) {
        if(authorizationRequest == null){
            CookieUtils.deleteCookie(response,request,OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);
            CookieUtils.deleteCookie(response,request,REDIRECT_URI_PARAM_COOKIE_NAME);
            CookieUtils.deleteCookie(response,request,CLIENT_TYPE);
            return;
        }
        String serialized = CookieUtils.serializeOAuth2AuthorizationRequest(authorizationRequest);
        CookieUtils.addCookie(response,OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME,serialized,cookieExpirationSeconds);
        String redirectUri = request.getParameter(REDIRECT_URI_PARAM_COOKIE_NAME);
        String clientType = request.getParameter(CLIENT_TYPE);
        if(StringUtils.isNotBlank(redirectUri)){
            CookieUtils.addCookie(response, REDIRECT_URI_PARAM_COOKIE_NAME, redirectUri, cookieExpirationSeconds);
        }
        if(StringUtils.isNotBlank(clientType)){
            CookieUtils.addCookie(response, CLIENT_TYPE, clientType, cookieExpirationSeconds);
        }
    }

    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request) {
        return loadAuthorizationRequest(request);
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request, HttpServletResponse response) {
        OAuth2AuthorizationRequest authReq =  loadAuthorizationRequest(request);
        CookieUtils.deleteCookie(response, request, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);
        return authReq;
    }
}
