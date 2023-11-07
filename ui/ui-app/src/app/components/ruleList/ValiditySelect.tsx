import { FunctionComponent, useState } from "react";
import { ObjectDropdown } from "@app/components/common/ObjectDropdown.tsx";


/**
 * Properties
 */
export type ValidityDropdownProps = {
    value: string;
    onSelect: (newValue: string) => void;
};

type ConfigItem = {
    label: string;
    value: string;
    testId: string;
}

const CONFIG_OPTIONS: ConfigItem[] = [
    { label: "Full", value: "FULL", testId: "validity-config-full" },
    { label: "Syntax only", value: "SYNTAX_ONLY", testId: "validity-config-syntax" },
    { label: "None", value: "NONE", testId: "validity-config-none" },
];

const valueToItem = (value: string): ConfigItem => {
    return CONFIG_OPTIONS.filter(item => item.value === value)[0];
};

/**
 * Component.
 */
export const ValiditySelect: FunctionComponent<ValidityDropdownProps> = (props: ValidityDropdownProps) => {
    const [currentValue, setCurrentValue] = useState(valueToItem(props.value));

    const onSelect = (item: any): void => {
        setCurrentValue(item);
        props.onSelect(item.value);
    };

    return (
        <ObjectDropdown
            value={currentValue}
            items={CONFIG_OPTIONS}
            onSelect={onSelect}
            testId="rules-validity-config-toggle"
            itemToTestId={item => item.testId}
            itemToString={item => item.label} />
    );

};
