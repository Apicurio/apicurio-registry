import { FunctionComponent } from "react";
import "./ArtifactList.css";
import { Badge, DataList, DataListCell, DataListItemCells, DataListItemRow } from "@patternfly/react-core";
import { ArtifactTypeIcon } from "@app/components";
import { ArtifactGroup, ArtifactName } from "@app/pages";
import { SearchedArtifact } from "@models/searchedArtifact.model.ts";

/**
 * Properties
 */
export type ArtifactListProps = {
    artifacts: SearchedArtifact[];
    onGroupClick: (groupId: string) => void;
};


/**
 * Models the list of artifacts.
 */
export const ArtifactList: FunctionComponent<ArtifactListProps> = (props: ArtifactListProps) => {

    const labels = (artifact: SearchedArtifact): string[] => {
        return artifact.labels ? artifact.labels : [];
    };

    const statuses = (artifact: SearchedArtifact): string[] => {
        const rval: string[] = [];
        if (artifact.state === "DISABLED") {
            rval.push("Disabled");
        }
        if (artifact.state === "DEPRECATED") {
            rval.push("Deprecated");
        }
        return rval;
    };

    const description = (artifact: SearchedArtifact): string => {
        if (artifact.description) {
            return artifact.description;
        }
        return `An artifact of type ${artifact.type} with no description.`;
    };

    return (
        <DataList aria-label="List of artifacts" className="artifact-list">
            {
                props.artifacts.map( (artifact, /* idx */) =>
                    <DataListItemRow className="artifact-list-item" key={artifact.id}>
                        <DataListItemCells
                            dataListCells={[
                                <DataListCell key="type icon" className="type-icon-cell">
                                    <ArtifactTypeIcon type={artifact.type}/>
                                </DataListCell>,
                                <DataListCell key="main content" className="content-cell">
                                    <div className="artifact-title">
                                        <ArtifactGroup groupId={artifact.groupId} onClick={props.onGroupClick} />
                                        <ArtifactName groupId={artifact.groupId} id={artifact.id} name={artifact.name} />
                                        {
                                            statuses(artifact).map( status =>
                                                <Badge className="status-badge" key={status} isRead={true}>{status}</Badge>
                                            )
                                        }
                                    </div>
                                    <div className="artifact-description">{description(artifact)}</div>
                                    <div className="artifact-tags">
                                        {
                                            labels(artifact).map( label =>
                                                <Badge key={label} isRead={true}>{label}</Badge>
                                            )
                                        }
                                    </div>
                                </DataListCell>
                            ]}
                        />
                    </DataListItemRow>
                )
            }
        </DataList>
    );

};
