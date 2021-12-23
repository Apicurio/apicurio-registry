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
import {PureComponent, PureComponentProps, PureComponentState} from "../baseComponent";
import {
    Button, Form, TextInput, FormGroup, Modal
} from "@patternfly/react-core";
import { CustomRule, CustomRuleTypes, CustomRuleUpdate, WebhookConfig } from 'src/models';
import { WebhookConfigForm } from './webhookConfigForm';


/**
 * Properties
 */
export interface UpdateCustomRuleProps extends PureComponentProps {
    customRule: CustomRule|null;
    isOpen: boolean;
    onClose: () => void;
    onSubmit: (data: CustomRuleUpdate) => void;
}

/**
 * State
 */
// tslint:disable-next-line:no-empty-interface
export interface UpdateCustomRuleState extends PureComponentState {
    customRuleDescription: string|null
    customRuleDescriptionValidated: any

    //global form valid flag
    isFormValid: boolean

    webhookConfig: WebhookConfig|null

    //generic custom rule type config form valid flag, reuse across all new custom rule types
    isConfigFormValid: boolean
}

export class UpdateCustomRuleForm extends PureComponent<UpdateCustomRuleProps, UpdateCustomRuleState> {

    constructor(props: Readonly<UpdateCustomRuleProps>) {
        super(props);
    }

    public render(): React.ReactElement {

        this.setMultiState(this.initializeState())

        let configForm: React.ReactElement = (<React.Fragment></React.Fragment>);

        //implement here for new custom rule types
        if (this.props.customRule != null && this.props.customRule.customRuleType === CustomRuleTypes.webhook) {
            configForm = (
                <WebhookConfigForm
                    inputData={this.props.customRule.webhookConfig}
                    onChange={this.onChangeWebhookForm}
                    onValid={this.onValidWebhookForm}/>
            );
        }

        return (
            <Modal
                title='Edit custom rule'
                variant="large"
                isOpen={this.props.isOpen}
                onClose={this.onCloseForm}
                className="pf-m-redhat-font"
                actions={[
                    <Button key="edit" variant="primary" data-testid="modal-btn-edit" onClick={this.updateCustomRule} isDisabled={!this.state.isFormValid}>Edit</Button>,
                    <Button key="cancel" variant="link" data-testid="modal-btn-cancel" onClick={this.onCloseForm}>Cancel</Button>
                ]}>
                <Form >
                    <FormGroup
                        label="Custom rule name"
                        isRequired
                        fieldId="cr-form-id-field"
                        helperText="Name for the custom rule."
                        >
                        <TextInput
                            isRequired
                            type="text"
                            id="cr-form-id-value"
                            name="cr-form-id-value"
                            aria-describedby="cr-form-id-value-helper"
                            value={this.props.customRule == null ? "" : this.props.customRule.id}
                            isDisabled={true}
                        />
                    </FormGroup>
                    <FormGroup
                        label="Custom rule description"
                        fieldId="cr-form-description-field"
                        helperText="Description for the custom rule."
                        validated={this.state.customRuleDescriptionValidated}
                        >
                        <TextInput
                            type="text"
                            id="cr-form-description-value"
                            name="cr-form-description-value"
                            aria-describedby="cr-form-description-value-helper"
                            value={this.state.customRuleDescription == null ? "" : this.state.customRuleDescription}
                            onChange={this.onChangeCustomRuleDescription}
                            validated={this.state.customRuleDescriptionValidated}
                        />
                    </FormGroup>
                    {configForm}
                </Form>
            </Modal>
        );

    }

    protected initializeState(): UpdateCustomRuleState {
        return {
            customRuleDescription: this.props.customRule == null ? "" : this.props.customRule.description,
            customRuleDescriptionValidated: this.buildCustomRuleDescriptionValidated(this.props.customRule == null ? "" : this.props.customRule.description),

            isFormValid: false,

            webhookConfig: this.props.customRule == null ? null : this.props.customRule.webhookConfig,

            isConfigFormValid: false
        };
    }

    protected buildCleanState(): UpdateCustomRuleState {
        return {
            customRuleDescription: "",
            customRuleDescriptionValidated: this.buildCustomRuleDescriptionValidated(""),

            isFormValid: false,

            webhookConfig: null,

            isConfigFormValid: false
        };
    }

    private onCloseForm = () => {
        console.log("close update form");
        this.setMultiState(this.buildCleanState())
        this.props.onClose();
    }

    private formCheckValid = (): void => {
        this.setMultiState({
            isFormValid: this.state.customRuleDescriptionValidated !== "error" && this.state.isConfigFormValid
        })
    }

    private onChangeCustomRuleDescription = (value: string): void => {
        this.setMultiState({
                customRuleDescription: value,
                customRuleDescriptionValidated: this.buildCustomRuleDescriptionValidated(value)
            }, this.formCheckValid);
    }

    private buildCustomRuleDescriptionValidated(value: string|null) {
        if (value == null || value.length == 0) {
            return "default"
        } else if (value && value.length >= 1024) { //TODO check max db length
            return "error";
        } else {
            return "success"
        }
    }

    private onChangeWebhookForm = (webhookConfig: WebhookConfig): void => {
        this.setMultiState(
            {
                webhookConfig
            }
        );
    };

    private onValidWebhookForm = (valid: boolean): void => {
        this.setMultiState({isConfigFormValid: valid}, this.formCheckValid);
    }

    private updateCustomRule(): void {
        if (this.state.isFormValid != null) {
            this.props.onSubmit({
                description: this.state.customRuleDescription,
                webhookConfig: this.state.webhookConfig
            })
        }
        // this.setMultiState(this.initializeState())
    }
}
