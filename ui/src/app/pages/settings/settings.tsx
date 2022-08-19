/**
 * @license
 * Copyright 2022 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import React from "react";
import "./settings.css";
import {
    Card,
    CardBody,
    CardTitle,
    PageSection,
    PageSectionVariants,
    SearchInput,
    TextContent
} from "@patternfly/react-core";
import { PageComponent, PageProps, PageState } from "../basePage";
import { Services } from "../../../services";
import { IfNotEmpty, RootPageHeader } from "../../components";
import { ConfigurationProperty } from "../../../models/configurationProperty.model";
import { ConfigProperty } from "./components";
import { If } from "../../components/common/if";


interface PropertyGroup {
    id: string,
    label: string,
    propertyNames: string[];
    properties?: ConfigurationProperty[];
}

const PROPERTY_GROUPS: PropertyGroup[] = [
    {
        id: "authn",
        label: "Authentication settings",
        propertyNames: [
            "registry.auth.basic-auth-client-credentials.enabled",
        ]
    },
    {
        id: "authz",
        label: "Authorization settings",
        propertyNames: [
            "registry.auth.owner-only-authorization",
            "registry.auth.owner-only-authorization.limit-group-access",
            "registry.auth.anonymous-read-access.enabled",
            "registry.auth.authenticated-read-access.enabled",
        ]
    },
    {
        id: "compatibility",
        label: "Compatibility settings",
        propertyNames: [
            "registry.ccompat.legacy-id-mode.enabled",
        ]
    },
    {
        id: "console",
        label: "Web console settings",
        propertyNames: [
            "registry.download.href.ttl",
            "registry.ui.features.readOnly"
        ]
    },
];


/**
 * Properties
 */
// tslint:disable-next-line:no-empty-interface
export interface SettingsPageProps extends PageProps {

}

/**
 * State
 */
// tslint:disable-next-line:no-empty-interface
export interface SettingsPageState extends PageState {
    properties?: ConfigurationProperty[];
    searchedProperties?: ConfigurationProperty[];
    searchCriteria: string;
}

/**
 * The settings page.
 */
export class SettingsPage extends PageComponent<SettingsPageProps, SettingsPageState> {

    constructor(props: Readonly<SettingsPageProps>) {
        super(props);
    }

    public renderPage(): React.ReactElement {
        return (
            <React.Fragment>
                <PageSection className="ps_settings-header" variant={PageSectionVariants.light} padding={{ default : "noPadding" }}>
                    <RootPageHeader tabKey={3} />
                </PageSection>
                <PageSection className="ps_settings-description" variant={PageSectionVariants.light}>
                    <TextContent>
                        Configure global settings for this Service Registry instance.
                    </TextContent>
                    <TextContent style={{marginTop: "10px", marginBottom: "5px", maxWidth: "450px"}}>
                        <SearchInput placeholder={`Filter by keyword`}
                                     aria-label="Filter by keyword"
                                     value={this.state.searchCriteria}
                                     onChange={this.onSearchCriteria}
                                     onSearch={this.onSearchSettings}
                                     onClear={this.onSearchClear}
                        />
                    </TextContent>
                </PageSection>
                <PageSection variant={PageSectionVariants.default} isFilled={true}>
                    <IfNotEmpty collection={this.state.searchedProperties} emptyStateMessage={`No settings found matching your search criteria.`}>
                        {
                            this.propertyGroups().map(group =>
                                <If key={group.id} condition={group.properties !== undefined && group.properties.length > 0}>
                                    <Card key={group.id} className="config-property-group">
                                        <CardTitle className="title">{group.label}</CardTitle>
                                        <CardBody className="config-properties">
                                            {
                                                group.properties?.map(prop =>
                                                    <ConfigProperty key={prop.name}
                                                                    property={prop}
                                                                    onChange={this.onPropertyChange}
                                                                    onReset={this.onPropertyReset}
                                                    />
                                                )
                                            }
                                        </CardBody>
                                    </Card>
                                </If>
                            )
                        }
                    </IfNotEmpty>
                </PageSection>
            </React.Fragment>
        );
    }

    protected initializePageState(): SettingsPageState {
        return {
            searchCriteria: ""
        };
    }

    // @ts-ignore
    protected createLoaders(): Promise {
        return Services.getAdminService().listConfigurationProperties().then( properties => {
                this.setMultiState({
                    isLoading: false,
                    properties
                });
                this.filterProperties();
            });
    }

    private groupFor(groups: PropertyGroup[], prop: ConfigurationProperty): PropertyGroup {
        for (const group of groups) {
            if (group.propertyNames.indexOf(prop.name) >= 0) {
                return group;
            }
        }
        // Default to the last group (additional properties).
        return groups[groups.length - 1];
    }

    private propertyGroups(): PropertyGroup[] {
        const groups: PropertyGroup[] = [...PROPERTY_GROUPS];
        groups.forEach(group => group.properties = []);
        const additionalGroup: PropertyGroup = {
            id: "additional",
            label: "Additional properties",
            properties: [],
            propertyNames: []
        };
        groups.push(additionalGroup);
        this.state.searchedProperties?.forEach(prop => {
            this.groupFor(groups, prop).properties?.push(prop);
        });
        groups.forEach(group => {
            group.properties = group.properties?.sort(
                (prop1, prop2) => prop1.label.localeCompare(prop2.label));
        });
        return groups;
    }

    private acceptProperty = (property: ConfigurationProperty): boolean => {
        if (!this.state.searchCriteria || this.state.searchCriteria.trim().length === 0) {
            return true;
        }
        const sc: string = this.state.searchCriteria.toLocaleLowerCase();
        return property.label.toLocaleLowerCase().indexOf(sc) >= 0 ||
            property.description.toLocaleLowerCase().indexOf(sc) >= 0;
    };

    private filterProperties(): void {
        const filteredProperties: ConfigurationProperty[] | undefined = this.state.properties?.filter(this.acceptProperty);
        this.setSingleState("searchedProperties", filteredProperties);
    }

    private onSearchCriteria = (criteria: string): void => {
        this.setSingleState("searchCriteria", criteria);
    };

    private onSearchSettings = (): void => {
        this.filterProperties();
    };

    private onSearchClear = (): void => {
        this.setMultiState({
            searchCriteria: ""
        }, this.onSearchSettings);
    }

    private onPropertyChange = (property: ConfigurationProperty, newValue: string): void => {
        property.value = newValue;
        Services.getAdminService().setConfigurationProperty(property.name, newValue).then(() => {
            // The property was updated successfully.  Update the UI to display all config
            // properties (the list may have changed by changing one of the values).
            this.createLoaders();
            Services.getAlertsService().settingChanged(property, newValue);
        }).catch(error => {
            // Failed to set the property... report the error somehow.
            this.handleServerError(error, "Error setting configuration property");
        });
    };

    private onPropertyReset = (property: ConfigurationProperty): void => {
        Services.getAdminService().resetConfigurationProperty(property.name).then(() => {
            // The property was updated successfully.  Update the UI to display all config
            // properties (the list may have changed by changing one of the values).
            this.createLoaders();
        }).catch(error => {
            // Failed to set the property... report the error somehow.
            this.handleServerError(error, "Error resetting configuration property");
        });
    }

}
