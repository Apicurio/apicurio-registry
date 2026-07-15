import { FunctionComponent, useState } from "react";
import "./ImportAgentModal.css";
import {
    Alert,
    Form,
    FormGroup,
    FormHelperText,
    Grid,
    GridItem,
    HelperText,
    HelperTextItem,
    TextInput,
    Wizard,
    WizardFooterProps,
    WizardStep
} from "@patternfly/react-core";
import { Modal } from "@patternfly/react-core/deprecated";
import { ExclamationCircleIcon } from "@patternfly/react-icons";
import { UrlUpload } from "@apicurio/common-ui-components";
import { AgentCard, AgentCardViewer } from "@app/components/agentCard";
import { UrlService, useUrlService } from "@services/useUrlService.ts";
import { CreateArtifact } from "@sdk/lib/generated-client/models";

type ValidType = "default" | "success" | "error";

const checkIdValid = (id: string | undefined | null): boolean => {
    if (!id) {
        return true;
    }
    const isAscii = (str: string) => {
        for (let i = 0; i < str.length; i++) {
            if (str.charCodeAt(i) > 127) {
                return false;
            }
        }
        return true;
    };
    return id.indexOf("%") === -1 && isAscii(id);
};

const validateField = (value: string | undefined | null): ValidType => {
    if (!checkIdValid(value)) {
        return "error";
    }
    if (value === undefined || value === null || value === "") {
        return "default";
    }
    return "success";
};

export type ImportAgentModalProps = {
    isOpen: boolean;
    onClose: () => void;
    onImport: (groupId: string, data: CreateArtifact) => void;
};

