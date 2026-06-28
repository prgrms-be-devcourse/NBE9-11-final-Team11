const BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080"

// ── Admin Pool Types ────────────────────────────────────────────

export type PoolStatus = "NORMAL" | "BELOW_FLOOR" | "ABOVE_CEILING"

export interface RecommendedAction {
  type: "BUY" | "SELL"
  amount: number
  counterAmount: number | null
}

export interface PoolStatusRes {
  currencyCode: "KRW" | "USD"
  balance: number
  targetBalance: number
  floorBalance: number
  safeFloorBalance: number
  ceilingBalance: number
  status: PoolStatus
  utilizationRate: number
  recommendedAction: RecommendedAction | null
}

export interface PoolDashboardResponse {
  asOf: string
  pools: PoolStatusRes[]
}

export interface RebalancingAction {
  buyCurrency: "KRW" | "USD"
  sellCurrency: "KRW" | "USD"
  buyAmount: number
  sellAmount: number
  midRate: number
  appliedRate: number
  status: string
  triggerType: string
}

export interface RebalanceResponse {
  executed: boolean
  action: RebalancingAction | null
  cappedBy: string | null
  reason: string | null
}

export interface RebalanceHistoryItem {
  id: number
  buyCurrency: "KRW" | "USD"
  sellCurrency: "KRW" | "USD"
  buyAmount: number
  sellAmount: number
  midRate: number
  appliedRate: number
  status: string
  cappedBy: string | null
  triggerType: string
  reason: string | null
  executedAt: string
}

export interface ApiError {
  status: number
  code: string
  message: string
}

// ── FxRate (환율) API ────────────────────────────────────────────

export interface FxRateLatest {
  baseCurrency: string
  quoteCurrency: string
  midRate: number
  buyRate: number
  sellRate: number
  fetchedAt: string // ISO-8601 LocalDateTime (예: "2026-06-21T12:34:56")
}

// ── Unified Transaction History API ─────────────────────────────

export type TransactionHistoryItemType = "CHARGE" | "WITHDRAW" | "EXCHANGE" | "P2P_TRANSFER" | "REMITTANCE"
export type LedgerEntryType = "CHARGE" | "WITHDRAW" | "EXCHANGE" | "TRANSFER"
export type LedgerDirection = "DEBIT" | "CREDIT"

interface TransactionHistoryBaseItem {
  transactionType: TransactionHistoryItemType
  journalId: string
  type: LedgerEntryType
  direction: LedgerDirection
  currency: string
  createdAt: string
}

interface SimpleTransactionHistoryItem extends TransactionHistoryBaseItem {
  refType: string
  amount: number
  balanceAfter: number
}

export interface ChargeTransactionHistoryItem extends SimpleTransactionHistoryItem {
  transactionType: "CHARGE"
}

export interface WithdrawTransactionHistoryItem extends SimpleTransactionHistoryItem {
  transactionType: "WITHDRAW"
}

export interface ExchangeTransactionHistoryItem extends TransactionHistoryBaseItem {
  transactionType: "EXCHANGE"
  fromCurrency: string
  toCurrency: string
  fromAmount: number
  toAmount: number
  exchangeRate: number
  feeAmount: number
}

export interface P2pTransferTransactionHistoryItem extends TransactionHistoryBaseItem {
  transactionType: "P2P_TRANSFER"
  refType: string
  amount: number
  counterpartyEmail: string
  memo?: string
}

export interface RemittanceTransactionHistoryItem extends TransactionHistoryBaseItem {
  transactionType: "REMITTANCE"
  refType: string
  amount: number
  balanceAfter: number
  remittanceId: number
  recipientName: string
  recipientBankName: string
  recipientAccountNumber: string
  status: string
  receiveAmount: number
  receiveCurrency: string
}

export type TransactionHistoryItem =
  | ChargeTransactionHistoryItem
  | WithdrawTransactionHistoryItem
  | ExchangeTransactionHistoryItem
  | P2pTransferTransactionHistoryItem
  | RemittanceTransactionHistoryItem

