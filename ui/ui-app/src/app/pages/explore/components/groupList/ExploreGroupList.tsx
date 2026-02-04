import React, { FunctionComponent } from "react";
import "./ExploreGroupList.css";
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
import { SearchedGroup } from "@apicurio/apicurio-registry-sdk/dist/generated-client/models";
import { ArtifactGroup } from "@app/components";
import { If, ObjectDropdown } from "@apicurio/common-ui-components";
import { UserService, useUserService } from "@services/useUserService.ts";

/**
 * Properties
 */
export type ExploreGroupListProps = {
    isFiltered: boolean;
    groups: SearchedGroup[];
    onExplore: (group: SearchedGroup) => void;
    onDelete: (group: SearchedGroup) => void;
};


/**
 * Models the list of groups.
 */
export const ExploreGroupList: FunctionComponent<ExploreGroupListProps> = (props: ExploreGroupListProps) => {

    const user: UserService = useUserService();

    const description = (group: SearchedGroup): string => {
        if (group.description) {
            return group.description;
        }
        return "A group with no description.";
    };

    const groupActions: any[] = [
        {
            id: "view-group-in-explorer",
            label: "Explore group",
            action: (group: SearchedGroup) => props.onExplore(group)
        },
        {
            divider: true,
            isVisible: (group: SearchedGroup) => user.isUserDeveloper(group.owner)
        },
        {
            id: "delete-group",
            label: "Delete group",
            action: (group: SearchedGroup) => props.onDelete(group),
            isVisible: (group: SearchedGroup) => user.isUserDeveloper(group.owner)
        }
    ];

    const navigateToGroup = (groupId: string): void => {
        const searchedGroup: SearchedGroup = props.groups.filter(g => g.groupId === groupId)[0];
        props.onExplore(searchedGroup);
    };

    return (
        <DataList
            aria-label="List of groups"
            className="explore-group-list"
            selectedDataListItemId={""}
            onSelectDataListItem={(_evt, id) => { navigateToGroup(id); }}
            onSelectableRowChange={() => {}}
        >
            <If condition={!props.isFiltered}>
                <DataListItemRow
                    className="explore-group-list-item default-group"
                    key="default"
                >
                    <DataListItemCells
                        dataListCells={[
                            <DataListCell key="type icon" className="type-icon-cell">
                                <Icon>
                                    <OutlinedFolderIcon />
                                </Icon>
                            </DataListCell>,
                            <DataListCell key="main content" className="content-cell">
                                <div className="group-title">
                                    <ArtifactGroup groupId="default" />
                                </div>
                                <div className="group-description">The default group.</div>
                            </DataListCell>
                        ]}
                    />
                </DataListItemRow>
            </If>
            {
                props.groups?.map( (group, idx) =>
                    <DataListItem id={group.groupId as string} key={group.groupId}>
                        <DataListItemRow className="explore-group-list-item">
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
                                    items={groupActions}
                                    onSelect={item => item.action(group)}
                                    itemToString={item => item.label}
                                    itemToTestId={() => `view-group-in-explorer-${idx}`}
                                    itemIsDivider={item => item.divider}
                                    itemIsVisible={item => !item.isVisible || item.isVisible(group)}
                                />
                            </DataListAction>
                        </DataListItemRow>
                    </DataListItem>
                )
            }
        </DataList>
    );

};
