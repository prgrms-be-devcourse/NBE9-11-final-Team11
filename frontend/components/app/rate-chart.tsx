"use client"

import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts"
import { rateHistory, type CurrencyCode } from "@/lib/fx-data"

export function RateChart({ code }: { code: Exclude<CurrencyCode, "KRW"> }) {
  const data = rateHistory(code)
  const values = data.map((d) => d.rate)
  const min = Math.floor(Math.min(...values) * 0.998)
  const max = Math.ceil(Math.max(...values) * 1.002)

  return (
    <ResponsiveContainer width="100%" height="100%">
      <AreaChart data={data} margin={{ top: 8, right: 8, left: 8, bottom: 0 }}>
        <defs>
          <linearGradient id={`fill-${code}`} x1="0" y1="0" x2="0" y2="1">
            <stop offset="5%" stopColor="var(--color-primary)" stopOpacity={0.25} />
            <stop offset="95%" stopColor="var(--color-primary)" stopOpacity={0} />
          </linearGradient>
        </defs>
        <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" vertical={false} />
        <XAxis
          dataKey="date"
          tickLine={false}
          axisLine={false}
          tick={{ fontSize: 11, fill: "var(--color-muted-foreground)" }}
          interval={5}
        />
        <YAxis
          domain={[min, max]}
          tickLine={false}
          axisLine={false}
          width={48}
          tick={{ fontSize: 11, fill: "var(--color-muted-foreground)" }}
        />
        <Tooltip
          contentStyle={{
            borderRadius: 12,
            border: "1px solid var(--color-border)",
            background: "var(--color-popover)",
            fontSize: 12,
          }}
          labelStyle={{ color: "var(--color-muted-foreground)" }}
          formatter={(v) => [`₩${Number(v).toLocaleString("ko-KR")}`, "환율"]}
        />
        <Area
          type="monotone"
          dataKey="rate"
          stroke="var(--color-primary)"
          strokeWidth={2}
          fill={`url(#fill-${code})`}
        />
      </AreaChart>
    </ResponsiveContainer>
  )
}
