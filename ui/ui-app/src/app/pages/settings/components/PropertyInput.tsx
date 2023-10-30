import { FunctionComponent, useEffect, useState } from "react";
import "./ConfigProperty.css";
import { InputGroup, TextInput } from "@patternfly/react-core";

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
            if (value.trim().length === 0) {
                return false;
            }
            const num: number = Number(value);
            return Number.isInteger(num);
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

    return <InputGroup>
        <TextInput name={ props.name }
            value={ currentValue }
            validated={ validated() }
            onChange={ handleInputChange }
            onKeyDown={ handleKeyPress }
            aria-label="configuration property input"/>
    </InputGroup>;

};
