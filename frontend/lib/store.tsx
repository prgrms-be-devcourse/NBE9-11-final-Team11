"use client"

import type React from "react"
import { createContext, useContext, useEffect, useState, useCallback } from "react"
import type { CurrencyCode } from "./fx-data"

export type TxType = "deposit" | "withdraw" | "exchange" | "transfer" | "remittance"
export type TxStatus = "completed" | "processing" | "failed" | "refunded"

export interface Transaction {
  id: string
  type: TxType
  title: string
  amountKRW: number // signed: negative = outflow
  fromCurrency?: CurrencyCode
  toCurrency?: CurrencyCode
  rate?: number
  fee?: number
  status: TxStatus
  createdAt: string // ISO
  detail?: string
}

export type ReservationType = "exchange" | "remittance"
export type ReservationStatus = "ACTIVE" | "TRIGGERED" | "COMPLETED" | "CANCELED" | "FAILED" | "EXPIRED"

export interface Reservation {
  id: string
  type: ReservationType
  currency: CurrencyCode
  targetRate: number
  amountKRW: number
  status: ReservationStatus
  expiresAt: string
  createdAt: string
}

export interface Recipient {
  id: string
  name: string
  country: string
  currency: CurrencyCode
  bank: string
  account: string
}

export type NotifType = "reservation" | "trigger" | "exchange" | "remittance" | "fail"
export interface AppNotification {
  id: string
  type: NotifType
  title: string
  body: string
  createdAt: string
  read: boolean
}

export interface User {
  name: string
  email: string
  verified: boolean
}

interface StoreState {
  user: User | null
  krwBalance: number
  fxBalances: Record<CurrencyCode, number>
  transactions: Transaction[]
  reservations: Reservation[]
  recipients: Recipient[]
  notifications: AppNotification[]
  annualUsedUSD: number
}

interface StoreContextValue extends StoreState {
  login: (email: string, name?: string) => void
  logout: () => void
  setVerified: () => void
  deposit: (amount: number, bank: string, account: string) => void
  withdraw: (amount: number, bank: string, account: string) => void
  exchange: (from: CurrencyCode, to: CurrencyCode, amountKRW: number, received: number, rate: number, fee: number) => void
  remit: (recipient: Recipient, amountKRW: number, received: number, rate: number, fee: number, reason: string) => string
  addRecipient: (r: Omit<Recipient, "id">) => Recipient
  addReservation: (r: Omit<Reservation, "id" | "createdAt" | "status">) => void
  cancelReservation: (id: string) => void
  markAllRead: () => void
  ready: boolean
}

const STORAGE_KEY = "fxflow-store-v1"

const seedNotifications: AppNotification[] = [
  {
    id: "n1",
    type: "trigger",
    title: "목표 환율 도달",
    body: "USD/KRW 예약 환율 1,380원에 도달했습니다.",
    createdAt: new Date(Date.now() - 1000 * 60 * 32).toISOString(),
    read: false,
  },
  {
    id: "n2",
    type: "exchange",
    title: "자동 환전 실행",
    body: "예약된 $1,000 환전이 자동으로 실행되었습니다.",
    createdAt: new Date(Date.now() - 1000 * 60 * 60 * 5).toISOString(),
    read: false,
  },
  {
    id: "n3",
    type: "reservation",
    title: "예약 등록됨",
    body: "EUR/KRW 환전 예약이 등록되었습니다.",
    createdAt: new Date(Date.now() - 1000 * 60 * 60 * 26).toISOString(),
    read: true,
  },
]

function seedTransactions(): Transaction[] {
  const now = Date.now()
  return [
    {
      id: "t1",
      type: "deposit",
      title: "KRW 입금",
      amountKRW: 1500000,
      status: "completed",
      createdAt: new Date(now - 1000 * 60 * 60 * 2).toISOString(),
      detail: "신한은행 110-***-****12",
    },
    {
      id: "t2",
      type: "exchange",
      title: "KRW → USD 환전",
      amountKRW: -693750,
      fromCurrency: "KRW",
      toCurrency: "USD",
      rate: 1387.5,
      fee: 2775,
      status: "completed",
      createdAt: new Date(now - 1000 * 60 * 60 * 24).toISOString(),
    },
    {
      id: "t3",
      type: "remittance",
      title: "해외송금 · 미국",
      amountKRW: -1390275,
      toCurrency: "USD",
      rate: 1387.5,
      fee: 12000,
      status: "processing",
      createdAt: new Date(now - 1000 * 60 * 60 * 30).toISOString(),
      detail: "John Smith · Chase Bank",
    },
    {
      id: "t4",
      type: "exchange",
      title: "KRW → JPY 환전",
      amountKRW: -456400,
      fromCurrency: "KRW",
      toCurrency: "JPY",
      rate: 912.8,
      fee: 1825,
      status: "completed",
      createdAt: new Date(now - 1000 * 60 * 60 * 50).toISOString(),
    },
    {
      id: "t5",
      type: "withdraw",
      title: "KRW 출금",
      amountKRW: -300000,
      status: "completed",
      createdAt: new Date(now - 1000 * 60 * 60 * 72).toISOString(),
      detail: "토스뱅크 1000-****-1234",
    },
  ]
}

