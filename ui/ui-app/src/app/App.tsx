import "./App.css";
import "@patternfly/patternfly/patternfly.css";
import "@patternfly/patternfly/patternfly-addons.css";

import { FunctionComponent } from "react";
import { BrowserRouter as Router } from "react-router";
import { ConfigService, useConfigService } from "@services/useConfigService.ts";
import { LoggerService, useLoggerService } from "@services/useLoggerService.ts";
import { MainPageWithAuth } from "@app/MainPageWithAuth.tsx";

export type AppProps = object;

/**
 * The main application class.
 */
export const App: FunctionComponent<AppProps> = () => {
    const config: ConfigService = useConfigService();
    const logger: LoggerService = useLoggerService();

    const contextPath: string | undefined = config.uiContextPath();
    logger.info("[App] Using app contextPath: ", contextPath);

    return (
        <Router basename={contextPath}>
            <MainPageWithAuth />
        </Router>
    );
};
