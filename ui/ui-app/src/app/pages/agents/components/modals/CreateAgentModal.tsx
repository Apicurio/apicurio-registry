import { FunctionComponent, useState } from "react";
import "./CreateAgentModal.css";
import {
    Form,
    FormGroup,
    FormHelperText,
    Grid,
    GridItem,
    HelperText,
    HelperTextItem,
    TextArea,
    TextInput,
    Wizard,
    WizardFooterProps,
    WizardStep
} from "@patternfly/react-core";
import { Modal } from "@patternfly/react-core/deprecated";
import { ExclamationCircleIcon } from "@patternfly/react-icons";
import { AgentCard, AgentCardEditor } from "@app/components/agentCard";
import { CreateArtifact } from "@sdk/lib/generated-client/models";
import { checkIdValid, validateField, ValidType } from "@utils/validation.utils.ts";

type Validities = {
    groupId?: ValidType;
    artifactId?: ValidType;
};

const EMPTY_AGENT_CARD: AgentCard = {
    name: "",
    defaultInputModes: ["text"],
    defaultOutputModes: ["text"]
};

export type CreateAgentModalProps = {
    isOpen: boolean;
    onClose: () => void;
    onCreate: (groupId: string, data: CreateArtifact) => void;
};

export const CreateAgentModal: FunctionComponent<CreateAgentModalProps> = (props: CreateAgentModalProps) => {
    const [validities, setValidities] = useState<Validities>({});
    const [groupId, setGroupId] = useState<string>("");
    const [artifactId, setArtifactId] = useState<string>("");
    const [version, setVersion] = useState<string>("");
    const [agentCard, setAgentCard] = useState<AgentCard>(EMPTY_AGENT_CARD);
    const [name, setName] = useState<string>("");
    const [description, setDescription] = useState<string>("");

    const resetForm = (): void => {
        setValidities({});
        setGroupId("");
        setArtifactId("");
        setVersion("");
        setAgentCard(EMPTY_AGENT_CARD);
        setName("");
        setDescription("");
    };

    const handleClose = (): void => {
        resetForm();
        props.onClose();
    };

    const handleCreate = (): void => {
        const data: CreateArtifact = {
            artifactId: artifactId || undefined,
            artifactType: "AGENT_CARD",
            name: name || agentCard.name || undefined,
            description: description || agentCard.description || undefined,
            firstVersion: {
                version: version || undefined,
                content: {
                    content: JSON.stringify(agentCard, null, 2),
                    contentType: "application/json"
                }
            }
        };
        props.onCreate(groupId || "default", data);
        resetForm();
    };

    const isCoordinatesStepValid = (): boolean => {
        return checkIdValid(groupId) && checkIdValid(artifactId);
    };

    const isContentStepValid = (): boolean => {
        return !!agentCard.name && agentCard.name.trim().length > 0;
    };

    const coordinatesStepFooter: Partial<WizardFooterProps> = {
        isNextDisabled: !isCoordinatesStepValid()
    };

    const contentStepFooter: Partial<WizardFooterProps> = {
        isNextDisabled: !isContentStepValid()
    };

    const metadataStepFooter: Partial<WizardFooterProps> = {
        nextButtonText: "Create",
        onNext: handleCreate
    };

    return (
        <Modal
            title="Create Agent Card"
            variant="large"
            isOpen={props.isOpen}
            onClose={handleClose}
            className="create-agent-modal pf-m-redhat-font"
        >
            <Wizard height={600}>
                <WizardStep
                    name="Agent Coordinates"
                    id="coordinates-step"
                    key={1}
                    footer={coordinatesStepFooter}
                >
                    <Form>
                        <Grid hasGutter md={6}>
                            <GridItem span={6}>
                                <FormGroup label="Group ID" fieldId="group-id">
                                    <TextInput
                                        isRequired={false}
                                        type="text"
                                        id="group-id"
                                        name="group-id"
                                        placeholder="default"
                                        value={groupId}
                                        validated={validities.groupId}
                                        onChange={(_event, value) => {
                                            setGroupId(value);
                                            setValidities({ ...validities, groupId: validateField(value) });
                                        }}
                                    />
                                    <FormHelperText>
                                        <HelperText>
                                            <HelperTextItem variant={validities.groupId}>
                                                {validities.groupId === "error"
                                                    ? "Group ID contains invalid characters."
                                                    : "The group for this agent card. Leave blank for \"default\"."}
                                            </HelperTextItem>
                                        </HelperText>
                                    </FormHelperText>
                                </FormGroup>
                            </GridItem>
                            <GridItem span={6}>
                                <FormGroup label="Artifact ID" fieldId="artifact-id">
                                    <TextInput
                                        isRequired={false}
                                        type="text"
                                        id="artifact-id"
                                        name="artifact-id"
                                        placeholder="Auto-generated if empty"
                                        value={artifactId}
                                        validated={validities.artifactId}
                                        onChange={(_event, value) => {
                                            setArtifactId(value);
                                            setValidities({ ...validities, artifactId: validateField(value) });
                                        }}
                                    />
                                    <FormHelperText>
                                        <HelperText>
                                            <HelperTextItem
                                                variant={validities.artifactId}
                                                icon={validities.artifactId === "error" ? <ExclamationCircleIcon /> : undefined}
                                            >
                                                {validities.artifactId === "error"
                                                    ? "Artifact ID contains invalid characters."
                                                    : "A unique ID for this agent card artifact."}
                                            </HelperTextItem>
                                        </HelperText>
                                    </FormHelperText>
                                </FormGroup>
                            </GridItem>
                            <GridItem span={6}>
                                <FormGroup label="Version" fieldId="version">
                                    <TextInput
                                        isRequired={false}
                                        type="text"
                                        id="version"
                                        name="version"
                                        placeholder="1.0.0"
                                        value={version}
                                        onChange={(_event, value) => setVersion(value)}
                                    />
                                    <FormHelperText>
                                        <HelperText>
                                            <HelperTextItem>The initial version number.</HelperTextItem>
                                        </HelperText>
                                    </FormHelperText>
                                </FormGroup>
                            </GridItem>
                        </Grid>
                    </Form>
                </WizardStep>
                <WizardStep
                    name="Agent Card Content"
                    id="content-step"
                    key={2}
                    footer={contentStepFooter}
                >
                    <AgentCardEditor
                        agentCard={agentCard}
                        onChange={setAgentCard}
                    />
                </WizardStep>
                <WizardStep
                    name="Metadata"
                    id="metadata-step"
                    key={3}
                    footer={metadataStepFooter}
                >
                    <Form>
                        <Grid hasGutter md={6}>
                            <GridItem span={12}>
                                <FormGroup label="Name" fieldId="agent-meta-name">
                                    <TextInput
                                        isRequired={false}
                                        type="text"
                                        id="agent-meta-name"
                                        name="agent-meta-name"
                                        placeholder={agentCard.name || "Agent name"}
                                        value={name}
                                        onChange={(_event, value) => setName(value)}
                                    />
                                    <FormHelperText>
                                        <HelperText>
                                            <HelperTextItem>
                                                Display name for the artifact. Defaults to the agent card name if left blank.
                                            </HelperTextItem>
                                        </HelperText>
                                    </FormHelperText>
                                </FormGroup>
                            </GridItem>
                            <GridItem span={12}>
                                <FormGroup label="Description" fieldId="agent-meta-description">
                                    <TextArea
                                        isRequired={false}
                                        id="agent-meta-description"
                                        name="agent-meta-description"
                                        placeholder={agentCard.description || "A description of this agent"}
                                        value={description}
                                        onChange={(_event, value) => setDescription(value)}
                                        rows={4}
                                    />
                                </FormGroup>
                            </GridItem>
                        </Grid>
                    </Form>
                </WizardStep>
            </Wizard>
        </Modal>
    );
};
