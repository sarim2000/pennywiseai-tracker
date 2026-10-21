import { useEffect, useState } from "react";
import { tool } from "./api";

type State<T> = { data?: T; error?: Error; loading: boolean };

// One in-memory cache per page load: switching tabs or periods back doesn't ask the phone again.
const cache = new Map<string, Promise<unknown>>();
export const clearCache = () => cache.clear();

/** Calls a tool when its arguments change. Pass `null` to skip. */
export function useTool<T>(name: Parameters<typeof tool>[0], args: object | null, version = 0): State<T> {
	const key = args ? `${version}|${name}|${JSON.stringify(args)}` : null;
	const [state, setState] = useState<State<T>>({ loading: key !== null });
	useEffect(() => {
		if (!key) return;
		let live = true;
		setState((s) => ({ data: s.data, loading: true }));
		let p = cache.get(key);
		if (!p) {
			p = tool(name, args as never);
			cache.set(key, p);
			p.catch(() => cache.delete(key));
		}
		p.then(
			(data) => live && setState({ data: data as T, loading: false }),
			(error: Error) => live && setState({ error, loading: false }),
		);
		return () => { live = false; };
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [key]);
	return state;
}
