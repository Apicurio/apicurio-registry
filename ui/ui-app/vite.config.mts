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
        server: {
            deps: {
                inline: ["@patternfly/react-styles"]
            }
        }
    },
    // define: {
    //     "process.platform": {}
    // }
});