function seedReservations(): Reservation[] {
  const now = Date.now()
  return [
    {
      id: "r1",
      type: "exchange",
      currency: "USD",
      targetRate: 1370,
      amountKRW: 1000000,
      status: "ACTIVE",
      expiresAt: new Date(now + 1000 * 60 * 60 * 24 * 14).toISOString(),
      createdAt: new Date(now - 1000 * 60 * 60 * 20).toISOString(),
    },
    {
      id: "r2",
      type: "remittance",
      currency: "EUR",
      targetRate: 1490,
      amountKRW: 2000000,
      status: "TRIGGERED",
      expiresAt: new Date(now + 1000 * 60 * 60 * 24 * 7).toISOString(),
      createdAt: new Date(now - 1000 * 60 * 60 * 48).toISOString(),
    },
    {
      id: "r3",
      type: "exchange",
      currency: "JPY",
      targetRate: 900,
      amountKRW: 500000,
      status: "COMPLETED",
      expiresAt: new Date(now - 1000 * 60 * 60 * 24 * 2).toISOString(),
      createdAt: new Date(now - 1000 * 60 * 60 * 24 * 10).toISOString(),
    },
  ]
}

function defaultState(): StoreState {
  return {
    user: null,
    krwBalance: 1500000,
    fxBalances: { KRW: 0, USD: 1000, JPY: 50000, EUR: 0, CNY: 0 },
    transactions: seedTransactions(),
    reservations: seedReservations(),
    recipients: [
      { id: "rc1", name: "John Smith", country: "미국", currency: "USD", bank: "Chase Bank", account: "****6789" },
    ],
    notifications: seedNotifications,
    annualUsedUSD: 12000,
  }
}

const StoreContext = createContext<StoreContextValue | null>(null)

function uid(prefix: string) {
  return prefix + Math.random().toString(36).slice(2, 9)
}

