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

import React, {Component} from "react";
import {
    Page,
    PageSectionVariants,
    PageSection
} from "@patternfly/react-core";
import {BrowserRouter as Router, Route} from 'react-router-dom';
import * as Pages from './pages';
import {Header} from "./components/header";
import {Sidebar} from "./components/sidebar";

/**
 * The main application class.
 */
export default class App extends Component {
    public state = {
        activeMenuGroup: "",
        activeMenuGroupItem: ""
    };

    public render() {
        const section = (
            <PageSection variant={PageSectionVariants.light}>
                <Route path='/' exact={true} component={Pages.Artifacts}/>
                <Route path='/dashboard' exact={true} component={Pages.Artifacts}/>
            </PageSection>
        );
        return (
            <Router>
                <Page
                    isManagedSidebar={true}
                    header={<Header/>}
                    sidebar={
                        <Sidebar
                            activeMenuGroup={this.state.activeMenuGroup}
                            activeMenuGroupItem={this.state.activeMenuGroupItem}
                            onSelect={this.onNavSelect}
                        />
                    }
                >
                    {section}
                </Page>
            </Router>
        );
    }

    private onNavSelect = ({groupId, itemId}: any) => {
        this.setState({
            activeMenuGroup: groupId,
            activeMenuGroupItem: itemId
        });
    };
}
