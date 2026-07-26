import { FunctionComponent, useEffect, useState } from "react";
import "./PromptTemplateTestPanel.css";
import {
    ActionGroup,
    Alert,
    Button,
    Card,
    CardBody,
    CardHeader,
    CardTitle,
    Checkbox,
    Form,
    FormGroup,
    FormSelect,
    FormSelectOption,
    Grid,
    Label,
    LabelGroup,
    Spinner,
    TextArea,
    TextInput,
    Title,
    Toolbar,
    ToolbarContent,
    ToolbarItem
} from "@patternfly/react-core";
import { TagIcon } from "@patternfly/react-icons";
import { PromptVariable } from "./PromptTemplateViewer";
import { GroupsService, useGroupsService } from "@services/useGroupsService.ts";
import { RenderPromptResponse, RenderPromptValidationError } from "@models/RenderPromptResponse.ts";
import { ArtifactLabel, LabelsFormGroup } from "@app/components/modals/LabelsFormGroup";
import { labelsToList, listToLabels } from "@utils/labels.utils.ts";
import { VersionMetaData } from "@sdk/lib/generated-client/models";

export type PromptTemplateTestPanelProps = {
    groupId: string;
    artifactId: string;
    version: string;
    variables: Record<string, PromptVariable> | PromptVariable[] | undefined;
    className?: string;
};

const getVariablesList = (variables: Record<string, PromptVariable> | PromptVariable[] | undefined): { name: string; variable: PromptVariable }[] => {
    if (!variables) return [];
    if (Array.isArray(variables)) {
        return variables.map(v => ({ name: v.name || "", variable: v }));
    }
    return Object.entries(variables).map(([name, variable]) => ({ name, variable }));
};

