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
import { PureComponent, PureComponentProps, PureComponentState } from "../../../../components";
import {
    Button,
    Dropdown,
    DropdownItem,
    DropdownToggle,
    ExpandableSection,
    Form,
    FormGroup,
    Grid,
    GridItem,
    Modal,
    TextInput
} from "@patternfly/react-core";
import { CaretDownIcon, CheckCircleIcon } from "@patternfly/react-icons";
import { ClientGeneration, Services } from "../../../../../services";
import { If } from "../../../../components/common/if";


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
    downloadData: string;
    isGenerating: boolean;
    isGenerated: boolean;
    isExpanded: boolean;
}

export class GenerateClientModal extends PureComponent<GenerateClientModalProps, GenerateClientModalState> {

    constructor(props: Readonly<GenerateClientModalProps>) {
        super(props);
    }

    public componentDidUpdate(prevProps: GenerateClientModalProps, prevState: GenerateClientModalState) {
        if (prevProps.isOpen !== this.props.isOpen) {
            this.setMultiState(this.initializeState());
        }
    }

    private onToggle = (isExpanded: boolean): void => {
        this.setSingleState("isExpanded", isExpanded);
    };

    private triggerDownload = (): void => {
        const fname: string = `client-${this.state.data.language.toLowerCase()}.zip`;
        Services.getDownloaderService().downloadBase64DataToFS(this.state.downloadData, fname);
    };

