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
    Button, Radio, Wizard, WizardFooter, WizardContextConsumer, WizardStep, Form, TextInput, FormGroup, Popover, Dropdown, DropdownToggle, DropdownItem, DropdownSeparator
} from "@patternfly/react-core";
import { ArtifactTypes, CustomRule, CustomRuleTypes, WebhookConfig } from 'src/models';
import { CaretDownIcon, HelpIcon } from '@patternfly/react-icons';
import { WebhookConfigForm } from './webhookConfigForm';


/**
 * Properties
 */
export interface CustomRuleFormProps extends PureComponentProps {
    // error: any;
    isOpen: boolean;
    onClose: () => void;
    onSubmit: (data: CustomRule) => void;
}

/**
 * State
 */
// tslint:disable-next-line:no-empty-interface
export interface CustomRuleFormState extends PureComponentState {
    showWebhookConfigStep: boolean,

    customRuleTypeSelected: string|null

    customRuleId: string
    customRuleIdValidated: any

    customRuleDescription: string
    customRuleDescriptionValidated: any

    supportedArtifactType: string
    artifactTypeIsExpanded: boolean

    isStartFormValid: boolean

    webhookConfig: WebhookConfig|null

    //generic custom rule type config form valid flag, reuse across all new custom rule types
    isConfigFormValid: boolean
}

const START_STEP_ID = "start";
const CONFIG_STEP_ID = "custom-rule-config";
const artifactTypes: any[] = ArtifactTypes.LabeledArtifactTypes;

export class CustomRuleForm extends PureComponent<CustomRuleFormProps, CustomRuleFormState> {

    constructor(props: Readonly<CustomRuleFormProps>) {
        super(props);
    }

