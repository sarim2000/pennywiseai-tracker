import { useEffect, useState } from "react";

export type ThemeChoice = "system" | "light" | "dark";

/** Follows the system by default; the toggle choice is remembered in this browser only. */
export function useTheme(): [ThemeChoice, (t: ThemeChoice) => void] {
	const [choice, setChoice] = useState<ThemeChoice>(() => {
		try { return (localStorage.getItem("pw_theme") as ThemeChoice) ?? "system"; } catch { return "system"; }
	});
	useEffect(() => {
		const mq = matchMedia("(prefers-color-scheme: dark)");
		const apply = () => {
			const dark = choice === "dark" || (choice === "system" && mq.matches);
			document.documentElement.classList.toggle("dark", dark);
			document.documentElement.style.colorScheme = dark ? "dark" : "light";
		};
		apply();
		mq.addEventListener("change", apply);
		try { localStorage.setItem("pw_theme", choice); } catch { /* private mode */ }
		return () => mq.removeEventListener("change", apply);
	}, [choice]);
	return [choice, setChoice];
}
