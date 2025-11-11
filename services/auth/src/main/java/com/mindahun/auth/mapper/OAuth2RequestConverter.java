package com.mindahun.auth.mapper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import java.util.HashMap;
import java.util.Set;

@Slf4j
public class OAuth2RequestConverter {
    public static SerializableOAuth2AuthorizationRequest toSerializable(OAuth2AuthorizationRequest authorizationRequest){
        if(authorizationRequest == null) return null;
        return SerializableOAuth2AuthorizationRequest.builder()
                .authorizationRequestUri(authorizationRequest.getAuthorizationRequestUri())
                .authorizationUri(authorizationRequest.getAuthorizationUri())
                .redirectUri(authorizationRequest.getRedirectUri())
                .scopes(authorizationRequest.getScopes())
                .state(authorizationRequest.getState())
                .clientId(authorizationRequest.getClientId())
                .responseType(authorizationRequest.getResponseType() !=null ? authorizationRequest.getResponseType().toString() : null )
                .attributes(authorizationRequest.getAttributes() != null ? new HashMap<>(authorizationRequest.getAttributes()) : null)
                .additionalParameters(authorizationRequest.getAdditionalParameters() != null ? new HashMap<>(authorizationRequest.getAdditionalParameters()) : null)
                .build();
    }

    public static OAuth2AuthorizationRequest fromSerializable(SerializableOAuth2AuthorizationRequest dto){
        if(dto == null) return null;
        OAuth2AuthorizationRequest.Builder builder = OAuth2AuthorizationRequest.authorizationCode();
        builder
                .authorizationRequestUri(dto.getAuthorizationRequestUri())
                .authorizationUri(dto.getAuthorizationUri())
                .redirectUri(dto.getRedirectUri())
                .state(dto.getState())
                .clientId(dto.getClientId())
                .scopes(dto.getScopes() != null ? dto.getScopes() : Set.of());

        if(dto.getAdditionalParameters() != null) builder.additionalParameters(dto.getAdditionalParameters());
        if(dto.getAttributes() != null) builder.attributes(dto.getAttributes());
        return builder.build();

    }
}
