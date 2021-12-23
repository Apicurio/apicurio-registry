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
import "./customRuleList.css"
import {
    Badge,
    Button,
    DataList,
    DataListAction,
    DataListCell,
    DataListItemCells,
    DataListItemRow,
    Modal
} from '@patternfly/react-core';
import {PureComponent, PureComponentProps, PureComponentState} from "../baseComponent";
import {OkIcon, PencilAltIcon, TrashIcon} from "@patternfly/react-icons";
import {IfFeature} from "../common/ifFeature";
import {IfAuth} from "../common";
import {CustomRule, CustomRuleUpdate} from "../../../models";
import { UpdateCustomRuleForm } from '../customRuleForm';


export interface CustomRuleListProps extends PureComponentProps {
    customRules: CustomRule[];
    onUpdateCustomRule: (id: string, customRule: CustomRuleUpdate) => void;
    onDeleteCustomRule: (id: string) => void;
}

// tslint:disable-next-line:no-empty-interface
export interface CustomRuleListState extends PureComponentState {
    isConfirmDeleteModalOpen: boolean;
    deletingCustomRule: CustomRule|null;

    isCustomRuleModalOpen: boolean;
    updatingCustomRule: CustomRule|null;
}


export class CustomRuleList extends PureComponent<CustomRuleListProps, CustomRuleListState> {

    constructor(props: Readonly<CustomRuleListProps>) {
        super(props);
    }

    public render(): React.ReactElement {

        //TODO rules execution ordering? this would be in the part of binding/enabling

        return (
            <React.Fragment>
                <DataList aria-label="Custom rules" className="custom-rules">
                    {
                        this.props.customRules.map( (customRule, idx) =>
                            <DataListItemRow className="custom-rules-item" key={customRule.id}>
                                <DataListItemCells
                                    dataListCells={[
                                        <DataListCell key="type icon" className="type-icon-cell">
                                            <OkIcon/>
                                        </DataListCell>,
                                        <DataListCell key="main content" className="content-cell">
                                            <div className="custom-rule-title">
                                                <span id={"custom-rule-" + customRule.id + "-name"}><b>{customRule.id}</b></span>
                                            </div>
                                            <div className="custom-rule-description">{this.description(customRule)}</div>
                                            <div className="custom-rule-tags">
                                                {this.supportedArtifactTypeBadge(customRule)}
                                                <p/>
                                                <Badge key={customRule.customRuleType} isRead={true}>{customRule.customRuleType}</Badge>
                                            </div>
                                        </DataListCell>
                                    ]}
                                />
                                <IfAuth isDeveloper={true}>
                                    <IfFeature feature="readOnly" isNot={true}>
                                        <DataListAction
                                            aria-labelledby="selectable-action-item1 selectable-action-action1"
                                            id={"selectable-action-action-" + customRule.id}
                                            aria-label="Actions"
                                        >
                                            {/* TODO view config button? */}
                                            <Button variant="plain"
                                                    key="edit-action"
                                                    data-testid={"custom-rule-" + customRule.id + "-edit"}
                                                    title="Edit the custom rule configuration"
                                                    onClick={this.doOpenEditModal(customRule)}
                                                    >
                                                    <PencilAltIcon /> Edit </Button>
                                            <Button variant="plain"
                                                    key="delete-action"
                                                    data-testid={"custom-rule-" + customRule.id + "-delete"}
                                                    title="Delete the custom rule"
                                                    onClick={this.doOpenConfirmDeleteModal(customRule)}
                                                    >
                                                    <TrashIcon /> Delete </Button>
                                        </DataListAction>
                                    </IfFeature>
                                </IfAuth>
                            </DataListItemRow>
                        )
                    }
                </DataList>
                <Modal
                    title="Remove custom rule?"
                    variant="small"
                    isOpen={this.state.isConfirmDeleteModalOpen}
                    onClose={this.onConfirmDeleteModalClose}
                    className="pf-m-redhat-font"
                    actions={[
                        <Button key="delete" variant="primary" data-testid="modal-btn-delete" onClick={this.doDeleteCustomRule}>Confirm delete</Button>,
                        <Button key="cancel" variant="link" data-testid="modal-btn-cancel" onClick={this.onConfirmDeleteModalClose}>Cancel</Button>
                    ]}
                >
                    <p>Confirm you want to delete the custom rule: <b>{this.state.deletingCustomRule?.id}</b></p>
                </Modal>
                <UpdateCustomRuleForm 
                    customRule={this.state.updatingCustomRule}
                    isOpen={this.state.isCustomRuleModalOpen}
                    onSubmit={this.doSubmitCustomRuleUpdateForm}
                    onClose={this.doCustomRuleModalClose}/>
            </React.Fragment>
        )
    }

    protected initializeState(): CustomRuleListState {
        return {
            isConfirmDeleteModalOpen: false,
            deletingCustomRule: null,

            isCustomRuleModalOpen: false,
            updatingCustomRule: null
        };
    }

    private onConfirmDeleteModalClose = (): void => {
        this.setSingleState("isConfirmDeleteModalOpen", false);
    };

    private doOpenConfirmDeleteModal(customRule: CustomRule): (() => void) {
        return () => {
            this.setMultiState({isConfirmDeleteModalOpen: true, deletingCustomRule: customRule})
        };
    };

    private doDeleteCustomRule = (): void => {
        if (this.state.deletingCustomRule != null) {
            this.props.onDeleteCustomRule(this.state.deletingCustomRule.id);
        }
        this.onConfirmDeleteModalClose();
    };

    private description(customRule: CustomRule): string {
        if (customRule.description) {
            return customRule.description;
        }
        return `A custom rule of type ${customRule.customRuleType} with no description.`;
    }

    private supportedArtifactTypeBadge(customRule: CustomRule): React.ReactElement {
        if (customRule.supportedArtifactType && customRule.supportedArtifactType.length>0) {
            return (
                <span>Supported artifact type <Badge className="status-badge" key={customRule.supportedArtifactType} isRead={true}>{customRule.supportedArtifactType}</Badge></span>
            )
        } else {
            return (
                <span>All artifact types supported</span>
            )
        }
    }

    private doOpenEditModal(customRule: CustomRule): (() => void) {
        return () => {
            this.setMultiState({isCustomRuleModalOpen: true, updatingCustomRule: customRule})
        };
    };

    private doCustomRuleModalClose = (): void => {
        this.setSingleState("isCustomRuleModalOpen", false);
    };

    private doSubmitCustomRuleUpdateForm = (customRuleFormData: CustomRuleUpdate): void => {
        // this.pleaseWait(true);
        if (customRuleFormData !== null && this.state.updatingCustomRule != null) {
            this.props.onUpdateCustomRule(this.state.updatingCustomRule.id, customRuleFormData);
        }
        this.doCustomRuleModalClose();
    };

}
