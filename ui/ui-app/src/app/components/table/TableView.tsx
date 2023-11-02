"use client";
import {
    Button,
    Divider,
    Dropdown,
    MenuToggle,
    OverflowMenu,
    OverflowMenuContent,
    OverflowMenuControl,
    OverflowMenuDropdownItem,
    OverflowMenuGroup,
    OverflowMenuItem,
    Select,
    SelectGroup,
    SelectOption,
    Toolbar,
    ToolbarContent,
    ToolbarProps,
    ToolbarGroup,
    ToolbarItem,
} from "@patternfly/react-core";
import {
    EllipsisVIcon,
    SortAmountDownIcon,
} from "@patternfly/react-icons";
import {
    InnerScrollContainer,
    OuterScrollContainer,
    SortByDirection,
} from "@patternfly/react-table";
import type { ReactElement } from "react";
import { useState } from "react";
import type { ChipFilterProps, ResponsiveTableProps } from "@app/components";
import { ChipFilter, Loading, Pagination, ResponsiveTable } from "@app/components";

export type ToolbarAction = {
  label: string;
  isPrimary: boolean;
  onClick: () => void;
};

export const DEFAULT_PERPAGE = 20;

export type TableViewProps<TRow, TCol> = {
  toolbarBreakpoint?: ToolbarProps["collapseListedFiltersBreakpoint"];
  filters?: ChipFilterProps["filters"];
  isFiltered?: boolean;
  actions?: ToolbarAction[];
  tools?: ReactElement[];
  itemCount: number | undefined;
  page: number;
  perPage?: number;
  onPageChange: (page: number, perPage: number) => void;
  onClearAllFilters?: () => void;
  data: ResponsiveTableProps<TRow, TCol>["data"] | null;
  emptyStateNoData: ReactElement;
  emptyStateNoResults: ReactElement;
} & Omit<ResponsiveTableProps<TRow, TCol>, "data">;
export const TableView = <TRow, TCol>({
    toolbarBreakpoint = "lg",
    filters,
    actions,
    tools,
    itemCount,
    page,
    perPage = DEFAULT_PERPAGE,
    columns,
    isColumnSortable,
    onPageChange,
    onClearAllFilters,

    isFiltered,
    emptyStateNoData,
    emptyStateNoResults,
    ...tableProps
}: TableViewProps<TRow, TCol>) => {
    const [isSortOpen, toggleIsSortOpen] = useState(false);
    const [isActionsOpen, toggleIsActionsOpen] = useState(false);
    const { data } = tableProps;
    const showPagination =
    data?.length !== 0 && itemCount && itemCount > DEFAULT_PERPAGE;
    const breakpoint = toolbarBreakpoint === "all" ? "lg" : toolbarBreakpoint;
    function notUndefined<T>(x: T | undefined): x is T {
        return x !== undefined;
    }
    const sortableColumns = isColumnSortable
        ? columns.map((c) => isColumnSortable(c)).filter(notUndefined)
        : undefined;
    const sortedColumn = sortableColumns?.find(
        (s) => s.sortBy.index === s.columnIndex,
    );
    if (data === null) {
        return <Loading />;
    }
    if (data?.length === 0 && !isFiltered) {
        return emptyStateNoData;
    }
    return (
        <OuterScrollContainer className={"pf-u-h-100"}>
            <Toolbar
                clearAllFilters={onClearAllFilters}
                collapseListedFiltersBreakpoint={toolbarBreakpoint}
            >
                <ToolbarContent>
                    {/* sort control for small viewports */}
                    {sortableColumns && (
                        <ToolbarItem visibility={{ [breakpoint]: "hidden" }}>
                            <Select
                                id="options-menu-multiple-options-example"
                                isOpen={isSortOpen}
                                toggle={(toggleRef) => (
                                    <MenuToggle
                                        ref={toggleRef}
                                        onClick={() => toggleIsSortOpen((o) => !o)}
                                        isExpanded={isSortOpen}
                                    >
                                        <SortAmountDownIcon />
                                    </MenuToggle>
                                )}
                                isPlain
                            >
                                <SelectGroup key="first group" aria-label="Sort column">
                                    {sortableColumns.map((sortObj, idx) => (
                                        <SelectOption
                                            key={idx}
                                            isSelected={
                                                sortedColumn?.columnIndex === sortObj.columnIndex
                                            }
                                            onSelect={(e) =>
                                                sortObj.onSort &&
                        sortObj.onSort(
                          e as unknown as React.MouseEvent,
                          sortObj.columnIndex,
                          (sortObj.sortBy.direction ||
                            sortObj.sortBy.defaultDirection) === "asc"
                              ? SortByDirection.asc
                              : SortByDirection.desc,
                          {},
                        )
                                            }
                                        >
                                            {sortObj.label}
                                        </SelectOption>
                                    ))}
                                </SelectGroup>
                                <Divider key="separator" />
                                <SelectGroup key="second group" aria-label="Sort direction">
                                    <SelectOption
                                        onSelect={() =>
                                            sortedColumn?.onSort &&
                      sortedColumn.onSort(
                        undefined as unknown as React.MouseEvent,
                        sortedColumn.columnIndex,
                        SortByDirection.asc,
                        {},
                      )
                                        }
                                        isSelected={sortedColumn?.sortBy.direction === "asc"}
                                        key="ascending"
                                    >
                    Ascending
                                    </SelectOption>
                                    <SelectOption
                                        onSelect={() =>
                                            sortedColumn?.onSort &&
                      sortedColumn.onSort(
                        undefined as unknown as React.MouseEvent,
                        sortedColumn.columnIndex,
                        SortByDirection.desc,
                        {},
                      )
                                        }
                                        isSelected={sortedColumn?.sortBy.direction === "desc"}
                                        key="descending"
                                    >
                    Descending
                                    </SelectOption>
                                </SelectGroup>
                            </Select>
                        </ToolbarItem>
                    )}

                    {/* responsive filters control */}
                    {filters && <ChipFilter breakpoint={breakpoint} filters={filters} />}

                    {/* responsive action buttons, fallback on a dropdown on small viewports */}
                    {actions && (
                        <OverflowMenu breakpoint={breakpoint}>
                            <OverflowMenuContent isPersistent>
                                <OverflowMenuGroup isPersistent groupType="button">
                                    <OverflowMenuItem>
                                        {actions.map((a, idx) => (
                                            <Button
                                                key={idx}
                                                variant={a.isPrimary ? "primary" : undefined}
                                                onClick={a.onClick}
                                            >
                                                {a.label}
                                            </Button>
                                        ))}
                                    </OverflowMenuItem>
                                </OverflowMenuGroup>
                            </OverflowMenuContent>
                            <OverflowMenuControl>
                                <Dropdown
                                    isPlain
                                    toggle={(toggleRef) => (
                                        <MenuToggle
                                            ref={toggleRef}
                                            aria-label="kebab dropdown toggle"
                                            variant="plain"
                                            onClick={() => toggleIsActionsOpen((o) => !o)}
                                            isExpanded={isActionsOpen}
                                        >
                                            <EllipsisVIcon />
                                        </MenuToggle>
                                    )}
                                    shouldFocusToggleOnSelect
                                    onOpenChange={toggleIsActionsOpen}
                                    isOpen={isActionsOpen}
                                >
                                    {actions.map((a, idx) => (
                                        <OverflowMenuDropdownItem
                                            key={idx}
                                            onClick={() => {
                                                a.onClick();
                                                toggleIsActionsOpen(false);
                                            }}
                                        >
                                            {a.label}
                                        </OverflowMenuDropdownItem>
                                    ))}
                                </Dropdown>
                            </OverflowMenuControl>
                        </OverflowMenu>
                    )}

                    {/* icon buttons */}
                    {tools && (
                        <ToolbarGroup variant="icon-button-group">
                            {tools.map((t, idx) => (
                                <ToolbarItem key={idx}>{t}</ToolbarItem>
                            ))}
                        </ToolbarGroup>
                    )}

                    {/* pagination controls */}
                    {showPagination && (
                        <ToolbarGroup
                            //alignment={{ default: "alignRight" }}
                        >
                            <Pagination
                                itemCount={itemCount}
                                page={page}
                                perPage={perPage}
                                onChange={onPageChange}
                                variant={"top"}
                                isCompact
                            />
                        </ToolbarGroup>
                    )}
                </ToolbarContent>
            </Toolbar>
            <InnerScrollContainer className={"pf-u-h-100"}>
                <ResponsiveTable
                    {...tableProps}
                    columns={columns}
                    isColumnSortable={isColumnSortable}
                    data={data}
                >
                    {emptyStateNoResults}
                </ResponsiveTable>
            </InnerScrollContainer>
            {showPagination && (
                <Pagination
                    itemCount={itemCount}
                    page={page}
                    perPage={perPage}
                    variant={"bottom"}
                    onChange={onPageChange}
                />
            )}
        </OuterScrollContainer>
    );
};