export const ImportAgentModal: FunctionComponent<ImportAgentModalProps> = (props: ImportAgentModalProps) => {
    const [fetchedContent, setFetchedContent] = useState<string>("");
    const [parsedCard, setParsedCard] = useState<AgentCard>();
    const [fetchError, setFetchError] = useState<string>();
    const [groupId, setGroupId] = useState<string>("");
    const [artifactId, setArtifactId] = useState<string>("");
    const [groupIdValidity, setGroupIdValidity] = useState<ValidType>("default");
    const [artifactIdValidity, setArtifactIdValidity] = useState<ValidType>("default");

    const urlService: UrlService = useUrlService();

    const resetForm = (): void => {
        setFetchedContent("");
        setParsedCard(undefined);
        setFetchError(undefined);
        setGroupId("");
        setArtifactId("");
        setGroupIdValidity("default");
        setArtifactIdValidity("default");
    };

    const handleClose = (): void => {
        resetForm();
        props.onClose();
    };

    const handleUrlChange = (value: string | undefined): void => {
        setFetchError(undefined);
        if (!value) {
            setFetchedContent("");
            setParsedCard(undefined);
            return;
        }
        setFetchedContent(value);
        try {
            const card = JSON.parse(value) as AgentCard;
            if (!card.name) {
                setFetchError("The fetched JSON does not contain a required \"name\" field.");
                setParsedCard(undefined);
                return;
            }
            setParsedCard(card);
        } catch {
            setFetchError("The fetched content is not valid JSON.");
            setParsedCard(undefined);
        }
    };

    const handleImport = (): void => {
        if (!parsedCard) return;
        const data: CreateArtifact = {
            artifactId: artifactId || undefined,
            artifactType: "AGENT_CARD",
            name: parsedCard.name,
            description: parsedCard.description || undefined,
            firstVersion: {
                content: {
                    content: fetchedContent,
                    contentType: "application/json"
                }
            }
        };
        props.onImport(groupId || "default", data);
        resetForm();
    };

    const isUrlStepValid = (): boolean => {
        return !!parsedCard && !fetchError;
    };

    const isSettingsStepValid = (): boolean => {
        return checkIdValid(groupId) && checkIdValid(artifactId);
    };

    const urlStepFooter: Partial<WizardFooterProps> = {
        isNextDisabled: !isUrlStepValid()
    };

    const previewStepFooter: Partial<WizardFooterProps> = {};

    const settingsStepFooter: Partial<WizardFooterProps> = {
        nextButtonText: "Import",
        isNextDisabled: !isSettingsStepValid(),
        onNext: handleImport
    };

    return (
        <Modal
            title="Import Agent from URL"
            variant="large"
            isOpen={props.isOpen}
            onClose={handleClose}
            className="import-agent-modal pf-m-redhat-font"
        >
            <Wizard height={600}>
                <WizardStep
                    name="Agent Card URL"
                    id="url-step"
                    key={1}
                    footer={urlStepFooter}
                >
                    <Form>
                        <FormGroup label="Fetch Agent Card from URL" fieldId="agent-url">
                            <UrlUpload
                                id="agent-card-url"
                                urlPlaceholder="https://agent.example.com/.well-known/agent.json"
                                testId="import-agent-url-upload"
                                onChange={handleUrlChange}
                                onUrlFetch={(url) => urlService.fetchUrlContent(url)}
                            />
                        </FormGroup>
                        {fetchError && (
                            <Alert variant="danger" isInline title="Invalid Agent Card">
                                {fetchError}
                            </Alert>
                        )}
                        {parsedCard && (
                            <Alert variant="success" isInline title={`Agent card "${parsedCard.name}" fetched successfully.`} />
                        )}
                    </Form>
                </WizardStep>
                <WizardStep
                    name="Preview"
                    id="preview-step"
                    key={2}
                    footer={previewStepFooter}
                >
                    {parsedCard && (
                        <AgentCardViewer agentCard={parsedCard} />
                    )}
                </WizardStep>
                <WizardStep
                    name="Import Settings"
                    id="settings-step"
                    key={3}
                    footer={settingsStepFooter}
                >
                    <Form>
                        <Grid hasGutter md={6}>
                            <GridItem span={6}>
                                <FormGroup label="Group ID" fieldId="import-group-id">
                                    <TextInput
                                        isRequired={false}
                                        type="text"
                                        id="import-group-id"
                                        name="import-group-id"
                                        placeholder="default"
                                        value={groupId}
                                        validated={groupIdValidity}
                                        onChange={(_event, value) => {
                                            setGroupId(value);
                                            setGroupIdValidity(validateField(value));
                                        }}
                                    />
                                    <FormHelperText>
                                        <HelperText>
                                            <HelperTextItem variant={groupIdValidity}>
                                                {groupIdValidity === "error"
                                                    ? "Group ID contains invalid characters."
                                                    : "The group for this agent card. Leave blank for \"default\"."}
                                            </HelperTextItem>
                                        </HelperText>
                                    </FormHelperText>
                                </FormGroup>
                            </GridItem>
                            <GridItem span={6}>
                                <FormGroup label="Artifact ID" fieldId="import-artifact-id">
                                    <TextInput
                                        isRequired={false}
                                        type="text"
                                        id="import-artifact-id"
                                        name="import-artifact-id"
                                        placeholder="Auto-generated if empty"
                                        value={artifactId}
                                        validated={artifactIdValidity}
                                        onChange={(_event, value) => {
                                            setArtifactId(value);
                                            setArtifactIdValidity(validateField(value));
                                        }}
                                    />
                                    <FormHelperText>
                                        <HelperText>
                                            <HelperTextItem
                                                variant={artifactIdValidity}
                                                icon={artifactIdValidity === "error" ? <ExclamationCircleIcon /> : undefined}
                                            >
                                                {artifactIdValidity === "error"
                                                    ? "Artifact ID contains invalid characters."
                                                    : "A unique ID for this agent card artifact."}
                                            </HelperTextItem>
                                        </HelperText>
                                    </FormHelperText>
                                </FormGroup>
                            </GridItem>
                        </Grid>
                    </Form>
                </WizardStep>
            </Wizard>
        </Modal>
    );
};
