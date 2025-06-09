import { FunctionComponent } from "react";
import "./VersionInfoTabContent.css";
import "@app/styles/empty.css";
import { ArtifactTypeIcon, IfAuth, IfFeature } from "@app/components";
import {
    Button,
    Card,
    CardBody,
    CardTitle,
    DescriptionList,
    DescriptionListDescription,
    DescriptionListGroup,
    DescriptionListTerm,
    Divider,
    Flex,
    FlexItem,
    Label,
    Truncate
} from "@patternfly/react-core";
import { PencilAltIcon } from "@patternfly/react-icons";
import { FromNow, If } from "@apicurio/common-ui-components";
import { ArtifactMetaData, VersionMetaData } from "@sdk/lib/generated-client/models";
import { labelsToAny } from "@utils/rest.utils.ts";
import { VersionComments } from "@app/pages";

/**
 * Properties
 */
export type VersionInfoTabContentProps = {
    artifact: ArtifactMetaData;
    version: VersionMetaData;
    codegenEnabled: boolean;
    onEditMetaData: () => void;
    onGenerateClient: () => void;
};

/**
 * Models the content of the Version Info (overview) tab.
 */
export const VersionInfoTabContent: FunctionComponent<VersionInfoTabContentProps> = (props: VersionInfoTabContentProps) => {

    const description = (): string => {
        return props.version.description || "No description";
    };

    const artifactName = (): string => {
        return props.version.name || "No name";
    };

    const labels: any = labelsToAny(props.version.labels);

    return (
        <div className="overview-tab-content">
            <div className="version-basics">
                <Card>
                    <CardTitle>
                        <div className="title-and-type">
                            <Flex>
                                <FlexItem className="type"><ArtifactTypeIcon
                                    artifactType={props.artifact.artifactType!}/></FlexItem>
                                <FlexItem className="title">Version metadata</FlexItem>
                                <FlexItem className="actions" align={{ default: "alignRight" }}>
                                    <If condition={(props.codegenEnabled && props.version.artifactType === "OPENAPI")}>
                                        <Button id="generate-client-action"
                                            data-testid="version-btn-gen-client"
                                            title="Generate a client"
                                            onClick={props.onGenerateClient}
                                            variant="link">Generate client SDK</Button>
                                    </If>
                                    <IfAuth isDeveloper={true} owner={props.artifact.owner}>
                                        <IfFeature feature="readOnly" isNot={true}>
                                            <Button id="edit-action"
                                                data-testid="version-btn-edit"
                                                onClick={props.onEditMetaData}
                                                variant="link"><PencilAltIcon/>{" "}Edit</Button>
                                        </IfFeature>
                                    </IfAuth>
                                </FlexItem>
                            </Flex>
                        </div>
                    </CardTitle>
                    <Divider/>
                    <CardBody>
                        <DescriptionList className="metaData" isCompact={true}>
                            <DescriptionListGroup>
                                <DescriptionListTerm>Name</DescriptionListTerm>
                                <DescriptionListDescription
                                    data-testid="version-details-name"
                                    className={!props.version.name ? "empty-state-text" : ""}
                                >
                                    {artifactName()}
                                </DescriptionListDescription>
                            </DescriptionListGroup>
                            <DescriptionListGroup>
                                <DescriptionListTerm>Description</DescriptionListTerm>
                                <DescriptionListDescription
                                    data-testid="version-details-description"
                                    className={!props.version.description ? "empty-state-text" : ""}
                                >
                                    {description()}
                                </DescriptionListDescription>
                            </DescriptionListGroup>
                            <DescriptionListGroup>
                                <DescriptionListTerm>Status</DescriptionListTerm>
                                <DescriptionListDescription
                                    data-testid="version-details-state">{props.version.state}</DescriptionListDescription>
                            </DescriptionListGroup>
                            <DescriptionListGroup>
                                <DescriptionListTerm>Created</DescriptionListTerm>
                                <DescriptionListDescription data-testid="version-details-created-on">
                                    <FromNow date={props.version.createdOn}/>
                                </DescriptionListDescription>
                            </DescriptionListGroup>
                            <If condition={props.version.owner !== undefined && props.version.owner !== ""}>
                                <DescriptionListGroup>
                                    <DescriptionListTerm>Owner</DescriptionListTerm>
                                    <DescriptionListDescription data-testid="version-details-created-by">
                                        <span>{props.version.owner}</span>
                                    </DescriptionListDescription>
                                </DescriptionListGroup>
                            </If>
                            <DescriptionListGroup>
                                <DescriptionListTerm>Modified</DescriptionListTerm>
                                <DescriptionListDescription data-testid="version-details-modified-on">
                                    <FromNow date={props.artifact.modifiedOn}/>
                                </DescriptionListDescription>
                            </DescriptionListGroup>
                            <DescriptionListGroup>
                                <DescriptionListTerm>Global ID</DescriptionListTerm>
                                <DescriptionListDescription
                                    data-testid="version-details-global-id">{props.version.globalId}</DescriptionListDescription>
                            </DescriptionListGroup>
                            <DescriptionListGroup>
                                <DescriptionListTerm>Content ID</DescriptionListTerm>
                                <DescriptionListDescription
                                    data-testid="version-details-content-id">{props.version.contentId}</DescriptionListDescription>
                            </DescriptionListGroup>
                            <DescriptionListGroup>
                                <DescriptionListTerm>Labels</DescriptionListTerm>
                                {!labels || !Object.keys(labels).length ?
                                    <DescriptionListDescription data-testid="version-details-labels"
                                        className="empty-state-text">No
                                        labels</DescriptionListDescription> :
                                    <DescriptionListDescription
                                        data-testid="version-details-labels">{Object.entries(labels).map(([key, value]) =>
                                            <Label key={`label-${key}`} color="purple"
                                                style={{ marginBottom: "2px", marginRight: "5px" }}>
                                                <Truncate className="label-truncate" content={`${key}=${value}`}/>
                                            </Label>
                                        )}</DescriptionListDescription>
                                }
                            </DescriptionListGroup>
                        </DescriptionList>
                    </CardBody>
                </Card>
            </div>
            <div className="version-comments">
                <Card>
                    <CardTitle>
                        <div className="comments-label">Comments</div>
                    </CardTitle>
                    <Divider/>
                    <CardBody>
                        <VersionComments version={props.version} />
                    </CardBody>
                </Card>
            </div>
        </div>
    );

};
