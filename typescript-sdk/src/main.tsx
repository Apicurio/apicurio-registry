import React from "react";
import ReactDOM from "react-dom/client";
import { RegistryClientFactory } from "../lib/sdk/factory.ts";

const client = RegistryClientFactory.createRegistryClient("http://localhost:8080/apis/registry/v3/");
await client.system.info.get().then(info => {
    console.info("SYSTEM INFO: ", info);
}).catch(error => {
    console.error("Failed to connect to Registry: ", error);
});

ReactDOM.createRoot(document.getElementById("root")!).render(
    <React.StrictMode>
        <h2>Demo</h2>
    </React.StrictMode>,
);
