package com.mindahun.auth.security.oauth2;

import com.mindahun.auth.utils.CookieUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

import static com.mindahun.auth.security.oauth2.HttpCookieOAuth2AuthorizationRequestRepository.REDIRECT_URI_PARAM_COOKIE_NAME;


@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {


    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException {
        String redirectUri;
        try{
            redirectUri = CookieUtils.getCookie(request, REDIRECT_URI_PARAM_COOKIE_NAME);

            if (!StringUtils.hasLength(redirectUri)) {
                redirectUri = "/"; // fallback
            }
        }
        catch (Exception e){
            redirectUri = "/";
        }
        CookieUtils.deleteCookie(response, request, HttpCookieOAuth2AuthorizationRequestRepository.REDIRECT_URI_PARAM_COOKIE_NAME);

        redirectUri = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("error", "Authentication failed.")
                .build().encode().toUriString();
        getRedirectStrategy().sendRedirect(request, response, redirectUri);
    }
}
