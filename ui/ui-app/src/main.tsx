import React from "react";
import ReactDOM from "react-dom/client";
import { App } from "@app/App.tsx";
import { ConfigService, useConfigService } from "@services/useConfigService.ts";
import { configureMonacoLoader } from "@utils/monacoLoader.ts";

// eslint-disable-next-line react-hooks/rules-of-hooks
const config: ConfigService = useConfigService();
config.fetchAndMergeConfigs().then(() => {
    configureMonacoLoader(config.uiMonacoEditorUrl());
    ReactDOM.createRoot(document.getElementById("root")!).render(
        <React.Fragment>
            <App />
        </React.Fragment>
    );
});
