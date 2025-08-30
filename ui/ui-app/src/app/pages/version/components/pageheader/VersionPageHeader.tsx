import { FunctionComponent } from "react";
import "./VersionPageHeader.css";
import { Button, Flex, FlexItem, Text, TextContent, TextVariants } from "@patternfly/react-core";
import { IfAuth, IfFeature } from "@app/components";
import { If } from "@apicurio/common-ui-components";
import { ArtifactMetaData, VersionMetaData } from "@sdk/lib/generated-client/models";


/**
 * Properties
 */
export type VersionPageHeaderProps = {
    artifact: ArtifactMetaData | undefined;
    version: VersionMetaData | undefined;
    codegenEnabled: boolean;
    onDelete: () => void;
    onDownload: () => void;
    onGenerateClient: () => void;
};

/**
 * Models the page header for the Artifact page.
 */
export const VersionPageHeader: FunctionComponent<VersionPageHeaderProps> = (props: VersionPageHeaderProps) => {
    const groupId: string = props.artifact?.groupId || "default";
    const artifactId: string = props.artifact?.artifactId || "";
    const version: string = props.version?.version || "";

    return (
        <Flex className="example-border">
            <FlexItem>
                <TextContent>
                    <Text component={TextVariants.h1}>
                        <If condition={groupId !== null && groupId !== undefined && groupId !== "default"}>
                            <span>{groupId}</span>
                            <span style={{ color: "#6c6c6c", marginLeft: "10px", marginRight: "10px" }}> / </span>
                        </If>
                        <span>{artifactId}</span>
                        <span style={{ color: "#6c6c6c", marginLeft: "10px", marginRight: "10px" }}> / </span>
                        <span>{version}</span>
                    </Text>
                </TextContent>
            </FlexItem>
            <FlexItem align={{ default: "alignRight" }}>
                <Button id="download-artifact-button" variant="primary"
                    data-testid="header-btn-download" onClick={props.onDownload}>Download</Button>
                <If condition={(props.codegenEnabled && props.version?.artifactType === "OPENAPI")}>
                    <Button id="generate-client-action"
                        data-testid="version-btn-gen-client"
                        title="Generate a client"
                        onClick={props.onGenerateClient}
                        variant="secondary">Generate client SDK</Button>
                </If>
                <IfAuth isDeveloper={true} owner={props.artifact?.owner}>
                    <IfFeature feature="readOnly" isNot={true}>
                        <IfFeature feature="deleteVersion" is={true}>
                            <Button id="delete-artifact-button" variant="danger"
                                data-testid="header-btn-delete" onClick={props.onDelete}>Delete version</Button>
                        </IfFeature>
                    </IfFeature>
                </IfAuth>
            </FlexItem>
        </Flex>
    );
};
