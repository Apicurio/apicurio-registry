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
import "./generateClientModal.css";
import { PureComponent, PureComponentProps, PureComponentState } from "../../../../components";
import {
    Button,
    Dropdown,
    DropdownItem,
    DropdownToggle,
    Form,
    FormGroup,
    Grid,
    GridItem,
    Label,
    Modal,
    TextInput
} from "@patternfly/react-core";
import { CaretDownIcon } from "@patternfly/react-icons";
import { ClientGeneration } from "../../../../../services";


/**
 * Properties
 */
export interface GenerateClientModalProps extends PureComponentProps {
    artifactContent: string;
    isOpen: boolean;
    onClose: () => void;
}

/**
 * State
 */
export interface GenerateClientModalState extends PureComponentState {
    data: ClientGeneration;
    languageIsExpanded: boolean;
    isValid: boolean;
    isErrorVisible: boolean;
    downloadLink: string;
    isDownloadLinkVisible: boolean;
}

export class GenerateClientModal extends PureComponent<GenerateClientModalProps, GenerateClientModalState> {

    constructor(props: Readonly<GenerateClientModalProps>) {
        super(props);
    }

    private downloadLink(): React.ReactElement {
        console.log(this.state);
        if (this.state.isDownloadLinkVisible) {
            return <a
                        key="DownloadLink"
                        href={this.state.downloadLink}
                        download={"client-" + this.state.data.language.toLowerCase() + ".zip"}
                        onClick={this.onDownloadLinkClick}
                    >
                {this.state.isErrorVisible ? "Error" : "Download"}
            </a>
        } else if (this.state.isErrorVisible) {
            return <Label key="Error">ERROR, please check the console logs for details.</Label>
        } else {
            return <div key="Placeholder"/>
        }
    }

    public render(): React.ReactElement {
        return (
            <Modal
                title="[EXPERIMENTAL] Client generation"
                variant="large"
                isOpen={this.props.isOpen}
                onClose={this.props.onClose}
                className="generate-client pf-m-redhat-font"
                actions={[
                    <Button key="Generate" variant="primary" data-testid="modal-btn-edit" onClick={this.doGenerate} isDisabled={!this.isGenerateEnabled()}>Generate</Button>,
                    this.downloadLink()
                ]}
            >
                <Form>
                    <Grid hasGutter md={6}>
                        <GridItem span={12}>
                            <Label>[EXPERIMENTAL] This is "in-browser" generation of the client code using&nbsp;<a href="https://github.com/microsoft/kiota">Kiota</a>, please refer to&nbsp;
                            <a href="https://microsoft.github.io/kiota/get-started/">the official documentation</a>&nbsp;to get started.</Label>
                        </GridItem>

                        <GridItem span={12}>
                            <FormGroup
                                label="Client Class Name"
                                fieldId="form-client-name"
                            >
                                <TextInput
                                    isRequired={true}
                                    type="text"
                                    id="form-client-name"
                                    data-testid="form-client-name"
                                    name="form-client-name"
                                    aria-describedby="form-client-name-helper"
                                    value={this.state.data.clientClassName}
                                    placeholder="The Class Name to be used"
                                    onChange={this.onClientNameChange}
                                />
                            </FormGroup>
                        </GridItem>

                        <GridItem span={12}>
                            <FormGroup
                                label="Namespace Name"
                                fieldId="form-namespace"
                            >
                                <TextInput
                                    isRequired={true}
                                    type="text"
                                    id="form-namespace"
                                    data-testid="form-namespace"
                                    name="form-namespace"
                                    aria-describedby="form-namespace-helper"
                                    value={this.state.data.namespaceName}
                                    placeholder="The Namespace to be used"
                                    onChange={this.onNamespaceChange}
                                />
                            </FormGroup>
                        </GridItem>

                        <FormGroup
                            label="Language"
                            fieldId="form-language"
                            isRequired={true}
                        >
                            <div>
                                <Dropdown
                                    toggle={
                                        <DropdownToggle id="form-type-toggle" data-testid="form-type-toggle" onToggle={this.onLanguageToggle} toggleIndicator={CaretDownIcon}>
                                            { this.state.data.language ? this.state.data.language : "Java" }
                                        </DropdownToggle>
                                    }
                                    onSelect={this.onLanguageSelect}
                                    isOpen={this.state.languageIsExpanded}
                                    dropdownItems={[
                                        <DropdownItem id="Java" key="Java" data-testid="form-type-auto"><i>Java</i></DropdownItem>,
                                        <DropdownItem id="CSharp" key="CSharp" data-testid="form-type-auto"><i>CSharp</i></DropdownItem>,
                                        <DropdownItem id="Go" key="Go" data-testid="form-type-auto"><i>Go</i></DropdownItem>,
                                        <DropdownItem id="Python" key="Python" data-testid="form-type-auto"><i>Python</i></DropdownItem>,
                                        <DropdownItem id="Ruby" key="Ruby" data-testid="form-type-auto"><i>Ruby</i></DropdownItem>,
                                        <DropdownItem id="TypeScript" key="TypeScript" data-testid="form-type-auto"><i>TypeScript</i></DropdownItem>,
                                        <DropdownItem id="PHP" key="PHP" data-testid="form-type-auto"><i>PHP</i></DropdownItem>,
                                        <DropdownItem id="Swift" key="Swift" data-testid="form-type-auto"><i>Swift</i></DropdownItem>,
                                    ]}
                                />
                            </div>
                        </FormGroup>

                    </Grid>
                </Form>
            </Modal>
        );
    }

