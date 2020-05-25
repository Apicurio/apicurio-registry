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
import React from 'react';
import "./uploadForm.css";
import {PureComponent, PureComponentProps, PureComponentState} from "../../../../components";
import {
    Button,
    debounce,
    Dropdown,
    DropdownItem,
    DropdownSeparator,
    DropdownToggle,
    FileUpload,
    Form,
    FormGroup,
    TextInput
} from "@patternfly/react-core";
import {CaretDownIcon} from "@patternfly/react-icons";
import {CreateArtifactData} from "@apicurio/registry-services";
import {ArtifactTypes} from "@apicurio/registry-models";


const artifactTypes: any[] = [
    { id: ArtifactTypes.AVRO, label: "Avro Schema" },
    { id: ArtifactTypes.PROTOBUF, label: "Protocol Buffer Schema" },
    { id: ArtifactTypes.JSON, label: "JSON Schema" },
    { id: ArtifactTypes.OPENAPI, label: "OpenAPI" },
    { id: ArtifactTypes.ASYNCAPI, label: "AsyncAPI" },
    { id: ArtifactTypes.GRAPHQL, label: "GraphQL" },
    { id: ArtifactTypes.KCONNECT, label: "Kafka Connect Schema" },
    { id: ArtifactTypes.WSDL, label: "WSDL" },
    { id: ArtifactTypes.XSD, label: "XML Schema" },
];

/**
 * Properties
 */
export interface UploadArtifactFormProps extends PureComponentProps {
    onValid: (valid: boolean) => void;
    onChange: (data: CreateArtifactData) => void;
}

/**
 * State
 */
export interface UploadArtifactFormState extends PureComponentState {
    id: string;
    type: string;
    typeIsExpanded: boolean;
    content: string;
    contentFilename: string;
    contentIsLoading: boolean;
    valid: boolean;
    debouncedOnChange: ((data: CreateArtifactData) => void) | null;
}

/**
 * Models the toolbar for the Artifacts page.
 */
export class UploadArtifactForm extends PureComponent<UploadArtifactFormProps, UploadArtifactFormState> {

    constructor(props: Readonly<UploadArtifactFormProps>) {
        super(props);
    }

    public render(): React.ReactElement {
        return (
            <Form>
                <FormGroup
                    label="ID"
                    fieldId="form-id"
                    helperText="(Optional) Leave the artifact ID empty to let the server auto-generate one."
                >
                    <TextInput
                        isRequired={true}
                        type="text"
                        id="form-id"
                        data-testid="form-id"
                        name="form-id"
                        aria-describedby="form-id-helper"
                        value={this.state.id}
                        placeholder="ID of the artifact"
                        onChange={this.onIdChange}
                    />
                </FormGroup>
                <FormGroup
                    label="Type"
                    fieldId="form-type"
                    isRequired={true}
                >
                    <div>
                        <Dropdown
                            toggle={
                                <DropdownToggle id="form-type-toggle" data-testid="form-type-toggle" onToggle={this.onTypeToggle} iconComponent={CaretDownIcon}>
                                    { this.state.type ? this.typeLabel(this.state.type) : "Auto-Detect" }
                                </DropdownToggle>
                            }
                            onSelect={this.onTypeSelect}
                            isOpen={this.state.typeIsExpanded}
                            dropdownItems={[
                                <DropdownItem key="auto" id="" data-testid="form-type-auto"><i>Auto-Detect</i></DropdownItem>,
                                <DropdownSeparator key="separator" />,
                                ...artifactTypes.map(t =>
                                    <DropdownItem key={t.id} id={t.id} data-testid={`form-type-${t.id}`}>{ t.label }</DropdownItem>
                                )
                            ]}
                        />
                    </div>
                </FormGroup>
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

    protected initializeState(): UploadArtifactFormState {
        return {
            content: "",
            contentFilename: "",
            contentIsLoading: false,
            debouncedOnChange: debounce(this.props.onChange, 200),
            id: "",
            type: "",
            typeIsExpanded: false,
            valid: false
        };
    }

    private onTypeToggle = (isExpanded: boolean): void => {
        this.setSingleState("typeIsExpanded", isExpanded);
    };

    private onTypeSelect = (event: React.SyntheticEvent<HTMLDivElement>|undefined): void => {
        const newType: string = event && event.currentTarget && event.currentTarget.id ? event.currentTarget.id : "";
        this.setState({
            type: newType,
            typeIsExpanded: false
        }, () => {
            this.fireOnChange();
            this.checkValid();
        });
    };

    private onIdChange = (value: any): void => {
        this.setSingleState("id", value, () => {
            this.fireOnChange();
            this.checkValid();
        });
    };

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
        const data: CreateArtifactData = this.currentData();
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

    private isValid(data: CreateArtifactData): boolean {
        return !!data.content;
    }

    private currentData(): CreateArtifactData {
        return {
            content: this.state.content,
            id: this.state.id,
            type: this.state.type
        };
    }

    private fireOnChange(): void {
        if (this.state.debouncedOnChange) {
            const data: CreateArtifactData = this.currentData();
            this.state.debouncedOnChange(data);
        }
    }

    private fireOnValid(): void {
        if (this.props.onValid) {
            this.props.onValid(this.state.valid);
        }
    }

    private typeLabel(type: string): string {
        return artifactTypes.filter( t => {
            return t.id === type;
        }).map( t => t.label )[0];
    }

}
