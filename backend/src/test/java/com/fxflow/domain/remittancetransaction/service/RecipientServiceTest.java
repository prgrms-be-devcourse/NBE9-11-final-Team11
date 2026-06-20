package com.fxflow.domain.remittancetransaction.service;

import com.fxflow.domain.remittancetransaction.dto.request.RecipientCreateRequest;
import com.fxflow.domain.remittancetransaction.dto.response.RecipientResponse;
import com.fxflow.domain.remittancetransaction.entity.Recipient;
import com.fxflow.domain.remittancetransaction.errorcode.RecipientErrorCode;
import com.fxflow.domain.remittancetransaction.repository.RecipientRepository;
import com.fxflow.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecipientServiceTest {

    @Mock
    private RecipientRepository recipientRepository;

    @Mock
    private RecipientPayoutAccountService recipientPayoutAccountService;

    @InjectMocks
    private RecipientService recipientService;

    @Test
    @DisplayName("성공: 해외송금 수취인을 등록한다")
    void createRecipient_success() {
        // given
        Long userId = 1L;
        RecipientCreateRequest request = createRequest();
        Recipient recipient = createRecipient(userId);

        when(recipientRepository.existsByUserIdAndCountryCodeAndBankNameAndAccountNumberAndDeletedAtIsNull(
                userId,
                request.countryCode(),
                request.bankName(),
                request.accountNumber()
        )).thenReturn(false);
        when(recipientRepository.save(any(Recipient.class))).thenReturn(recipient);

        // when
        RecipientResponse response = recipientService.createRecipient(userId, request);

        // then
        assertThat(response.name()).isEqualTo("John Doe");
        assertThat(response.countryCode()).isEqualTo("US");
        assertThat(response.currencyCode()).isEqualTo("USD");
        assertThat(response.bankName()).isEqualTo("Chase Bank");
        assertThat(response.accountNumber()).isEqualTo("1234567890");

        verify(recipientPayoutAccountService).ensurePayoutAccount(any(Recipient.class));
        verify(recipientRepository).save(any(Recipient.class));
    }

    @Test
    @DisplayName("실패: 동일한 해외 수취 계좌가 이미 등록되어 있으면 예외가 발생한다")
    void createRecipient_fail_duplicateRecipient() {
        // given
        Long userId = 1L;
        RecipientCreateRequest request = createRequest();

        when(recipientRepository.existsByUserIdAndCountryCodeAndBankNameAndAccountNumberAndDeletedAtIsNull(
                userId,
                request.countryCode(),
                request.bankName(),
                request.accountNumber()
        )).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> recipientService.createRecipient(userId, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(RecipientErrorCode.DUPLICATE_RECIPIENT);
    }

    @Test
    @DisplayName("성공: 사용자가 등록한 수취인 목록을 최신순으로 조회한다")
    void getRecipients_success() {
        // given
        Long userId = 1L;
        Recipient recipient = createRecipient(userId);

        when(recipientRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(recipient));

        // when
        List<RecipientResponse> responses = recipientService.getRecipients(userId);

        // then
        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().name()).isEqualTo("John Doe");
        assertThat(responses.getFirst().countryCode()).isEqualTo("US");
        assertThat(responses.getFirst().currencyCode()).isEqualTo("USD");
        assertThat(responses.getFirst().bankName()).isEqualTo("Chase Bank");
        assertThat(responses.getFirst().accountNumber()).isEqualTo("1234567890");
    }

    @Test
    @DisplayName("성공: 수취인을 소프트 삭제한다")
    void deleteRecipient_success() {
        // given
        Long userId = 1L;
        Long recipientId = 1L;
        Recipient recipient = createRecipient(userId);

        when(recipientRepository.findByIdAndUserIdAndDeletedAtIsNull(recipientId, userId))
                .thenReturn(Optional.of(recipient));

        // when
        recipientService.deleteRecipient(userId, recipientId);

        // then
        assertThat(recipient.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("실패: 삭제할 수취인을 찾을 수 없으면 예외가 발생한다")
    void deleteRecipient_fail_recipientNotFound() {
        // given
        Long userId = 1L;
        Long recipientId = 1L;

        when(recipientRepository.findByIdAndUserIdAndDeletedAtIsNull(recipientId, userId))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> recipientService.deleteRecipient(userId, recipientId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(RecipientErrorCode.RECIPIENT_NOT_FOUND);
    }

    private RecipientCreateRequest createRequest() {
        return new RecipientCreateRequest(
                "John Doe",
                "US",
                "USD",
                "Chase Bank",
                "1234567890"
        );
    }

    private Recipient createRecipient(Long userId) {
        return Recipient.create(
                userId,
                "John Doe",
                "US",
                "USD",
                "Chase Bank",
                "1234567890"
        );
    }
}
