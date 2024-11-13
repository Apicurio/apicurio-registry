import React, { FunctionComponent, useEffect, useState } from "react";
import {
    Button,
    ExpandableSection,
    Form,
    FormGroup, FormHelperText,
    Grid,
    GridItem, HelperText, HelperTextItem,
    Modal,
    TextInput
} from "@patternfly/react-core";
import { If, ObjectSelect } from "@apicurio/common-ui-components";
import { ClientGeneration } from "@services/useGroupsService.ts";
import { DownloadService, useDownloadService } from "@services/useDownloadService.ts";
import { CheckCircleIcon } from "@patternfly/react-icons";
import { CodeEditor } from "@patternfly/react-code-editor";
import { ConfigService, useConfigService } from "@services/useConfigService.ts";


/**
 * Properties
 */
export type GenerateClientModalProps = {
    artifactContent: string;
    isOpen: boolean;
    onClose: () => void;
};

/**
 * A modal to prompt the user to generate a client SDK.
 */
export const GenerateClientModal: FunctionComponent<GenerateClientModalProps> = (props: GenerateClientModalProps) => {
    const [data, setData] = useState<ClientGeneration>({
        content: props.artifactContent,
        clientClassName: "MySdkClient",
        namespaceName: "io.example.sdk",
        language: "Java",
        includePatterns: "",
        excludePatterns: "",
    });
    const [isValid, setIsValid] = useState(true);
    const [isErrorVisible, setIsErrorVisible] = useState(false);
    const [errorMessage, setErrorMessage] = useState("");
    const [isGenerating, setIsGenerating] = useState(false);
    const [isGenerated, setIsGenerated] = useState(false);
    const [isExpanded, setIsExpanded] = useState(false);

    const config: ConfigService = useConfigService();
    const download: DownloadService = useDownloadService();

    useEffect(() => {
        validate();
    }, [data]);

    useEffect(() => {
        setData({
            ...data,
            content: props.artifactContent
        });
    }, [props.artifactContent]);

    const onToggle = (_evt: any, isExpanded: boolean): void => {
        setIsExpanded(isExpanded);
    };

    const triggerDownload = (content: string): void => {
        const fname: string = `client-${data.language.toLowerCase()}.zip`;
        download.downloadBase64DataToFS(content, fname);
    };

    const doGenerate = async (): Promise<void> => {
        setIsErrorVisible(false);
        setErrorMessage("");
        setIsGenerating(true);
        setIsGenerated(false);

        const kiotaWasmUrl: string = `${config.uiContextPath() || "/"}kiota-wasm/main.js?url`;
        const { generate } = await import(/* @vite-ignore */ kiotaWasmUrl);

        try {
            console.debug("GENERATING USING KIOTA:");
            console.debug(data);

            const zipData = await generate(
                data.content,
                data.language,
                data.clientClassName,
                data.namespaceName,
                data.includePatterns,
                data.excludePatterns,
            );

            setIsErrorVisible(false);
            setIsGenerated(true);

            setTimeout(() => {
                triggerDownload(zipData);
                setIsGenerating(false);
            }, 250);
        } catch (e) {
            setIsErrorVisible(true);
            setErrorMessage("" + e);
            setIsGenerating(false);
            setIsGenerated(false);
            console.error(e);
        }
    };

    const onLanguageSelect = (selectedLanguage: any): void => {
        const newLang: string = selectedLanguage.id;
        setData({
            ...data,
            language: newLang,
        });
    };

    const onClientNameChange = (_evt: any, value: string): void => {
        setData({
            ...data,
            clientClassName: value,
        });
    };

    const onNamespaceChange = (_evt: any, value: string): void => {
        setData({
            ...data,
            namespaceName: value,
        });
    };

    const onIncludePatternsChange = (_evt: any, value: string): void => {
        setData({
            ...data,
            includePatterns: value,
        });
    };

    const onExcludePatternsChange = (_evt: any, value: string): void => {
        setData({
            ...data,
            excludePatterns: value,
        });
    };

    const isGenerateEnabled = (): boolean => {
        return isValid;
    };

    const validate = (): void => {
        const isValid: boolean = (
            data.clientClassName !== undefined &&
            data.clientClassName.trim().length > 0 &&
            data.namespaceName !== undefined &&
            data.namespaceName.trim().length > 0
        );
        setIsValid(isValid);
    };

    const generatingProps = {
        spinnerAriaValueText: "Generating",
        spinnerAriaLabelledBy: "generate-client-button",
        isLoading: isGenerating
    } as any;

    let actions = [
        <Button key="Generate" variant="primary" id="generate-client-button" data-testid="modal-btn-edit"
            onClick={doGenerate} isDisabled={!isGenerateEnabled() || isGenerating} {...generatingProps}
        ><If condition={isGenerated}><CheckCircleIcon style={{ marginRight: "5px" }} /></If><span>Generate and download</span></Button>,
        <Button key="Cancel" variant="link" onClick={props.onClose}>Cancel</Button>
    ];
    if (isErrorVisible) {
        actions = [
            <Button key="Close" variant="link" onClick={props.onClose}>Close</Button>
        ];
    }

    const onEditorDidMount = (editor: any, monaco: any) => {
        editor.layout();
        editor.focus();
        monaco.editor.getModels()[0].updateOptions({ tabSize: 4 });
    };

    const languages = [
        { id: "CSharp", testId: "language-type-csharp" },
        { id: "Go", testId: "language-type-go" },
        { id: "Java", testId: "language-type-java" },
        { id: "PHP", testId: "language-type-php" },
        { id: "Python", testId: "language-type-python" },
        { id: "Ruby", testId: "language-type-ruby" },
        { id: "Swift", testId: "language-type-swift" },
        { id: "TypeScript", testId: "language-type-typescript" },
    ];
    const selectedLanguage: any = languages.filter(item => item.id === data.language)[0];

    return (
        <Modal
            title="Generate client SDK"
            variant="medium"
            isOpen={props.isOpen}
            onClose={props.onClose}
            className="generate-client pf-m-redhat-font"
            actions={actions}
        >
            <Form>
                <If condition={isErrorVisible}>
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
                        code={errorMessage}
                        onEditorDidMount={onEditorDidMount}
                        height="400px"
                    />
                </If>
                <If condition={!isErrorVisible}>
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
                                    <ObjectSelect
                                        value={selectedLanguage}
                                        items={languages}
                                        onSelect={onLanguageSelect}
                                        itemToString={item => item.id}
                                        itemToTestId={item => item.testId}
                                        appendTo="document"
                                        testId="select-language"
                                        toggleId="select-language-toggle"
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
                                    value={data.clientClassName}
                                    isDisabled={isGenerating}
                                    onChange={onClientNameChange}
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
                                    value={data.namespaceName}
                                    isDisabled={isGenerating}
                                    onChange={onNamespaceChange}
                                />
                            </FormGroup>
                        </GridItem>

                        <GridItem span={12}>

                            <ExpandableSection
                                toggleText={ isExpanded ? "Hide advanced options" : "Show advanced options" }
                                onToggle={ onToggle }
                                isExpanded={ isExpanded }
                            >
                                <h2 style={{ fontWeight: "bold", fontSize: "large" }}>Include paths</h2>
                                <p style={{ fontSize: "smaller", marginBottom: "15px" }}>
                                    Enter a comma-separated list of patterns to specify the paths used to generate the
                                    client SDK (for example, /., **/my-path/*).
                                </p>

                                <FormGroup
                                    label="Include path patterns"
                                    fieldId="form-include-patterns"
                                >
                                    <TextInput
                                        isRequired={false}
                                        type="text"
                                        id="form-include-patterns"
                                        data-testid="form-include-patterns"
                                        name="form-include-patterns"
                                        aria-describedby="form-include-patterns-helper"
                                        value={data.includePatterns}
                                        placeholder="Enter path1, path2, ..."
                                        isDisabled={isGenerating}
                                        onChange={onIncludePatternsChange}
                                    />
                                    <FormHelperText>
                                        <HelperText>
                                            <HelperTextItem>If this field is empty, all paths are included</HelperTextItem>
                                        </HelperText>
                                    </FormHelperText>
                                </FormGroup>

                                <FormGroup
                                    label="Exclude path patterns"
                                    fieldId="form-exclude-patterns"
                                    style={{ marginTop: "10px" }}
                                >
                                    <TextInput
                                        isRequired={false}
                                        type="text"
                                        id="form-exclude-patterns"
                                        data-testid="form-exclude-patterns"
                                        name="form-exclude-patterns"
                                        aria-describedby="form-exclude-patterns-helper"
                                        value={data.excludePatterns}
                                        placeholder="Enter path1, path2, ..."
                                        isDisabled={isGenerating}
                                        onChange={onExcludePatternsChange}
                                    />
                                    <FormHelperText>
                                        <HelperText>
                                            <HelperTextItem>If this field is empty, no paths are excluded</HelperTextItem>
                                        </HelperText>
                                    </FormHelperText>
                                </FormGroup>

                            </ExpandableSection>
                        </GridItem>
                    </Grid>
                </If>
            </Form>
        </Modal>
    );

};
