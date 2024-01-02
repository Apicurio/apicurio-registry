import React, { FunctionComponent, useState } from "react";
import "./VersionSelector.css";
import {
    Button,
    ButtonVariant,
    Dropdown,
    InputGroup,
    MenuToggle,
    MenuToggleElement,
    TextInput
} from "@patternfly/react-core";
import { SearchIcon } from "@patternfly/react-icons";
import { Link } from "react-router-dom";
import { SearchedVersion } from "@models/searchedVersion.model.ts";
import { Services } from "@services/services.ts";
import { useAppNavigation } from "@hooks/useAppNavigation.ts";
import { FromNow } from "@apicurio/common-ui-components";


/**
 * Properties
 */
export type VersionSelectorProps = {
    groupId: string;
    artifactId: string;
    version: string;
    versions: SearchedVersion[];
}

export const VersionSelector: FunctionComponent<VersionSelectorProps> = (props: VersionSelectorProps) => {
    const [isOpen, setOpen] = useState<boolean>(false);

    const appNav = useAppNavigation();

    const dropdownClasses = (): string => {
        const classes: string[] = [ "version-selector-dropdown" ];
        if (Services.getConfigService().featureReadOnly()) {
            classes.push("dropdown-align-right");
        }
        return classes.join(" ");
    };

    const onToggle = (): void => {
        setOpen(!isOpen);
    };

    return (
        <Dropdown
            className={dropdownClasses()}
            onOpenChange={setOpen}
            toggle={(toggleRef: React.Ref<MenuToggleElement>) => (
                <MenuToggle data-testid="versions-toggle" ref={toggleRef} onClick={onToggle} isExpanded={isOpen}>
                    Version: { props.version }
                </MenuToggle>
            )}
            isOpen={isOpen}
        >
            <div className="version-filter" style={{ display: "none" }}>
                <InputGroup>
                    <TextInput name="filter" id="versionFilter" type="search" data-testid="versions-form-filter" aria-label="Version filter" />
                    <Button variant={ButtonVariant.control} data-testid="versions-form-btn-search" aria-label="search button for search input">
                        <SearchIcon />
                    </Button>
                </InputGroup>
            </div>
            <div className="version-header">
                <div className="version-item">
                    <span className="name">Version</span>
                    <span className="date">Created On</span>
                </div>
            </div>
            <div className="version-list">
                <Link key="latest"
                    data-testid="versions-lnk-latest"
                    to={appNav.createLink(`/artifacts/${encodeURIComponent(props.groupId)}/${encodeURIComponent(props.artifactId)}/versions/latest`)}
                    className="version-item latest">
                    <span className="name">latest</span>
                    <span className="date" />
                </Link>
                {
                    props.versions.map((v, idx) =>
                        <Link key={v.version}
                            data-testid={`versions-lnk-${idx}`}
                            to={appNav.createLink(`/artifacts/${encodeURIComponent(props.groupId)}/${encodeURIComponent(props.artifactId)}/versions/${v.version}`)}
                            className="version-item">
                            <span className="name">{ v.version }</span>
                            <span className="date">
                                <FromNow date={v.createdOn} />
                            </span>
                        </Link>
                    )
                }
            </div>
        </Dropdown>
    );
};
