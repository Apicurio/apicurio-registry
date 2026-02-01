import React, { FunctionComponent } from "react";
import "./GroupList.css";
import {
    DataList,
    DataListAction,
    DataListCell,
    DataListItem,
    DataListItemCells,
    DataListItemRow,
    Icon
} from "@patternfly/react-core";
import { OutlinedFolderIcon } from "@patternfly/react-icons";
import { SearchedGroup } from "@sdk/lib/generated-client/models";
import { ArtifactGroup, ListedItemLabels } from "@app/components";
import { ObjectDropdown } from "@apicurio/common-ui-components";

/**
 * Properties
 */
export type SearchGroupListProps = {
    groups: SearchedGroup[];
    onExplore: (group: SearchedGroup) => void;
    onFilterByLabel: (key: string, value: string | undefined) => void;
};


/**
 * Models the list of groups.
 */
export const SearchGroupList: FunctionComponent<SearchGroupListProps> = (props: SearchGroupListProps) => {

    const description = (group: SearchedGroup): string => {
        if (group.description) {
            return group.description;
        }
        return "A group with no description.";
    };

    return (
        <DataList aria-label="List of groups" className="group-list">
            {
                props.groups?.map( (group, idx) =>
                    <DataListItem>
                        <DataListItemRow className="group-list-item" key={group.groupId}>
                            <DataListItemCells
                                dataListCells={[
                                    <DataListCell key="type icon" className="type-icon-cell">
                                        <Icon>
                                            <OutlinedFolderIcon />
                                        </Icon>
                                    </DataListCell>,
                                    <DataListCell key="main content" className="content-cell">
                                        <div className="group-title">
                                            <ArtifactGroup groupId={group.groupId!} />
                                        </div>
                                        <div className="group-description">{description(group)}</div>
                                        <div className="group-labels">
                                            <ListedItemLabels item={group} onClick={props.onFilterByLabel} />
                                        </div>
                                    </DataListCell>
                                ]}
                            />
                            <DataListAction
                                id={`group-actions-${idx}`}
                                aria-label="Group actions"
                                aria-labelledby={`group-actions-${idx}`}

                            >
                                <ObjectDropdown
                                    label=""
                                    isKebab={true}
                                    testId={`group-actions-dropdown-${idx}`}
                                    popperProps={{
                                        position: "right"
                                    }}
                                    items={[
                                        {
                                            id: "view-group-in-explorer",
                                            label: "Explore group",
                                            testId: "view-group-in-explorer-" + idx,
                                            action: () => props.onExplore(group)
                                        }
                                    ]}
                                    onSelect={item => item.action()}
                                    itemToString={item => item.label}
                                    itemToTestId={item => item.testId}
                                    itemIsDivider={item => item.divider}
                                    itemIsVisible={item => !item.isVisible || item.isVisible()}
                                />
                            </DataListAction>
                        </DataListItemRow>
                    </DataListItem>
                )
            }
        </DataList>
    );

};
