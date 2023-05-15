import React from "react";
import "./generateClientModal.css";
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
import { CodeEditor } from "@patternfly/react-code-editor";


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
    errorMessage: string;
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

        let actions = [
            <Button key="Generate" variant="primary" id="generate-client-button" data-testid="modal-btn-edit"
                onClick={this.doGenerate} isDisabled={!this.isGenerateEnabled()} {...generatingProps}
            ><If condition={this.state.isGenerated}><CheckCircleIcon style={{ marginRight: "5px" }} /></If><span>Generate and download</span></Button>,
            <Button key="Cancel" variant="link" onClick={this.props.onClose}>Cancel</Button>
        ];
        if (this.state.isErrorVisible) {
            actions = [
                <Button key="Close" variant="link" onClick={this.props.onClose}>Close</Button>
            ];
        }

        const onEditorDidMount = (editor, monaco) => {
            editor.layout();
            editor.focus();
            monaco.editor.getModels()[0].updateOptions({ tabSize: 4 });
        };

        return (
            <Modal
                title="Generate client SDK"
                variant="medium"
                isOpen={this.props.isOpen}
                onClose={this.props.onClose}
                className="generate-client pf-m-redhat-font"
                actions={actions}
            >
                <Form>
                    <If condition={this.state.isErrorVisible}>
                        <p>
                            Invalid artifact content.  See the log below for details.  When the issue is resolved,
                            upload a new version of the artifact and then try again.
                        </p>
                        <CodeEditor
                            id="error-console"
                            className="error-console"
                            isDarkTheme={false}
                            isLineNumbersVisible={true}
                            isReadOnly={true}
                            isMinimapVisible={false}
                            isLanguageLabelVisible={false}
                            code={this.state.errorMessage}
                            onEditorDidMount={onEditorDidMount}
                            height="400px"
                        />
                    </If>
                    <If condition={!this.state.isErrorVisible}>
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
                    </If>
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
            errorMessage: "",
            isGenerating: false,
            isGenerated: false,
            isExpanded: false
        };
    }

    private doGenerate = async (): Promise<void> => {
        this.setMultiState({
            isErrorVisible: false,
            errorMessage: "",
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
                    this.state.data.includePatterns,
                    this.state.data.excludePatterns,
                );

                this.setMultiState({
                    downloadData: zipData,
                    isErrorVisible: false,
                    isGenerating: false,
                    isGenerated: true
                });

                setTimeout(this.triggerDownload, 250);
            } catch (e) {
                this.setMultiState({
                    isErrorVisible: true,
                    errorMessage: "" + e,
                    isGenerating: false,
                    isGenerated: false,
                });
                console.error(e);
            }
        } else {
            console.error("Kiota is not available");
            this.setMultiState({
                isErrorVisible: true,
                errorMessage: "Kiota is not available in the runtime",
                isGenerating: false,
                isGenerated: false
            });
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
        return this.state.isValid;
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
