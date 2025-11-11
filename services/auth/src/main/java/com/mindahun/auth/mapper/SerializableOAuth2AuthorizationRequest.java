package com.mindahun.auth.mapper;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SerializableOAuth2AuthorizationRequest {
    public String authorizationRequestUri;
    public String authorizationUri;
    public String clientId;
    public String redirectUri;
    public Set<String> scopes;
    public String state;
    public String responseType;
    public Map<String, Object> attributes;
    public Map<String, Object> additionalParameters;
}
