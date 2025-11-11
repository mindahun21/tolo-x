package com.mindahun.auth.security.oauth2;

import com.mindahun.auth.config.PropertiesConfig;
import com.mindahun.auth.models.User;
import com.mindahun.auth.repository.RoleRepository;
import com.mindahun.auth.repository.UserRepository;
import com.mindahun.auth.service.CustomUserDetails;
import com.mindahun.auth.utils.CookieUtils;
import com.mindahun.auth.utils.JwtUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.coyote.BadRequestException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final PropertiesConfig propertiesConfig;
    private final JwtUtil jwtUtil;
    private final HttpCookieOAuth2AuthorizationRequestRepository httpCookieOAuth2AuthorizationRequestRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuthenticationFailureHandler failureHandler;


    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException{
        String redirectUri;
        try{
            redirectUri = determineTargetUrl(request, response);
            if(response.isCommitted()){
                log.debug("Response has already been committed cannot redirect to {}", redirectUri);
                return;
            }

            Object principal = authentication.getPrincipal();
            CustomUserDetails userDetails;

            if(principal instanceof CustomUserDetails customUserDetails){
                userDetails = customUserDetails;
            }else if (principal instanceof OidcUser oidcUser){

                User user = userRepository.findByEmail((String) oidcUser.getAttributes().get("email")).orElseThrow(()-> new AuthenticationServiceException("User not found with email: " + oidcUser.getAttributes().get("email")));
                userDetails = CustomUserDetails.from(user,oidcUser.getAttributes(), oidcUser.getIdToken(), oidcUser.getUserInfo());
            }else if (principal instanceof OAuth2User oauth2User){
                User user = userRepository.findByEmail((String) oauth2User.getAttributes().get("email")).orElseThrow(()-> new AuthenticationServiceException("User not found with email: " + oauth2User.getAttributes().get("email")));
                userDetails = CustomUserDetails.from(user, oauth2User.getAttributes());
            }else{

                throw new AuthenticationServiceException("Invalid user principal type");
            }

            String token = jwtUtil.generateToken(userDetails);
            String clientTypeHeader = request.getHeader("X-Client-Type");
            String clientTypeParameter = CookieUtils.getCookie(request,HttpCookieOAuth2AuthorizationRequestRepository.CLIENT_TYPE);
            if("web".equalsIgnoreCase(clientTypeHeader) || "web".equalsIgnoreCase(clientTypeParameter)){
                Cookie cookie = new Cookie("access_token", token);
                cookie.setHttpOnly(true);
                cookie.setPath("/");
                cookie.setMaxAge((int) propertiesConfig.getTokenExpirationMsec());
                response.addCookie(cookie);


            }else{
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write("{\"access_token\":\"" + token + "\"}");

            }
        }catch (Exception e){
            failureHandler.onAuthenticationFailure(
                    request,
                    response,
                    new AuthenticationServiceException(e.getMessage(), e)
            );
            return;
        }
        CookieUtils.deleteCookie(response, request, HttpCookieOAuth2AuthorizationRequestRepository.REDIRECT_URI_PARAM_COOKIE_NAME);
        getRedirectStrategy().sendRedirect(request, response, redirectUri);
        clearAuthenticationAttributes(request, response);
    }

    protected String determineTargetUrl(HttpServletRequest request, HttpServletResponse response) {
        String redirectUri = CookieUtils.getCookie(request, HttpCookieOAuth2AuthorizationRequestRepository.REDIRECT_URI_PARAM_COOKIE_NAME);
        if (StringUtils.hasLength(redirectUri) && !isAuthorizedRedirectUri(redirectUri)) {
            try {
                throw new BadRequestException("Sorry! We've got an Unauthorized Redirect URI and can't proceed with the authentication");
            } catch (BadRequestException e) {
                throw new RuntimeException(e);
            }
        }


        return redirectUri;
    }

    private boolean isAuthorizedRedirectUri(String redirectUri) {
        URI clientRedirectUri = URI.create(redirectUri);
        return propertiesConfig.getAuthorizedRedirectUris().stream()
                .anyMatch(authorizedRedirectUri ->{
                    URI authorizedRedirectURI = URI.create(authorizedRedirectUri);
                    if(authorizedRedirectURI.getHost().equalsIgnoreCase(clientRedirectUri.getHost())
                    && authorizedRedirectURI.getPort() == clientRedirectUri.getPort()){
                        return true;
                    }
                    return false;

                });
    }

    protected void clearAuthenticationAttributes(HttpServletRequest request, HttpServletResponse response) {
        super.clearAuthenticationAttributes(request);
    }
}
