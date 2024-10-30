import { defineConfig } from "vite";
import tsconfigPaths from "vite-tsconfig-paths";
import react from "@vitejs/plugin-react-swc";

// eslint-disable-next-line @typescript-eslint/ban-ts-comment
// @ts-expect-error
const PORT: number = parseInt(process.env.SERVER_PORT || "8888");

export default defineConfig({
    base: "./",
    plugins: [react(), tsconfigPaths()],
    server: {
        port: PORT
    },
    // define: {
    //     "process.platform": {}
    // }
});
