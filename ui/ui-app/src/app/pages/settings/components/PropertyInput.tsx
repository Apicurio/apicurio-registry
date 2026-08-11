import { FunctionComponent, useEffect, useState } from "react";
import {
    InputGroup,
    TextInput,
    HelperText,
    HelperTextItem
} from "@patternfly/react-core";

/**
 * Properties
 */
export type PropertyInputProps = {
    name: string;
    value: string;
    type:
        | "text"
        | "number";
    onChange: (newValue: string) => void;
    onValid: (valid: boolean) => void;
    onCancel: () => void;
    onSave: () => void;
}

/**
 * Models a single editable config property.
 */
export const PropertyInput: FunctionComponent<PropertyInputProps> = (props: PropertyInputProps) => {
    const [currentValue, setCurrentValue] = useState<string>(props.value);
    const [isDirty, setIsDirty] = useState(false);
    const [isValid, setIsValid] = useState(true);
    const [errorMessage, setErrorMessage] = useState<string | undefined>();

    type ValidationResult = {
        isValid: boolean;
        errorMessage?: string;
    };

    const validated = (): "success" | "warning" | "error" | "default" => {
        return isValid ? "default" : "error";
    };

    const validate = (value: string): ValidationResult => {
        if (props.type === "text") {
            return value.trim().length > 0
                ? { isValid: true }
                : {
                    isValid: false,
                    errorMessage: "Value cannot be empty"
                };
        }

        if (props.type === "number") {
            if (value.trim().length === 0) {
                return {
                    isValid: false,
                    errorMessage: "Value cannot be empty"
                };
            }

            const num: number = Number(value);

            return Number.isInteger(num)
                ? { isValid: true }
                : {
                    isValid: false,
                    errorMessage: "Value must be a valid integer"
                };
        }

        return { isValid: true };
    };

    const handleInputChange = (_event: any, value: string): void => {
        const validation = validate(value);

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

    return <InputGroup>
        <TextInput name={props.name}
            value={currentValue}
            validated={validated()}
            onChange={handleInputChange}
            onKeyDown={handleKeyPress}
            aria-label="configuration property input"/>
        {errorMessage && (
            <HelperText>
                <HelperTextItem variant="error">
                    {errorMessage}
                </HelperTextItem>
            </HelperText>
        )}
    </InputGroup>;
};
