import React, { FunctionComponent, useState } from "react";
import { Divider, MenuToggle, MenuToggleElement, Select, SelectOption } from "@patternfly/react-core";


/**
 * Properties
 */
export type ObjectSelectProps = {
    value: any;
    items: any[];
    onSelect: (value: any) => void;
    itemToString: (value: any) => string;
    itemIsDivider?: (value: any) => boolean;
    itemToTestId?: (value: any) => string;
    noSelectionLabel?: string;
    toggleId?: string;
    toggleClassname?: string;
    testId?: string;
};

/**
 * A generic control that makes it easier to create a <Select> from an array of objects.
 */
export const ObjectSelect: FunctionComponent<ObjectSelectProps> = (props: ObjectSelectProps) => {

    const [isToggled, setToggled] = useState<boolean>(false);

    const onSelectInternal = (_event: any, value?: string | number): void => {
        setToggled(false);
        props.onSelect(props.items[value as number]);
    };

    const toggle = (): void => {
        setToggled(!isToggled);
    };

    const itemToTestId = (item: any): string | undefined => {
        let testId: string | undefined = undefined;
        if (props.itemToTestId !== undefined) {
            testId = props.itemToTestId(item);
        }
        return testId;
    };

    const menuToggle = (toggleRef: React.Ref<MenuToggleElement>) => (
        <MenuToggle
            ref={toggleRef}
            className={props.toggleClassname || "menu-toggle"}
            onClick={toggle}
            data-testid={props.testId}
            isExpanded={isToggled}
        >
            { props.value ? props.itemToString(props.value) : props.noSelectionLabel }
        </MenuToggle>
    );

    return (
        <Select
            toggle={menuToggle}
            id={props.toggleId}
            onSelect={onSelectInternal}
            onOpenChange={setToggled}
            isOpen={isToggled}>
            {
                props.items?.map((item: any, index: any) => {
                    if (props.itemIsDivider && props.itemIsDivider(item)) {
                        return <Divider key={index} />;
                    } else {
                        return (
                            <SelectOption
                                isSelected={item === props.value}
                                component={props => <button {...props} data-testid={itemToTestId(item)} />}
                                key={index}
                                value={index}
                            >
                                { props.itemToString(item) }
                            </SelectOption>
                        );
                    }
                })
            }
        </Select>
    );
};
