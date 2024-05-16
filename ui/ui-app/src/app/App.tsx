import "./App.css";
import "@patternfly/patternfly/patternfly.css";
import "@patternfly/patternfly/patternfly-addons.css";

import { FunctionComponent } from "react";
import { Page } from "@patternfly/react-core";
import { BrowserRouter as Router, Route, Routes } from "react-router-dom";
import { AppHeader } from "@app/components";
import {
    ExplorePage,
    NotFoundPage,
    RootRedirectPage,
    RulesPage
} from "@app/pages";
import { RolesPage, SettingsPage } from "./pages";
import { ConfigService, useConfigService } from "@services/useConfigService.ts";
import { LoggerService, useLoggerService } from "@services/useLoggerService.ts";
import { ApplicationAuth, AuthConfig, AuthConfigContext } from "@apicurio/common-ui-components";

export type AppProps = {
    // No props
};

/**
 * The main application class.
 */
export const App: FunctionComponent<AppProps> = () => {
    const config: ConfigService = useConfigService();
    const logger: LoggerService = useLoggerService();

    const contextPath: string | undefined = config.uiContextPath();
    logger.info("[App] Using app contextPath: ", contextPath);

    const authConfig: AuthConfig = {
        type: config.authType() as "none" | "oidc",
        options: config.authOptions()
    };
    if (authConfig.type === "oidc" && (authConfig.options.redirectUri && authConfig.options.redirectUri.startsWith("/"))) {
        authConfig.options.redirectUri = window.location.origin + authConfig.options.redirectUri;
    }

    return (
        <AuthConfigContext.Provider value={authConfig}>
            <ApplicationAuth>
                <Router basename={contextPath}>
                    <Page
                        className="pf-m-redhat-font"
                        isManagedSidebar={false}
                        header={<AppHeader />}
                    >
                        <Routes>
                            <Route path="/" element={ <RootRedirectPage /> } />
                            <Route path="/rules" element={ <RulesPage /> } />
                            <Route path="/roles" element={ <RolesPage /> } />
                            <Route path="/settings" element={ <SettingsPage /> } />
                            <Route path="/explore" element={ <ExplorePage /> } />
                            <Route element={ <NotFoundPage /> } />
                        </Routes>
                    </Page>
                </Router>
            </ApplicationAuth>
        </AuthConfigContext.Provider>
    );
};
