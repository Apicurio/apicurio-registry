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
import "./uploadForm.css";
import { PureComponent, PureComponentProps, PureComponentState } from "../../../../components";
import { FileUpload, Form, FormGroup } from "@patternfly/react-core";


/**
 * Properties
 */
export interface UploadVersionFormProps extends PureComponentProps {
    onValid: (valid: boolean) => void;
    onChange: (data: string) => void;
}

/**
 * State
 */
export interface UploadVersionFormState extends PureComponentState {
    content: string;
    contentFilename: string;
    contentIsLoading: boolean;
    valid: boolean;
}

/**
 * Models the toolbar for the Artifacts page.
 */
export class UploadVersionForm extends PureComponent<UploadVersionFormProps, UploadVersionFormState> {

    constructor(props: Readonly<UploadVersionFormProps>) {
        super(props);
    }

    public render(): React.ReactElement {
        return (
            <Form>
                <FormGroup
                    label="Artifact"
                    isRequired={true}
                    fieldId="form-artifact"
                >
                    <FileUpload
                        id="artifact-content"
                        data-testid="form-upload"
                        type="text"
                        filename={this.state.contentFilename}
                        value={this.state.content}
                        isRequired={true}
                        allowEditingUploadedText={true}
                        onChange={this.onContentChange}
                        onReadStarted={this.onFileReadStarted}
                        onReadFinished={this.onFileReadFinished}
                        isLoading={this.state.contentIsLoading}
                    />
                </FormGroup>
            </Form>
        );
    }

    protected initializeState(): UploadVersionFormState {
        return {
            content: "",
            contentFilename: "",
            contentIsLoading: false,
            valid: false
        };
    }

    private onContentChange = (value: any, filename: string, event: any): void => {
        this.setSingleState("content", value, () => {
            this.fireOnChange();
            this.checkValid();
        });
    };

    private onFileReadStarted = (): void => {
        this.setSingleState("contentIsLoading", true);
    };

    private onFileReadFinished = (): void => {
        this.setSingleState("contentIsLoading", false);
    };

    private checkValid(): void {
        const data: string = this.currentData();
        const oldValid: boolean = this.state.valid;
        const newValid: boolean = this.isValid(data);
        const validityChanged: boolean = oldValid !== newValid;
        this.setState({
            valid: newValid
        }, () => {
            if (validityChanged) {
                this.fireOnValid();
            }
        });
    }

    private isValid(data: string): boolean {
        return !!data;
    }

    private currentData(): string {
        return this.state.content;
    }

    private fireOnChange(): void {
        if (this.props.onChange) {
            this.props.onChange(this.currentData());
        }
    }

    private fireOnValid(): void {
        if (this.props.onValid) {
            this.props.onValid(this.state.valid);
        }
    }

}
