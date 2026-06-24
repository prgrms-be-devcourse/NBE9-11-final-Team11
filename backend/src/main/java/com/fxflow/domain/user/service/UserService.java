package com.fxflow.domain.user.service;

import com.fxflow.domain.remittancetransaction.enums.TransferStatus;
import com.fxflow.domain.remittancetransaction.repository.RemittanceTransactionRepository;
import com.fxflow.domain.user.dto.request.LoginRequest;
import com.fxflow.domain.user.dto.request.SignupRequest;
import com.fxflow.domain.user.dto.response.SignupResponse;
import com.fxflow.domain.user.dto.response.UserCheckResponse;
import com.fxflow.domain.user.dto.response.WithdrawUserResponse;
import com.fxflow.domain.user.dto.response.UserCheckResponse;
import com.fxflow.domain.user.dto.response.WithdrawUserResponse;
import com.fxflow.domain.user.entity.User;
import com.fxflow.domain.user.enums.UserStatus;
import com.fxflow.domain.user.errorcode.UserErrorCode;
import com.fxflow.domain.user.repository.UserRepository;
import com.fxflow.domain.wallet.errorcode.P2pTransferErrorCode;
import com.fxflow.domain.wallet.entity.Wallet;
import com.fxflow.domain.wallet.repository.WalletRepository;
import com.fxflow.domain.wallet.errorcode.P2pTransferErrorCode;
import com.fxflow.domain.wallet.entity.Wallet;
import com.fxflow.domain.wallet.repository.WalletRepository;
import com.fxflow.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
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

        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            // existsByEmail() 체크 이후 동시 요청이 먼저 INSERT를 끝낸 경우 (TOCTOU)
            log.warn("[회원가입 실패] 동시 요청으로 인한 이메일 중복 — email={}", request.email());
            throw new BusinessException(UserErrorCode.EMAIL_DUPLICATED);
        }

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
    * 비밀번호를 재설정한다.
    * 현재 비밀번호로 1차 검증을 진행한다.
    * 변경할려는 비밀번호가 현재 비밀번호랑 같을시 에러를 발생한다.
    */
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        log.info("[비밀번호 변경 시작] userId={}", userId);

        User user = getUser(userId);
        if (user.getStatus() == UserStatus.WITHDRAWN) {
            log.warn("[비밀번호 변경 실패] 탈퇴한 회원 — userId={}", userId);
            throw new BusinessException(UserErrorCode.ALREADY_WITHDRAWN);
        }

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            log.warn("[비밀번호 변경 실패] 현재 비밀번호 불일치 — userId={}", userId);
            throw new BusinessException(UserErrorCode.PASSWORD_MISMATCH);
        }

        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            log.warn("[비밀번호 변경 실패] 기존과 동일한 비밀번호 — userId={}", userId);
            throw new BusinessException(UserErrorCode.SAME_AS_OLD_PASSWORD);
        }

        String encodedNewPassword = passwordEncoder.encode(newPassword);
        user.changePassword(encodedNewPassword);

        log.info("[비밀번호 변경 완료] userId={}", userId);
    }


    /**
     * 회원 탈퇴
     * 비밀번호를 재확인하고, 사용자의 지갑 잔액들이 비어있고,
     *  진행 중인 해외송금 거래가 없다면 회원 탈퇴를 진행한다.
     */
    @Transactional
    public WithdrawUserResponse withDrawn(Long userId, String password) {
        log.info("[회원 탈퇴 시작]: userId={}", userId);
        User user = getUser(userId);

        validateNotAlreadyWithdrawn(user);
        validatePassword(user, password);
        validateNoBalance(userId);
        validateNoActiveTransfer(userId);

        String maskedEmail = "del_" + userId + "@delect.com";
        String maskedName = "탈퇴한 회원_" + userId;
        LocalDateTime withdrawAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        user.withdraw(maskedEmail, maskedName);
        log.info("[회원 탈퇴 완료] userId={}", userId);
        return WithdrawUserResponse.of(user, withdrawAt);
    }

    public User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    }

    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    }

    public UserCheckResponse checkRecipient(Long currentUserId, String email) {
        User recipient = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
        if (recipient.getId().equals(currentUserId)) {
            throw new BusinessException(P2pTransferErrorCode.SELF_TRANSFER_NOT_ALLOWED);
        }
        return UserCheckResponse.of(recipient);
    }

    /**
     * 모든 지갑 잔액이 0원인지 확인한다. 잔액이 남아있다면, 회원 탈퇴를 막는다.
     */
    private void validateNoBalance(Long userId) {
        List<Wallet> wallets = walletRepository.findByUserId(userId);
        boolean hasBalance = wallets.stream()
                .anyMatch(w -> w.getBalance() != null && w.getBalance().compareTo(BigDecimal.ZERO) != 0);
        if (hasBalance) {
            log.warn("[회원 탈퇴 실패] 잔액 존재 — userId={}", userId);
            throw new BusinessException(UserErrorCode.WITHDRAWAL_BLOCKED);
        }
    }

    /**
     * 진행 중(PENDING/FUNDED/PROCESSING)인 거래가 있는지 확인한다.
     * 진행 중(PENDING/FUNDED/PROCESSING)인 해외송금 거래가 있는지 확인한다.
     */
    private void validateNoActiveTransfer(Long userId) {
        boolean hasActiveTransfer = remittanceTransactionRepository.existsByUserIdAndStatusIn(
                userId,
                List.of(TransferStatus.PENDING, TransferStatus.FUNDED, TransferStatus.PROCESSING)
        );
        if (hasActiveTransfer) {
            log.warn("[회원 탈퇴 실패] 진행 중 해외송금 거래 존재 — userId={}", userId);
            throw new BusinessException(UserErrorCode.WITHDRAWAL_BLOCKED);
        }
    }

    /**
     * 이미 탈퇴 처리된 회원인지 확인한다. 토큰 재사용 등으로 인한 중복 탈퇴를 막는다.
     */
    private void validateNotAlreadyWithdrawn(User user) {
        if (user.getStatus() == UserStatus.WITHDRAWN) {
            log.warn("[회원 탈퇴 실패] 이미 탈퇴한 회원 — userId={}", user.getId());
            throw new BusinessException(UserErrorCode.ALREADY_WITHDRAWN);
        }
    }

    /**
     * 탈퇴는 비밀번호 재확인을 거친다. 세션 탈취 등으로 인한 임의 탈퇴를 막는다.
     */
    private void validatePassword(User user, String password) {
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            log.warn("[회원 탈퇴 실패] 비밀번호 불일치 — userId={}", user.getId());
            throw new BusinessException(UserErrorCode.INVALID_CREDENTIALS);
        }
    }



}
