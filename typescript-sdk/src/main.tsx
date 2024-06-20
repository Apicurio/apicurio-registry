import React from "react";
import ReactDOM from "react-dom/client";
import { RegistryClientFactory } from "../lib/sdk";

const client = RegistryClientFactory.createRegistryClient("http://localhost:8080/apis/registry/v3/");
let info: any = {};
await client.system.info.get().then(i => {
    info = i;
    console.info("SYSTEM INFO: ", info);
}).catch(error => {
    console.error("Failed to connect to Registry: ", error);
});

ReactDOM.createRoot(document.getElementById("root")!).render(
    <React.StrictMode>
        <h2>Demo</h2>
        <pre>
            {
                JSON.stringify(info, null, 4)
            }
        </pre>
    </React.StrictMode>,
);
