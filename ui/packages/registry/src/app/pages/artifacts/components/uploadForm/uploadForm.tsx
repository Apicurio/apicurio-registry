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
    TextInput,
    FormHelperText
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
    group: string;
    type: string;
    typeIsExpanded: boolean;
    content: string;
    contentFilename: string;
    contentIsLoading: boolean;
    formValid: boolean;
    idValid: boolean;
    groupValid: boolean;
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
                    label="Group & ID"
                    fieldId="form-id"
                    helperText="(Optional) Group and Artifact ID are optional.  If Artifact ID is left blank, the server will generate one for you."
                >
                    <div className="group-and-id">
                        <TextInput
                            className="group"
                            isRequired={false}
                            type="text"
                            id="form-group"
                            data-testid="form-group"
                            name="form-group"
                            aria-describedby="form-group-helper"
                            value={this.state.group}
                            placeholder="Group"
                            onChange={this.onGroupChange}
                            validated={this.groupValidated()}
                        />
                        <span className="separator">/</span>
                        <TextInput
                            className="artifact-id"
                            isRequired={false}
                            type="text"
                            id="form-id"
                            data-testid="form-id"
                            name="form-id"
                            aria-describedby="form-id-helper"
                            value={this.state.id}
                            placeholder="ID of the artifact"
                            onChange={this.onIdChange}
                            validated={this.idValidated()}
                        />
                    </div>
                    <FormHelperText
                        isError={true}
                        isHidden={this.state.idValid && this.state.groupValid}
                    >
                        Character % and non ASCII characters are not allowed
                    </FormHelperText>
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
            group: "",
            type: "",
            typeIsExpanded: false,
            formValid: false,
            idValid: true,
            groupValid: true
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
            this.checkFormValid();
        });
    };

    private onIdChange = (value: any): void => {
        this.setMultiState({
            id: value,
            idValid: this.isIdValid(value)
        }, () => {
            this.fireOnChange();
            this.checkFormValid();
        });
    };

    private onGroupChange = (value: any): void => {
        this.setMultiState({
            group: value,
            groupValid: this.isIdValid(value)
        }, () => {
            this.fireOnChange();
            this.checkFormValid();
        });
    };

    private onContentChange = (value: any, filename: string, event: any): void => {
        this.setSingleState("content", value, () => {
            this.fireOnChange();
            this.checkFormValid();
        });
    };

    private onFileReadStarted = (): void => {
        this.setSingleState("contentIsLoading", true);
    };

    private onFileReadFinished = (): void => {
        this.setSingleState("contentIsLoading", false);
    };

    private checkFormValid(): void {
        const data: CreateArtifactData = this.currentData();
        const oldValid: boolean = this.state.formValid;
        const newValid: boolean = this.isFormValid(data);
        const validityChanged: boolean = oldValid !== newValid;
        this.setState({
            formValid: newValid
        }, () => {
            if (validityChanged) {
                this.fireOnFormValid();
            }
        });
    }

    private isFormValid(data: CreateArtifactData): boolean {
        return !!data.content && this.isIdValid(data.id) && this.isIdValid(data.groupId);
    }

    private isIdValid(id: string|null): boolean {
        if (!id) {
            //id is optional, server can generate it
            return true;
        } else {
            // character % breaks the ui
            const isAscii = (str: string) => {
                for (let i = 0; i < str.length; i++){
                    if(str.charCodeAt(i)>127){
                        return false;
                    }
                }
                return true;
            }
            return id.indexOf("%") == -1 && isAscii(id);
        }
    }

    private currentData(): CreateArtifactData {
        return {
            content: this.state.content,
            groupId: this.state.group,
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

    private fireOnFormValid(): void {
        if (this.props.onValid) {
            this.props.onValid(this.state.formValid);
        }
    }

    private typeLabel(type: string): string {
        return artifactTypes.filter( t => {
            return t.id === type;
        }).map( t => t.label )[0];
    }

    private idValidated(): any {
        const data: CreateArtifactData = this.currentData();
        if (this.isIdValid(data.id)) {
            if (!data.id) {
                return "default"
            }
            return "success"
        } else {
            return "error"
        }
    }

    private groupValidated(): any {
        const data: CreateArtifactData = this.currentData();
        if (this.isIdValid(data.groupId)) {
            if (!data.groupId) {
                return "default"
            }
            return "success"
        } else {
            return "error"
        }
    }

}
