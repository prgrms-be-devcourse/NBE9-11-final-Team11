"use client"

import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts"

const tooltipStyle = {
  borderRadius: 12,
  border: "1px solid var(--color-border)",
  background: "var(--color-popover)",
  fontSize: 12,
}

/** Pool balance trend (in 억원) */
export function PoolBalanceChart({
  data,
  floor,
  ceiling,
}: {
  data: { date: string; balance: number }[]
  floor: number
  ceiling: number
}) {
  const values = data.map((d) => d.balance)
  const min = Math.floor(Math.min(...values, floor) * 0.95)
  const max = Math.ceil(Math.max(...values, ceiling) * 1.05)

  return (
    <ResponsiveContainer width="100%" height="100%">
      <AreaChart data={data} margin={{ top: 8, right: 8, left: 8, bottom: 0 }}>
        <defs>
          <linearGradient id="fill-pool" x1="0" y1="0" x2="0" y2="1">
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
          interval={4}
        />
        <YAxis
          domain={[min, max]}
          tickLine={false}
          axisLine={false}
          width={40}
          tick={{ fontSize: 11, fill: "var(--color-muted-foreground)" }}
        />
        <Tooltip
          contentStyle={tooltipStyle}
          labelStyle={{ color: "var(--color-muted-foreground)" }}
          formatter={(v) => [`${Number(v).toLocaleString("ko-KR")}억원`, "잔고"]}
        />
        <ReferenceLine y={ceiling} stroke="var(--color-chart-3)" strokeDasharray="4 4" />
        <ReferenceLine y={floor} stroke="var(--color-destructive)" strokeDasharray="4 4" />
        <Area type="monotone" dataKey="balance" stroke="var(--color-primary)" strokeWidth={2} fill="url(#fill-pool)" />
      </AreaChart>
    </ResponsiveContainer>
  )
}

/** Recent rebalancing events (signed 억원: + inflow, - outflow) */
export function RebalancingChart({ data }: { data: { date: string; amount: number }[] }) {
  return (
    <ResponsiveContainer width="100%" height="100%">
      <BarChart data={data} margin={{ top: 8, right: 8, left: 8, bottom: 0 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" vertical={false} />
        <XAxis
          dataKey="date"
          tickLine={false}
          axisLine={false}
          tick={{ fontSize: 11, fill: "var(--color-muted-foreground)" }}
        />
        <YAxis
          tickLine={false}
          axisLine={false}
          width={40}
          tick={{ fontSize: 11, fill: "var(--color-muted-foreground)" }}
        />
        <Tooltip
          contentStyle={tooltipStyle}
          cursor={{ fill: "var(--color-secondary)" }}
          formatter={(v) => [`${Number(v) >= 0 ? "+" : ""}${Number(v).toLocaleString("ko-KR")}억원`, "리밸런싱"]}
        />
        <ReferenceLine y={0} stroke="var(--color-border)" />
        <Bar dataKey="amount" radius={[4, 4, 4, 4]}>
          {data.map((d, i) => (
            <Cell key={i} fill={d.amount >= 0 ? "var(--color-accent)" : "var(--color-destructive)"} />
          ))}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  )
}
