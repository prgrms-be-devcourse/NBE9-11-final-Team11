package com.fxflow.domain.user.service;

import com.fxflow.domain.user.dto.request.LoginRequest;
import com.fxflow.domain.user.dto.request.SignupRequest;
import com.fxflow.domain.user.dto.response.SignupResponse;
import com.fxflow.domain.user.entity.User;
import com.fxflow.domain.user.enums.UserStatus;
import com.fxflow.domain.user.errorcode.UserErrorCode;
import com.fxflow.domain.user.repository.UserRepository;
import com.fxflow.global.config.PasswordEncoderConfig;
import com.fxflow.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    /**
     * 회원가입
     * 이메일 중복 확인 후 유저 생성
     */
    @Transactional
    public SignupResponse signup(SignupRequest request) {
        log.info("[회원가입 시작] email={}", request.email());

        // 이메일 중복 확인
        if (userRepository.existsByEmail(request.email())) {
            log.warn("[회원가입 실패] 이메일 중복 — email={}", request.email());
            throw new BusinessException(UserErrorCode.EMAIL_DUPLICATED);
        }

        // 비밀번호 암호화
        String encodedPassword = passwordEncoder.encode(request.password());

        // 유저 생성
        User user = User.create(
                request.email(),
                encodedPassword,
                request.name()
        );

        userRepository.save(user);

        log.info("[회원가입 완료] userId={}, email={}", user.getId(), user.getEmail());

        return SignupResponse.of(user);
    }
    @Transactional
    public User login(LoginRequest request){
        log.info("[로그인 시작] email={}", request.email());
        // 이메일로 유저 조회
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> {
                    log.warn("[로그인 실패] 이메일 없음 — email={}", request.email());
                    return new BusinessException(UserErrorCode.INVALID_CREDENTIALS);
                });
        //탈퇴한 회원 확인
        if(user.getStatus()== UserStatus.WITHDRAWN){
            log.warn("[로그인 실패] 탈퇴한 회원 -email={}",request.email());
            throw new BusinessException(UserErrorCode.INVALID_CREDENTIALS);
        }
        // 비밀번호 검증
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("[로그인 실패] 비밀번호 불일치 — email={}", request.email());
            throw new BusinessException(UserErrorCode.INVALID_CREDENTIALS);
        }
        log.info("[로그인 완료] userId={}, email={}", user.getId(), user.getEmail());
        return user;
    }
}
