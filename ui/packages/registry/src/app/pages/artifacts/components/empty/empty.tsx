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
import {Button, EmptyState, EmptyStateBody, EmptyStateIcon, EmptyStateVariant, Title} from '@patternfly/react-core';
import {PlusCircleIcon} from "@patternfly/react-icons";
import {PureComponent, PureComponentProps, PureComponentState} from "../../../../components";
import {IfFeature} from "../../../../components/common/ifFeature";

/**
 * Properties
 */
export interface ArtifactsPageEmptyStateProps extends PureComponentProps {
    isFiltered: boolean;
    onUploadArtifact: () => void;
}

/**
 * State
 */
// tslint:disable-next-line:no-empty-interface
export interface ArtifactsPageEmptyStateState extends PureComponentState {
}


/**
 * Models the empty state for the Artifacts page (when there are no artifacts).
 */
export class ArtifactsPageEmptyState extends PureComponent<ArtifactsPageEmptyStateProps, ArtifactsPageEmptyStateState> {

    constructor(props: Readonly<ArtifactsPageEmptyStateProps>) {
        super(props);
    }

    public render(): React.ReactElement {
        return (
            <EmptyState variant={EmptyStateVariant.full}>
                <EmptyStateIcon icon={PlusCircleIcon}/>
                <Title headingLevel="h5" size="lg">
                    No Artifacts Found!
                </Title>
                {
                    this.props.isFiltered ?
                        <EmptyStateBody>
                            No artifacts match your filter settings.  Change your filter or perhaps Upload a new
                            artifact.
                        </EmptyStateBody>
                        :
                        <EmptyStateBody>
                            There are currently no artifacts in the registry. You may want to upload something first.
                        </EmptyStateBody>
                }
                <IfFeature feature="readOnly" isNot={true}>
                    <Button variant="primary" data-testid="empty-btn-upload" onClick={this.props.onUploadArtifact}>Upload artifact</Button>
                </IfFeature>
            </EmptyState>
        );
    }

    protected initializeState(): ArtifactsPageEmptyStateState {
        return {};
    }

}