export interface UnifiedTransactionHistoryResponse {
  data: TransactionHistoryItem[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface TransactionHistoryParams {
  page?: number
  size?: number
  currency?: string
  type?: LedgerEntryType
  from?: string
  to?: string
}

// ── Admin Transaction Types ─────────────────────────────────────

export type AdminSourceType = "LEDGER" | "REBALANCING"
export type AdminDirection = "DEBIT" | "CREDIT"
export type AdminAccountRole = "WALLET" | "BANK" | "KRW_POOL" | "USD_POOL"

export interface AdminTransactionItem {
  id: number
  sourceType: AdminSourceType
  subType: string
  createdAt: string
  amount: number | null
  currencyCode: string | null
  journalId: string | null
  triggerType: string | null
  direction: AdminDirection | null
  accountRole: AdminAccountRole | null
  krwPoolChange: number | null
  usdPoolChange: number | null
}

export interface AdminTransactionPageResponse {
  data: AdminTransactionItem[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface AdminTransactionParams {
  page?: number
  size?: number
  from?: string
  to?: string
}

// ── Admin Pool API Functions ────────────────────────────────────

export const getPoolDashboard = () =>
  apiRequest<PoolDashboardResponse>("GET", "/api/v1/admin/pools/dashboard")

export const triggerRebalance = (reason?: string) =>
  apiRequest<RebalanceResponse>("POST", "/api/v1/admin/pools/rebalance", reason ? { reason } : {})

export const getRebalanceHistory = () =>
  apiRequest<RebalanceHistoryItem[]>("GET", "/api/v1/admin/pools/rebalance/history")

// 최신 매매기준율 조회 (기본 USD/KRW). 데이터가 없으면 404를 던진다.
export const getLatestRate = (base = "USD", quote = "KRW") =>
  apiRequest<FxRateLatest>("GET", `/api/v1/fxrates/latest?base=${base}&quote=${quote}`)

export const getAdminTransactions = (params: AdminTransactionParams = {}) => {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") search.set(key, String(value))
  })
  const query = search.toString()
  return apiRequest<AdminTransactionPageResponse>("GET", `/api/v1/admin/transactions${query ? `?${query}` : ""}`)
}

export const getTransactionHistory = (params: TransactionHistoryParams = {}) => {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") {
      search.set(key, String(value))
    }
  })

  const query = search.toString()
  return apiRequest<UnifiedTransactionHistoryResponse>("GET", `/api/v1/transactions${query ? `?${query}` : ""}`)
}

// ── 핵심: fetch 단일 실행 ───────────────────────────────────────

async function fetchOnce(
  method: string,
  path: string,
  body?: any,
  headers?: Record<string, string>
): Promise<Response> {
  const url = `${BASE_URL}${path}`
  const options: RequestInit = {
    method,
    headers: {
      "Content-Type": "application/json",
      ...headers,
    },
    credentials: "include",
  }
  if (body) options.body = JSON.stringify(body)
  return fetch(url, options)
}

// ── Refresh Token으로 Access Token 재발급 ──────────────────────
// 동시에 여러 요청이 401을 받아도 /refresh는 딱 1번만 호출되도록 Promise 공유.
// 같은 탭 안에서는 refreshPromise로 막지만, 그것만으로는 "여러 탭"이 동시에
// 같은 refreshToken으로 /refresh를 날리는 건 막을 수 없다 (탭마다 별도의 JS 메모리).
// 백엔드는 Refresh Token Rotation으로 동작해서, 같은 RT로 동시에 두 요청이 오면
// 하나만 성공시키고 나머지는 "재사용 탐지"로 간주해 해당 유저를 강제 로그아웃시킨다.
// 즉 탭이 여러 개 열려있는 정상 사용자도 잘못 로그아웃될 수 있으므로,
// Web Locks API(navigator.locks)로 탭 간에도 refresh를 한 번에 하나씩만 실행한다.
// 미지원 브라우저에서는 같은 탭 내 중복 방지만 적용된다 (기존 동작과 동일).

const REFRESH_LOCK_NAME = "fxflow-refresh-lock"

let refreshPromise: Promise<boolean> | null = null

