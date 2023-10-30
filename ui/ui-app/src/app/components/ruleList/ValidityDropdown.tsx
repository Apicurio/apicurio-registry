
import React, { FunctionComponent, useState } from "react";
import {
    Dropdown,
    DropdownItem,
    DropdownList,
    MenuToggle,
    MenuToggleElement
} from "@patternfly/react-core";


/**
 * Properties
 */
export type ValidityDropdownProps = {
    value: string;
    onSelect: (newValue: string) => void;
};


/**
 * Component.
 */
export const ValidityDropdown: FunctionComponent<ValidityDropdownProps> = (props: ValidityDropdownProps) => {
    const [isOpen, setIsOpen] = useState(false);

    const onToggle = (): void => {
        setIsOpen(!isOpen);
    };

    const onSelect = (event: any): void => {
        const newValue: string = event && event.currentTarget && event.currentTarget.id ? event.currentTarget.id : "";
        props.onSelect(newValue);
        setIsOpen(false);
    };

    const displayValue = (): string => {
        switch (props.value) {
            case "FULL":
                return "Full";
            case "SYNTAX_ONLY":
                return "Syntax Only";
            case "NONE":
                return "None";
        }
        return props.value;
    };
    
    return (
        <Dropdown
            onOpenChange={setIsOpen}
            onSelect={onSelect}
            toggle={(toggleRef: React.Ref<MenuToggleElement>) => (
                <MenuToggle id="toggle-id" ref={toggleRef}
                    data-testid="rules-validity-config-toggle"
                    onClick={onToggle} isExpanded={isOpen}>
                    {displayValue()}
                </MenuToggle>
            )}
            isOpen={isOpen}
        >
            <DropdownList>
                <DropdownItem key="FULL"
                    tooltipProps={{ content: "Syntactic and semantic validation" }}
                    id="FULL"
                    data-testid="rules-validity-config-full">Full</DropdownItem>
                <DropdownItem key="SYNTAX_ONLY"
                    tooltipProps={{ content: "Only syntactic validation" }}
                    id="SYNTAX_ONLY"
                    data-testid="rules-validity-config-syntaxOnly">Syntax Only</DropdownItem>
                <DropdownItem key="NONE"
                    tooltipProps={{ content: "No validation" }}
                    id="NONE"
                    data-testid="rules-validity-config-none">None</DropdownItem>
            </DropdownList>
        </Dropdown>
    );

};