    protected initializeState(): GenerateClientModalState {
        return {
            data: {
                content: this.props.artifactContent,
                clientClassName: "MyClient",
                namespaceName: "io.dummy",
                language: "Java"
            },
            languageIsExpanded: false,
            isValid: true,
            downloadLink: "",
            isErrorVisible: false,
            isDownloadLinkVisible: false,
        };
    }

    private doGenerate = async (): Promise<void> => {
        const global = window as any;

        if (global.kiota !== undefined && global.kiota.generate !== undefined) {
            try {
                const zip = 'data:text/plain;base64,' + await global.kiota.generate(
                    this.state.data.content,
                    this.state.data.language,
                    this.state.data.clientClassName,
                    this.state.data.namespaceName,
                );

                this.setMultiState({
                    data: {
                        ...this.state.data,
                    },
                    languageIsExpanded: false,
                    isValid: false,
                    downloadLink: zip,
                    isErrorVisible: false,
                    isDownloadLinkVisible: true,
                });
            } catch (e) {
                this.setSingleState("isErrorVisible", true);
                console.error(e);
            }
        } else {
            console.error("Kiota is not available");
            this.setSingleState("isErrorVisible", true);
        }
    };

    private onLanguageToggle = (isExpanded: boolean): void => {
        this.setSingleState("languageIsExpanded", isExpanded);
    };

    private onLanguageSelect = (event: React.SyntheticEvent<HTMLDivElement>|undefined): void => {
        const newLang: string = event && event.currentTarget && event.currentTarget.id ? event.currentTarget.id : "";
        this.setMultiState({
            data: {
                ...this.state.data,
                language: newLang,
            },
            languageIsExpanded: false,
            isValid: this.state.isValid,
            downloadLink: "",
            isErrorVisible: false,
            isDownloadLinkVisible: false,
        }, () => {
            this.validate();
        });
    };

    private onDownloadLinkClick = (): void => {
        this.setSingleState("isDownloadLinkVisible", false, () => { this.validate(); });
    };

    private onClientNameChange = (value: string): void => {
        this.setMultiState({
            data: {
                ...this.state.data,
                clientClassName: value,
            },
            languageIsExpanded: false,
            isValid: this.state.isValid,
            downloadLink: "",
            isErrorVisible: false,
            isDownloadLinkVisible: false,
        }, () => {
            this.validate();
        });
    };

    private onNamespaceChange = (value: string): void => {
        this.setMultiState({
            data: {
                ...this.state.data,
                namespaceName: value,
            },
            languageIsExpanded: false,
            isValid: this.state.isValid,
            downloadLink: "",
            isErrorVisible: false,
            isDownloadLinkVisible: false,
        }, () => {
            this.validate();
        });
    };

    private isGenerateEnabled = (): boolean => {
        return this.state.isValid && !this.state.isErrorVisible && !this.state.isDownloadLinkVisible;
    };

    private validate = (): void => {
        let isValid: boolean = (
            this.state.data.clientClassName !== undefined &&
            this.state.data.clientClassName.trim().length > 0 &&
            this.state.data.namespaceName !== undefined &&
            this.state.data.namespaceName.trim().length > 0
        );

        this.setSingleState("isValid", isValid);
    };
}