    public render(): React.ReactElement {

        const CustomFooter = (
            <WizardFooter>
              <WizardContextConsumer>
                {({ activeStep, goToStepByName, goToStepById, onNext, onBack, onClose }) => {
                  return (
                    <>
                      <Button variant="primary"
                            type="submit"
                            onClick={() => this.getNextStep(activeStep, onNext)}
                            isDisabled={this.isNextButtonDisabled(activeStep.id)}
                            >
                        {activeStep.id === CONFIG_STEP_ID ? 'Finish' : 'Next'}
                      </Button>
                      <Button variant="secondary"
                            onClick={() => this.getPreviousStep(activeStep, onBack)}
                            isDisabled={activeStep.id === START_STEP_ID}>
                        Back
                      </Button>
                      <Button variant="link" onClick={onClose}>
                        Cancel
                      </Button>
                    </>
                  )}}
              </WizardContextConsumer>
            </WizardFooter>
        );

        const getStartedStep: WizardStep = {
            name: 'Get started',
            id: START_STEP_ID,
            component: (
              <Form >
                <FormGroup
                    label="Custom rule name"
                    labelIcon={
                        <Popover
                        headerContent={
                            <div>Name that identifies this custom rule</div>
                        }
                        bodyContent=""
                        >
                        <button
                            type="button"
                            aria-label="More info for id field"
                            onClick={e => e.preventDefault()}
                            className="pf-c-form__group-label-help"
                        >
                            <HelpIcon noVerticalAlign />
                        </button>
                        </Popover>
                    }
                    isRequired
                    fieldId="cr-form-id-field"
                    helperText="Name for the custom rule."
                    validated={this.state.customRuleIdValidated}
                    >
                    <TextInput
                        isRequired
                        maxLength={35} //limit in the database
                        type="text"
                        id="cr-form-id-value"
                        name="cr-form-id-value"
                        aria-describedby="cr-form-id-value-helper"
                        value={this.state.customRuleId}
                        onChange={this.onChangeCustomRuleId}
                        validated={this.state.customRuleIdValidated}
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
                        value={this.state.customRuleDescription}
                        onChange={this.onChangeCustomRuleDescription}
                        validated={this.state.customRuleDescriptionValidated}
                    />
                </FormGroup>
                <FormGroup 
                        label="Custom rule type"
                        fieldId="cr-form-rule-type-field">
                    {/* <Radio
                        value="oas-validator"
                        isChecked={this.state.customRuleTypeSelected === 'oas-validator'}
                        onChange={this.onChangeCustomRuleTypeSelected}
                        label="Configure a custom rule using OpenApi Validator"
                        name="radio-step-oas-validator"
                        id="radio-step-start-1"
                        /> */}
                    {' '}
                    <Radio
                        value={CustomRuleTypes.webhook}
                        isChecked={this.state.customRuleTypeSelected === CustomRuleTypes.webhook}
                        // onChange={(_, event) => this.setMultiState({customRuleTypeSelected: event.currentTarget.value})}
                        onChange={this.onChangeCustomRuleTypeSelected}
                        label="Configure a custom rule using a custom Webhook"
                        name="radio-step-webhook"
                        id="radio-step-start-2"
                        />
                    {' '}
                </FormGroup>
                <FormGroup
                    label="Supported artifact type"
                    fieldId="cr-form-artifact-type-field">
                    <div>
                        <Dropdown
                            toggle={
                                <DropdownToggle id="form-type-toggle" data-testid="form-type-toggle" onToggle={this.onTypeToggle} toggleIndicator={CaretDownIcon}>
                                    { this.state.supportedArtifactType ? this.typeLabel(this.state.supportedArtifactType) : "Any type" }
                                </DropdownToggle>
                            }
                            onSelect={this.onTypeSelect}
                            isOpen={this.state.artifactTypeIsExpanded}
                            dropdownItems={[
                                <DropdownItem key="any" id="" data-testid="form-type-any"><i>Any type</i></DropdownItem>,
                                <DropdownSeparator key="separator" />,
                                ...artifactTypes.map(t =>
                                    <DropdownItem key={t.id} id={t.id} data-testid={`form-type-${t.id}`}>{ t.label }</DropdownItem>
                                )
                            ]}
                        />
                    </div>
                </FormGroup>
            </Form>
            )
        };

        const webhookStep: WizardStep = {
            name: "Webhook custom rule",
            id: CONFIG_STEP_ID, // use this id for every custom rule type
            component: (
                <WebhookConfigForm
                    inputData={null}
                    onChange={this.onChangeWebhookForm}
                    onValid={this.onValidWebhookForm}/>
            )
        }

        const steps: WizardStep[] = [
            getStartedStep,

            ...(this.state.showWebhookConfigStep || this.state.customRuleTypeSelected === CustomRuleTypes.webhook ? [webhookStep] : []),

        ];

        const title = 'Create custom rule wizard';
        return (
            <Wizard
                navAriaLabel={`${title} steps`}
                mainAriaLabel={`${title} content`}
                onClose={this.closeWizard}
                onGoToStep={this.onGoToStep}
                footer={CustomFooter}
                steps={steps}
                height={400}
                isOpen={this.props.isOpen}
            />
        );

    }

    protected initializeState(): CustomRuleFormState {
        return {
            showWebhookConfigStep: false,
            
            customRuleTypeSelected: null,

            customRuleId: "",
            customRuleIdValidated: this.buildCustomRuleIdValidated(""),

            customRuleDescription: "",
            customRuleDescriptionValidated: this.buildCustomRuleDescriptionValidated(""),

            supportedArtifactType: "",
            artifactTypeIsExpanded: false,

            isStartFormValid: false,

            webhookConfig: null,

            isConfigFormValid: false
        };
    }

    private closeWizard = () => {
        console.log("close wizard");
        this.setMultiState(this.initializeState())
        this.props.onClose();
    }

    private onGoToStep = (step: any): void => {
        // Remove steps after the currently clicked step
        if (step.id === START_STEP_ID) {
          this.setMultiState({
            showWebhookConfigStep: false,
          });
        }
      };

