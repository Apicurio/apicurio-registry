import { FunctionComponent, useEffect, useState } from "react";
import {
    InputGroup,
    TextInput,
    HelperText,
    HelperTextItem
} from "@patternfly/react-core";
import { validatePropertyValue } from "./PropertyInput.utils";

/**
 * Properties
 */
export type PropertyInputProps = {
    name: string;
    value: string;
    type: "text" | "number";
    onChange: (newValue: string) => void;
    onValid: (valid: boolean) => void;
    onCancel: () => void;
    onSave: () => void;
};

/**
 * Models a single editable config property.
 */
export const PropertyInput: FunctionComponent<PropertyInputProps> = (props: PropertyInputProps) => {
    const [currentValue, setCurrentValue] = useState<string>(props.value);
    const [isDirty, setIsDirty] = useState(false);
    const [isValid, setIsValid] = useState(true);
    const [errorMessage, setErrorMessage] = useState<string | undefined>();

    const validated = (): "success" | "warning" | "error" | "default" => {
        return isValid ? "default" : "error";
    };

    const handleInputChange = (_event: any, value: string): void => {
        const validation = validatePropertyValue(value, props.type);

        setCurrentValue(value);
        setIsDirty(value !== props.value);
        setIsValid(validation.isValid);
        setErrorMessage(validation.errorMessage);
    };

    const handleKeyPress = (event: any): void => {
        if (event.code === "Escape") {
            props.onCancel();
        }

        if (event.code === "Enter" && isDirty && isValid) {
            props.onSave();
        }
    };

    useEffect(() => {
        props.onValid(isValid);
    }, [isValid]);

    useEffect(() => {
        props.onChange(currentValue);
    }, [currentValue]);

    const errorId = `${props.name}-error`;

    return (
        <>
            <InputGroup>
                <TextInput
                    name={props.name}
                    value={currentValue}
                    type={props.type === "number" ? "number" : "text"}
                    min={props.type === "number" ? 0 : undefined}
                    validated={validated()}
                    onChange={handleInputChange}
                    onKeyDown={handleKeyPress}
                    aria-label="configuration property input"
                    aria-describedby={errorMessage ? errorId : undefined}
                />
            </InputGroup>

            {errorMessage && (
                <HelperText>
                    <HelperTextItem id={errorId} variant="error">
                        {errorMessage}
                    </HelperTextItem>
                </HelperText>
            )}
        </>
    );
};
