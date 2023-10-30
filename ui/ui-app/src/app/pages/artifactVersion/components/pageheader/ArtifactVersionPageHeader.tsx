import { FunctionComponent } from "react";
import "./ArtifactVersionPageHeader.css";
import { Button, Flex, FlexItem, Text, TextContent, TextVariants } from "@patternfly/react-core";
import { IfAuth, IfFeature } from "@app/components";
import { VersionSelector } from "@app/pages";
import { SearchedVersion } from "@models/searchedVersion.model.ts";


/**
 * Properties
 */
export type ArtifactVersionPageHeaderProps = {
    title: string;
    groupId: string;
    artifactId: string;
    onDeleteArtifact: () => void;
    onUploadVersion: () => void;
    version: string;
    versions: SearchedVersion[];
};

/**
 * Models the page header for the Artifact page.
 */
export const ArtifactVersionPageHeader: FunctionComponent<ArtifactVersionPageHeaderProps> = (props: ArtifactVersionPageHeaderProps) => {
    return (
        <Flex className="example-border">
            <FlexItem>
                <TextContent>
                    <Text component={TextVariants.h1}>{ props.title }</Text>
                </TextContent>
            </FlexItem>
            <FlexItem align={{ default: "alignRight" }}>
                <VersionSelector version={props.version} versions={props.versions}
                    groupId={props.groupId} artifactId={props.artifactId} />
                <IfAuth isDeveloper={true}>
                    <IfFeature feature="readOnly" isNot={true}>
                        <Button id="delete-artifact-button" variant="secondary"
                            data-testid="header-btn-delete" onClick={props.onDeleteArtifact}>Delete</Button>
                        <Button id="upload-version-button" variant="primary"
                            data-testid="header-btn-upload-version" onClick={props.onUploadVersion}>Upload new version</Button>
                    </IfFeature>
                </IfAuth>
            </FlexItem>
        </Flex>
    );
};
