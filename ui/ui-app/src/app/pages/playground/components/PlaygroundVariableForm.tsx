import { FunctionComponent } from "react";
import {
    ActionGroup,
    Button,
    Form,
    FormGroup,
    TextInput,
} from "@patternfly/react-core";

export type PlaygroundVariableFormProps = {
    variables: string[];
    values: Record<string, string>;
    isRendering: boolean;
    onValueChange: (varName: string, newValue: string) => void;
    onRender: () => void;
};

/**
 * Renders a form with one text input per extracted template variable,
 * plus a Render button. Extracted from PromptPlaygroundPage to follow SRP.
 */
export const PlaygroundVariableForm: FunctionComponent<PlaygroundVariableFormProps> = (props) => {
    const { variables, values, isRendering, onValueChange, onRender } = props;

    return (
        <Form className="test-panel-form">
            {variables.length === 0 && (
                <span className="no-variables-text">
                    No variables detected in template.
                </span>
            )}
            {variables.map((varName) => (
                <FormGroup
                    key={varName}
                    label={varName}
                    fieldId={`playground-var-${varName}`}
                >
                    <TextInput
                        type="text"
                        id={`playground-var-${varName}`}
                        value={values[varName] || ""}
                        onChange={(_event, val) => onValueChange(varName, val)}
                        aria-label={varName}
                        placeholder={`Enter value for {{${varName}}}`}
                    />
                </FormGroup>
            ))}
            <ActionGroup>
                <Button
                    variant="primary"
                    onClick={onRender}
                    isDisabled={isRendering || variables.length === 0}
                    isLoading={isRendering}
                >
                    Render
                </Button>
            </ActionGroup>
        </Form>
    );
};