    private getNextStep(activeStep: WizardStep, callback: (() => void)): void {
        if (activeStep.id === START_STEP_ID) {
            //jump to selected custom rule config page
            if (this.state.customRuleTypeSelected === CustomRuleTypes.webhook) {
                this.setMultiState({
                    showWebhookConfigStep: true,
                }, callback)
            } else {
                console.log("ERROR unknown custom rule type " + this.state.customRuleTypeSelected)
            }
        } else if (activeStep.id === CONFIG_STEP_ID){
            //create custom rule!
            //TODO validate again?
            this.createCustomRule();
        }
    }

    private getPreviousStep(activeStep: WizardStep, callback: (() => void)): void {
        if (activeStep.id === CONFIG_STEP_ID) {
            this.setMultiState({
                showWebhookConfigStep: false,
            }, callback)
        }
    };

    private isNextButtonDisabled(activeStepId: string|number|undefined): boolean {
        if (activeStepId === START_STEP_ID) {
            return !this.state.isStartFormValid;
        } else {
            return !this.state.isStartFormValid && !this.state.isConfigFormValid;
        }
    }

    //call this function on every field change to start form
    private startFormOnChange = (): void => {
        this.setMultiState({
            isStartFormValid: this.state.customRuleIdValidated === "success"
                && this.state.customRuleDescriptionValidated !== "error"
                && this.state.customRuleTypeSelected != null
            //add conditions here as more fields are added
        })
    }

    private onChangeCustomRuleTypeSelected = (checked: boolean, event: React.FormEvent<HTMLInputElement>): void => {
        // this.setMultiState({customRuleTypeSelected: event.currentTarget.value});
        this.setMultiState({
                customRuleTypeSelected: event.currentTarget.value
            }, this.startFormOnChange);
    }
    private onChangeCustomRuleId = (value: string): void => {
        this.setMultiState({
                customRuleId: value,
                customRuleIdValidated: this.buildCustomRuleIdValidated(value)
            }, this.startFormOnChange);
    }
    private onChangeCustomRuleDescription = (value: string): void => {
        this.setMultiState({
                customRuleDescription: value,
                customRuleDescriptionValidated: this.buildCustomRuleDescriptionValidated(value)
            }, this.startFormOnChange);
    }

    private buildCustomRuleIdValidated(value: string) {
        if (!value || value.length == 0) {
            return "default";
        } else {
            //TODO implement no spaces validation?
            return "success"
        }
    }

    private buildCustomRuleDescriptionValidated(value: string) {
        if (!value || value.length == 0) {
            return "default"
        } else if (value && value.length >= 1024) { //TODO check max db length
            return "error";
        } else {
            return "success"
        }
    }

    private onTypeToggle = (isExpanded: boolean): void => {
        this.setSingleState("artifactTypeIsExpanded", isExpanded);
    };
    private typeLabel(type: string): string {
        return artifactTypes.filter( t => {
            return t.id === type;
        }).map( t => t.label )[0];
    }
    private onTypeSelect = (event: React.SyntheticEvent<HTMLDivElement>|undefined): void => {
        const newType: string = event && event.currentTarget && event.currentTarget.id ? event.currentTarget.id : "";
        this.setMultiState({
            supportedArtifactType: newType,
            artifactTypeIsExpanded: false
        });
    };

    private onChangeWebhookForm = (webhookConfig: WebhookConfig): void => {
        this.setMultiState(
            {
                webhookConfig
            }
        );
    };

    private onValidWebhookForm = (valid: boolean): void => {
        this.setMultiState({isConfigFormValid: valid});
    }

    private createCustomRule(): void {
        if (this.state.customRuleTypeSelected != null) {
            this.props.onSubmit({
                id: this.state.customRuleId,
                description: this.state.customRuleDescription,
                customRuleType: this.state.customRuleTypeSelected,
                supportedArtifactType: this.state.supportedArtifactType && this.state.supportedArtifactType.length > 0 ? this.state.supportedArtifactType : null,
                //TODO extract this config part when multiple custom rule types exist
                webhookConfig: this.state.webhookConfig == null ? null : this.state.webhookConfig
            });
        }
        this.setMultiState(this.initializeState())
    }
}
