import { FunctionComponent, useState } from "react";
import { Button, Toolbar, ToolbarContent, ToolbarGroup, ToolbarItem } from "@patternfly/react-core";
import { QuestionCircleIcon } from "@patternfly/react-icons";
import { AvatarDropdown, IfAuth } from "@app/components";
import { AppAboutModal, BackendInfo, FrontendInfo } from "@apicurio/common-ui-components";
import { useVersionService, VersionService } from "@services/useVersionService.ts";
import { SystemService, useSystemService } from "@services/useSystemService.ts";


export type AppHeaderToolbarProps = {
    // No properties.
};


export const AppHeaderToolbar: FunctionComponent<AppHeaderToolbarProps> = () => {
    const [isAboutModalOpen, setIsAboutModalOpen] = useState(false);
    const version: VersionService = useVersionService();
    const system: SystemService = useSystemService();

    const frontendInfo: FrontendInfo = {
        ...version.getVersion()
    };

    const fetchBackendInfo = async (): Promise<BackendInfo> => {
        return system.getInfo().then(info => {
            return {
                name: info.name,
                description: info.description,
                version: info.version,
                builtOn: info.builtOn,
                digest: ""
            } as BackendInfo;
        });
    };

    return (
        <>
            <AppAboutModal
                frontendInfo={frontendInfo}
                backendInfo={fetchBackendInfo}
                backendLabel="Registry API info"
                brandImageSrc="/apicurio_registry_logo_reverse.svg"
                brandImageAlt={version.getVersion().name}
                isOpen={isAboutModalOpen}
                onClose={() => setIsAboutModalOpen(false)} />
            <Toolbar id="app-header-toolbar" isFullHeight={true}>
                <ToolbarContent>
                    <ToolbarGroup align={{ default: "alignRight" }}>
                        <ToolbarItem>
                            <Button variant="plain" onClick={() => setIsAboutModalOpen(!isAboutModalOpen)}>
                                <QuestionCircleIcon style={{ fontSize: "16px" }} />
                            </Button>
                        </ToolbarItem>
                        <ToolbarItem>
                            <IfAuth enabled={true}>
                                <AvatarDropdown />
                            </IfAuth>
                        </ToolbarItem>
                    </ToolbarGroup>
                </ToolbarContent>
            </Toolbar>
        </>
    );

};
