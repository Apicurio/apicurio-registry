/**
 * @license
 * Copyright 2022 JBoss Inc
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
import "./errorPage.css";
import {
    Button,
    EmptyState,
    EmptyStateBody,
    EmptyStateIcon,
    EmptyStateSecondaryActions,
    EmptyStateVariant,
    PageSection,
    PageSectionVariants,
    Title
} from "@patternfly/react-core";
import { ExclamationCircleIcon } from "@patternfly/react-icons";
import "ace-builds/src-noconflict/mode-text";
import "ace-builds/src-noconflict/theme-tomorrow";
import { ErrorPage, ErrorPageProps } from "./errorPage";


export class RateLimitErrorPage extends ErrorPage {

    constructor(props: Readonly<ErrorPageProps>) {
        super(props);
    }

    public render(): React.ReactElement {
        return (
            <React.Fragment>
                <PageSection className="ps_error" variant={PageSectionVariants.light}>
                    <div className="centerizer">
                        <EmptyState variant={EmptyStateVariant.large}>
                            <EmptyStateIcon icon={ExclamationCircleIcon} />
                            <Title headingLevel="h5" size="lg">Current usage is too high</Title>
                            <EmptyStateBody>
                                This Service Registry instance is throttled due to a high request rate. Ensure
                                that existing applications are properly configured to cache the schemas.
                            </EmptyStateBody>
                            <EmptyStateSecondaryActions>
                                <Button variant="link"
                                        data-testid="error-btn-back"
                                        onClick={this.navigateBack}>Return to previous page</Button>
                            </EmptyStateSecondaryActions>
                        </EmptyState>
                    </div>
                </PageSection>
            </React.Fragment>
        );
    }

    protected navigateBack = (): void => {
        window.history.back();
    };

}
