import { defineConfig } from "vite";
import tsconfigPaths from "vite-tsconfig-paths";
import react from "@vitejs/plugin-react-swc";

// https://vitejs.dev/config/
export default defineConfig({
    base: "",
    plugins: [react(), tsconfigPaths()],
    server: {
        port: 8888
    }
    // define: {
    //     "process.platform": {}
    // }
});
