import React, { FunctionComponent } from "react";
import "./ExploreGroupList.css";
import {
    DataList, DataListAction,
    DataListCell,
    DataListItemCells,
    DataListItemRow,
    Icon,
    Label,
    Truncate
} from "@patternfly/react-core";
import { OutlinedFolderIcon } from "@patternfly/react-icons";
import { SearchedGroup } from "@apicurio/apicurio-registry-sdk/dist/generated-client/models";
import { ArtifactGroup } from "@app/components";
import { labelsToAny } from "@utils/rest.utils.ts";
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

    return (
        <DataList aria-label="List of groups" className="group-list">
            <If condition={!props.isFiltered}>
                <DataListItemRow
                    className="group-list-item default-group"
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
                                        {
                                            Object.entries(labelsToAny(group.labels)).map(([key, value]) =>
                                                <Label
                                                    key={`label-${key}`}
                                                    color="purple"
                                                    style={{ marginBottom: "2px", marginRight: "5px", marginTop: "5px" }}
                                                >
                                                    <Truncate
                                                        className="label-truncate"
                                                        content={`${key}=${value}`} />
                                                </Label>
                                            )
                                        }
                                    </div>
                                </DataListCell>
                            ]}
                        />
                        <DataListAction
                            id={`group-actions-${idx}`}
                            aria-label="Group actions"
                            aria-labelledby={`group-actions-${idx}`}
                            isPlainButtonAction={true}
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
                )
            }
        </DataList>
    );

};
