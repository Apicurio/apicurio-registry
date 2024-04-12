import { FunctionComponent, useEffect, useState } from "react";
import "./SettingsPage.css";
import {
    Card,
    CardBody,
    CardTitle,
    PageSection,
    PageSectionVariants,
    SearchInput,
    TextContent
} from "@patternfly/react-core";
import { RootPageHeader } from "@app/components";
import { ConfigurationProperty } from "@models/configurationProperty.model.ts";
import { ConfigProperty, PageDataLoader, PageError, PageErrorHandler, toPageError } from "@app/pages";
import { If, IfNotEmpty } from "@apicurio/common-ui-components";
import { AdminService, useAdminService } from "@services/useAdminService.ts";
import { AlertsService, useAlertsService } from "@services/useAlertsService.tsx";


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
            "apicurio.authn.basic-client-credentials.enabled",
        ]
    },
    {
        id: "authz",
        label: "Authorization settings",
        propertyNames: [
            "apicurio.auth.owner-only-authorization",
            "apicurio.auth.owner-only-authorization.limit-group-access",
            "apicurio.auth.anonymous-read-access.enabled",
            "apicurio.auth.authenticated-read-access.enabled",
        ]
    },
    {
        id: "compatibility",
        label: "Compatibility settings",
        propertyNames: [
            "apicurio.ccompat.legacy-id-mode.enabled",
            "apicurio.ccompat.use-canonical-hash",
            "apicurio.ccompat.max-subjects",
        ]
    },
    {
        id: "console",
        label: "Web console settings",
        propertyNames: [
            "apicurio.download.href.ttl",
            "apicurio.ui.features.readOnly"
        ]
    },
];


export type SettingsPageProps = {
    // No props
}

/**
 * The settings page.
 */
export const SettingsPage: FunctionComponent<SettingsPageProps> = () => {
    const [pageError, setPageError] = useState<PageError>();
    const [loaders, setLoaders] = useState<Promise<any> | Promise<any>[] | undefined>();
    const [properties, setProperties] = useState<ConfigurationProperty[]>([]);
    const [searchedProperties, setSearchedProperties] = useState<ConfigurationProperty[]>([]);
    const [filterValue, setFilterValue] = useState("");
    const [searchCriteria, setSearchCriteria] = useState("");

    const admin: AdminService = useAdminService();
    const alerts: AlertsService = useAlertsService();

    const createLoaders = async (): Promise<any> => {
        return admin.listConfigurationProperties().then( properties => {
            setProperties(properties);
            filterProperties(properties);
        }).catch(error => {
            setPageError(toPageError(error, "Error loading properties."));
        });
    };

    const groupFor = (groups: PropertyGroup[], prop: ConfigurationProperty): PropertyGroup => {
        for (const group of groups) {
            if (group.propertyNames.indexOf(prop.name) >= 0) {
                return group;
            }
        }
        // Default to the last group (additional properties).
        return groups[groups.length - 1];
    };

    const propertyGroups = (): PropertyGroup[] => {
        const groups: PropertyGroup[] = [...PROPERTY_GROUPS];
        groups.forEach(group => group.properties = []);
        const additionalGroup: PropertyGroup = {
            id: "additional",
            label: "Additional properties",
            properties: [],
            propertyNames: []
        };
        groups.push(additionalGroup);
        searchedProperties?.forEach(prop => {
            groupFor(groups, prop).properties?.push(prop);
        });
        groups.forEach(group => {
            group.properties = group.properties?.sort(
                (prop1, prop2) => prop1.label.localeCompare(prop2.label));
        });
        return groups;
    };

    const acceptProperty = (property: ConfigurationProperty): boolean => {
        if (!searchCriteria || searchCriteria.trim().length === 0) {
            return true;
        }
        const sc: string = searchCriteria.toLocaleLowerCase();
        return property.label.toLocaleLowerCase().indexOf(sc) >= 0 ||
            property.description.toLocaleLowerCase().indexOf(sc) >= 0;
    };

    const filterProperties = (properties: ConfigurationProperty[]): void => {
        const filteredProperties: ConfigurationProperty[] | undefined = properties.filter(acceptProperty);
        setSearchedProperties(filteredProperties);
    };

    const onPropertyChange = (property: ConfigurationProperty, newValue: string): void => {
        property.value = newValue;
        admin.setConfigurationProperty(property.name, newValue).then(() => {
            // The property was updated successfully.  Update the UI to display all config
            // properties (the list may have changed by changing one of the values).
            createLoaders();
            alerts.settingChanged(property, newValue);
        }).catch(error => {
            // Failed to set the property... report the error somehow.
            setPageError(toPageError(error, "Error setting configuration property"));
        });
    };

    useEffect(() => {
        filterProperties(properties);
    }, [searchCriteria]);

    useEffect(() => {
        setLoaders(createLoaders());
    }, []);

    return (
        <PageErrorHandler error={pageError}>
            <PageDataLoader loaders={loaders}>
                <PageSection className="ps_settings-header" variant={PageSectionVariants.light} padding={{ default: "noPadding" }}>
                    <RootPageHeader tabKey={3} />
                </PageSection>
                <PageSection className="ps_settings-description" variant={PageSectionVariants.light}>
                    <TextContent>
                        Configure global settings for this Registry instance.
                    </TextContent>
                    <TextContent style={{ marginTop: "10px", marginBottom: "5px", maxWidth: "450px" }}>
                        <SearchInput placeholder={"Filter by keyword"}
                            aria-label="Filter by keyword"
                            value={filterValue}
                            data-testid="settings-search-widget"
                            onChange={(_evt, value) => setFilterValue(value)}
                            onSearch={() => setSearchCriteria(filterValue)}
                            onClear={() => {
                                setFilterValue("");
                                setSearchCriteria("");
                            }}
                        />
                    </TextContent>
                </PageSection>
                <PageSection variant={PageSectionVariants.default} isFilled={true} data-testid="config-groups">
                    <IfNotEmpty collection={searchedProperties} emptyStateMessage={"No settings found matching your search criteria."}>
                        {
                            propertyGroups().map(group =>
                                <If key={group.id} condition={group.properties !== undefined && group.properties.length > 0}>
                                    <Card key={group.id} className="config-property-group" role="group">
                                        <CardTitle className="title">{group.label}</CardTitle>
                                        <CardBody className="config-properties">
                                            {
                                                group.properties?.map(prop =>
                                                    <ConfigProperty key={prop.name}
                                                        property={prop}
                                                        onChange={onPropertyChange}
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
            </PageDataLoader>
        </PageErrorHandler>
    );

};
