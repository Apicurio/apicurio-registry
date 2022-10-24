/**
 * @license
 * Copyright 2021 Red Hat
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
import { Divider, Select, SelectGroup, SelectOption, SelectVariant, } from "@patternfly/react-core";
import { PureComponent, PureComponentProps, PureComponentState } from "../../../../components";
import { Principal } from "../../../../../services/config";
import { RoleMapping } from "../../../../../models";

export interface SelectPrincipalAccountProps extends PureComponentProps {
    id: string | undefined;
    onIdUpdate: (id: string) => void;
    initialOptions: () => Principal[];
    onToggle: (isOpen: boolean) => void;
    isUpdateAccess: boolean;
    isUsersOnly?: boolean;
    defaultRole?: RoleMapping;
}

export interface SelectPrincipalAccountState extends PureComponentState {
    id: string | undefined;
    isOpen: boolean;
}

export class SelectPrincipalAccount extends PureComponent<SelectPrincipalAccountProps, SelectPrincipalAccountState> {

    constructor(props: Readonly<SelectPrincipalAccountProps>) {
        super(props);
    }

    componentDidUpdate(prevProps: SelectPrincipalAccountProps){
        if (this.props.id && this.props.id !== prevProps.id) {
            this.setSingleState("id", this.props.id);
        }
    }

    private onToggle = (isOpen: boolean) => {
        this.setSingleState("isOpen", isOpen);
        this.props.onToggle(isOpen);
    };

    private clearSelection = () => {
        this.setMultiState({
            id: "",
            isOpen: false
        });
        this.props.onIdUpdate("");
    };

    private onSelect = (_event: any, selection: any, isPlaceholder: any) => {
        if (isPlaceholder) {
            this.clearSelection();
        } else {
            this.setSingleState("id", selection);
            this.onToggle(false);
            this.props.onIdUpdate(selection);
        }
    };

    protected initializeState(): SelectPrincipalAccountState {
        return {
            id: "",
            isOpen: false
        };
    }

    public render(): React.ReactElement {
        const { isUpdateAccess, defaultRole } = this.props;
        const children: React.ReactElement[] = this.filter(null, "");

        return (
            <Select
                variant={SelectVariant.typeahead}
                typeAheadAriaLabel={"Select an account"}
                createText={"Use"}
                onToggle={this.onToggle}
                onSelect={this.onSelect}
                onClear={this.clearSelection}
                selections={this.state.id}
                isOpen={this.state.isOpen}
                isInputValuePersisted={true}
                placeholderText={isUpdateAccess ? defaultRole?.principalId : "Select an account"}
                isCreatable={true}
                menuAppendTo="parent"
                maxHeight={400}
                isGrouped={true}
                onFilter={this.filter}
                children={children}
                isDisabled={isUpdateAccess}
            />
        );
    }

    private filter = (event: any, criteria: string): React.ReactElement[] => {
        const principalToSelectOption: (p: Principal, index: number) => React.ReactElement = (principal: Principal, index: number): React.ReactElement => {
            return (
                <SelectOption
                    key={index}
                    value={principal.id}
                    description={principal.displayName}
                >
                    {principal.id}
                </SelectOption>
            );
        };

        const filteredSAs: Principal[] = this.props.initialOptions().filter(
            (principal) =>
                principal.principalType === "SERVICE_ACCOUNT"
        ).filter(
            (principal) =>
                !this.props.isUsersOnly && (
                    principal.id.toLowerCase().includes(criteria.toLowerCase()) ||
                    principal.displayName?.toLowerCase().includes(criteria.toLowerCase())
                )
        );
        const filteredUsers: Principal[] = this.props.initialOptions().filter(
            (principal) =>
                principal.principalType === "USER_ACCOUNT"
        ).filter(
            (principal) =>
                principal.id.toLowerCase().includes(criteria.toLowerCase()) ||
                principal.displayName?.toLowerCase().includes(criteria.toLowerCase())
        );

        const rval: React.ReactElement[] = [];

        if (filteredSAs.length > 0) {
            rval.push(
                <SelectGroup label={"Service accounts"} key="service_accounts_group">
                    {
                        filteredSAs.sort((a, b) => a.displayName && b.displayName ? a.displayName.localeCompare(b.displayName) : -1).
                            map(principalToSelectOption)
                    }
                </SelectGroup>,
            );
        }

        if (filteredSAs.length > 0 && filteredUsers.length > 0) {
            rval.push(
                <Divider key='divider' />,
            );
        }

        if (filteredUsers.length > 0) {
            rval.push(
                <SelectGroup label={"User accounts"} key="user_accounts_group">
                    {
                        filteredUsers.map(principalToSelectOption)
                    }
                </SelectGroup>,
            );
        }

        return rval;
    };

}
