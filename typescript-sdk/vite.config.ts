import { defineConfig } from "vite";
import { resolve } from "path";
import dts from "vite-plugin-dts";
import react from "@vitejs/plugin-react";

// https://vitejs.dev/config/
export default defineConfig({
    plugins: [
        react(),
        dts({ include: ["lib"] })
    ],
    build: {
        copyPublicDir: false,
        lib: {
            fileName: "main",
            entry: resolve(__dirname, "lib/main.ts"),
            formats: ["es"]
        },
        rollupOptions: {
            external: [
                "@patternfly/patternfly",
                "@patternfly/react-core",
                "@patternfly/react-icons",
                "@patternfly/react-table",
                "react",
                "react-dom",
                "react/jsx-runtime"
            ]
        }
    }
});
