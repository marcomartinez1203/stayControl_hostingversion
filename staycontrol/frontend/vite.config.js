import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

export default defineConfig({
    plugins: [vue()],
    publicDir: ".public",
    build: { outDir: "dist", emptyOutDir: true, assetsDir: "vue-assets" },
    server: { proxy: { "/api": "http://127.0.0.1:8080" } }
});
