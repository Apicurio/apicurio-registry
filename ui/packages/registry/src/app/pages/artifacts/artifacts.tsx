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
import {
    Button,
    EmptyState,
    EmptyStateBody,
    EmptyStateIcon,
    EmptyStateVariant,
    PageSection,
    PageSectionVariants,
    Title
} from '@patternfly/react-core';
import {ArtifactsPageHeader} from "./components/pageheader";
import {ArtifactsToolbar} from "./components/toolbar";
import {PlusCircleIcon} from "@patternfly/react-icons";
import "./artifacts.css";


/**
 * Properties
 */
export interface ArtifactsProps {

}

/**
 * State
 */
export interface ArtifactsState {
}

/**
 * The artifacts page.
 */
export class Artifacts extends React.Component<ArtifactsProps, ArtifactsState> {

    constructor(props: Readonly<ArtifactsProps>) {
        super(props);
        this.state = {};
    }

    public render(): React.ReactElement {
        return (
            <React.Fragment>
                <PageSection className="ps_artifacts-header" variant={PageSectionVariants.light}>
                    <ArtifactsPageHeader/>
                </PageSection>
                <PageSection variant={PageSectionVariants.light} noPadding={true}>
                    <ArtifactsToolbar/>
                </PageSection>
                <PageSection variant={PageSectionVariants.default} isFilled={true}>
                    <EmptyState variant={EmptyStateVariant.full}>
                        <EmptyStateIcon icon={PlusCircleIcon} />
                        <Title headingLevel="h5" size="lg">
                            No Artifacts Found!
                        </Title>
                        <EmptyStateBody>
                            There are currently no artifacts in the registry.  You may want to upload something by clicking
                            the button below.
                        </EmptyStateBody>
                        <Button variant="primary">Upload Artifact</Button>
                    </EmptyState>
                </PageSection>
            </React.Fragment>
        );
    }

}
