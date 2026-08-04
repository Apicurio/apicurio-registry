import { Drawer, DrawerContent, DrawerContentBody, DrawerPanelContent } from "@patternfly/react-core";
import { FunctionComponent, ReactNode } from "react";
import "./ResizableOverviewDrawer.css";

const MINIMUM_PANE_WIDTH = "400px";

export type ResizableOverviewDrawerProps = {
    head: ReactNode;
    body: ReactNode;
};

/**
 * Shared split layout for the artifact, version, and group overview pages.
 */
export const ResizableOverviewDrawer: FunctionComponent<ResizableOverviewDrawerProps> = ({ head, body }) => (
    <Drawer className="resizable-overview-drawer" isExpanded={true} onExpand={() => {}} isInline={true} position="start">
        <DrawerContent
            panelContent={
                <DrawerPanelContent
                    className="resizable-overview-drawer__panel"
                    isResizable={true}
                    defaultSize="500px"
                    minSize={MINIMUM_PANE_WIDTH}
                    maxSize={`calc(100% - ${MINIMUM_PANE_WIDTH})`}
                    resizeAriaLabel="Resize overview panes"
                >
                    {head}
                </DrawerPanelContent>
            }
            style={{ backgroundColor: "white" }}
        >
            <DrawerContentBody hasPadding={false}>{body}</DrawerContentBody>
        </DrawerContent>
    </Drawer>
);
