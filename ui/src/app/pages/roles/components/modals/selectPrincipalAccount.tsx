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

import React from 'react';
import {Divider, Select, SelectGroup, SelectOption, SelectVariant,} from '@patternfly/react-core';
import {PureComponent, PureComponentProps, PureComponentState} from "../../../../components";
import {Principal} from '../../../../../services/config';

export interface SelectPrincipalAccountProps extends PureComponentProps{
  id: string | undefined;
  onIdUpdate: (id: string) => void;
  initialOptions: Principal[];
}

export interface SelectPrincipalAccountState extends PureComponentState{
  id: string | undefined;
  isOpen: boolean;
}

export class SelectPrincipalAccount extends PureComponent<SelectPrincipalAccountProps, SelectPrincipalAccountState> {

  constructor(props: Readonly<SelectPrincipalAccountProps>) {
    super(props);
}
    private onToggle = (isOpen: boolean) => {
      this.setSingleState("isOpen", isOpen);
    };

    private clearSelection = () => {
      this.reset();
    };

    private onSelect = (_event:any, selection:any, isPlaceholder:any) => {
      if (isPlaceholder) {
        this.clearSelection();
      } else {
        this.setMultiState({
          id: selection,
          isOpen: false
        })
      }
      this.props.onIdUpdate(selection);
    };

  protected initializeState(): SelectPrincipalAccountState {
    return {
      id: "",
      isOpen: false
    };
  }

  public reset(): void {
    this.setMultiState(this.initializeState());
  }

  public render(): React.ReactElement {
    const { initialOptions,
      id
    } = this.props;

    return (
      <Select
        variant={SelectVariant.typeahead}
        typeAheadAriaLabel={"Select an account"}
        onToggle={this.onToggle}
        onSelect={this.onSelect}
        onClear={this.clearSelection}
        selections={id}
        isOpen={this.state.isOpen}
        isInputValuePersisted={true}
        placeholderText={"Select an account"}
        isCreatable={false}
        maxHeight={400}
        isGrouped={true}
      >
        {[
          <SelectGroup
            label={"Service accounts"}
            key='service_accounts_group'
          >
            {
            initialOptions
              .filter(
                (principal) =>
                  principal.principalType === "SERVICE_ACCOUNT"
              )
              .map((principal, index) => (
                <SelectOption
                  key={index}
                  value={principal.id}
                  description={principal.displayName}
                >
                  {principal.id}
                </SelectOption>
              ))}
          </SelectGroup>,
          <Divider key='divider' />,
          <SelectGroup
            label={"User accounts"}
            key='user_accounts_group'
          >
            {initialOptions
              .filter(
                (principal) =>
                  principal.principalType === "USER_ACCOUNT"
              )
              .map((principal, index) => (
                <SelectOption
                  key={index}
                  value={principal.id}
                  description={principal.displayName}
                >
                  {principal.id}
                </SelectOption>
              ))}
          </SelectGroup>,
        ]}
      </Select>
    );
  };
}
