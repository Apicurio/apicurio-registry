import { FunctionComponent, ReactNode, useMemo } from "react";
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
    Label,
    Spinner,
    TextArea,
    TextInput,
    Title
} from "@patternfly/react-core";
import {
    extractTemplateVariableNames,
    reconcileTemplateVariables,
    ReconciledVariable,
    VariableSchema
} from "./promptTemplateVariables";
import { useGroupsService } from "@services/useGroupsService.ts";
import {
    coerceEnumValue,
    schemaForField,
    toDeclaredMap
} from "./PromptTemplateTestPanel.utils";
import { usePromptTemplateTestPanelState } from "./usePromptTemplateTestPanelState";

export type PromptTemplateTestPanelProps = {
    groupId: string;
    artifactId: string;
    version: string;
    template?: string;
    variables: Record<string, VariableSchema> | VariableSchema[] | undefined;
    className?: string;
};

const sourceLabel = (source: ReconciledVariable["source"]): { text: string; color: "grey" | "orange" } | undefined => {
    if (source === "detected") {
        return { text: "not in schema", color: "orange" };
    }
    if (source === "declared") {
        return { text: "unused", color: "grey" };
    }
    return undefined;
};

export const PromptTemplateTestPanel: FunctionComponent<PromptTemplateTestPanelProps> = (props: PromptTemplateTestPanelProps) => {
    const groups = useGroupsService();

    const reconciledVariables = useMemo(() => {
        const names = extractTemplateVariableNames(props.template || "");
        return reconcileTemplateVariables(names, toDeclaredMap(props.variables));
    }, [props.template, props.variables]);

    const {
        values,
        renderedOutput,
        validationErrors,
        isLoading,
        error,
        setValue,
        doRender
    } = usePromptTemplateTestPanelState({
        groupId: props.groupId,
        artifactId: props.artifactId,
        version: props.version,
        template: props.template,
        variables: props.variables,
        groups
    });

    const renderField = (name: string, variable: VariableSchema): ReactNode => {
        const type = (variable.type || "string").toLowerCase();

        if (variable.enum && variable.enum.length > 0) {
            return (
                <FormSelect
                    value={values[name] ?? ""}
                    onChange={(_event, val) => setValue(name, coerceEnumValue(val, type))}
                    aria-label={name}
                >
                    <FormSelectOption key="placeholder" value="" label="-- Select --" />
                    {variable.enum.map((opt, i) => (
                        <FormSelectOption key={i} value={String(opt)} label={String(opt)} />
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
                                        style={{ color: "var(--pf-t--global--color--status--danger--default)", marginLeft: "0.25rem" }}
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
                        value={values[name] ?? ""}
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

    const renderLabel = (entry: ReconciledVariable): ReactNode => {
        const schema = schemaForField(entry);
        const base = schema.description ? `${entry.name} - ${schema.description}` : entry.name;
        const indicator = sourceLabel(entry.source);
        if (!indicator) {
            return base;
        }
        return (
            <span className="label-inline">
                <span>{base}</span>
                <Label color={indicator.color} isCompact>
                    {indicator.text}
                </Label>
            </span>
        );
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
                    {reconciledVariables.map((entry) => {
                        const schema = schemaForField(entry);
                        const isBoolean = (schema.type || "string").toLowerCase() === "boolean";
                        const indicator = sourceLabel(entry.source);
                        return (
                            <FormGroup
                                key={`${entry.source}-${entry.name}`}
                                label={isBoolean ? undefined : renderLabel(entry)}
                                isRequired={!!schema.required}
                                fieldId={`var-${entry.name}`}
                            >
                                {isBoolean && indicator && (
                                    <Label color={indicator.color} isCompact style={{ marginBottom: "0.5rem" }}>
                                        {indicator.text}
                                    </Label>
                                )}
                                {renderField(entry.name, schema)}
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
                            {validationErrors.map((ve, i) => (
                                <li key={i}>
                                    {ve.variableName ? `${ve.variableName}: ` : ""}
                                    {ve.message}
                                    {ve.expectedType && ve.actualType && (
                                        <span style={{ color: "var(--pf-t--global--color--600)", marginLeft: "0.5rem" }}>
                                            (Expected: {ve.expectedType}, Actual: {ve.actualType})
                                        </span>
                                    )}
                                </li>
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
