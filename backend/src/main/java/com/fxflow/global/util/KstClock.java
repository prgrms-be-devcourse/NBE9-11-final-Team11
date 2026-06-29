package com.fxflow.global.util;

import java.time.LocalDateTime;
import java.time.ZoneId;

// JVM 기본 타임존(systemDefault)에 의존하면 운영(docker, KST)과 로컬/CI(보통 UTC) 환경에서
// LocalDateTime.now()가 서로 다른 결과를 낼 수 있다. 항상 KST를 명시해 이 문제를 막는다.
public final class KstClock {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private KstClock() {
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }
}
