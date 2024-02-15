import React from "react";
import ReactDOM from "react-dom/client";
import {RegistryClientFactory} from "../lib/sdk";

const factory: RegistryClientFactory = new RegistryClientFactory();
factory.test();

ReactDOM.createRoot(document.getElementById("root")!).render(
    <React.StrictMode>
        <h2>Demo</h2>
    </React.StrictMode>,
);
