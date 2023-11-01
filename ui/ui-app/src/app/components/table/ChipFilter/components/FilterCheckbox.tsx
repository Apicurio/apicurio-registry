import { MenuToggle, Select, SelectOption } from "@patternfly/react-core";
import { useState } from "react";
import type { CheckboxType } from "../types";

export function FilterCheckbox({
    label,
    chips,
    options,
    onToggle,
}: Pick<CheckboxType<any>, "chips" | "options" | "onToggle"> & {
  label: string;
}) {
    const [isOpen, setIsOpen] = useState(false);
    return (
        <Select
            aria-label={label}
            onSelect={(_, value) => {
                onToggle(value);
            }}
            selected={chips}
            isOpen={isOpen}
            toggle={(toggleRef) => (
                <MenuToggle
                    ref={toggleRef}
                    onClick={() => setIsOpen((o) => !o)}
                    isExpanded={isOpen}
                    style={
            {
                width: "200px",
            } as React.CSSProperties
                    }
                >
                    {`Filter by ${label}`}
                </MenuToggle>
            )}
        >
            {Object.entries(options).map(([key, label]) => (
                <SelectOption key={key} value={key} hasCheckbox={true}>
                    {label}
                </SelectOption>
            ))}
        </Select>
    );
}
