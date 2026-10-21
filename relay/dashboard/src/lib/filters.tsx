import { createContext, useContext } from "react";
import type { Range } from "./api";

export type Tab = "overview" | "analytics" | "transactions" | "accounts";

export type Filters = {
	range: Range;
	previous: Range;
	/** The currency charts are drawn in. Totals elsewhere show every currency separately. */
	currency: string;
	currencies: string[];
	version: number;
	/** Opens Transactions filtered to one category or merchant. */
	drill: (f: { category?: string; query?: string }) => void;
	drilled: { category?: string; query?: string };
	setTab: (t: Tab) => void;
};

export const FiltersContext = createContext<Filters | null>(null);
export const useFilters = () => {
	const f = useContext(FiltersContext);
	if (!f) throw new Error("useFilters outside FiltersContext");
	return f;
};
