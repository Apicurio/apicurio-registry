/**
 * @license
 * Copyright 2020 JBoss Inc
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
import {Flex, FlexItem, PageSection, PageSectionVariants, Pagination, Spinner} from '@patternfly/react-core';
import {ArtifactsPageHeader} from "./components/pageheader";
import {ArtifactsToolbar} from "./components/toolbar";
import "./artifacts.css";
import {ArtifactsEmptyState} from "./components/empty";
import {ArtifactsSearchResults, GetArtifactsCriteria, Services} from "@apicurio/registry-services";
import {ArtifactList} from "./components/artifactList";
import {PureComponent} from "../../components";
import {Artifact} from "@apicurio/registry-models";
import {Paging} from "@apicurio/registry-services/src";


/**
 * Properties
 */
export interface ArtifactsProps {

}

/**
 * State
 */
export interface ArtifactsState {
    criteria: GetArtifactsCriteria;
    isLoading: boolean;
    paging: Paging;
    results: ArtifactsSearchResults | null;
}

/**
 * The artifacts page.
 */
export class Artifacts extends PureComponent<ArtifactsProps, ArtifactsState> {

    constructor(props: Readonly<ArtifactsProps>) {
        super(props);
        this.state = {
            criteria: {
                sortAscending: true,
                type: "Everything",
                value: "",
            },
            isLoading: true,
            paging: {
                page: 1,
                pageSize: 10
            },
            results: null
        };

        this.search();
    }

    public render(): React.ReactElement {
        return (
            <React.Fragment>
                <PageSection className="ps_artifacts-header" variant={PageSectionVariants.light}>
                    <ArtifactsPageHeader/>
                </PageSection>
                <PageSection variant={PageSectionVariants.light} noPadding={true}>
                    <ArtifactsToolbar artifactsCount={this.totalArtifactsCount()} onChange={this.onFilterChange}/>
                </PageSection>
                <PageSection variant={PageSectionVariants.default} isFilled={true}>
                    {
                        this.state.isLoading ?
                            <Flex>
                                <FlexItem><Spinner size="lg"/></FlexItem>
                                <FlexItem><span>Loading, please wait...</span></FlexItem>
                            </Flex>
                        : this.artifactsCount() === 0 ?
                            <ArtifactsEmptyState isFiltered={false}/>
                        :
                            <React.Fragment>
                                <ArtifactList artifacts={this.artifacts()}/>
                                <Pagination
                                    variant="bottom"
                                    dropDirection="up"
                                    itemCount={this.totalArtifactsCount()}
                                    perPage={3}
                                    page={1}
                                    onSetPage={this.onSetPage}
                                    widgetId="artifact-list-pagination"
                                    className="artifact-list-pagination"
                                />
                            </React.Fragment>
                    }
                </PageSection>
            </React.Fragment>
        );
    }

    private onArtifactsLoaded(results: ArtifactsSearchResults): void {
        this.setMultiState({
            isLoading: false,
            results
        });
    }

    private artifacts(): Artifact[] {
        if (this.state.results) {
            return this.state.results.artifacts;
        }
        return [];
    }

    private artifactsCount(): number {
        if (this.state.results) {
            return this.state.results.artifacts.length;
        }
        return 0;
    }

    private totalArtifactsCount(): number {
        if (this.state.results) {
            return this.state.results.count;
        }
        return 0;
    }

    private onFilterChange = (criteria: GetArtifactsCriteria): void => {
        this.setMultiState({
            criteria,
            isLoading: true
        });
        this.search();
    };

    private search(): void {
        const criteria: GetArtifactsCriteria = this.state.criteria;
        const paging: Paging = this.state.paging;

        Services.getArtifactsService().getArtifacts(criteria, paging).then(results => {
            this.onArtifactsLoaded(results);
        }).then(error => {
            // TODO handle errors!
        });
    }

    private onSetPage = (event: any, newPage: number, perPage?: number): void => {
        this.setMultiState({
            isLoading: true,
            paging: {
                page: newPage,
                pageSize: perPage ? perPage : this.state.paging.pageSize
            }
        });
        this.search();
    };

}
