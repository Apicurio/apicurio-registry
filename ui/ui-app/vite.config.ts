import { defineConfig } from "vite";
import tsconfigPaths from "vite-tsconfig-paths";
import react from "@vitejs/plugin-react-swc";

const PORT: number = parseInt(process.env.SERVER_PORT || "8888");

export default defineConfig({
    plugins: [react(), tsconfigPaths()],
    server: {
        port: PORT
    },
    // START: To use npm link
    // optimizeDeps: {
    //     exclude: ['@apicurio/common-ui-components'],
    // },
    // END: To use npm link
    // define: {
    //     "process.platform": {}
    // }
});
