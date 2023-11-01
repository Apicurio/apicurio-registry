import React, { FunctionComponent, useState } from "react";
import { Dropdown, DropdownItem, DropdownList, MenuToggle, MenuToggleElement } from "@patternfly/react-core";


/**
 * Properties
 */
export type CompatibilityDropdownProps = {
    value: string;
    onSelect: (newValue: string) => void;
};


/**
 * Component.
 */
export const CompatibilityDropdown: FunctionComponent<CompatibilityDropdownProps> = (props: CompatibilityDropdownProps) => {
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
            case "NONE":
                return "None";
            case "BACKWARD":
                return "Backward";
            case "BACKWARD_TRANSITIVE":
                return "Backward Transitive";
            case "FORWARD":
                return "Forward";
            case "FORWARD_TRANSITIVE":
                return "Forward Transitive";
            case "FULL":
                return "Full";
            case "FULL_TRANSITIVE":
                return "Full Transitive";
        }
        return props.value;
    };

    return (
        <Dropdown
            onOpenChange={setIsOpen}
            onSelect={onSelect}
            toggle={(toggleRef: React.Ref<MenuToggleElement>) => (
                <MenuToggle id="toggle-id" ref={toggleRef}
                    data-testid="rules-compatibility-config-toggle"
                    onClick={onToggle} isExpanded={isOpen}>
                    {displayValue()}
                </MenuToggle>
            )}
            isOpen={isOpen}
        >
            <DropdownList>
                <DropdownItem key="NONE"
                    tooltipProps={{ content: "No compatibility checking should be performed" }}
                    data-testid="rules-compatibility-config-none"
                    id="NONE">None</DropdownItem>
                <DropdownItem key="BACKWARD"
                    tooltipProps={{ content: "Clients using the new artifact can read data written using the most recently added artifact" }}
                    data-testid="rules-compatibility-config-backward"
                    id="BACKWARD">Backward</DropdownItem>
                <DropdownItem key="BACKWARD_TRANSITIVE"
                    tooltipProps={{ content: "Clients using the new artifact can read data written using all previously added artifacts" }}
                    data-testid="rules-compatibility-config-backwardTrans"
                    id="BACKWARD_TRANSITIVE">Backward Transitive</DropdownItem>
                <DropdownItem key="FORWARD"
                    tooltipProps={{ content: "Clients using the most recently added artifact can read data written using the new artifact" }}
                    data-testid="rules-compatibility-config-forward"
                    id="FORWARD">Forward</DropdownItem>
                <DropdownItem key="FORWARD_TRANSITIVE"
                    tooltipProps={{ content: "Clients using all previously added artifacts can read data written using the new artifact" }}
                    data-testid="rules-compatibility-config-forwardTrans"
                    id="FORWARD_TRANSITIVE">Forward Transitive</DropdownItem>
                <DropdownItem key="FULL"
                    tooltipProps={{ content: "The new artifact is forward and backward compatible with the most recently added artifact" }}
                    data-testid="rules-compatibility-config-full"
                    id="FULL">Full</DropdownItem>
                <DropdownItem key="FULL_TRANSITIVE"
                    tooltipProps={{ content: "The new artifact is forward and backward compatible with all previously added artifacts" }}
                    data-testid="rules-compatibility-config-fullTrans"
                    id="FULL_TRANSITIVE">Full Transitive</DropdownItem>
            </DropdownList>
        </Dropdown>
    );

};
