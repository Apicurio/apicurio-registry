import { FunctionComponent, useEffect, useState } from "react";
import { FormHelperText, HelperText, HelperTextItem, InputGroup, TextInput } from "@patternfly/react-core";
import { isNonNegativeInteger } from "@utils/validation.utils.ts";

/**
 * Properties
 */
export type PropertyInputProps = {
    name: string;
    value: string;
    type:
        | "text"
        | "number"
        ;
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

    const validated = (): "success" | "warning" | "error" | "default" => {
        return isValid ? "default" : "error";
    };

    const handleInputChange = (_event: any, value: string): void => {
        const isValid: boolean = validate(value);
        setCurrentValue(value);
        setIsDirty(value !== props.value);
        setIsValid(isValid);
    };

    const validate = (value: string): boolean => {
        if (props.type === "text") {
            return value.trim().length > 0;
        } else if (props.type === "number") {
            return isNonNegativeInteger(value);
        }
        return true;
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

    const getErrorMessage = (): string => {
        if (props.type === "number") {
            return "Value must be a non-negative integer.";
        }
        return "Value cannot be empty.";
    };

    return (
        <div>
            <InputGroup>
                <TextInput name={ props.name }
                    value={ currentValue }
                    validated={ validated() }
                    onChange={ handleInputChange }
                    onKeyDown={ handleKeyPress }
                    aria-label="configuration property input"/>
            </InputGroup>
            {!isValid && (
                <FormHelperText>
                    <HelperText>
                        <HelperTextItem variant="error">
                            {getErrorMessage()}
                        </HelperTextItem>
                    </HelperText>
                </FormHelperText>
            )}
        </div>
    );

};

