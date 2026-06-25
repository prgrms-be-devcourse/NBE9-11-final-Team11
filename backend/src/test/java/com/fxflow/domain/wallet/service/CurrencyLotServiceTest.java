package com.fxflow.domain.wallet.service;

import com.fxflow.domain.user.entity.User;
import com.fxflow.domain.wallet.entity.CurrencyLot;
import com.fxflow.domain.wallet.entity.Wallet;
import com.fxflow.domain.wallet.errorcode.LotErrorCode;
import com.fxflow.domain.wallet.repository.CurrencyLotRepository;
import com.fxflow.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CurrencyLotServiceTest {

    @Mock
    private CurrencyLotRepository currencyLotRepository;
    @InjectMocks
    private CurrencyLotService currencyLotService;

    private Wallet usdWallet;
    private Wallet krwWallet;

    @BeforeEach
    void setUp() {
        User user = User.create("email", "password", "name");
        usdWallet = Wallet.create(user, "USD", new BigDecimal("500"));
        ReflectionTestUtils.setField(usdWallet, "id", 1L);
        krwWallet = Wallet.create(user, "KRW", new BigDecimal("500000"));
        ReflectionTestUtils.setField(krwWallet, "id", 2L);
    }

    @Test
    @DisplayName("FIFO 순서로 lot이 소비된다")
    void consumeLots_FIFO() {
        CurrencyLot lotA = CurrencyLot.create(usdWallet, "USD", new BigDecimal("100"), new BigDecimal("1300"), "tx1");
        CurrencyLot lotB = CurrencyLot.create(usdWallet, "USD", new BigDecimal("100"), new BigDecimal("1500"), "tx2");
        given(currencyLotRepository.findAvailableLotsFIFO(1L)).willReturn(List.of(lotA, lotB));

        currencyLotService.consumeLots(usdWallet, new BigDecimal("150"), new BigDecimal("1600"));

        assertThat(lotA.getRemainingQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(lotB.getRemainingQuantity()).isEqualByComparingTo(new BigDecimal("50"));
    }

    @Test
    @DisplayName("lot 소비 시 실현손익이 기록된다")
    void consumeLots_realizedProfitRecorded() {
        CurrencyLot lotA = CurrencyLot.create(usdWallet, "USD", new BigDecimal("100"), new BigDecimal("1300"), "tx1");
        CurrencyLot lotB = CurrencyLot.create(usdWallet, "USD", new BigDecimal("100"), new BigDecimal("1500"), "tx2");
        given(currencyLotRepository.findAvailableLotsFIFO(1L)).willReturn(List.of(lotA, lotB));

        currencyLotService.consumeLots(usdWallet, new BigDecimal("150"), new BigDecimal("1600"));

        // lotA fully consumed: (1600 - 1300) * 100 = 30000
        assertThat(lotA.getRealizedProfit()).isEqualByComparingTo(new BigDecimal("30000"));
        // lotB partially consumed: (1600 - 1500) * 50 = 5000
        assertThat(lotB.getRealizedProfit()).isEqualByComparingTo(new BigDecimal("5000"));
    }

    @Test
    @DisplayName("잔액 부족 시 예외 발생")
    void consumeLots_insufficientBalance() {
        CurrencyLot lot = CurrencyLot.create(usdWallet, "USD", new BigDecimal("50"), new BigDecimal("1300"), "tx1");
        given(currencyLotRepository.findAvailableLotsFIFO(1L)).willReturn(List.of(lot));

        assertThatThrownBy(() -> currencyLotService.consumeLots(usdWallet, new BigDecimal("100"), new BigDecimal("1300")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(LotErrorCode.INSUFFICIENT_LOT_BALANCE);
    }

    @Test
    @DisplayName("lot 소비 시 실현손익이 올바르게 반환된다")
    void consumeLots_returnsTotalRealizedProfit() {
        CurrencyLot lotA = CurrencyLot.create(usdWallet, "USD", new BigDecimal("100"), new BigDecimal("1300"), "tx1");
        CurrencyLot lotB = CurrencyLot.create(usdWallet, "USD", new BigDecimal("100"), new BigDecimal("1500"), "tx2");
        given(currencyLotRepository.findAvailableLotsFIFO(1L)).willReturn(List.of(lotA, lotB));

        BigDecimal realizedProfit = currencyLotService.consumeLots(usdWallet, new BigDecimal("200"), new BigDecimal("1600")).realizedProfit();

        // lotA: (1600 - 1300) * 100 = 30000
        // lotB: (1600 - 1500) * 100 = 10000
        // total = 40000
        assertThat(realizedProfit).isEqualByComparingTo(new BigDecimal("40000"));
    }

    @Test
    @DisplayName("USD→KRW settleLots: from lot 소비, to lot 미생성")
    void settleLots_usdToKrw() {
        CurrencyLot lot = CurrencyLot.create(usdWallet, "USD", new BigDecimal("100"), new BigDecimal("1300"), "tx1");
        given(currencyLotRepository.findAvailableLotsFIFO(1L)).willReturn(List.of(lot));

        currencyLotService.settleLots(usdWallet, krwWallet, new BigDecimal("100"), "tx2");

        verify(currencyLotRepository, never()).save(any(CurrencyLot.class));
        assertThat(lot.getRemainingQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("KRW→USD settleLots: from lot 미소비, to lot 생성")
    void settleLots_krwToUsd() {
        currencyLotService.settleLots(krwWallet, usdWallet, new BigDecimal("500000"), new BigDecimal("100"), new BigDecimal("1363.5"), "tx1");

        verify(currencyLotRepository).save(any(CurrencyLot.class));
        verify(currencyLotRepository, never()).findAvailableLotsFIFO(any());
    }
}