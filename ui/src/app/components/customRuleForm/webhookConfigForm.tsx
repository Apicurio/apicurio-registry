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
    Form, TextInput, FormGroup, Popover, debounce
} from "@patternfly/react-core";
import { WebhookConfig } from 'src/models';
import { HelpIcon } from '@patternfly/react-icons';
import isURL from 'validator/lib/isURL';


/**
 * Properties
 */
export interface WebhookConfigFormProps extends PureComponentProps {
    inputData: WebhookConfig|null;
    onValid: (valid: boolean) => void;
    onChange: (webhookConfig: WebhookConfig) => void;
}

/**
 * State
 */
// tslint:disable-next-line:no-empty-interface
export interface WebhookConfigFormState extends PureComponentState {
    webhookUrl: string;
    urlValidated: any;

    webhookSecret: string|null;

    //generic custom rule type config form valid flag, reuse across all new custom rule types
    isConfigFormValid: boolean;

    debouncedOnChange: ((data: WebhookConfig) => void) | null;

}

export class WebhookConfigForm extends PureComponent<WebhookConfigFormProps, WebhookConfigFormState> {

    constructor(props: Readonly<WebhookConfigFormProps>) {
        super(props);
    }

    public render(): React.ReactElement {

        return (
            <Form >
                <FormGroup
                    label="Webhook URL"
                    labelIcon={
                        <Popover
                        headerContent={
                            <div>Target URL for the Webhook</div>
                        }
                        bodyContent={
                            <div>The complete URL used by the registry server to send POST requests to execute this custom rule.</div>
                        }
                        >
                        <button
                            type="button"
                            aria-label="More info for url field"
                            onClick={e => e.preventDefault()}
                            className="pf-c-form__group-label-help"
                        >
                            <HelpIcon noVerticalAlign />
                        </button>
                        </Popover>
                    }
                    isRequired
                    fieldId="webhook-form-url"
                    helperText="Target URL for the Webhook."
                    validated={this.state.urlValidated}
                    >
                    <TextInput
                        isRequired
                        type="url"
                        id="webhookUrl"
                        name="webhook-form-url-value"
                        aria-describedby="webhook-form-url-value-helper"
                        value={this.state.webhookUrl}
                        onChange={this.onChangeWebhookForm}
                        validated={this.state.urlValidated}
                    />
                </FormGroup>
                <FormGroup
                    label="Secret"
                    labelIcon={
                        <Popover
                        headerContent={
                            <div>Secret used to create a hash signature for each Webhook request payload</div>
                        }
                        bodyContent={
                            <div>
                                <div>A random string that will be used to create a hash signature for each Webhook request.</div>
                                <div>This hash signature is included with the headers of each request as X-Registry-Signature-256.</div>
                            </div>
                        }
                        >
                        <button
                            type="button"
                            aria-label="More info for secret field"
                            onClick={e => e.preventDefault()}
                            className="pf-c-form__group-label-help"
                        >
                            <HelpIcon noVerticalAlign />
                        </button>
                        </Popover>
                    }
                    fieldId="webhook-form-secret"
                    helperText="Secret for signign Webhook request payloads."
                    >
                    <TextInput
                        type="text"
                        id="webhookSecret"
                        name="webhook-form-secret-value"
                        aria-describedby="webhook-form-secret-value-helper"
                        value={this.state.webhookSecret == null ? "" : this.state.webhookSecret}
                        onChange={this.onChangeWebhookForm}
                    />
                </FormGroup>
            </Form>
        );

    }

    protected initializeState(): WebhookConfigFormState {
        return {
            webhookUrl: this.props.inputData == null ? "" : this.props.inputData.url,
            urlValidated: this.buildUrlValidated(this.props.inputData == null ? "" : this.props.inputData.url),

            webhookSecret: this.props.inputData == null ? null : this.props.inputData.secret,

            isConfigFormValid: false,

            debouncedOnChange: debounce(this.props.onChange, 200),
        };
    }

    private currentData(): WebhookConfig {
        return {
            url: this.state.webhookUrl,
            secret: this.state.webhookSecret
        };
    }

    private fireOnChange(): void {
        if (this.state.debouncedOnChange) {
            this.state.debouncedOnChange(this.currentData());
        }
    }

    private fireOnFormValid(): void {
        if (this.props.onValid) {
            this.props.onValid(this.state.isConfigFormValid);
        }
    }

    //call this function on every config form field change
    private configFormOnChange = (): void => {
        this.fireOnChange();
        let urlValidated: string = this.buildUrlValidated(this.state.webhookUrl);
        let isConfigFormValid: boolean = urlValidated === "success"; //add conditions here as more fields are added
        this.setMultiState({
            urlValidated,
            isConfigFormValid
        },this.fireOnFormValid);
    }

    private onChangeWebhookForm = (value: string, event: React.FormEvent<HTMLInputElement>): void => {
        console.log("running on change event element: " + event.currentTarget.id)
        this.setSingleState(event.currentTarget.id, value, this.configFormOnChange);
    };

    private buildUrlValidated(value: string): any {
        if (value.length == 0) {
            return "default"
        }else if (isURL(value, {
                            protocols: ['http','https'],
                            require_tld: false,
                            require_protocol: true,
                            require_host: true,
                            require_port: false,
                            require_valid_protocol: true,
                            allow_underscores: true,
                            host_whitelist: false,
                            host_blacklist: false,
                            allow_trailing_dot: false,
                            allow_protocol_relative_urls: false,
                            allow_fragments: true,
                            allow_query_components: true,
                            disallow_auth: false,
                            validate_length: true
                        })) {
            return "success"
        } else {
            return "error"
        }
    }

}
