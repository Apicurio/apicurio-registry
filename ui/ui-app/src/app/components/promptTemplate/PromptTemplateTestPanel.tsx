import { FunctionComponent, useCallback, useEffect, useMemo, useRef, useState } from "react";
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
    Spinner,
    TextArea,
    TextInput,
    Title
} from "@patternfly/react-core";
import { PromptVariable } from "./PromptTemplateViewer";
import { GroupsService, useGroupsService } from "@services/useGroupsService.ts";
import { RenderPromptResponse, RenderPromptValidationError } from "@models/RenderPromptResponse.ts";
import { coerceEnumValue } from "./PromptTemplateTestPanel.utils";

export type PromptTemplateTestPanelProps = {
    groupId: string;
    artifactId: string;
    version: string;
    variables: Record<string, PromptVariable> | PromptVariable[] | undefined;
    className?: string;
};

type VariableValue = string | number | boolean | Record<string, unknown> | unknown[];

const getVariablesList = (variables: Record<string, PromptVariable> | PromptVariable[] | undefined): { name: string; variable: PromptVariable }[] => {
    if (!variables) return [];
    if (Array.isArray(variables)) {
        return variables.map(v => ({ name: v.name || "", variable: v }));
    }
    return Object.entries(variables).map(([name, variable]) => ({ name, variable }));
};

export const PromptTemplateTestPanel: FunctionComponent<PromptTemplateTestPanelProps> = (props: PromptTemplateTestPanelProps) => {
    const groups: GroupsService = useGroupsService();
    const groupsRef = useRef<GroupsService>(groups);
    useEffect(() => {
        groupsRef.current = groups;
    });

    const variablesList = useMemo(
        () => getVariablesList(props.variables),
        [props.variables]
    );

    const [values, setValues] = useState<Record<string, VariableValue>>(() => {
        const init: Record<string, VariableValue> = {};
        getVariablesList(props.variables).forEach(({ name, variable }) => {
            init[name] = variable.default !== undefined ? variable.default : "";
        });
        return init;
    });

    useEffect(() => {
        setValues(prev => {
            const next = { ...prev };
            variablesList.forEach(({ name, variable }) => {
                if (!(name in next)) {
                    const type = (variable.type || "string").toLowerCase();
                    next[name] = variable.default ?? (type === "boolean" ? false : "");
                }
            });
            return next;
        });
    }, [variablesList]);

    const [renderedOutput, setRenderedOutput] = useState<string>("");
    const [validationErrors, setValidationErrors] = useState<RenderPromptValidationError[]>([]);
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState<string>("");

    const setValue = useCallback((name: string, value: VariableValue): void => {
        setValues(prev => ({ ...prev, [name]: value }));
    }, []);

    const doRender = useCallback((): void => {
        setIsLoading(true);
        setError("");
        setValidationErrors([]);
        setRenderedOutput("");

        let gid: string | null = props.groupId;
        if (gid === "default") {
            gid = null;
        }

        groupsRef.current.renderPromptTemplate(gid, props.artifactId, props.version, values)
            .then((response: RenderPromptResponse) => {
                setRenderedOutput(response.rendered || "");
                if (response.validationErrors && response.validationErrors.length > 0) {
                    setValidationErrors(response.validationErrors);
                }
            })
            .catch((err: unknown) => {
                const message = err instanceof Error
                    ? err.message
                    : (err && typeof err === "object" && "message" in err)
                        ? (err as { message: string }).message
                        : "Error rendering prompt template";
                setError(message);
            })
            .finally(() => {
                setIsLoading(false);
            });
    }, [props.groupId, props.artifactId, props.version, values]);

    const renderField = (name: string, variable: PromptVariable): React.ReactNode => {
        const type = (variable.type || "string").toLowerCase();

        if (variable.enum && variable.enum.length > 0) {
            return (
                <FormSelect
                    value={(values[name] as string) ?? ""}
                    onChange={(_event, val) => setValue(name, coerceEnumValue(val, type))}
                    aria-label={name}
                >
                    <FormSelectOption key="placeholder" value="" label="-- Select --" />
                    {variable.enum.map((opt) => (
                        <FormSelectOption key={String(opt)} value={opt} label={String(opt)} />
                    ))}
                </FormSelect>
            );
        }

        switch (type) {
            case "boolean": {
                const booleanLabelText = variable.description ? `${name} - ${variable.description}` : name;
                return (
                    <Checkbox
                        id={`var-${name}`}
                        isChecked={!!values[name]}
                        onChange={(_event, checked) => setValue(name, checked)}
                        label={
                            variable.required ? (
                                <>
                                    {booleanLabelText}
                                    <span
                                        aria-hidden="true"
                                        className="required-asterisk"
                                    >
                                        *
                                    </span>
                                </>
                            ) : booleanLabelText
                        }
                    />
                );
            }
            case "integer":
            case "number":
                return (
                    <TextInput
                        type="number"
                        value={(values[name] as string | number) ?? ""}
                        onChange={(_event, val) => {
                            const n = type === "integer" ? parseInt(val) : parseFloat(val);
                            setValue(name, isNaN(n) ? "" : n);
                        }}
                        aria-label={name}
                    />
                );
            case "array":
            case "object":
                return (
                    <TextArea
                        value={typeof values[name] === "string" ? (values[name] as string) : JSON.stringify(values[name] || "", null, 2)}
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
                        value={(values[name] as string) || ""}
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
            </CardHeader>
            <CardBody>
                <Form className="test-panel-form">
                    {variablesList.map(({ name, variable }) => {
                        const isBoolean = (variable.type || "string").toLowerCase() === "boolean";
                        return (
                            <FormGroup
                                key={name}
                                label={isBoolean ? undefined : (variable.description ? `${name} - ${variable.description}` : name)}
                                isRequired={variable.required}
                                fieldId={`var-${name}`}
                            >
                                {renderField(name, variable)}
                            </FormGroup>
                        );
                    })}
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
                            {validationErrors.map((ve) => (
                                <li key={`${ve.path ?? "root"}-${ve.message}`}>{ve.path ? `${ve.path}: ` : ""}{ve.message}</li>
                            ))}
                        </ul>
                    </Alert>
                )}

                {renderedOutput && (
                    <>
                        <Title headingLevel="h4" size="md" className="rendered-output-title">Rendered Output</Title>
                        <div className="rendered-output">{renderedOutput}</div>
                    </>
                )}
            </CardBody>
        </Card>
    );
};
