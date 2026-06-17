package com.fxflow.global.fx;

/**
 * 새 환율이 수집·저장될 때 발행되는 도메인 이벤트.
 * notification(환율 알림)·reservation(목표가 도달 체크) 등이 @EventListener로 수신한다.
 */
public record FxRateUpdatedEvent(FxRateSnapshot snapshot) {
}
