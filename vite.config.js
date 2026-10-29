import vue from "@vitejs/plugin-vue";
import tailwindcss from "@tailwindcss/vite";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { defineConfig } from "vite";

const projectRoot = path.dirname(fileURLToPath(import.meta.url));

export default defineConfig({
  plugins: [vue(), tailwindcss()],
  resolve: {
    alias: { "@": path.resolve(projectRoot, "src") },
  },
  server: {
    host: "localhost",
    port: 5173,
    strictPort: false,
    open: false,
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
        secure: false,
        timeout: 15000,
        proxyTimeout: 15000,
      },
    },
  },
  preview: { host: "localhost", port: 4173, strictPort: false },
  build: {
    outDir: "dist",
    chunkSizeWarningLimit: 900,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes("node_modules")) {
            if (id.includes("chart.js")) return "vendor-chart";
            if (id.includes("vue")) return "vendor-vue";
            return "vendor";
          }
        },
      },
    },
  },
});