export const PromptTemplateTestPanel: FunctionComponent<PromptTemplateTestPanelProps> = (props: PromptTemplateTestPanelProps) => {
    const groups: GroupsService = useGroupsService();
    const variablesList = getVariablesList(props.variables);

    const initialValues: Record<string, any> = {};
    variablesList.forEach(({ name, variable }) => {
        if (variable.default !== undefined) {
            initialValues[name] = variable.default;
        } else {
            initialValues[name] = "";
        }
    });

    const [values, setValues] = useState<Record<string, any>>(initialValues);

    useEffect(() => {
        setValues(prev => {
            const next = { ...prev };
            variablesList.forEach(({ name, variable }) => {
                if (!(name in next)) {
                    const type = (variable.type || "string").toLowerCase();
                    next[name] = type === "boolean" ? false : (variable.default ?? "");
                }
            });
            return next;
        });
    }, [props.variables]);

    const [renderedOutput, setRenderedOutput] = useState<string>("");
    const [validationErrors, setValidationErrors] = useState<RenderPromptValidationError[]>([]);
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState<string>("");

    const [savedLabels, setSavedLabels] = useState<ArtifactLabel[]>([]);
    const [draftLabels, setDraftLabels] = useState<ArtifactLabel[]>([]);
    const [isEditingLabels, setIsEditingLabels] = useState(false);
    const [isSavingLabels, setIsSavingLabels] = useState(false);
    const [labelsError, setLabelsError] = useState<string>("");
    const requestIdRef = { current: 0 };

    useEffect(() => {
        let gid: string | null = props.groupId;
        if (gid === "default") {
            gid = null;
        }
        const thisRequestId = ++requestIdRef.current;
        groups.getArtifactVersionMetaData(gid, props.artifactId, props.version)
            .then((meta: VersionMetaData) => {
                if (thisRequestId !== requestIdRef.current) {
                    return;
                }
                const loaded = labelsToList(meta.labels);
                setSavedLabels(loaded);
                setDraftLabels(loaded);
            })
            .catch(() => {
                // Non-fatal: if metadata can't be loaded, the toolbar simply starts empty.
            });
    }, [props.groupId, props.artifactId, props.version]);

    const openLabelsEditor = (): void => {
        setDraftLabels(savedLabels);
        setLabelsError("");
        setIsEditingLabels(true);
    };

    const cancelLabelsEditor = (): void => {
        setDraftLabels(savedLabels);
        setLabelsError("");
        setIsEditingLabels(false);
    };

    const saveLabels = (): void => {
        let gid: string | null = props.groupId;
        if (gid === "default") {
            gid = null;
        }
        setIsSavingLabels(true);
        setLabelsError("");
        groups.updateArtifactVersionMetaData(gid, props.artifactId, props.version, {
            labels: listToLabels(draftLabels)
        })
            .then(() => {
                setSavedLabels(draftLabels);
                setIsEditingLabels(false);
            })
            .catch((err: any) => {
                setLabelsError(err?.message || "Error saving tags for this version.");
            })
            .finally(() => {
                setIsSavingLabels(false);
            });
    };

    const setValue = (name: string, value: any): void => {
        setValues(prev => ({ ...prev, [name]: value }));
    };

    const doRender = (): void => {
        setIsLoading(true);
        setError("");
        setValidationErrors([]);
        setRenderedOutput("");

        let gid: string | null = props.groupId;
        if (gid === "default") {
            gid = null;
        }

        groups.renderPromptTemplate(gid, props.artifactId, props.version, values)
            .then((response: RenderPromptResponse) => {
                setRenderedOutput(response.rendered || "");
                if (response.validationErrors && response.validationErrors.length > 0) {
                    setValidationErrors(response.validationErrors);
                }
            })
            .catch((err: any) => {
                setError(err?.message || "Error rendering prompt template");
            })
            .finally(() => {
                setIsLoading(false);
            });
    };

    const renderField = (name: string, variable: PromptVariable): React.ReactNode => {
        const type = (variable.type || "string").toLowerCase();

        if (variable.enum && variable.enum.length > 0) {
            return (
                <FormSelect
                    value={values[name] || ""}
                    onChange={(_event, val) => setValue(name, val)}
                    aria-label={name}
                >
                    <FormSelectOption key="placeholder" value="" label="-- Select --" />
                    {variable.enum.map((opt, i) => (
                        <FormSelectOption key={i} value={opt} label={opt} />
                    ))}
                </FormSelect>
            );
        }

        switch (type) {
            case "boolean":
                return (
                    <Checkbox
                        id={`var-${name}`}
                        isChecked={!!values[name]}
                        onChange={(_event, checked) => setValue(name, checked)}
                        label={name}
                    />
                );
            case "integer":
            case "number":
                return (
                    <TextInput
                        type="number"
                        value={values[name] || ""}
                        onChange={(_event, val) => setValue(name, type === "integer" ? parseInt(val) || "" : parseFloat(val) || "")}
                        aria-label={name}
                    />
                );
            case "array":
            case "object":
                return (
                    <TextArea
                        value={typeof values[name] === "string" ? values[name] : JSON.stringify(values[name] || "", null, 2)}
                        onChange={(_event, val) => {
                            try {
                                setValue(name, JSON.parse(val));
                            } catch {
                                setValue(name, val);
                            }
                        }}
                        aria-label={name}
                        rows={3}
                    />
                );
            default:
                return (
                    <TextInput
                        type="text"
                        value={values[name] || ""}
                        onChange={(_event, val) => setValue(name, val)}
                        aria-label={name}
                    />
                );
        }
    };

    return (
        <Card className={`prompt-template-test-panel ${props.className || ""}`}>
            <CardHeader>
                <CardTitle>
                    <Title headingLevel="h3" size="md">Test Prompt</Title>
                </CardTitle>
                <Toolbar className="playground-toolbar" inset={{ default: "insetNone" }}>
                    <ToolbarContent>
                        <ToolbarItem>
                            <LabelGroup data-testid="playground-version-labels">
                                {savedLabels.filter(l => l.name).map((label) => (
                                    <Label
                                        key={`${label.name}=${label.value ?? ""}`}
                                        color="purple"
                                        isCompact
                                    >
                                        {label.name}{label.value ? `=${label.value}` : ""}
                                    </Label>
                                ))}
                            </LabelGroup>
                        </ToolbarItem>
                        <ToolbarItem align={{ default: "alignEnd" }}>
                            <Button
                                variant="link"
                                icon={<TagIcon />}
                                data-testid="playground-tag-version-button"
                                onClick={() => isEditingLabels ? cancelLabelsEditor() : openLabelsEditor()}
                            >
                                Tag this version
                            </Button>
                        </ToolbarItem>
                    </ToolbarContent>
                </Toolbar>
                {isEditingLabels && (
                    <Grid hasGutter className="playground-labels-editor" data-testid="playground-labels-editor">
                        <LabelsFormGroup labels={draftLabels} onChange={setDraftLabels} />
                        {labelsError && (
                            <Alert variant="danger" isInline title={labelsError} />
                        )}
                        <ActionGroup>
                            <Button
                                variant="primary"
                                data-testid="playground-labels-save-button"
                                onClick={saveLabels}
                                isDisabled={isSavingLabels}
                                isLoading={isSavingLabels}
                            >
                                Save
                            </Button>
                            <Button
                                variant="link"
                                data-testid="playground-labels-cancel-button"
                                onClick={cancelLabelsEditor}
                                isDisabled={isSavingLabels}
                            >
                                Cancel
                            </Button>
                        </ActionGroup>
                    </Grid>
                )}
            </CardHeader>
            <CardBody>
                <Form className="test-panel-form">
                    {variablesList.map(({ name, variable }, index) => (
                        <FormGroup
                            key={index}
                            label={variable.description ? `${name} - ${variable.description}` : name}
                            isRequired={variable.required}
                            fieldId={`var-${name}`}
                        >
                            {renderField(name, variable)}
                        </FormGroup>
                    ))}
                    <ActionGroup>
                        <Button
                            variant="primary"
                            onClick={doRender}
                            isDisabled={isLoading}
                            isLoading={isLoading}
                        >
                            Render
                        </Button>
                    </ActionGroup>
                </Form>

                {isLoading && <Spinner size="md" />}

                {error && (
                    <Alert variant="danger" title="Render Error" className="validation-errors">
                        {error}
                    </Alert>
                )}

                {validationErrors.length > 0 && (
                    <Alert variant="warning" title="Validation Errors" className="validation-errors">
                        <ul>
                            {validationErrors.map((ve, i) => (
                                <li key={i}>{ve.path ? `${ve.path}: ` : ""}{ve.message}</li>
                            ))}
                        </ul>
                    </Alert>
                )}

                {renderedOutput && (
                    <>
                        <Title headingLevel="h4" size="md" style={{ marginTop: "16px" }}>Rendered Output</Title>
                        <div className="rendered-output">{renderedOutput}</div>
                    </>
                )}
            </CardBody>
        </Card>
    );
};
