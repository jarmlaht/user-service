package com.vinsguru.netflux.user.service;

import com.vinsguru.netflux.user.config.JwtProperties;
import com.vinsguru.netflux.user.dto.LoginRequest;
import com.vinsguru.netflux.user.dto.LoginResponse;
import com.vinsguru.netflux.user.dto.RegisterRequest;
import com.vinsguru.netflux.user.exceptions.UsernameAlreadyExistsException;
import com.vinsguru.netflux.user.mapper.UserMapper;
import com.vinsguru.netflux.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtEncoder jwtEncoder,
                       JwtProperties jwtProperties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
    }

    @Transactional
    public void register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new UsernameAlreadyExistsException(request.username());
        }
        try {
            userRepository.saveAndFlush(UserMapper.toEntity(request, passwordEncoder.encode(request.password())));
        } catch (DataIntegrityViolationException ex) {
            throw new UsernameAlreadyExistsException(request.username());
        }
    }

    public LoginResponse login(LoginRequest request) {
        var authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password())
        );
        var principal = (UserPrincipal) authentication.getPrincipal();
        return UserMapper.toLoginResponse(principal, generateToken(principal));
    }

    private String generateToken(UserPrincipal principal) {
        var now = Instant.now();
        var claims = JwtClaimsSet.builder()
                .subject(String.valueOf(principal.id()))
                .issuedAt(now)
                .expiresAt(now.plus(jwtProperties.expiration()))
                .build();
        var header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
