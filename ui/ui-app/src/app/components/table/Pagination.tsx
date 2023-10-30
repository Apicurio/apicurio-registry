import type { PaginationProps as PFPaginationProps } from "@patternfly/react-core";
import {
    Pagination as PFPagination,
    PaginationVariant,
} from "@patternfly/react-core";

export type PaginationProps = {
  itemCount: number;
  page: number;
  perPage: number;
  isCompact?: boolean;
  onChange: (page: number, perPage: number) => void;
} & Pick<PFPaginationProps, "variant">;
export function Pagination({
    itemCount,
    page,
    perPage,
    isCompact = false,
    onChange,
    variant = PaginationVariant.top,
}: PaginationProps) {
    return (
        <PFPagination
            itemCount={itemCount}
            page={page}
            perPage={perPage}
            onSetPage={(_, page) => onChange(page, perPage)}
            onPerPageSelect={(_, perPage) => onChange(1, perPage)}
            variant={variant}
            isCompact={isCompact}
        />
    );
}
