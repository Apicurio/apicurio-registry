import { defineConfig } from "vitest/config";
import tsconfigPaths from "vite-tsconfig-paths";
import react from "@vitejs/plugin-react-swc";

const PORT: number = parseInt(process.env.SERVER_PORT || "8888");

export default defineConfig({
    base: "./",
    plugins: [react(), tsconfigPaths()],
    server: {
        port: PORT
    },
    test: {
        environment: "node",
        include: ["src/**/*.test.ts", "src/**/*.test.tsx"],
        setupFiles: ["./src/setupTests.ts"],
        alias: [
            {
                find: /.*apicurioRegistryClient\.js$/,
                replacement: "../../typescript-sdk/lib/generated-client/apicurioRegistryClient.ts"
            }
        ]
    },
    // define: {
    //     "process.platform": {}
    // }
});
