import type { FunctionComponent } from "react";
import type { SearchType } from "../types";
import { SearchInput } from "@patternfly/react-core";
import * as React from "react";

export const FilterSearch: FunctionComponent<
  Pick<SearchType, "onSearch" | "validate" | "errorMessage"> & { label: string }
> = ({ label, onSearch }) => {

    const onSearchInternal = (_event: React.SyntheticEvent<HTMLButtonElement>,
        value: string,
        // eslint-disable-next-line @typescript-eslint/no-unused-vars
        _attrValueMap: { [key: string]: string }) => {
        onSearch(value);
    };

    return (
        <SearchInput
            placeholder={`Filter by ${label}`}
            onSearch={onSearchInternal}
        />
    );
};
