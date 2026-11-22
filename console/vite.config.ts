import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/identity": {
        target: "http://localhost:8085",
        rewrite: (path) => path.replace(/^\/identity/, ""),
      },
      "/reporting": {
        target: "http://localhost:8089",
        rewrite: (path) => path.replace(/^\/reporting/, ""),
      },
      "/api": {
        target: "http://localhost:8081",
      },
    },
  },
});
