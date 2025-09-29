import React, { FunctionComponent } from "react";
import "./SearchArtifactList.css";
import { DataList, DataListAction, DataListCell, DataListItemCells, DataListItemRow } from "@patternfly/react-core";
import { ArtifactGroup, ArtifactTypeIcon, ListedItemLabels } from "@app/components";
import { SearchArtifactName } from "@app/pages";
import { SearchedVersion } from "@sdk/lib/generated-client/models";
import { shash } from "@utils/string.utils.ts";
import { ObjectDropdown } from "@apicurio/common-ui-components";

/**
 * Properties
 */
export type SearchArtifactListProps = {
    artifacts: SearchedVersion[];
    onExplore: (artifact: SearchedVersion) => any;
};


/**
 * Models the list of artifacts.
 */
export const SearchArtifactList: FunctionComponent<SearchArtifactListProps> = (props: SearchArtifactListProps) => {

    const description = (artifact: SearchedVersion): string => {
        if (artifact.description) {
            return artifact.description;
        }
        return `An artifact of type ${artifact.artifactType} with no description.`;
    };

    return (
        <DataList aria-label="List of artifacts" className="artifact-list">
            {
                props.artifacts.map( (artifact, idx) =>
                    <DataListItemRow className="artifact-list-item" key={shash(artifact.groupId + ":" + artifact.artifactId)}>
                        <DataListItemCells
                            dataListCells={[
                                <DataListCell key="type icon" className="type-icon-cell">
                                    <ArtifactTypeIcon artifactType={artifact.artifactType!}/>
                                </DataListCell>,
                                <DataListCell key="main content" className="content-cell">
                                    <div className="artifact-title">
                                        <ArtifactGroup groupId={artifact.groupId!} />
                                        <SearchArtifactName groupId={artifact.groupId!} artifactId={artifact.artifactId!} name={artifact.name!} />
                                    </div>
                                    <div className="artifact-description">{description(artifact)}</div>
                                    <div className="artifact-labels">
                                        <ListedItemLabels item={artifact} />
                                    </div>
                                </DataListCell>
                            ]}
                        />
                        <DataListAction
                            id={`artifact-actions-${idx}`}
                            aria-label="Draft actions"
                            aria-labelledby={`artifact-actions-${idx}`}
                            isPlainButtonAction={true}
                        >
                            <ObjectDropdown
                                label=""
                                isKebab={true}
                                testId={`artifact-actions-dropdown-${idx}`}
                                popperProps={{
                                    position: "right"
                                }}
                                items={[
                                    {
                                        id: "view-artifact-in-explorer",
                                        label: "Explore artifact",
                                        testId: "view-artifact-in-explorer-" + idx,
                                        action: () => props.onExplore(artifact)
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
                )
            }
        </DataList>
    );

};
