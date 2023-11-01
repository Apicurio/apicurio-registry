import type { ToolbarToggleGroupProps } from "@patternfly/react-core";
import {
    InputGroup,
    ToolbarFilter,
    ToolbarGroup,
    ToolbarItem,
    ToolbarToggleGroup,
} from "@patternfly/react-core";
import { FilterIcon } from "@patternfly/react-icons";
import { useState } from "react";
import { FilterCheckbox, FilterSearch, FilterSelector } from "./components";
import type { FilterType } from "./types";

export type ChipFilterProps = {
  filters: { [label: string]: FilterType };
  breakpoint?: ToolbarToggleGroupProps["breakpoint"];
};

export function ChipFilter({ filters, breakpoint = "md" }: ChipFilterProps) {
    const options = Object.keys(filters);
    const [selectedOption, setSelectedOption] = useState<string>(options[0]);

    const getFilterComponent = (label: string, f: FilterType) => {
        switch (f.type) {
            case "search":
                return (
                    <FilterSearch
                        onSearch={f.onSearch}
                        label={label}
                        validate={f.validate}
                        errorMessage={f.errorMessage}
                    />
                );
            case "checkbox":
                return (
                    <FilterCheckbox
                        chips={f.chips}
                        options={f.options}
                        onToggle={f.onToggle}
                        label={label}
                    />
                );
        }
    };

    return (
        <>
            <ToolbarItem
                variant={"search-filter"}
                visibility={{ default: "hidden", [breakpoint]: "visible" }}
                data-testid={"large-viewport-toolbar"}
            >
                <InputGroup>
                    {options.length > 1 && (
                        <FilterSelector
                            options={options}
                            value={selectedOption}
                            onChange={setSelectedOption}
                            ouiaId={"chip-filter-selector-large-viewport"}
                        />
                    )}
                    {getFilterComponent(selectedOption, filters[selectedOption])}
                </InputGroup>
            </ToolbarItem>
            <ToolbarToggleGroup
                toggleIcon={<FilterIcon />}
                breakpoint={breakpoint}
                visibility={{ default: "visible", [breakpoint]: "hidden" }}
            >
                <ToolbarGroup variant="filter-group">
                    {options.length > 1 && (
                        <FilterSelector
                            options={options}
                            value={selectedOption}
                            onChange={setSelectedOption}
                            ouiaId={"chip-filter-selector-small-viewport"}
                        />
                    )}
                    {Object.entries(filters).map(([label, f], index) => (
                        <ToolbarFilter
                            key={index}
                            chips={
                                f.type === "checkbox"
                                    ? f.chips.map((c) => ({ key: c, node: f.options[c] }))
                                    : f.chips
                            }
                            deleteChip={(_, chip) =>
                                f.onRemoveChip(typeof chip === "string" ? chip : chip.key)
                            }
                            deleteChipGroup={f.onRemoveGroup}
                            categoryName={label}
                            showToolbarItem={label === selectedOption}
                        >
                            {label === selectedOption && getFilterComponent(label, f)}
                        </ToolbarFilter>
                    ))}
                </ToolbarGroup>
            </ToolbarToggleGroup>
        </>
    );
}
