"use client"

import { useState, useEffect, useCallback } from "react"
import { getAdminTransactions, AdminTransactionPageResponse } from "@/lib/api"

export function useAdminTransactions(params: {
  page: number
  size: number
  from: string
  to: string
}) {
  const [data, setData] = useState<AdminTransactionPageResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const { page, size, from, to } = params

  const fetch = useCallback(() => {
    setLoading(true)
    setError(null)
    getAdminTransactions({ page, size, from: from || undefined, to: to || undefined })
      .then(setData)
      .catch((e: Error) => setError(e.message ?? "조회 실패"))
      .finally(() => setLoading(false))
  }, [page, size, from, to])

  useEffect(() => {
    fetch()
  }, [fetch])

  return { data, loading, error, refetch: fetch }
}
