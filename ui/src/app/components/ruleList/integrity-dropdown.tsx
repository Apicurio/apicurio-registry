/**
 * @license
 * Copyright 2023 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import React, { FunctionComponent, useEffect, useState } from "react";
import { Select, SelectOption, SelectList } from "@patternfly/react-core/next";
import { MenuToggle, MenuToggleElement, Tooltip } from "@patternfly/react-core";


/**
 * Properties
 */
export type IntegrityDropdownProps = {
    value: string;
    onSelect: (newValue: string) => void;
};

export const IntegrityDropdown: FunctionComponent<IntegrityDropdownProps> = ({ value, onSelect }: IntegrityDropdownProps) => {
    const menuRef = React.useRef<HTMLDivElement>(null);
    const [isOpen, setOpen] = useState<boolean>(false);
    const [selectedItems, setSelectedItems] = useState(["FULL"]);

    const parseValue = (value: string): string[] => {
        if (value) {
            return value.split(",").filter(value => value && value.trim().length > 0);
        }
        return [];
    };

    useEffect(() => {
        setSelectedItems(parseValue(value));
    }, [value]);

    const onToggle = (): void => {
        setOpen(!isOpen);
    };

    const onOpenChange = (isOpen: boolean): void => {
        if (!isOpen) {
            const newValue: string = selectedItems.join(",");
            if (newValue !== value) {
                onSelect(newValue);
            }
        }
        setOpen(isOpen);
    };

    const doSelect = (event: any, itemId: string | number | undefined): void => {
        let newSelectedItems: string[];

        if (selectedItems.includes(itemId as string)) {
            newSelectedItems = selectedItems.filter(item => item !== itemId);
        } else {
            newSelectedItems = [...selectedItems, itemId as string];
        }

        if ((itemId === "FULL" || itemId == "NONE") && newSelectedItems.includes(itemId as string)) {
            newSelectedItems = [itemId as string];
        } else {
            newSelectedItems = newSelectedItems.filter(item => item !== "NONE" && item !== "FULL");
        }

        // Handle the case where there aren't any items in the array.  The user has deselected *everything*
        if (newSelectedItems.length === 0) {
            newSelectedItems = ["NONE"];
        }

        setSelectedItems(newSelectedItems);
    };

    const displayValue = (): string => {
        if (selectedItems.length === 1) {
            switch (selectedItems[0]) {
                case "FULL":
                    return "Full";
                case "NONE":
                    return "None";
                case "NO_DUPLICATES":
                    return "No duplicates";
                case "REFS_EXIST":
                    return "Refs must exist";
                case "ALL_REFS_MAPPED":
                    return "No unmapped refs";
            }
        } else {
            return "(Multiple)";
        }

        return "";
    }

    const toggle = (toggleRef: React.Ref<MenuToggleElement>) => (
        <MenuToggle
            ref={toggleRef}
            onClick={onToggle}
            isExpanded={isOpen}
            style={{ width: "200px" }}
        >
            <span>{ displayValue() }</span>
        </MenuToggle>
    );

    return (
        <Select
            role="menu"
            id="checkbox-select"
            ref={menuRef}
            isOpen={isOpen}
            selected={selectedItems}
            onSelect={doSelect}
            onOpenChange={onOpenChange}
            toggle={toggle}
        >
            <SelectList>
                <SelectOption hasCheck itemId="NONE" isSelected={selectedItems.includes("NONE")}>
                    <Tooltip content={<div>The rule will do nothing.</div>}>
                        <span>None</span>
                    </Tooltip>
                </SelectOption>
                <SelectOption hasCheck itemId="NO_DUPLICATES" isSelected={selectedItems.includes("NO_DUPLICATES")}>
                    <Tooltip content={<div>Disallows multiple mappings for the same reference (even when the mapping is the same).</div>}>
                        <span>No duplicates</span>
                    </Tooltip>
                </SelectOption>
                <SelectOption hasCheck itemId="REFS_EXIST" isSelected={selectedItems.includes("REFS_EXIST")}>
                    <Tooltip content={<div>Ensures that all provided reference mappings point to existing artifacts.</div>}>
                        <span>Refs must exist</span>
                    </Tooltip>
                </SelectOption>
                <SelectOption hasCheck itemId="ALL_REFS_MAPPED" isSelected={selectedItems.includes("ALL_REFS_MAPPED")}>
                    <Tooltip content={<div>Requires that all references found in the content being registered have an appropriate mapping.</div>}>
                        <span>No unmapped refs</span>
                    </Tooltip>
                </SelectOption>
                <SelectOption hasCheck itemId="FULL" isSelected={selectedItems.includes("FULL")}>
                    <Tooltip content={<div>Enable all of the above settings.</div>}>
                        <span>Full (all)</span>
                    </Tooltip>
                </SelectOption>
            </SelectList>
        </Select>
    );
};
