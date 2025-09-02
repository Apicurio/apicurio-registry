import { FunctionComponent } from "react";
import "./EditorContext.css";
import { Breadcrumb, BreadcrumbItem, Button, Icon, Popover } from "@patternfly/react-core";
import { FromNow, If, ObjectDropdown } from "@apicurio/common-ui-components";
import { Draft } from "@models/drafts";
import { Link } from "react-router-dom";
import { ExclamationTriangleIcon } from "@patternfly/react-icons";
import { AppNavigation, useAppNavigation } from "@services/useAppNavigation.ts";
import { ArtifactTypes } from "@services/useArtifactTypesService.ts";

/**
 * Properties
 */
export type EditorContextProps = {
    draft: Draft;
    dirty: boolean;
    contentConflict: boolean;
    onSave: () => void;
    onFormat: () => void;
    onDownload: () => void;
    onCompareContent: () => void;
}

type EditorContextMenuItem = {
    label?: string;
    isDivider?: boolean;
    testId?: string;
    isVisible?: () => boolean;
    isDisabled?: () => boolean;
    onSelect?: () => void;
};


/**
 * The context of the draft when editing a draft on the editor page.
 */
export const EditorContext: FunctionComponent<EditorContextProps> = (props: EditorContextProps) => {
    const appNavigation: AppNavigation = useAppNavigation();

    const menuItems: EditorContextMenuItem[] = [
        {
            label: "Format draft content",
            testId: "action-format",
            isVisible: () => { return [ArtifactTypes.AVRO, ArtifactTypes.JSON].includes(props.draft?.type); },
            onSelect: props.onFormat
        },
        {
            label: "Show draft changes",
            testId: "action-compare",
            isDisabled: () => { return !props.dirty; },
            onSelect: props.onCompareContent
        },
        {
            isDivider: true
        },
        {
            label: "Download draft",
            testId: "action-download",
            onSelect: props.onDownload
        },
    ];

    const groupId: string = encodeURIComponent(props.draft.groupId || "default");
    const draftId: string = encodeURIComponent(props.draft.draftId!);
    const version: string = encodeURIComponent(props.draft.version!);
    
    const breadcrumbs = (
        <Breadcrumb>
            <BreadcrumbItem><Link to={appNavigation.createLink("/explore")} data-testid="breadcrumb-lnk-explore">Explore</Link></BreadcrumbItem>
            <BreadcrumbItem><Link to={appNavigation.createLink(`/explore/${ groupId }`)}
                data-testid="breadcrumb-lnk-group">{ groupId }</Link></BreadcrumbItem>
            <BreadcrumbItem><Link to={appNavigation.createLink(`/explore/${ groupId }/${ draftId }`)}
                data-testid="breadcrumb-lnk-artifact">{ draftId }</Link></BreadcrumbItem>
            <BreadcrumbItem><Link to={appNavigation.createLink(`/explore/${ groupId }/${ draftId }/versions/${ version }`)}
                data-testid="breadcrumb-lnk-version">{ version as string }</Link></BreadcrumbItem>
            <BreadcrumbItem isActive={true}><b>Editor</b></BreadcrumbItem>
        </Breadcrumb>
    );

    const contentConflictHeader = (
        <span>Content conflict</span>
    );
    const contentConflictComponent = (
        <div>The content of this Draft has been saved by someone else since you opened this editor!</div>
    );

    return (
        <div className="editor-context">
            <div key="editor-context-breadcrumbs" className="editor-context-breadcrumbs" children={breadcrumbs} />
            <If condition={props.contentConflict}>
                <div key="editor-context-conflict" className="editor-context-conflict">
                    <Icon status="warning">
                        <Popover
                            triggerAction="hover"
                            headerContent={contentConflictHeader}
                            bodyContent={contentConflictComponent}>
                            <ExclamationTriangleIcon className="icon-pulse" />
                        </Popover>
                    </Icon>
                </div>
            </If>
            <If condition={props.draft.modifiedOn !== undefined}>
                <div key="editor-context-last-modified" className="editor-context-last-modified">
                    <span>Last modified:</span>
                    <FromNow date={props.draft.modifiedOn}/>
                </div>
            </If>
            <div key="editor-context-actions" className="editor-context-actions">
                <ObjectDropdown
                    label="Actions"
                    items={menuItems}
                    testId="select-actions"
                    onSelect={item => item.onSelect()}
                    noSelectionLabel="Actions"
                    itemToTestId={item => item.testId}
                    itemIsVisible={item => !item.isVisible || item.isVisible()}
                    itemIsDivider={item => item.isDivider}
                    itemIsDisabled={item => item.isDisabled === undefined ? false : item.isDisabled()}
                    itemToString={item => item.label} />
            </div>
            <div key="editor-context-save" className="editor-context-save">
                <Button className="btn-save" variant="primary" onClick={() => props.onSave()} isDisabled={!props.dirty}>Save</Button>
            </div>
        </div>
    );
};