export function StoreProvider({ children }: { children: React.ReactNode }) {
  const [state, setState] = useState<StoreState>(defaultState)
  const [ready, setReady] = useState(false)

  useEffect(() => {
    try {
      const raw = localStorage.getItem(STORAGE_KEY)
      if (raw) setState(JSON.parse(raw))
    } catch {
      // ignore
    }
    setReady(true)
  }, [])

  useEffect(() => {
    if (ready) localStorage.setItem(STORAGE_KEY, JSON.stringify(state))
  }, [state, ready])

  const login = useCallback((email: string, name?: string) => {
    setState((s) => ({ ...s, user: { email, name: name ?? s.user?.name ?? "사용자", verified: s.user?.verified ?? false } }))
  }, [])

  // 로그아웃 시 user뿐 아니라 잔액·거래내역·예약·수취인·알림 등 전체 상태를
  // defaultState()로 초기화한다.
  // (이전에는 user만 null로 바꿔서, 같은 브라우저에서 다른 계정으로 로그인하면
  //  이전 사용자의 잔액/거래내역이 그대로 남아있는 데이터 격리 문제가 있었음)
  const logout = useCallback(() => {
    setState(defaultState())
  }, [])

  const setVerified = useCallback(() => {
    setState((s) => ({ ...s, user: s.user ? { ...s.user, verified: true } : s.user }))
  }, [])

  const pushNotif = (s: StoreState, n: Omit<AppNotification, "id" | "createdAt" | "read">): AppNotification[] => [
    { ...n, id: uid("n"), createdAt: new Date().toISOString(), read: false },
    ...s.notifications,
  ]

  const deposit = useCallback((amount: number, bank: string, account: string) => {
    setState((s) => ({
      ...s,
      krwBalance: s.krwBalance + amount,
      transactions: [
        {
          id: uid("t"),
          type: "deposit",
          title: "KRW 입금",
          amountKRW: amount,
          status: "completed",
          createdAt: new Date().toISOString(),
          detail: `${bank} ${account}`,
        },
        ...s.transactions,
      ],
    }))
  }, [])

  const withdraw = useCallback((amount: number, bank: string, account: string) => {
    setState((s) => ({
      ...s,
      krwBalance: s.krwBalance - amount,
      transactions: [
        {
          id: uid("t"),
          type: "withdraw",
          title: "KRW 출금",
          amountKRW: -amount,
          status: "completed",
          createdAt: new Date().toISOString(),
          detail: `${bank} ${account}`,
        },
        ...s.transactions,
      ],
    }))
  }, [])

  const exchange = useCallback(
    (from: CurrencyCode, to: CurrencyCode, amountKRW: number, received: number, rate: number, fee: number) => {
      setState((s) => ({
        ...s,
        krwBalance: s.krwBalance - amountKRW - fee,
        fxBalances: { ...s.fxBalances, [to]: (s.fxBalances[to] ?? 0) + received },
        transactions: [
          {
            id: uid("t"),
            type: "exchange",
            title: `${from} → ${to} 환전`,
            amountKRW: -(amountKRW + fee),
            fromCurrency: from,
            toCurrency: to,
            rate,
            fee,
            status: "completed",
            createdAt: new Date().toISOString(),
          },
          ...s.transactions,
        ],
        notifications: pushNotif(s, {
          type: "exchange",
          title: "환전 완료",
          body: `${from} → ${to} 환전이 완료되었습니다.`,
        }),
      }))
    },
    [],
  )

  const remit = useCallback(
    (recipient: Recipient, amountKRW: number, received: number, rate: number, fee: number, reason: string): string => {
      const id = uid("t")
      setState((s) => ({
        ...s,
        krwBalance: s.krwBalance - amountKRW - fee,
        annualUsedUSD: s.annualUsedUSD + Math.round((amountKRW + fee) / 1387.5),
        transactions: [
          {
            id,
            type: "remittance",
            title: `해외송금 · ${recipient.country}`,
            amountKRW: -(amountKRW + fee),
            toCurrency: recipient.currency,
            rate,
            fee,
            status: "processing",
            createdAt: new Date().toISOString(),
            detail: `${recipient.name} · ${recipient.bank} · ${reason}`,
          },
          ...s.transactions,
        ],
        notifications: pushNotif(s, {
          type: "remittance",
          title: "송금 신청 완료",
          body: `${recipient.name}님께 송금이 신청되었습니다.`,
        }),
      }))
      return id
    },
    [],
  )

  const addRecipient = useCallback((r: Omit<Recipient, "id">): Recipient => {
    const rec = { ...r, id: uid("rc") }
    setState((s) => ({ ...s, recipients: [...s.recipients, rec] }))
    return rec
  }, [])

  const addReservation = useCallback((r: Omit<Reservation, "id" | "createdAt" | "status">) => {
    setState((s) => ({
      ...s,
      reservations: [
        { ...r, id: uid("r"), createdAt: new Date().toISOString(), status: "ACTIVE" as ReservationStatus },
        ...s.reservations,
      ],
      notifications: pushNotif(s, {
        type: "reservation",
        title: "예약 등록됨",
        body: `${r.currency} ${r.type === "exchange" ? "환전" : "송금"} 예약이 등록되었습니다.`,
      }),
    }))
  }, [])

  const cancelReservation = useCallback((id: string) => {
    setState((s) => ({
      ...s,
      reservations: s.reservations.map((r) => (r.id === id ? { ...r, status: "CANCELED" as ReservationStatus } : r)),
    }))
  }, [])

  const markAllRead = useCallback(() => {
    setState((s) => ({ ...s, notifications: s.notifications.map((n) => ({ ...n, read: true })) }))
  }, [])

  const value: StoreContextValue = {
    ...state,
    ready,
    login,
    logout,
    setVerified,
    deposit,
    withdraw,
    exchange,
    remit,
    addRecipient,
    addReservation,
    cancelReservation,
    markAllRead,
  }

  return <StoreContext.Provider value={value}>{children}</StoreContext.Provider>
}

export function useStore() {
  const ctx = useContext(StoreContext)
  if (!ctx) throw new Error("useStore must be used within StoreProvider")
  return ctx
}
