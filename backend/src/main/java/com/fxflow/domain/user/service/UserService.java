package com.fxflow.domain.user.service;

import com.fxflow.domain.remittancetransaction.enums.TransferStatus;
import com.fxflow.domain.remittancetransaction.repository.RemittanceTransactionRepository;
import com.fxflow.domain.user.dto.request.LoginRequest;
import com.fxflow.domain.user.dto.request.SignupRequest;
import com.fxflow.domain.user.dto.response.SignupResponse;
import com.fxflow.domain.user.dto.response.WithdrawUserResponse;
import com.fxflow.domain.user.entity.User;
import com.fxflow.domain.user.enums.UserStatus;
import com.fxflow.domain.user.errorcode.UserErrorCode;
import com.fxflow.domain.user.repository.UserRepository;
import com.fxflow.domain.wallet.entity.Wallet;
import com.fxflow.domain.wallet.repository.WalletRepository;
import com.fxflow.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final WalletRepository walletRepository;
    private final RemittanceTransactionRepository remittanceTransactionRepository;

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
    public User login(LoginRequest request) {
        log.info("[로그인 시작] email={}", request.email());
        // 이메일로 유저 조회
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> {
                    log.warn("[로그인 실패] 이메일 없음 — email={}", request.email());
                    return new BusinessException(UserErrorCode.INVALID_CREDENTIALS);
                });
        //탈퇴한 회원 확인
        if (user.getStatus() == UserStatus.WITHDRAWN) {
            log.warn("[로그인 실패] 탈퇴한 회원 -email={}", request.email());
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


    /*
     *회원 탈퇴
     * 사용자의 지갑 잔액들이 비어있고, 송금이 진행중이 아니라면, 회원탈퇴를 진행한다.
     */
    @Transactional
    public WithdrawUserResponse withDrawn(Long userId) {
        log.info("[회원 탈퇴 시작]: userId={}", userId);
        User user = getUser(userId);
        validateNoBalance(userId);
        validateNoActiveTransfer(userId);
        String maskedEmail = "del_" + userId + "@delect.com";
        String maskedName = "탈퇴한 회원_" + userId;
        LocalDateTime withdrawAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        log.info("[회원 탈퇴 완료] userId={}", userId);
        return WithdrawUserResponse.of(user, withdrawAt);

    }

    public User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    }

    /**
     * 모든 지갑 잔액이 0원인지 확인한다. 잔액이 남아있다면, 회원 탈퇴를 막는다.
     */
    private void validateNoBalance(Long userId) {
        List<Wallet> wallets = walletRepository.findByUserId(userId);
        boolean hasBalance = wallets.stream()
                .anyMatch(w -> w.getBalance().compareTo(BigDecimal.ZERO) > 0);
        if (hasBalance) {
            log.warn("[회원 탈퇴 실패] 잔액 존재 — userId={}", userId);
            throw new BusinessException(UserErrorCode.WITHDRAWAL_BLOCKED);
        }
    }

    /**
     * 진행 중(PENDING/FUNDED/PROCESSING)인 해외송금 거래가 있는지 확인한다.
     */
    private void validateNoActiveTransfer(Long userId) {
        boolean hasActiveTransfer = remittanceTransactionRepository.existsByUserIdAndStatusIn(
                userId,
                List.of(TransferStatus.PENDING, TransferStatus.FUNDED, TransferStatus.PROCESSING)
        );
        if (hasActiveTransfer) {
            log.warn("[회원 탈퇴 실패] 진행 중 거래 존재 — userId={}", userId);
            throw new BusinessException(UserErrorCode.WITHDRAWAL_BLOCKED);
        }
    }

}
