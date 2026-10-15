package com.vinsguru.netflux.user.mapper;

import com.vinsguru.netflux.user.dto.LoginResponse;
import com.vinsguru.netflux.user.dto.RegisterRequest;
import com.vinsguru.netflux.user.entity.User;
import com.vinsguru.netflux.user.service.UserPrincipal;

public class UserMapper {

    public static User toEntity(RegisterRequest request, String encodedPassword) {
        var user = new User();
        user.setUsername(request.username());
        user.setPassword(encodedPassword);
        user.setName(request.name());
        return user;
    }

    public static UserPrincipal toPrincipal(User user) {
        return new UserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getPassword(),
                user.getName()
        );
    }

    public static LoginResponse toLoginResponse(UserPrincipal principal, String token) {
        return new LoginResponse(
                token,
                principal.name()
        );
    }
}
