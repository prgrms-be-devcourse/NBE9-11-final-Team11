package com.fxflow.domain.wallet.service;

import com.fxflow.domain.wallet.dto.request.ExchangeQuoteRequest;
import com.fxflow.domain.wallet.dto.request.ExchangeRequest;
import com.fxflow.domain.wallet.dto.response.ExchangeQuoteResponse;
import com.fxflow.domain.wallet.dto.response.ExchangeResponse;
import com.fxflow.domain.wallet.repository.ExchangeTransactionRepository;
import com.fxflow.global.exchange.ExchangeExecutionCommand;
import com.fxflow.global.exchange.ExchangeExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 즉시 환전 체결 포트({@link ExchangeExecutor})의 wallet 도메인 구현
 * 호출 시점의 시세로 견적을 만들어 그대로 체결하고, 생성된 환전 거래의 PK 를 반환
 *
 * 독립 트랜잭션(REQUIRES_NEW)으로 실행 — 호출 측(예약 체결) 트랜잭션과 분리되어,
 * 잔액 부족 등으로 환전이 실패하면 이 트랜잭션만 롤백되고 호출 측은 실패를 기록할 수 있음.
 */
@Service
@RequiredArgsConstructor
public class WalletExchangeExecutor implements ExchangeExecutor {

    private final ExchangeService exchangeService;
    private final ExchangeTransactionRepository exchangeTransactionRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long execute(ExchangeExecutionCommand command) {
        ExchangeQuoteResponse quote = exchangeService.getExchangeQuote(
                command.userId(),
                new ExchangeQuoteRequest(command.fromCurrency(), command.toCurrency(), command.amount()));

        ExchangeResponse result = exchangeService.exchange(
                command.userId(),
                new ExchangeRequest(quote.quoteId()));

        // ExchangeResponse 는 비즈니스 식별자(String)만 노출하므로, 거래 PK(Long)는 조회로 확보
        return exchangeTransactionRepository
                .findAllByTransactionIdIn(List.of(result.transactionId()))
                .getFirst()
                .getId();
    }
}
