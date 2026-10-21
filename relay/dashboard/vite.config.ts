import path from "node:path";
import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

// Built into the relay's static assets and served at /dashboard/ by the Worker.
export default defineConfig({
	base: "/dashboard/",
	plugins: [react(), tailwindcss()],
	resolve: { alias: { "@": path.resolve(import.meta.dirname, "./src") } },
	build: { outDir: "../public/dashboard", emptyOutDir: true },
	server: { proxy: { "/oauth": "https://ai.pennywise.zynth.dev", "/mcp": "https://ai.pennywise.zynth.dev", "/authorize": "https://ai.pennywise.zynth.dev" } },
});
