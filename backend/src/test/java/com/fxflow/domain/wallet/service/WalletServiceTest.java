package com.fxflow.domain.wallet.service;


import com.fxflow.domain.fxrate.service.FxRateService;
import com.fxflow.domain.wallet.dto.response.WalletBalanceResponse;
import com.fxflow.domain.wallet.entity.Wallet;
import com.fxflow.domain.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;
    @Mock
    private FxRateService fxRateService;

    @InjectMocks
    private WalletService walletService;
    private Wallet usdWallet;
    private Wallet krwWallet;

    @BeforeEach
    void setUp() throws Exception {
        usdWallet = Wallet.create(null, "USD", BigDecimal.ZERO);
        krwWallet = Wallet.create(null, "KRW", BigDecimal.ZERO);
    }

    @Test
    void getWalletBalance_success() {
        // given
        Long userId = 1L;
        when(walletRepository.findByUserId(userId))
                .thenReturn(List.of(usdWallet, krwWallet));
        when(fxRateService.getRate("USD", "KRW"))
                .thenReturn(new BigDecimal("1300"));
        when(fxRateService.getRate("KRW", "KRW"))
                .thenReturn(BigDecimal.ONE);
        // when
        WalletBalanceResponse response =
                walletService.getWalletBalance(userId);
        // then
        // 100 USD * 1300 + 50000 KRW
        // = 180000 KRW

        assertThat(response.totalKrw()).isEqualTo(180000L);
        assertThat(response.walletResponseList()).hasSize(2);
    }

    @Test
    void getWalletBalance_emptyWallets() {

        when(walletRepository.findByUserId(1L))
                .thenReturn(List.of());

        WalletBalanceResponse response =
                walletService.getWalletBalance(1L);

        assertThat(response.totalKrw()).isEqualTo(0L);
        assertThat(response.walletResponseList()).isEmpty();
    }
}