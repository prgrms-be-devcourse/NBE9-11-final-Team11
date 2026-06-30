"use client"

import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts"

export interface RateChartPoint {
  label: string // X축 표시 라벨 (기간별 포맷은 호출부에서 결정)
  rate: number
}

export function RateChart({ data }: { data: RateChartPoint[] }) {
  // 조회 기간 내 데이터가 없으면(빈 배열) 차트 대신 안내 문구를 보여 차트가 깨지지 않게 한다.
  if (data.length === 0) {
    return (
      <div className="flex h-full w-full items-center justify-center text-sm text-muted-foreground">
        표시할 데이터가 없습니다
      </div>
    )
  }

  const values = data.map((d) => d.rate)
  const min = Math.floor(Math.min(...values) * 0.998)
  const max = Math.ceil(Math.max(...values) * 1.002)

  return (
    <ResponsiveContainer width="100%" height="100%">
      <AreaChart data={data} margin={{ top: 8, right: 8, left: 8, bottom: 0 }}>
        <defs>
          <linearGradient id="fill-rate" x1="0" y1="0" x2="0" y2="1">
            <stop offset="5%" stopColor="var(--color-primary)" stopOpacity={0.25} />
            <stop offset="95%" stopColor="var(--color-primary)" stopOpacity={0} />
          </linearGradient>
        </defs>
        <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" vertical={false} />
        <XAxis
          dataKey="label"
          tickLine={false}
          axisLine={false}
          tick={{ fontSize: 11, fill: "var(--color-muted-foreground)" }}
          // 포인트 수가 기간별로 크게 달라(1D ~720 ~ 1M ~30) 라벨 간격을 자동 조정
          interval="preserveStartEnd"
          minTickGap={40}
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
        <Area type="monotone" dataKey="rate" stroke="var(--color-primary)" strokeWidth={2} fill="url(#fill-rate)" />
      </AreaChart>
    </ResponsiveContainer>
  )
}
