package com.mindahun.auth.security.oauth2;

import com.mindahun.auth.exception.OAuth2AuthenticationProcessingException;
import com.mindahun.auth.models.AuthProvider;
import com.mindahun.auth.models.Role;
import com.mindahun.auth.models.User;
import com.mindahun.auth.repository.RoleRepository;
import com.mindahun.auth.repository.UserRepository;
import com.mindahun.auth.security.oauth2.user.OAuth2UserInfo;
import com.mindahun.auth.security.oauth2.user.OAuth2UserInfoFactory;
import com.mindahun.auth.service.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class CustomOidcUserService extends OidcUserService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException{
        OidcUser oidcUser = super.loadUser(userRequest);
        try{
            return processOidcUser(userRequest,oidcUser);
        }catch (Exception ex) {
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_login", ex.getMessage(), null));
        }
    }

    private OidcUser processOidcUser(OidcUserRequest userRequest, OidcUser oidcUser) {
        OAuth2UserInfo oAuth2UserInfo = OAuth2UserInfoFactory.getOAuth2UserInfo(userRequest.getClientRegistration().getRegistrationId(),oidcUser.getIdToken().getClaims());

        if(!StringUtils.hasLength(oAuth2UserInfo.getEmail())){
            throw new OAuth2AuthenticationProcessingException("Email not found form Oauth2 Provider");
        }
        Optional<User> userOptional = userRepository.findByEmail(oAuth2UserInfo.getEmail());
        User user;
        if(userOptional.isPresent()){
            user = userOptional.get();
            if(!user.getProvider().equals(AuthProvider.valueOf(userRequest.getClientRegistration().getRegistrationId().toUpperCase()))){
                throw new OAuth2AuthenticationProcessingException("Looks like you are signed up with " +user.getProvider() +" account. Please use your "+user.getProvider()+" account to login.");
            }
            user = updateExistingUser(user,oAuth2UserInfo);

        }else{
            user= registerNewUser(userRequest,oAuth2UserInfo);

        }

        return CustomUserDetails.from(user, oidcUser.getAttributes(), oidcUser.getIdToken(), oidcUser.getUserInfo());
    }

    private User registerNewUser(OAuth2UserRequest oAuth2UserRequest, OAuth2UserInfo oAuth2UserInfo) {

        Role defaultRole = roleRepository.findByName("ROLE_USER").orElseThrow(() -> new RuntimeException("Default role not found"));

        User user = new User();
        user.setEmail(oAuth2UserInfo.getEmail());
        user.setName(oAuth2UserInfo.getName());
        user.setProvider(AuthProvider.valueOf(oAuth2UserRequest.getClientRegistration().getRegistrationId().toUpperCase()));
        user.setProviderId(oAuth2UserInfo.getId());
        user.setImageUrl(oAuth2UserInfo.getImageUrl());
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));

        user.setRoles(Set.of(defaultRole));
        return userRepository.save(user);
    }

    private User updateExistingUser(User existingUser, OAuth2UserInfo oAuth2UserInfo) {
        existingUser.setName(oAuth2UserInfo.getName());
        existingUser.setImageUrl(oAuth2UserInfo.getImageUrl());
        return userRepository.save(existingUser);
    }
}
