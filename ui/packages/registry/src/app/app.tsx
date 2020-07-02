/**
 * @license
 * Copyright 2020 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import React from "react";
import {Page} from "@patternfly/react-core";
import {BrowserRouter as Router, Route, Switch} from 'react-router-dom';
import {AppHeader} from "./components/header";
import {RulesPage} from "./pages/rules";
import {ArtifactsPage} from "./pages/artifacts";
import {ArtifactRedirectPage} from "./pages/artifact";
import {ArtifactVersionPage} from "./pages/artifactVersion";
import {Services} from "@apicurio/registry-services";
import {RootRedirectPage} from "./pages/root";
import {NotFoundPage} from "./pages/404";
import Keycloak from 'keycloak-js';


/**
 * The main application class.
 */
export default class App extends React.PureComponent<{}, {}> {

    constructor(props: Readonly<any>) {
        super(props);
        this.state = {keycloak: null, authenticated: false};
    }

    componentDidMount() {
        let initOptions = {
            url: Services.getConfigService().authUrl(), realm: Services.getConfigService().authRealm(), clientId: Services.getConfigService().authClientId(), onLoad: Services.getConfigService().authOnLoad()
        }
        const keycloak = Keycloak(initOptions);
        keycloak.init({onLoad: 'login-required'}).then(authenticated => {
            this.setState({keycloak: keycloak, authenticated: authenticated})
        })
    }

    public render() {
        const contextPath: string | undefined = Services.getConfigService().uiContextPath();
        Services.getLoggerService().info("Using app contextPath: ", contextPath);

        // Function to force the Artifact Version Page to fully remount each time we navigate to it.  This
        // is needed because we want the page to fully rerender whenever the browser location changes, which
        // happens when switching between versions of the artifact content (e.g. switch from version 1 to version 3).
        const artifactVersionPage = (props: any): React.ReactElement => {
            const location: string = props.location.pathname;
            return (
                <ArtifactVersionPage key={location} {...props} />
            );
        };

        if (this.state.keycloak) {
            if (this.state.authenticated) {
                return (
                    <Router basename={contextPath}>
                        <Page
                            className="pf-m-redhat-font"
                            isManagedSidebar={false}
                            header={<AppHeader/>}
                        >
                            <Switch>
                                <Route path='/' exact={true} component={RootRedirectPage}/>
                                <Route path='/rules' exact={true} component={RulesPage}/>
                                <Route path='/artifacts' exact={true} component={ArtifactsPage}/>
                                <Route path='/artifacts/:artifactId' exact={true} component={ArtifactRedirectPage}/>
                                <Route path='/artifacts/:artifactId/versions/:version' exact={true}
                                       component={artifactVersionPage}/>
                                <Route component={NotFoundPage}/>
                            </Switch>
                        </Page>
                    </Router>
                );
            } else return (<div>Unable to authenticate!</div>)
        }
        return (
            <div>Initializing Keycloak...</div>
        );
    }
}
