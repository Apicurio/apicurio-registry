import { FunctionComponent, useEffect, useState } from "react";
import {
    InputGroup,
    TextInput,
    FormSelect,
    FormSelectOption,
    HelperText,
    HelperTextItem
} from "@patternfly/react-core";
import { validatePropertyValue, LOG_LEVEL_OPTIONS } from "./PropertyInput.utils";

export { LOG_LEVEL_OPTIONS };

/**
 * Properties
 */
export type PropertyInputProps = {
    name: string;
    value: string;
    type: "text" | "number";
    options?: string[];
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
        const validation = validatePropertyValue(value, props.type, props.options);

        setCurrentValue(value);
        setIsDirty(value !== props.value);
        setIsValid(validation.isValid);
        setErrorMessage(validation.errorMessage);
    };

    const handleSelectChange = (_event: any, value: string): void => {
        const validation = validatePropertyValue(value, props.type, props.options);

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
                {props.options && props.options.length > 0 ? (
                    <FormSelect
                        name={props.name}
                        value={currentValue}
                        onChange={handleSelectChange}
                        onKeyDown={handleKeyPress}
                        aria-label="configuration property input"
                        aria-describedby={errorMessage ? errorId : undefined}
                    >
                        {props.options.map((opt) => (
                            <FormSelectOption key={opt} value={opt} label={opt} />
                        ))}
                    </FormSelect>
                ) : (
                    <TextInput
                        name={props.name}
                        value={currentValue}
                        validated={validated()}
                        onChange={handleInputChange}
                        onKeyDown={handleKeyPress}
                        aria-label="configuration property input"
                        aria-describedby={errorMessage ? errorId : undefined}
                    />
                )}
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
