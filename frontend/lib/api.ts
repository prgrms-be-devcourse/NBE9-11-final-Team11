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

// ── Admin Pool API Functions ────────────────────────────────────

export const getPoolDashboard = () =>
  apiRequest<PoolDashboardResponse>("GET", "/api/v1/admin/pools/dashboard")

export const triggerRebalance = (reason?: string) =>
  apiRequest<RebalanceResponse>("POST", "/api/v1/admin/pools/rebalance", reason ? { reason } : {})

export const getRebalanceHistory = () =>
  apiRequest<RebalanceHistoryItem[]>("GET", "/api/v1/admin/pools/rebalance/history")

export async function apiRequest<T>(
  method: string,
  path: string,
  body?: any
): Promise<T> {
  const url = `${BASE_URL}${path}`

  // HTTP specifications (RFC 7231) recommend against sending bodies in GET requests.
  if (method === "GET" && body) {
    throw new Error("GET requests should not have a body")
  }

  const options: RequestInit = {
    method,
    headers: {
      "Content-Type": "application/json",
    },
    credentials: "include",
  }

  if (body) {
    options.body = JSON.stringify(body)
  }

  const res = await fetch(url, options)

  if (!res.ok) {
    let errorData
    try {
      errorData = await res.json()
    } catch (e) {
      throw new Error(res.statusText || `Request failed with status ${res.status}`)
    }

    // 쿠키(JWT)가 없거나 만료/위조된 경우 백엔드는 401 + code:"UNAUTHORIZED"를 반환한다.
    // (로그인 실패 시의 401은 code:"INVALID_CREDENTIALS"라 여기서 걸리지 않는다.)
    // 이 경우 전역 이벤트를 발생시켜, SessionWatcher가 자동 로그아웃을 처리하게 한다.
    if (res.status === 401 && errorData?.code === "UNAUTHORIZED" && typeof window !== "undefined") {
      window.dispatchEvent(new CustomEvent("fxflow:session-expired"))
    }

    throw { ...errorData, status: res.status }
  }

  // Handle empty or void responses (e.g. logout)
  if (res.status === 204 || res.headers.get("Content-Length") === "0") {
    return {} as T
  }

  try {
    return await res.json()
  } catch (e) {
    return {} as T
  }
}