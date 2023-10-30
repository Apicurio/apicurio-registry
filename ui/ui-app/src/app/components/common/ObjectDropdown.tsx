import React, { FunctionComponent, useState } from "react";
import {
    Divider,
    Dropdown,
    DropdownItem,
    DropdownList, DropdownPopperProps,
    MenuToggle,
    MenuToggleElement
} from "@patternfly/react-core";
import { EllipsisVIcon } from "@patternfly/react-icons";

export const ObjectDropdownItemDivider = {};

/**
 * Properties
 */
export type ObjectDropdownProps = {
    value?: any | undefined;
    items: any[];
    onSelect: (value: any | undefined) => void;
    itemToString: (value: any) => string;
    noSelectionLabel?: string;
    menuAppendTo?: HTMLElement | (() => HTMLElement) | "inline";
    isKebab?: boolean;
};

/**
 * A generic control that makes it easier to create a <Select> from an array of objects.
 */
export const ObjectDropdown: FunctionComponent<ObjectDropdownProps> = (props: ObjectDropdownProps) => {
    const [isOpen, setIsOpen] = useState<boolean>(false);

    const onSelectInternal = (_event: any, value?: string | number | undefined): void => {
        setIsOpen(false);
        const idx: number | undefined = value as number | undefined;
        if (idx !== undefined && idx >= 0) {
            props.onSelect(props.items[idx]);
        } else {
            props.onSelect(undefined);
        }
    };

    const onToggleClick = () => {
        setIsOpen(!isOpen);
    };

    const popperProps: DropdownPopperProps = {
        appendTo: props.menuAppendTo
    };

    let toggleValue = <EllipsisVIcon />;
    if (!props.isKebab) {
        if (props.value) {
            toggleValue = <React.Fragment>{ props.itemToString(props.value) }</React.Fragment>;
        } else {
            toggleValue = <React.Fragment>{ props.noSelectionLabel }</React.Fragment>;
        }
    }

    return (
        <Dropdown
            isOpen={isOpen}
            onSelect={onSelectInternal}
            onOpenChange={(isOpen: boolean) => setIsOpen(isOpen)}
            toggle={(toggleRef: React.Ref<MenuToggleElement>) => (
                <MenuToggle ref={toggleRef} onClick={onToggleClick} isExpanded={isOpen} variant={props.isKebab ? "plain" : "default"}>
                    {
                        toggleValue
                    }
                </MenuToggle>
            )}
            ouiaId="ObjectDropdown"
            popperProps={popperProps}
            shouldFocusToggleOnSelect
        >
            <DropdownList>
                {
                    props.items.map((item, index) => {
                        return (
                            item == ObjectDropdownItemDivider ?
                                <Divider component="li" key={`divider-${index}`} />
                                :
                                <DropdownItem value={index} key={`action-${index}`}>
                                    { props.itemToString(item) }
                                </DropdownItem>
                        );
                    })
                }
            </DropdownList>
        </Dropdown>
    );
};
