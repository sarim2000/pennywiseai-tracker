import type { Group, Money, Range } from "./api";

/** Formats one amount in its own currency. Amounts in different currencies are never added together. */
export function fmt(m: Money | undefined, compact = false): string {
	if (!m) return "—";
	try {
		return new Intl.NumberFormat(undefined, {
			style: "currency", currency: m.currency, notation: compact ? "compact" : "standard", maximumFractionDigits: compact ? 1 : 2,
		}).format(Number(m.amount));
	} catch {
		return `${m.currency} ${m.amount}`;
	}
}

export const amountIn = (totals: Money[], currency: string) => Number(totals.find((t) => t.currency === currency)?.amount ?? 0);

/** Currencies present, most-used first (by how many groups carry them). */
export function currenciesOf(groups: Group[]): string[] {
	const n: Record<string, number> = {};
	for (const g of groups) for (const t of g.totals) n[t.currency] = (n[t.currency] ?? 0) + 1;
	return Object.keys(n).sort((a, b) => n[b] - n[a]);
}

/** Percentage change, or null when there is nothing to compare with. */
export const change = (now: number, before: number) => (before === 0 ? null : ((now - before) / before) * 100);

// ── Date ranges ─────────────────────────────────────────────────────────
const iso = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
const parse = (s: string) => { const [y, m, d] = s.split("-").map(Number); return new Date(y, m - 1, d); };

export type Preset = "this-month" | "last-month" | "3m" | "6m" | "12m" | "ytd" | "custom";
export const PRESETS: { id: Exclude<Preset, "custom">; label: string }[] = [
	{ id: "this-month", label: "This month" },
	{ id: "last-month", label: "Last month" },
	{ id: "3m", label: "Last 3 months" },
	{ id: "6m", label: "Last 6 months" },
	{ id: "12m", label: "Last 12 months" },
	{ id: "ytd", label: "This year" },
];

export function presetRange(p: Exclude<Preset, "custom">, now = new Date()): Range {
	const y = now.getFullYear(), m = now.getMonth();
	const d = (yy: number, mm: number, dd: number) => new Date(yy, mm, dd);
	switch (p) {
		case "this-month": return { from: iso(d(y, m, 1)), to: iso(now) };
		case "last-month": return { from: iso(d(y, m - 1, 1)), to: iso(d(y, m, 0)) };
		case "6m": return { from: iso(d(y, m - 5, 1)), to: iso(now) };
		case "12m": return { from: iso(d(y, m - 11, 1)), to: iso(now) };
		case "ytd": return { from: iso(d(y, 0, 1)), to: iso(now) };
		default: return { from: iso(d(y, m - 2, 1)), to: iso(now) };
	}
}

/** The period of the same length just before `r`, for comparisons. */
export function previousRange(r: Range): Range {
	const from = parse(r.from), to = parse(r.to);
	const days = Math.round((to.getTime() - from.getTime()) / 86_400_000) + 1;
	const prevTo = new Date(from); prevTo.setDate(prevTo.getDate() - 1);
	const prevFrom = new Date(prevTo); prevFrom.setDate(prevFrom.getDate() - days + 1);
	return { from: iso(prevFrom), to: iso(prevTo) };
}

export const monthLabel = (ym: string) => parse(`${ym}-01`).toLocaleDateString(undefined, { month: "short", year: "2-digit" });
export const dayLabel = (ymd: string) => parse(ymd).toLocaleDateString(undefined, { day: "numeric", month: "short" });
export const rangeLabel = (r: Range) =>
	`${parse(r.from).toLocaleDateString(undefined, { day: "numeric", month: "short", year: "numeric" })} – ${parse(r.to).toLocaleDateString(undefined, { day: "numeric", month: "short", year: "numeric" })}`;
