import type { SelectProps } from "@patternfly/react-core";
import {
    MenuToggle,
    Select,
    SelectList,
    SelectOption,
} from "@patternfly/react-core";
import { FilterIcon } from "@patternfly/react-icons";
import { useState } from "react";

export function FilterSelector({
    options,
    value,
    onChange,
    ouiaId,
}: {
  options: string[];
  value: string;
  onChange: (value: string) => void;
  ouiaId: string;
}) {
    const [isOpen, setIsOpen] = useState(false);

    const onSelect: SelectProps["onSelect"] = (_, value) => {
        onChange(value as string);
        setIsOpen(false);
    };

    return (
        <Select
            toggle={(toggleRef) => (
                <MenuToggle
                    ref={toggleRef}
                    onClick={() => setIsOpen((o) => !o)}
                    isExpanded={isOpen}
                    variant={"plain"}
                >
                    <FilterIcon />
                </MenuToggle>
            )}
            aria-label={"table:select_filter"}
            selected={value}
            isOpen={isOpen}
            onSelect={onSelect}
            ouiaId={ouiaId}
        >
            <SelectList>
                {options.map((option, index) => (
                    <SelectOption value={option} key={index}>
                        {option}
                    </SelectOption>
                ))}
            </SelectList>
        </Select>
    );
}
