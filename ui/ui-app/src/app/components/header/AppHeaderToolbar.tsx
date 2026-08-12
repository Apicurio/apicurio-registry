import { FunctionComponent, useState } from "react";
import { Button, Toolbar, ToolbarContent, ToolbarGroup, ToolbarItem } from "@patternfly/react-core";
import { QuestionCircleIcon, SunIcon, MoonIcon } from "@patternfly/react-icons";
import { AvatarDropdown, IfAuth } from "@app/components";
import { AppAboutModal, BackendInfo, FrontendInfo } from "@apicurio/common-ui-components";
import { useVersionService, VersionService } from "@services/useVersionService.ts";
import { SystemService, useSystemService } from "@services/useSystemService.ts";

import { useThemeService, useLogoSrc } from "@services/useThemeService.tsx";


export type AppHeaderToolbarProps = object;


export const AppHeaderToolbar: FunctionComponent<AppHeaderToolbarProps> = () => {
    const [isAboutModalOpen, setIsAboutModalOpen] = useState(false);
    const version: VersionService = useVersionService();
    const system: SystemService = useSystemService();

    const { isDark, toggleTheme } = useThemeService();

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

    const logoSrc: string = useLogoSrc();

    return (
        <>
            <AppAboutModal
                frontendInfo={frontendInfo}
                backendInfo={fetchBackendInfo}
                backendLabel="Registry API info"
                brandImageSrc={logoSrc}
                brandImageAlt={version.getVersion().name}
                isOpen={isAboutModalOpen}
                onClose={() => setIsAboutModalOpen(false)} />
            <Toolbar id="app-header-toolbar" isFullHeight={true}>
                <ToolbarContent>
                    <ToolbarGroup align={{ default: "alignEnd" }}>
                        <ToolbarItem>
                            <Button
                                aria-label={isDark ? "Switch to light theme" : "Switch to dark theme"}
                                aria-pressed={isDark}
                                icon={isDark ? <SunIcon style={{ fontSize: "16px" }} /> : <MoonIcon style={{ fontSize: "16px" }} />}
                                variant="plain"
                                onClick={toggleTheme}
                            />
                        </ToolbarItem>
                        <ToolbarItem>
                            <Button icon={<QuestionCircleIcon style={{ fontSize: "16px" }} />} variant="plain" aria-label="About" onClick={() => setIsAboutModalOpen(!isAboutModalOpen)} />
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