    public render(): React.ReactElement {
        const generatingProps = {} as any;
        generatingProps.spinnerAriaValueText = "Generating";
        generatingProps.spinnerAriaLabelledBy = "generate-client-button";
        generatingProps.isLoading = this.state.isGenerating;

        return (
            <Modal
                title="Generate client SDK"
                variant="medium"
                isOpen={this.props.isOpen}
                onClose={this.props.onClose}
                className="generate-client pf-m-redhat-font"
                actions={[
                    <Button key="Generate" variant="primary" id="generate-client-button" data-testid="modal-btn-edit"
                        onClick={this.doGenerate} isDisabled={!this.isGenerateEnabled()} {...generatingProps}
                    ><If condition={this.state.isGenerated}><CheckCircleIcon style={{ marginRight: "5px" }} /></If><span>Generate and download</span></Button>,
                    <Button key="Cancel" variant="link" onClick={this.props.onClose}>Cancel</Button>
                ]}
            >
                <Form>
                    <Grid hasGutter md={6}>
                        <GridItem span={12}>
                            <span>
                                Configure your client SDK before you generate and download it.  You must manually regenerate the client
                                SDK each time a new version of the artifact is registered.
                            </span>
                        </GridItem>

                        <GridItem span={12}>
                            <FormGroup
                                label={<a target="_blank" href="https://github.com/microsoft/kiota#supported-languages">Language</a>}
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
                                        menuAppendTo="parent"
                                        disabled={this.state.isGenerating}
                                        dropdownItems={[
                                            <DropdownItem id="CSharp" key="CSharp" data-testid="form-type-auto"><i>CSharp</i></DropdownItem>,
                                            <DropdownItem id="Go" key="Go" data-testid="form-type-auto"><i>Go</i></DropdownItem>,
                                            <DropdownItem id="Java" key="Java" data-testid="form-type-auto"><i>Java</i></DropdownItem>,
                                            <DropdownItem id="PHP" key="PHP" data-testid="form-type-auto"><i>PHP</i></DropdownItem>,
                                            <DropdownItem id="Python" key="Python" data-testid="form-type-auto"><i>Python</i></DropdownItem>,
                                            <DropdownItem id="Ruby" key="Ruby" data-testid="form-type-auto"><i>Ruby</i></DropdownItem>,
                                            <DropdownItem id="Swift" key="Swift" data-testid="form-type-auto"><i>Swift</i></DropdownItem>,
                                            <DropdownItem id="TypeScript" key="TypeScript" data-testid="form-type-auto"><i>TypeScript</i></DropdownItem>,
                                        ]}
                                    />
                                </div>
                            </FormGroup>
                        </GridItem>

                        <GridItem span={6}>
                            <FormGroup
                                label="Generated client class name"
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
                                    isDisabled={this.state.isGenerating}
                                    onChange={this.onClientNameChange}
                                />
                            </FormGroup>
                        </GridItem>

                        <GridItem span={6}>
                            <FormGroup
                                label="Generated client package name"
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
                                    isDisabled={this.state.isGenerating}
                                    onChange={this.onNamespaceChange}
                                />
                            </FormGroup>
                        </GridItem>

                        <GridItem span={12}>

                            <ExpandableSection toggleText={this.state.isExpanded ? "Hide advanced options" : "Show advanced options"}
                                onToggle={ this.onToggle }
                                isExpanded={ this.state.isExpanded }>

                                <h2 style={{ fontWeight: "bold", fontSize: "large" }}>Include paths</h2>
                                <p style={{ fontSize: "smaller", marginBottom: "15px" }}>
                                    Enter a comma-separated list of patterns to specify the paths used to generate the
                                    client SDK (for example, /., **/my-path/*).
                                </p>

                                <FormGroup
                                    label="Include path patterns"
                                    fieldId="form-include-patterns"
                                    helperText="If this field is empty, all paths are included"
                                >
                                    <TextInput
                                        isRequired={false}
                                        type="text"
                                        id="form-include-patterns"
                                        data-testid="form-include-patterns"
                                        name="form-include-patterns"
                                        aria-describedby="form-include-patterns-helper"
                                        value={this.state.data.includePatterns}
                                        placeholder="Enter path1, path2, ..."
                                        isDisabled={this.state.isGenerating}
                                        onChange={this.onIncludePatternsChange}
                                    />
                                </FormGroup>

                                <FormGroup
                                    label="Exclude path patterns"
                                    fieldId="form-exclude-patterns"
                                    style={{ marginTop: "10px" }}
                                    helperText="If this field is empty, no paths are excluded"
                                >
                                    <TextInput
                                        isRequired={false}
                                        type="text"
                                        id="form-exclude-patterns"
                                        data-testid="form-exclude-patterns"
                                        name="form-exclude-patterns"
                                        aria-describedby="form-exclude-patterns-helper"
                                        value={this.state.data.excludePatterns}
                                        placeholder="Enter path1, path2, ..."
                                        isDisabled={this.state.isGenerating}
                                        onChange={this.onExcludePatternsChange}
                                    />
                                </FormGroup>

                            </ExpandableSection>
                        </GridItem>
                    </Grid>
                </Form>
            </Modal>
        );
    }

    protected initializeState(): GenerateClientModalState {
        return {
            data: {
                content: this.props.artifactContent,
                clientClassName: "MySdkClient",
                namespaceName: "io.example.sdk",
                language: "Java",
                includePatterns: "",
                excludePatterns: "",
            },
            languageIsExpanded: false,
            isValid: true,
            downloadData: "",
            isErrorVisible: false,
            isGenerating: false,
            isGenerated: false,
            isExpanded: false
        };
    }

    private doGenerate = async (): Promise<void> => {
        this.setMultiState({
            isErrorVisible: false,
            isGenerating: true,
            isGenerated: false
        });

        const global = window as any;

        if (global.kiota !== undefined && global.kiota.generate !== undefined) {
            try {
                const zipData = await global.kiota.generate(
                    this.state.data.content,
                    this.state.data.language,
                    this.state.data.clientClassName,
                    this.state.data.namespaceName,
                );

                this.setMultiState({
                    downloadData: zipData,
                    isErrorVisible: false,
                    isGenerating: false,
                    isGenerated: true
                });

                setTimeout(this.triggerDownload, 250);
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
            languageIsExpanded: false
        }, () => {
            this.validate();
        });
    };

    private onClientNameChange = (value: string): void => {
        this.setMultiState({
            data: {
                ...this.state.data,
                clientClassName: value,
            }
        }, () => {
            this.validate();
        });
    };

    private onNamespaceChange = (value: string): void => {
        this.setMultiState({
            data: {
                ...this.state.data,
                namespaceName: value,
            }
        }, () => {
            this.validate();
        });
    };

    private onIncludePatternsChange = (value: string): void => {
        this.setMultiState({
            data: {
                ...this.state.data,
                includePatterns: value,
            }
        }, () => {
            this.validate();
        });
    };

    private onExcludePatternsChange = (value: string): void => {
        this.setMultiState({
            data: {
                ...this.state.data,
                excludePatterns: value,
            }
        }, () => {
            this.validate();
        });
    };

    private isGenerateEnabled = (): boolean => {
        return this.state.isValid && !this.state.isErrorVisible;
    };

    private validate = (): void => {
        const isValid: boolean = (
            this.state.data.clientClassName !== undefined &&
            this.state.data.clientClassName.trim().length > 0 &&
            this.state.data.namespaceName !== undefined &&
            this.state.data.namespaceName.trim().length > 0
        );

        this.setSingleState("isValid", isValid);
    };
}
