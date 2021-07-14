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
import {If} from "../../../../components/common/if";

/**
 * Properties
 */
export interface RoleMappingsEmptyStateProps extends PureComponentProps {
    isFiltered?: boolean;
}

/**
 * State
 */
// tslint:disable-next-line:no-empty-interface
export interface RoleMappingsEmptyStateState extends PureComponentState {
}


/**
 * Models the empty state for the Artifacts page (when there are no artifacts).
 */
export class RoleMappingsEmptyState extends PureComponent<RoleMappingsEmptyStateProps, RoleMappingsEmptyStateState> {

    constructor(props: Readonly<RoleMappingsEmptyStateProps>) {
        super(props);
    }

    public render(): React.ReactElement {
        return (
            <EmptyState variant={EmptyStateVariant.full}>
                <EmptyStateIcon icon={PlusCircleIcon}/>
                <Title headingLevel="h5" size="lg">
                    No Role Mappings
                </Title>
                <If condition={() => this.props.isFiltered === true}>
                    <EmptyStateBody>
                        No role mappings match your filter settings.  Change your filter or perhaps create a new
                        role mapping.
                    </EmptyStateBody>
                </If>
                <If condition={() => !this.props.isFiltered}>
                    <EmptyStateBody>
                        There are currently no role mappings configured for the registry.  Click the "Grant Access"
                        button above to grant access to a user.
                    </EmptyStateBody>
                </If>
            </EmptyState>
        );
    }

    protected initializeState(): RoleMappingsEmptyStateState {
        return {};
    }

}
