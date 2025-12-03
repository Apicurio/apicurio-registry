import React, { FunctionComponent } from "react";
import "./SearchVersionList.css";
import { DataList, DataListAction, DataListCell, DataListItemCells, DataListItemRow } from "@patternfly/react-core";
import { ArtifactGroup, ArtifactTypeIcon, ListedItemLabels, VersionStateBadge } from "@app/components";
import { SearchVersionName } from "@app/pages";
import { SearchedVersion } from "@sdk/lib/generated-client/models";
import { shash } from "@utils/string.utils.ts";
import { ObjectDropdown } from "@apicurio/common-ui-components";
import { ConfigService, useConfigService } from "@services/useConfigService.ts";
import { UserService, useUserService } from "@services/useUserService.ts";

/**
 * Properties
 */
export type SearchVersionListProps = {
    versions: SearchedVersion[];
    onEdit: (version: SearchedVersion) => void;
    onExplore: (version: SearchedVersion) => void;
    onFilterByLabel: (key: string, value: string | undefined) => void;
};


/**
 * Models the list of versions.
 */
export const SearchVersionList: FunctionComponent<SearchVersionListProps> = (props: SearchVersionListProps) => {
    const config: ConfigService = useConfigService();
    const user: UserService = useUserService();

    const description = (version: SearchedVersion): string => {
        if (version.description) {
            return version.description;
        }
        return `An artifact version of type ${version.artifactType} with no description.`;
    };

    return (
        <DataList aria-label="List of versions" className="version-list">
            {
                props.versions.map( (version, idx) =>
                    <DataListItemRow className="version-list-item" key={shash(version.groupId + ":" + version.artifactId + ":" + version.version)}>
                        <DataListItemCells
                            dataListCells={[
                                <DataListCell key="type icon" className="type-icon-cell">
                                    <ArtifactTypeIcon artifactType={version.artifactType!}/>
                                </DataListCell>,
                                <DataListCell key="main content" className="content-cell">
                                    <div className="version-title">
                                        <ArtifactGroup groupId={version.groupId!} />
                                        <SearchVersionName
                                            groupId={version.groupId!}
                                            artifactId={version.artifactId!}
                                            version={version.version!}
                                            name={version.name!} />
                                        <VersionStateBadge version={version} />
                                    </div>
                                    <div className="version-description">{description(version)}</div>
                                    <div className="version-labels">
                                        <ListedItemLabels item={version} onClick={props.onFilterByLabel} />
                                    </div>
                                </DataListCell>
                            ]}
                        />
                        <DataListAction
                            id={`version-actions-${idx}`}
                            aria-label="Draft actions"
                            aria-labelledby={`version-actions-${idx}`}
                            isPlainButtonAction={true}
                        >
                            <ObjectDropdown
                                label=""
                                isKebab={true}
                                testId={`version-actions-dropdown-${idx}`}
                                popperProps={{
                                    position: "right"
                                }}
                                items={[
                                    {
                                        id: "view-version-in-explorer",
                                        label: "Explore version",
                                        testId: "view-version-in-explorer-" + idx,
                                        action: () => props.onExplore(version)
                                    },
                                    {
                                        id: "edit-version",
                                        label: "Edit draft",
                                        testId: "edit-version-" + idx,
                                        isVisible: () => !config.featureReadOnly() &&
                                            config.featureDraftMutability() &&
                                            version.state === "DRAFT" &&
                                            user.isUserDeveloper(version.owner),
                                        action: () => props.onEdit(version)
                                    },
                                ]}
                                onSelect={item => item.action()}
                                itemToString={item => item.label}
                                itemToTestId={item => item.testId}
                                itemIsDivider={item => item.divider}
                                itemIsVisible={item => !item.isVisible || item.isVisible()}
                            />
                        </DataListAction>
                    </DataListItemRow>
                )
            }
        </DataList>
    );

};
