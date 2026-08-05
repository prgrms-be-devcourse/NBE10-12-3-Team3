const compactFormatter = new Intl.NumberFormat("ko-KR", { notation: "compact" });

export function formatCompact(value: number): string {
  return compactFormatter.format(value);
}

// 평균/비율 등 소수점이 나올 수 있는 통계 수치를 소수점 1자리로 통일해서 표시한다.
export function formatDecimal(value: number): string {
  return value.toFixed(1);
}
