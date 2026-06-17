package com.fxflow.domain.remittancetransaction.service;

import com.fxflow.domain.remittancetransaction.dto.request.RecipientCreateRequest;
import com.fxflow.domain.remittancetransaction.dto.response.RecipientResponse;
import com.fxflow.domain.remittancetransaction.entity.Recipient;
import com.fxflow.domain.remittancetransaction.errorcode.RecipientErrorCode;
import com.fxflow.domain.remittancetransaction.repository.RecipientRepository;
import com.fxflow.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecipientService {

    private final RecipientRepository recipientRepository;

    /**
     * 해외송금 수취인을 등록한다.
     * 동일 사용자가 같은 국가, 은행명, 계좌번호 조합을 중복 등록할 수 없도록 검증한다.
     */
    @Transactional
    public RecipientResponse createRecipient(Long userId, RecipientCreateRequest request) {
        validateDuplicateRecipient(userId, request);

        Recipient recipient = Recipient.create(
                userId,
                request.name(),
                request.countryCode(),
                request.currencyCode(),
                request.bankName(),
                request.accountNumber()
        );

        Recipient savedRecipient = recipientRepository.save(recipient);
        return RecipientResponse.from(savedRecipient);
    }

    /**
     * 특정 사용자가 등록한 해외송금 수취인 목록을 최신순으로 조회한다.
     */
    public List<RecipientResponse> getRecipients(Long userId) {
        return recipientRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId)
                .stream()
                .map(RecipientResponse::from)
                .toList();
    }

    /**
     * 수취인 주소록에서 특정 수취인을 Soft Delete 처리한다.
     * 수취인 정보는 수정하지 않고, 새 계좌가 필요하면 새 수취인을 등록하는 정책을 따른다.
     */
    @Transactional
    public void deleteRecipient(Long userId, Long recipientId) {
        Recipient recipient = recipientRepository.findByIdAndUserIdAndDeletedAtIsNull(recipientId, userId)
                .orElseThrow(() -> new BusinessException(RecipientErrorCode.RECIPIENT_NOT_FOUND));

        recipient.delete(LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul")));
    }

    /**
     * 동일한 해외 수취 계좌가 이미 등록되어 있는지 검증한다.
     * 중복이면 DUPLICATE_RECIPIENT 예외를 발생시킨다.
     */
    private void validateDuplicateRecipient(Long userId, RecipientCreateRequest request) {
        boolean exists = recipientRepository.existsByUserIdAndCountryCodeAndBankNameAndAccountNumberAndDeletedAtIsNull(
                userId,
                request.countryCode(),
                request.bankName(),
                request.accountNumber()
        );

        if (exists) {
            throw new BusinessException(RecipientErrorCode.DUPLICATE_RECIPIENT);
        }
    }
}