async function callRefreshEndpoint(): Promise<boolean> {
  try {
    const res = await fetch(`${BASE_URL}/api/v1/auth/refresh`, {
      method: "POST",
      credentials: "include", // refreshToken 쿠키 자동 포함
    })
    return res.ok
  } catch {
    return false
  }
}

async function tryRefresh(): Promise<boolean> {
  // 이미 refresh 진행 중이면 같은 Promise 반환 (탭 내 중복 호출 방지)
  if (refreshPromise) return refreshPromise

  const hasWebLocks = typeof navigator !== "undefined" && "locks" in navigator

  refreshPromise = (
    hasWebLocks
      // 탭 간에도 한 번에 하나씩만 /refresh를 실행 (RT 동시 회전 race 방지)
      ? navigator.locks.request(REFRESH_LOCK_NAME, () => callRefreshEndpoint())
      : callRefreshEndpoint()
  ).finally(() => {
    // refresh 완료 후 초기화
    refreshPromise = null
  })

  return refreshPromise
}

// ── 세션 만료 이벤트 중복 방지 ─────────────────────────────────
// 동시 요청이 여러 개 실패해도 session-expired 이벤트는 1번만 발생

let sessionExpiredFired = false

function fireSessionExpired() {
  if (typeof window === "undefined") return
  if (sessionExpiredFired) return

  sessionExpiredFired = true
  window.dispatchEvent(new CustomEvent("fxflow:session-expired"))

  // 5초 후 초기화 — 재로그인 후 다시 감지할 수 있도록
  setTimeout(() => {
    sessionExpiredFired = false
  }, 5000)
}

// ── 응답 파싱 ──────────────────────────────────────────────────

async function parseResponse<T>(res: Response): Promise<T> {
  if (res.status === 204 || res.headers.get("Content-Length") === "0") {
    return {} as T
  }
  try {
    return await res.json()
  } catch {
    return {} as T
  }
}

// ── 메인 apiRequest ────────────────────────────────────────────

export async function apiRequest<T>(
  method: string,
  path: string,
  body?: any,
  headers?: Record<string, string>
): Promise<T> {
  if (method === "GET" && body) {
    throw new Error("GET requests should not have a body")
  }

  const res = await fetchOnce(method, path, body, headers)

  // 401 + UNAUTHORIZED → Refresh Token으로 재발급 시도
  // (로그인 실패 시의 401은 code:"INVALID_CREDENTIALS"라 여기서 걸리지 않는다.)
  if (res.status === 401) {
    let errorData: any = {}
    try {
      errorData = await res.clone().json()
    } catch {}

    if (errorData?.code === "UNAUTHORIZED") {
      const refreshed = await tryRefresh()

      if (refreshed) {
        // 재발급 성공 → 원래 요청 재시도
        const retryRes = await fetchOnce(method, path, body, headers)
        if (retryRes.ok) return parseResponse<T>(retryRes)

        // 재시도도 실패
        let retryError: any = {}
        try {
          retryError = await retryRes.json()
        } catch {}
        const err = new Error(retryError?.message ?? "요청에 실패했습니다.")
        Object.assign(err, retryError, { status: retryRes.status })
        throw err
      }

      // Refresh도 실패 → 세션 만료 이벤트 발생 (중복 방지)
      // SessionWatcher가 감지해서 다이얼로그 표시 후 로그인 페이지로 이동
      fireSessionExpired()

      // throw하지 않고 pending 상태로 두어 각 페이지의 에러 핸들러가
      // 토스트를 띄우지 않도록 함 — SessionWatcher가 처리
      return new Promise(() => {})
    }

    // UNAUTHORIZED 외의 401 (로그인 실패 등)은 세션 만료 처리 안 함
    const error = new Error(errorData?.message || res.statusText)
    Object.assign(error, errorData, { status: res.status })
    throw error
  }

  if (!res.ok) {
    let errorData: any = {}
    try {
      errorData = await res.json()
    } catch {}
    const message = errorData?.message || errorData?.code || res.statusText
    const error = new Error(message)
    Object.assign(error, errorData, { status: res.status })
    throw error
  }

  return parseResponse<T>(res)
}
