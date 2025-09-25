import "./App.css";
import "@patternfly/patternfly/patternfly.css";
import "@patternfly/patternfly/patternfly-addons.css";

import { FunctionComponent } from "react";
import { Page } from "@patternfly/react-core";
import { BrowserRouter as Router, Route, Routes } from "react-router-dom";
import { AppHeader } from "@app/components";
import {
    ArtifactPage,
    BranchPage, DraftsPage, EditorPage,
    ExplorePage,
    GroupPage,
    NotFoundPage,
    RootRedirectPage,
    RulesPage,
    SearchPage,
    VersionPage
} from "@app/pages";
import { RolesPage, SettingsPage } from "./pages";
import { ConfigService, useConfigService } from "@services/useConfigService.ts";
import { LoggerService, useLoggerService } from "@services/useLoggerService.ts";
import { ApplicationAuth, AuthConfig, AuthConfigContext } from "@apicurio/common-ui-components";

export type AppProps = object;

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
    if (authConfig.type === "oidc") {
        // Only set redirectUri as fallback if not already configured
        if (!authConfig.options.redirectUri && window.location?.href) {
            authConfig.options.redirectUri = window.location.href;
        } else if (authConfig.options.redirectUri && authConfig.options.redirectUri.startsWith("/")) {
            authConfig.options.redirectUri = window.location.origin + authConfig.options.redirectUri;
        }
        if (authConfig.options.logoutUrl && authConfig.options.logoutUrl.startsWith("/")) {
            authConfig.options.logoutUrl = window.location.origin + authConfig.options.logoutUrl;
        }
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
                            <Route path="/search" element={ <SearchPage /> } />
                            <Route path="/drafts" element={ <DraftsPage /> } />
                            <Route path="/explore" element={ <ExplorePage /> } />

                            <Route
                                path="/explore/:groupId"
                                element={ <GroupPage /> }
                            />
                            <Route
                                path="/explore/:groupId/rules"
                                element={ <GroupPage /> }
                            />

                            <Route
                                path="/explore/:groupId/:artifactId"
                                element={ <ArtifactPage /> }
                            />
                            <Route
                                path="/explore/:groupId/:artifactId/rules"
                                element={ <ArtifactPage /> }
                            />
                            <Route
                                path="/explore/:groupId/:artifactId/branches"
                                element={ <ArtifactPage /> }
                            />

                            <Route
                                path="/explore/:groupId/:artifactId/versions/:version"
                                element={ <VersionPage /> }
                            />
                            <Route
                                path="/explore/:groupId/:artifactId/versions/:version/:editor"
                                element={ <EditorPage /> }
                            />
                            <Route
                                path="/explore/:groupId/:artifactId/versions/:version/content"
                                element={ <VersionPage /> }
                            />
                            <Route
                                path="/explore/:groupId/:artifactId/versions/:version/documentation"
                                element={ <VersionPage /> }
                            />
                            <Route
                                path="/explore/:groupId/:artifactId/versions/:version/references"
                                element={ <VersionPage /> }
                            />

                            <Route
                                path="/explore/:groupId/:artifactId/branches/:branchId"
                                element={ <BranchPage /> }
                            />
                            <Route
                                path="/explore/:groupId/:artifactId/branches/:branchId/versions"
                                element={ <BranchPage /> }
                            />


                            <Route element={ <NotFoundPage /> } />
                        </Routes>
                    </Page>
                </Router>
            </ApplicationAuth>
        </AuthConfigContext.Provider>
    );
};
