import { FunctionComponent, useMemo, useState } from "react";
import "./PromptTemplatePreviewPanel.css";
import {
    Card,
    CardBody,
    CardHeader,
    CardTitle,
    Divider,
    Form,
    FormGroup,
    TextInput,
    Title
} from "@patternfly/react-core";
import { extractPromptVariables, renderTemplatePreview } from "./PromptTemplateViewer.utils";

export type PromptTemplatePreviewPanelProps = {
    template: string;
    className?: string;
};

// Client-side prompt playground: substitution happens entirely in the browser
// (see renderTemplatePreview), no call to the render API. For a validated,
// server-rendered preview see PromptTemplateTestPanel instead.
export const PromptTemplatePreviewPanel: FunctionComponent<PromptTemplatePreviewPanelProps> = (props: PromptTemplatePreviewPanelProps) => {
    const { template, className } = props;
    const variableNames = useMemo(() => extractPromptVariables(template), [template]);
    const [values, setValues] = useState<Record<string, string>>({});

    const setValue = (name: string, value: string): void => {
        setValues(prev => ({ ...prev, [name]: value }));
    };

    const preview = useMemo(() => renderTemplatePreview(template, values), [template, values]);

    return (
        <Card className={`prompt-template-preview-panel ${className || ""}`}>
            <CardHeader>
                <CardTitle>
                    <Title headingLevel="h3" size="md">Playground</Title>
                </CardTitle>
            </CardHeader>
            <CardBody>
                {variableNames.length > 0 ? (
                    <Form className="preview-panel-form">
                        {variableNames.map((name) => (
                            <FormGroup key={name} label={name} fieldId={`preview-var-${name}`}>
                                <TextInput
                                    id={`preview-var-${name}`}
                                    type="text"
                                    value={values[name] ?? ""}
                                    onChange={(_event, val) => setValue(name, val)}
                                    aria-label={name}
                                />
                            </FormGroup>
                        ))}
                    </Form>
                ) : (
                    <p className="no-variables">No variables detected in this template.</p>
                )}

                <Divider className="section-divider" />
                <Title headingLevel="h4" size="md">Preview</Title>
                <div className="preview-output">{preview}</div>
            </CardBody>
        </Card>
    );
};
