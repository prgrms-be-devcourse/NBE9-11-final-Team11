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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

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

    // ── consumeLots ──────────────────────────────────────────────

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
    @DisplayName("lot 소비 시 각 lot에 실현손익이 기록된다")
    void consumeLots_realizedProfitRecordedOnEachLot() {
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
    @DisplayName("lot 소비 시 총 실현손익이 반환된다")
    void consumeLots_returnsTotalRealizedProfit() {
        CurrencyLot lotA = CurrencyLot.create(usdWallet, "USD", new BigDecimal("100"), new BigDecimal("1300"), "tx1");
        CurrencyLot lotB = CurrencyLot.create(usdWallet, "USD", new BigDecimal("100"), new BigDecimal("1500"), "tx2");
        given(currencyLotRepository.findAvailableLotsFIFO(1L)).willReturn(List.of(lotA, lotB));

        BigDecimal realizedProfit = currencyLotService.consumeLots(usdWallet, new BigDecimal("200"), new BigDecimal("1600"));

        // lotA: (1600 - 1300) * 100 = 30000
        // lotB: (1600 - 1500) * 100 = 10000
        // total = 40000
        assertThat(realizedProfit).isEqualByComparingTo(new BigDecimal("40000"));
    }

    @Test
    @DisplayName("잔액 부족 시 INSUFFICIENT_LOT_BALANCE 예외 발생")
    void consumeLots_insufficientBalance() {
        CurrencyLot lot = CurrencyLot.create(usdWallet, "USD", new BigDecimal("50"), new BigDecimal("1300"), "tx1");
        given(currencyLotRepository.findAvailableLotsFIFO(1L)).willReturn(List.of(lot));

        assertThatThrownBy(() -> currencyLotService.consumeLots(usdWallet, new BigDecimal("100"), new BigDecimal("1600")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(LotErrorCode.INSUFFICIENT_LOT_BALANCE);
    }

    @Test
    @DisplayName("lot 소비 후 saveAll이 호출된다")
    void consumeLots_savesAllLots() {
        CurrencyLot lot = CurrencyLot.create(usdWallet, "USD", new BigDecimal("100"), new BigDecimal("1300"), "tx1");
        given(currencyLotRepository.findAvailableLotsFIFO(1L)).willReturn(List.of(lot));

        currencyLotService.consumeLots(usdWallet, new BigDecimal("100"), new BigDecimal("1600"));

        verify(currencyLotRepository).saveAll(anyList());
    }

    // ── exchange settleLots ───────────────────────────────────────

    @Test
    @DisplayName("USD→KRW settleLots: from lot 소비, to lot 미생성")
    void settleLots_usdToKrw() {
        CurrencyLot lot = CurrencyLot.create(usdWallet, "USD", new BigDecimal("100"), new BigDecimal("1300"), "tx1");
        given(currencyLotRepository.findAvailableLotsFIFO(1L)).willReturn(List.of(lot));

        currencyLotService.settleLots(usdWallet, krwWallet, new BigDecimal("100"), new BigDecimal("130000"), new BigDecimal("1300"), "tx2");

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

    @Test
    @DisplayName("USD→KRW settleLots: 실현손익이 lot에 기록된다")
    void settleLots_usdToKrw_recordsRealizedProfit() {
        CurrencyLot lot = CurrencyLot.create(usdWallet, "USD", new BigDecimal("100"), new BigDecimal("1300"), "tx1");
        given(currencyLotRepository.findAvailableLotsFIFO(1L)).willReturn(List.of(lot));

        currencyLotService.settleLots(usdWallet, krwWallet, new BigDecimal("100"), new BigDecimal("160000"), new BigDecimal("1600"), "tx2");

        // (1600 - 1300) * 100 = 30000
        assertThat(lot.getRealizedProfit()).isEqualByComparingTo(new BigDecimal("30000"));
    }

    // ── p2p settleLots ───────────────────────────────────────────

    @Test
    @DisplayName("USD→USD p2p: sender lot 소비, receiver lot 생성")
    void settleLots_p2p_usdToUsd() {
        User recipient = User.create("r@r.com", "pw", "recipient");
        Wallet recipientWallet = Wallet.create(recipient, "USD", new BigDecimal("0"));
        ReflectionTestUtils.setField(recipientWallet, "id", 3L);

        CurrencyLot lot = CurrencyLot.create(usdWallet, "USD", new BigDecimal("100"), new BigDecimal("1300"), "tx1");
        given(currencyLotRepository.findAvailableLotsFIFO(1L)).willReturn(List.of(lot));

        currencyLotService.settleLots(usdWallet, recipientWallet, new BigDecimal("100"), "tx2");

        assertThat(lot.getRemainingQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(currencyLotRepository, times(2)).saveAll(anyList());
    }

    @Test
    @DisplayName("USD→USD p2p: receiver lot의 취득단가가 sender lot에서 상속된다")
    void settleLots_p2p_inheritsCostBasis() {
        User recipient = User.create("r@r.com", "pw", "recipient");
        Wallet recipientWallet = Wallet.create(recipient, "USD", new BigDecimal("0"));
        ReflectionTestUtils.setField(recipientWallet, "id", 3L);

        CurrencyLot lotA = CurrencyLot.create(usdWallet, "USD", new BigDecimal("100"), new BigDecimal("1300"), "tx1");
        CurrencyLot lotB = CurrencyLot.create(usdWallet, "USD", new BigDecimal("100"), new BigDecimal("1500"), "tx2");
        given(currencyLotRepository.findAvailableLotsFIFO(1L)).willReturn(List.of(lotA, lotB));

        currencyLotService.settleLots(usdWallet, recipientWallet, new BigDecimal("150"), "tx3");

        // sender: lotA fully consumed, lotB partially consumed
        assertThat(lotA.getRemainingQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(lotB.getRemainingQuantity()).isEqualByComparingTo(new BigDecimal("50"));
    }

    @Test
    @DisplayName("KRW→KRW p2p: lot 관련 동작 없음")
    void settleLots_p2p_krwToKrw() {
        currencyLotService.settleLots(krwWallet, krwWallet, new BigDecimal("100000"), "tx1");

        verify(currencyLotRepository, never()).findAvailableLotsFIFO(any());
        verify(currencyLotRepository, never()).save(any());
        verify(currencyLotRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("USD→USD p2p 잔액 부족 시 예외 발생")
    void settleLots_p2p_insufficientBalance() {
        User recipient = User.create("r@r.com", "pw", "recipient");
        Wallet recipientWallet = Wallet.create(recipient, "USD", new BigDecimal("0"));
        ReflectionTestUtils.setField(recipientWallet, "id", 3L);

        CurrencyLot lot = CurrencyLot.create(usdWallet, "USD", new BigDecimal("50"), new BigDecimal("1300"), "tx1");
        given(currencyLotRepository.findAvailableLotsFIFO(1L)).willReturn(List.of(lot));

        assertThatThrownBy(() -> currencyLotService.settleLots(usdWallet, recipientWallet, new BigDecimal("100"), "tx2"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(LotErrorCode.INSUFFICIENT_LOT_BALANCE);
    }
}