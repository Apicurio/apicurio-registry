import { Drawer, DrawerContent, DrawerContentBody, DrawerPanelContentProps } from "@patternfly/react-core";
import { cloneElement, FunctionComponent, ReactElement, ReactNode } from "react";
import "./ResizableOverviewDrawer.css";

const MINIMUM_PANE_WIDTH = "400px";

export type ResizableOverviewDrawerProps = {
    panelContent: ReactElement<DrawerPanelContentProps>;
    children: ReactNode;
};

/**
 * Shared split layout for the artifact, version, and group overview pages.
 */
export const ResizableOverviewDrawer: FunctionComponent<ResizableOverviewDrawerProps> = ({ panelContent, children }) => (
    <Drawer className="resizable-overview-drawer" isExpanded={true} onExpand={() => {}} isInline={true} position="start">
        <DrawerContent panelContent={cloneElement(panelContent, {
            className: `resizable-overview-drawer__panel ${panelContent.props.className || ""}`,
            isResizable: true,
            defaultSize: "500px",
            minSize: MINIMUM_PANE_WIDTH,
            maxSize: `calc(100% - ${MINIMUM_PANE_WIDTH})`,
            resizeAriaLabel: "Resize overview panes"
        })} style={{ backgroundColor: "white" }}>
            <DrawerContentBody hasPadding={false}>{children}</DrawerContentBody>
        </DrawerContent>
    </Drawer>
);
