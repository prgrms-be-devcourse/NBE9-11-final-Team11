 bpackage com.fxflow.global.fx;

import java.util.Optional;

/**
 * 다른 도메인(환전·송금·알림 등)이 최신 환율을 조회하는 공통 포트.
 * fxrate 도메인이 구현하며, 소비 도메인은 fxrate 내부가 아닌 이 인터페이스에만 의존한다.
 * B(환전)·C(송금)는 외부 환율 API를 직접 호출하지 말고 반드시 이 포트를 주입받아 사용한다.
 */
public interface ExchangeRateProvider {

    // 통화쌍의 가장 최신 환율 스냅샷 조회 (저장된 환율이 없으면 empty)
    Optional<FxRateSnapshot> getLatestRate(String baseCurrency, String quoteCurrency);
}
