import { FunctionComponent } from "react";
import "./VersionPageHeader.css";
import {
    ActionList,
    ActionListItem,
    Button,
    Flex,
    FlexItem,
    Text,
    TextContent,
    TextVariants
} from "@patternfly/react-core";
import { If, ObjectDropdown } from "@apicurio/common-ui-components";
import { ArtifactMetaData, VersionMetaData } from "@sdk/lib/generated-client/models";
import { useUserService } from "@services/useUserService.ts";
import { useConfigService } from "@services/useConfigService.ts";


/**
 * Properties
 */
export type VersionPageHeaderProps = {
    artifact: ArtifactMetaData | undefined;
    version: VersionMetaData | undefined;
    codegenEnabled: boolean;
    onEdit: () => void;
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

    const user = useUserService();
    const config = useConfigService();

    const actions: any[] = [
        {
            label: "Generate client SDK",
            testId: "action-generate-client-sdk",
            onSelect: () => props.onGenerateClient(),
            isVisible: () => {
                return props.codegenEnabled && props.version?.artifactType === "OPENAPI";
            }
        },
        {
            label: "Edit draft",
            testId: "action-edit-draft",
            onSelect: () => props.onEdit(),
            isVisible: () => {
                return !config.featureReadOnly() &&
                    config.featureDraftMutability() &&
                    props.version?.state === "DRAFT" &&
                    user.isUserDeveloper(props.artifact?.owner);
            }
        },
        {
            label: "Delete version",
            testId: "action-delete-version",
            onSelect: () => props.onDelete(),
            isVisible: () => {
                return !config.featureReadOnly() && config.featureDeleteVersion() && user.isUserDeveloper(props.artifact?.owner);
            }
        }
    ];

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
                <ActionList>
                    <ActionListItem>
                        <Button id="download-artifact-button" variant="secondary"
                            data-testid="header-btn-download" onClick={props.onDownload}>Download</Button>
                    </ActionListItem>
                    <ActionListItem>
                        <ObjectDropdown
                            label=""
                            items={actions}
                            onSelect={item => item.onSelect()}
                            itemToString={item => item.label}
                            itemToTestId={item => item.testId}
                            itemIsVisible={item => item.isVisible()}
                            popperProps={{
                                position: "right"
                            }}
                            isKebab={true}
                        />
                    </ActionListItem>
                </ActionList>
            </FlexItem>
        </Flex>
    );
};
