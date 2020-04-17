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
import {PageSection, PageSectionVariants} from '@patternfly/react-core';
import "./rules.css";
import {PageComponent, PageProps, PageState} from "../basePage";
import {RulesPageHeader} from "./components/pageheader";


/**
 * Properties
 */
// tslint:disable-next-line:no-empty-interface
export interface RulesPageProps extends PageProps {

}

/**
 * State
 */
// tslint:disable-next-line:no-empty-interface
export interface RulesPageState extends PageState {
}

/**
 * The artifacts page.
 */
export class RulesPage extends PageComponent<RulesPageProps, RulesPageState> {

    constructor(props: Readonly<RulesPageProps>) {
        super(props);
    }

    public render(): React.ReactElement {
        return (
            <React.Fragment>
                <PageSection className="ps_rules-header" variant={PageSectionVariants.light}>
                    <RulesPageHeader />
                </PageSection>
                <PageSection variant={PageSectionVariants.default} isFilled={true}>
                    <h1>To be done!</h1>
                </PageSection>
            </React.Fragment>
        );
    }

    protected initializeState(): RulesPageState {
        return {
            isLoading: false
        };
    }

    protected loadPageData(): void {
        // TODO load global rules
    }
}
