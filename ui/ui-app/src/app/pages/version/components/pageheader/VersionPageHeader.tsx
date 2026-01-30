import { FunctionComponent } from "react";
import "./VersionPageHeader.css";
import {
    ActionList,
    ActionListItem,
    Button,
    Flex,
    FlexItem,
    Content,
    ContentVariants
} from "@patternfly/react-core";
import { If, ObjectDropdown } from "@apicurio/common-ui-components";
import { ArtifactMetaData, VersionMetaData } from "@sdk/lib/generated-client/models";
import { useUserService } from "@services/useUserService.ts";
import { useConfigService } from "@services/useConfigService.ts";
import { PencilAltIcon } from "@patternfly/react-icons";
import { IfFeature } from "@app/components";


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
    onFinalizeDraft: () => void;
    onCreateDraftFrom: () => void;
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
            label: "Create draft from...",
            testId: "action-create-draft",
            onSelect: () => props.onCreateDraftFrom(),
            isVisible: () => {
                return !config.featureReadOnly() &&
                    config.featureDraftMutability();
            }
        },
        {
            label: "Finalize draft",
            testId: "action-finalize-draft",
            onSelect: () => props.onFinalizeDraft(),
            isVisible: () => {
                return !config.featureReadOnly() &&
                    config.featureDraftMutability() &&
                    props.version?.state === "DRAFT" &&
                    user.isUserDeveloper(props.artifact?.owner);
            }
        },
        {
            divider: true,
            isVisible: () => {
                return !config.featureReadOnly() && config.featureDeleteVersion() && user.isUserDeveloper(props.artifact?.owner);
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
                <Content>
                    <Content component={ContentVariants.h1}>
                        <If condition={groupId !== null && groupId !== undefined && groupId !== "default"}>
                            <span>{groupId}</span>
                            <span style={{ color: "#6c6c6c", marginLeft: "10px", marginRight: "10px" }}> / </span>
                        </If>
                        <span>{artifactId}</span>
                        <span style={{ color: "#6c6c6c", marginLeft: "10px", marginRight: "10px" }}> / </span>
                        <span>{version}</span>
                    </Content>
                </Content>
            </FlexItem>
            <FlexItem align={{ default: "alignRight" }}>
                <ActionList>
                    <ActionListItem>
                        <Button id="download-version-button" variant="secondary"
                            data-testid="header-btn-download" onClick={props.onDownload}>Download</Button>
                        <IfFeature feature="readOnly" isNot={true}>
                            <IfFeature feature="draftMutability" is={true}>
                                <If condition={props.version?.state === "DRAFT" && user.isUserDeveloper(props.artifact?.owner)}>
                                    <Button id="edit-version-button" variant="primary" icon={<PencilAltIcon />}
                                        data-testid="header-btn-edit" onClick={props.onEdit}>Edit draft</Button>
                                </If>
                            </IfFeature>
                        </IfFeature>
                    </ActionListItem>
                    <ActionListItem>
                        <ObjectDropdown
                            label=""
                            items={actions}
                            onSelect={item => item.onSelect()}
                            itemToString={item => item.label}
                            itemToTestId={item => item.testId}
                            itemIsVisible={item => item.isVisible()}
                            itemIsDivider={item => item.divider}
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
