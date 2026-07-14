import { FunctionComponent, useEffect, useState } from "react";
import { Button, ButtonVariant, Form, InputGroup, InputGroupItem, TextInput } from "@patternfly/react-core";
import { SearchIcon } from "@patternfly/react-icons";
import { ChipFilterCriteria, ChipFilterType, ObjectSelect } from "@apicurio/common-ui-components";

/**
 * Extended filter type that can optionally render as a dropdown (select)
 * instead of a free-text input.
 */
export interface EnhancedFilterType extends ChipFilterType {
    type?: "text" | "select";
    options?: { value: string; label: string }[];
    noSelectionLabel?: string;
}

export type FilterInputProps = {
    filterTypes: EnhancedFilterType[];
    onAddCriteria: (criteria: ChipFilterCriteria) => void;
};

/**
 * A filter input component that extends @apicurio/common-ui-components ChipFilterInput.
 *
 * When a filter type has `type: "select"` and an `options` list, the value input
 * is rendered as a dropdown. Otherwise, the standard free-text input is shown.
 */
export const FilterInput: FunctionComponent<FilterInputProps> = (props: FilterInputProps) => {
    const [selectedFilterType, setSelectedFilterType] = useState<EnhancedFilterType>(props.filterTypes[0]);
    const [filterValue, setFilterValue] = useState<string>("");

    // Reset the selected filter type whenever the available filter types change.
    useEffect(() => {
        setSelectedFilterType(props.filterTypes[0]);
    }, [props.filterTypes]);

    const isSelect = selectedFilterType.type === "select" && (selectedFilterType.options?.length ?? 0) > 0;

    const onSubmit = (event?: React.FormEvent<HTMLFormElement>): void => {
        event?.preventDefault();
        if (filterValue) {
            props.onAddCriteria({
                filterBy: selectedFilterType,
                filterValue
            });
            setFilterValue("");
        }
    };

    const onValueSelect = (item: { value: string; label: string }): void => {
        // Immediately submit when picking from a dropdown — no need for the extra button click.
        props.onAddCriteria({
            filterBy: selectedFilterType,
            filterValue: item.value
        });
        setFilterValue("");
    };

    return (
        <Form onSubmit={onSubmit}>
            <InputGroup>
                <InputGroupItem>
                    <ObjectSelect
                        value={selectedFilterType}
                        items={props.filterTypes}
                        testId="chip-filter-select"
                        toggleClassname="chip-filter-toggle"
                        onSelect={(item) => setSelectedFilterType(item)}
                        appendTo="document"
                        itemToTestId={(item: EnhancedFilterType) => item.testId}
                        itemToString={(item: EnhancedFilterType) => item.label}
                    />
                </InputGroupItem>
                <InputGroupItem isFill>
                    {isSelect ? (
                        <ObjectSelect
                            value={selectedFilterType.options?.find(o => o.value === filterValue) || undefined}
                            items={selectedFilterType.options!}
                            testId="chip-filter-value-select"
                            toggleClassname="chip-filter-toggle"
                            noSelectionLabel={selectedFilterType.noSelectionLabel || "Select a value"}
                            onSelect={onValueSelect}
                            appendTo="document"
                            itemToTestId={(item: { value: string; label: string }) => `filter-option-${item.value}`}
                            itemToString={(item: { value: string; label: string }) => item.label}
                        />
                    ) : (
                        <TextInput
                            name="filterValue"
                            id="filterValue"
                            type="search"
                            value={filterValue}
                            onChange={(_event, value) => setFilterValue(value)}
                            data-testid="chip-filter-value"
                            aria-label="search input"
                        />
                    )}
                </InputGroupItem>
                <InputGroupItem>
                    <Button
                        icon={<SearchIcon />}
                        variant={ButtonVariant.control}
                        onClick={() => onSubmit()}
                        data-testid="chip-filter-search"
                        aria-label="search button for search input"
                    />
                </InputGroupItem>
            </InputGroup>
        </Form>
    );
};
