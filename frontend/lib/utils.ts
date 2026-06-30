import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

// 로컬 타임존 기준 YYYY-MM-DD 문자열.
// toISOString()은 UTC 기준이라 KST(UTC+9) 오전 9시 전에는 전날 날짜가 나오므로,
// 날짜 입력의 기본값·min 처럼 "오늘/지정일"을 다룰 때는 이 함수를 쓴다.
export function getLocalIsoDate(date: Date = new Date()): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, "0")
  const day = String(date.getDate()).padStart(2, "0")
  return `${year}-${month}-${day}`
}

// ISO LocalDateTime 문자열을 "MM.DD HH:mm" 형태로 표시한다. (환율 갱신 시각 표기 공용)
export function formatFetchedAt(iso: string): string {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  return d.toLocaleString("ko-KR", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  })
}
