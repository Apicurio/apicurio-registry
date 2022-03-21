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
import {PageSection, PageSectionVariants, TextContent} from '@patternfly/react-core';
import {PageComponent, PageProps, PageState} from "../basePage";
import {Services} from "../../../services";
import {RootPageHeader} from "../../components";
import {ConfigurationProperty} from "../../../models/configurationProperty.model";
import {ConfigProperty} from "./components";


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
    properties: ConfigurationProperty[] | null;
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
                </PageSection>
                <PageSection variant={PageSectionVariants.default} isFilled={true}>
                    <div className="config-properties">
                        {
                            this.state.properties?.map(prop =>
                                <ConfigProperty key={prop.name}
                                                property={prop}
                                                onChange={this.onPropertyChange}
                                                onReset={this.onPropertyReset}
                                />
                            )
                        }
                    </div>
                </PageSection>
            </React.Fragment>
        );
    }

    protected initializePageState(): SettingsPageState {
        return {
            properties: null
        };
    }

    // @ts-ignore
    protected createLoaders(): Promise {
        return Services.getAdminService().listConfigurationProperties().then( properties => {
                this.setMultiState({
                    isLoading: false,
                    properties
                });
            });
    }

    private onPropertyChange = (property: ConfigurationProperty, newValue: string): void => {
        property.value = newValue;
        Services.getAdminService().setConfigurationProperty(property.name, newValue).then(() => {
            // The property was updated successfully.  Update the UI to display all config
            // properties (the list may have changed by changing one of the values).
            this.createLoaders();
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
