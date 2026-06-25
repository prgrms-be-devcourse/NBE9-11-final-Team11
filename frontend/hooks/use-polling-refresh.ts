"use client"

import { useEffect, useRef } from "react"

interface PollingRefreshOptions {
  /** 기본 폴링 주기 (ms). 기본 60초. */
  intervalMs?: number
  /** 포커스 재조회 throttle 간격 (ms). 마지막 호출 후 이 시간 내면 포커스 재조회 무시. (5초 단위) */
  throttleMs?: number
  /** false 면 폴링·이벤트 구독을 모두 중단. */
  enabled?: boolean
}

/**
 * 콜백을 [주기적으로 + 화면 복귀 시] 호출하는 폴링 훅.
 *
 * - 마운트 시 1회 즉시 호출 후 intervalMs 주기로 반복
 * - 탭/창으로 다시 돌아올 때(focus / visibilitychange) 즉시 재조회
 * - 단, 마지막 호출(폴링·포커스·즉시 호출 모두 포함) 시점으로부터 5초 이내면 재조회 무시
 */
export function usePollingRefresh(
  callback: () => void,
  { intervalMs = 60_000, throttleMs = 5_000, enabled = true }: PollingRefreshOptions = {},
) {
  const callbackRef = useRef(callback)
  const lastCalledAtRef = useRef(0)

  // 최신 콜백을 ref 로 유지 — 콜백이 매 렌더 새로 만들어져도 effect 를 재설정하지 않음
  useEffect(() => {
    callbackRef.current = callback
  }, [callback])

  useEffect(() => {
    if (!enabled) return

    // 호출 시각을 기록하며 콜백 실행 (모든 호출 경로 공용)
    const run = () => {
      lastCalledAtRef.current = Date.now()
      callbackRef.current()
    }

    // 마운트 즉시 1회 + 기본 폴링(60초)
    run()
    const intervalId = setInterval(run, intervalMs)

    // 화면 복귀 시 재조회 — 단 throttleMs 이내 재호출은 무시
    const onFocusBack = () => {
      if (document.visibilityState === "hidden") return // 창을 떠나는 visibilitychange 는 무시
      if (Date.now() - lastCalledAtRef.current < throttleMs) return // 연속 호출 방어(throttle)
      run()
    }

    window.addEventListener("focus", onFocusBack)
    document.addEventListener("visibilitychange", onFocusBack)

    return () => {
      clearInterval(intervalId)
      window.removeEventListener("focus", onFocusBack)
      document.removeEventListener("visibilitychange", onFocusBack)
    }
  }, [intervalMs, throttleMs, enabled])
}
