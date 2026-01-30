import { FunctionComponent, useState } from "react";
import "./AgentCardEditor.css";
import {
    Card,
    CardBody,
    CardHeader,
    CardTitle,
    Divider,
    Form,
    FormGroup,
    TextArea,
    TextInput,
    Title,
    Select,
    SelectOption,
    MenuToggle,
    MenuToggleElement,
    Label,
    LabelGroup,
    InputGroup,
    InputGroupItem
} from "@patternfly/react-core";
import { CapabilityEditor } from "./CapabilityEditor";
import { SkillEditor } from "./SkillEditor";
import { AgentCard } from "./AgentCardViewer";
import { AgentCapabilities } from "./AgentCardCapabilities";
import { AgentSkill } from "./AgentCardSkills";

/**
 * Properties
 */
export type AgentCardEditorProps = {
    agentCard: AgentCard;
    onChange: (agentCard: AgentCard) => void;
    className?: string;
};

const INPUT_MODES = ["text", "image", "audio", "video", "file"];
const OUTPUT_MODES = ["text", "image", "audio", "video", "file"];
const AUTH_SCHEMES = ["bearer", "api-key", "basic", "oauth2", "none"];

/**
 * Component for editing Agent Card content using a form-based interface.
 */
export const AgentCardEditor: FunctionComponent<AgentCardEditorProps> = (props: AgentCardEditorProps) => {
    const { agentCard, onChange, className } = props;
    const [inputModeSelectOpen, setInputModeSelectOpen] = useState(false);
    const [outputModeSelectOpen, setOutputModeSelectOpen] = useState(false);
    const [authSchemeSelectOpen, setAuthSchemeSelectOpen] = useState(false);

    const updateField = <K extends keyof AgentCard>(field: K, value: AgentCard[K]): void => {
        onChange({
            ...agentCard,
            [field]: value
        });
    };

    const handleCapabilitiesChange = (capabilities: AgentCapabilities): void => {
        updateField("capabilities", capabilities);
    };

    const handleSkillsChange = (skills: AgentSkill[]): void => {
        updateField("skills", skills);
    };

    const handleAddInputMode = (mode: string): void => {
        if (!mode || agentCard.defaultInputModes?.includes(mode)) return;
        const modes = [...(agentCard.defaultInputModes || []), mode];
        updateField("defaultInputModes", modes);
    };

    const handleRemoveInputMode = (mode: string): void => {
        const modes = (agentCard.defaultInputModes || []).filter(m => m !== mode);
        updateField("defaultInputModes", modes);
    };

    const handleAddOutputMode = (mode: string): void => {
        if (!mode || agentCard.defaultOutputModes?.includes(mode)) return;
        const modes = [...(agentCard.defaultOutputModes || []), mode];
        updateField("defaultOutputModes", modes);
    };

    const handleRemoveOutputMode = (mode: string): void => {
        const modes = (agentCard.defaultOutputModes || []).filter(m => m !== mode);
        updateField("defaultOutputModes", modes);
    };

    const handleAuthSchemeSelect = (scheme: string): void => {
        const currentSchemes = agentCard.authentication?.schemes || [];
        const newSchemes = currentSchemes.includes(scheme)
            ? currentSchemes.filter(s => s !== scheme)
            : [...currentSchemes, scheme];

        updateField("authentication", {
            ...agentCard.authentication,
            schemes: newSchemes
        });
    };

    return (
        <Card className={`agent-card-editor ${className || ""}`}>
            <CardHeader>
                <CardTitle>
                    <Title headingLevel="h2">Edit Agent Card</Title>
                </CardTitle>
            </CardHeader>
            <CardBody>
                <Form>
                    {/* Basic Info Section */}
                    <Title headingLevel="h3" className="section-header">Basic Information</Title>

                    <FormGroup label="Name" isRequired fieldId="agent-name">
                        <TextInput
                            id="agent-name"
                            value={agentCard.name}
                            onChange={(_event, value) => updateField("name", value)}
                            placeholder="Agent name"
                        />
                    </FormGroup>

                    <FormGroup label="Description" fieldId="agent-description">
                        <TextArea
                            id="agent-description"
                            value={agentCard.description || ""}
                            onChange={(_event, value) => updateField("description", value)}
                            placeholder="Describe what this agent does..."
                            rows={3}
                        />
                    </FormGroup>

                    <FormGroup label="Version" fieldId="agent-version">
                        <TextInput
                            id="agent-version"
                            value={agentCard.version || ""}
                            onChange={(_event, value) => updateField("version", value)}
                            placeholder="e.g., 1.0.0"
                        />
                    </FormGroup>

                    <FormGroup label="URL" fieldId="agent-url">
                        <TextInput
                            id="agent-url"
                            type="url"
                            value={agentCard.url || ""}
                            onChange={(_event, value) => updateField("url", value)}
                            placeholder="https://example.com/agent"
                        />
                    </FormGroup>

                    <Divider className="section-divider" />

                    {/* Provider Section */}
                    <Title headingLevel="h3" className="section-header">Provider</Title>

                    <FormGroup label="Organization" fieldId="provider-org">
                        <TextInput
                            id="provider-org"
                            value={agentCard.provider?.organization || ""}
                            onChange={(_event, value) => updateField("provider", {
                                ...agentCard.provider,
                                organization: value
                            })}
                            placeholder="Organization name"
                        />
                    </FormGroup>

                    <FormGroup label="Provider URL" fieldId="provider-url">
                        <TextInput
                            id="provider-url"
                            type="url"
                            value={agentCard.provider?.url || ""}
                            onChange={(_event, value) => updateField("provider", {
                                ...agentCard.provider,
                                url: value
                            })}
                            placeholder="https://provider.com"
                        />
                    </FormGroup>

                    <Divider className="section-divider" />

                    {/* Input/Output Modes Section */}
                    <Title headingLevel="h3" className="section-header">Input/Output Modes</Title>

                    <FormGroup label="Input Modes" fieldId="input-modes">
                        <InputGroup>
                            <InputGroupItem isFill>
                                <Select
                                    id="input-mode-select"
                                    isOpen={inputModeSelectOpen}
                                    onOpenChange={setInputModeSelectOpen}
                                    toggle={(toggleRef: React.Ref<MenuToggleElement>) => (
                                        <MenuToggle
                                            ref={toggleRef}
                                            onClick={() => setInputModeSelectOpen(!inputModeSelectOpen)}
                                            isExpanded={inputModeSelectOpen}
                                        >
                                            Select input mode...
                                        </MenuToggle>
                                    )}
                                    onSelect={(_event, value) => {
                                        handleAddInputMode(value as string);
                                        setInputModeSelectOpen(false);
                                    }}
                                >
                                    {INPUT_MODES.filter(m => !agentCard.defaultInputModes?.includes(m)).map(mode => (
                                        <SelectOption key={mode} value={mode}>
                                            {mode}
                                        </SelectOption>
                                    ))}
                                </Select>
                            </InputGroupItem>
                        </InputGroup>
                        {agentCard.defaultInputModes && agentCard.defaultInputModes.length > 0 && (
                            <LabelGroup className="modes-list">
                                {agentCard.defaultInputModes.map((mode, index) => (
                                    <Label
                                        key={index}
                                        color="teal"
                                        onClose={() => handleRemoveInputMode(mode)}
                                    >
                                        {mode}
                                    </Label>
                                ))}
                            </LabelGroup>
                        )}
                    </FormGroup>

                    <FormGroup label="Output Modes" fieldId="output-modes">
                        <InputGroup>
                            <InputGroupItem isFill>
                                <Select
                                    id="output-mode-select"
                                    isOpen={outputModeSelectOpen}
                                    onOpenChange={setOutputModeSelectOpen}
                                    toggle={(toggleRef: React.Ref<MenuToggleElement>) => (
                                        <MenuToggle
                                            ref={toggleRef}
                                            onClick={() => setOutputModeSelectOpen(!outputModeSelectOpen)}
                                            isExpanded={outputModeSelectOpen}
                                        >
                                            Select output mode...
                                        </MenuToggle>
                                    )}
                                    onSelect={(_event, value) => {
                                        handleAddOutputMode(value as string);
                                        setOutputModeSelectOpen(false);
                                    }}
                                >
                                    {OUTPUT_MODES.filter(m => !agentCard.defaultOutputModes?.includes(m)).map(mode => (
                                        <SelectOption key={mode} value={mode}>
                                            {mode}
                                        </SelectOption>
                                    ))}
                                </Select>
                            </InputGroupItem>
                        </InputGroup>
                        {agentCard.defaultOutputModes && agentCard.defaultOutputModes.length > 0 && (
                            <LabelGroup className="modes-list">
                                {agentCard.defaultOutputModes.map((mode, index) => (
                                    <Label
                                        key={index}
                                        color="teal"
                                        onClose={() => handleRemoveOutputMode(mode)}
                                    >
                                        {mode}
                                    </Label>
                                ))}
                            </LabelGroup>
                        )}
                    </FormGroup>

                    <Divider className="section-divider" />

                    {/* Authentication Section */}
                    <Title headingLevel="h3" className="section-header">Authentication</Title>

                    <FormGroup label="Supported Schemes" fieldId="auth-schemes">
                        <Select
                            id="auth-scheme-select"
                            isOpen={authSchemeSelectOpen}
                            onOpenChange={setAuthSchemeSelectOpen}
                            toggle={(toggleRef: React.Ref<MenuToggleElement>) => (
                                <MenuToggle
                                    ref={toggleRef}
                                    onClick={() => setAuthSchemeSelectOpen(!authSchemeSelectOpen)}
                                    isExpanded={authSchemeSelectOpen}
                                >
                                    Select authentication schemes...
                                </MenuToggle>
                            )}
                            onSelect={(_event, value) => {
                                handleAuthSchemeSelect(value as string);
                            }}
                        >
                            {AUTH_SCHEMES.map(scheme => (
                                <SelectOption
                                    key={scheme}
                                    value={scheme}
                                    hasCheckbox
                                    isSelected={agentCard.authentication?.schemes?.includes(scheme)}
                                >
                                    {scheme}
                                </SelectOption>
                            ))}
                        </Select>
                        {agentCard.authentication?.schemes && agentCard.authentication.schemes.length > 0 && (
                            <LabelGroup className="modes-list">
                                {agentCard.authentication.schemes.map((scheme, index) => (
                                    <Label
                                        key={index}
                                        color="orange"
                                        onClose={() => handleAuthSchemeSelect(scheme)}
                                    >
                                        {scheme}
                                    </Label>
                                ))}
                            </LabelGroup>
                        )}
                    </FormGroup>

                    <Divider className="section-divider" />

                    {/* Capabilities Section */}
                    <CapabilityEditor
                        capabilities={agentCard.capabilities || {}}
                        onChange={handleCapabilitiesChange}
                    />

                    <Divider className="section-divider" />

                    {/* Skills Section */}
                    <SkillEditor
                        skills={agentCard.skills || []}
                        onChange={handleSkillsChange}
                    />
                </Form>
            </CardBody>
        </Card>
    );
};
