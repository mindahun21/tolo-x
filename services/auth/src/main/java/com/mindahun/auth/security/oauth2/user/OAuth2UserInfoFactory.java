package com.mindahun.auth.security.oauth2.user;

import com.mindahun.auth.exception.OAuth2AuthenticationProcessingException;

import java.util.Map;

import static com.mindahun.auth.models.AuthProvider.GOOGLE;

public class OAuth2UserInfoFactory {
    public static OAuth2UserInfo getOAuth2UserInfo(String registrationId, Map<String, Object> attributes) {
        if(registrationId.equalsIgnoreCase(GOOGLE.toString())) {
            return new GoogleOAuth2UserInfo(attributes);
        }else {
            throw new OAuth2AuthenticationProcessingException("Sorry! login with" + registrationId + " is not supported");
        }
    }
}
