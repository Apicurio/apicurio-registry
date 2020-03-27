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
import {
    Button,
    Flex,
    FlexItem,
    Modal,
    PageSection,
    PageSectionVariants,
    Pagination,
    Spinner
} from '@patternfly/react-core';
import {ArtifactsPageHeader} from "./components/pageheader";
import "./artifacts.css";
import {ArtifactsSearchResults, GetArtifactsCriteria, Services} from "@apicurio/registry-services";
import {ArtifactList} from "./components/artifactList";
import {Artifact} from "@apicurio/registry-models";
import {Paging} from "@apicurio/registry-services/src";
import {PageComponent, PageProps, PageState} from "../basePage";
import {ArtifactsPageToolbar} from "./components/toolbar";
import {ArtifactsPageEmptyState} from "./components/empty";


/**
 * Properties
 */
// tslint:disable-next-line:no-empty-interface
export interface ArtifactsPageProps extends PageProps {

}

/**
 * State
 */
export interface ArtifactsPageState extends PageState {
    criteria: GetArtifactsCriteria;
    isUploadModalOpen: boolean;
    paging: Paging;
    results: ArtifactsSearchResults | null;
}

/**
 * The artifacts page.
 */
export class ArtifactsPage extends PageComponent<ArtifactsPageProps, ArtifactsPageState> {

    constructor(props: Readonly<ArtifactsPageProps>) {
        super(props);
    }

    public render(): React.ReactElement {
        return (
            <React.Fragment>
                <PageSection className="ps_artifacts-header" variant={PageSectionVariants.light}>
                    <ArtifactsPageHeader onUploadArtifact={this.onUploadArtifact}/>
                </PageSection>
                <PageSection variant={PageSectionVariants.light} noPadding={true}>
                    <ArtifactsPageToolbar artifactsCount={this.totalArtifactsCount()} onChange={this.onFilterChange}/>
                </PageSection>
                <PageSection variant={PageSectionVariants.default} isFilled={true}>
                    {
                        this.state.isLoading ?
                            <Flex>
                                <FlexItem><Spinner size="lg"/></FlexItem>
                                <FlexItem><span>Loading, please wait...</span></FlexItem>
                            </Flex>
                        : this.artifactsCount() === 0 ?
                            <ArtifactsPageEmptyState onUploadArtifact={this.onUploadArtifact} isFiltered={false}/>
                        :
                            <React.Fragment>
                                <ArtifactList artifacts={this.artifacts()}/>
                                <Pagination
                                    variant="bottom"
                                    dropDirection="up"
                                    itemCount={this.totalArtifactsCount()}
                                    perPage={this.state.paging.pageSize}
                                    page={this.state.paging.page}
                                    onSetPage={this.onSetPage}
                                    onPerPageSelect={this.onPerPageSelect}
                                    widgetId="artifact-list-pagination"
                                    className="artifact-list-pagination"
                                />
                            </React.Fragment>
                    }
                </PageSection>
                <Modal
                    title="Modal Header"
                    isOpen={this.state.isUploadModalOpen}
                    onClose={this.onUploadModalClose}
                    actions={[
                        <Button key="confirm" variant="primary" onClick={this.onUploadModalClose}>
                            Confirm
                        </Button>,
                        <Button key="cancel" variant="link" onClick={this.onUploadModalClose}>
                            Cancel
                        </Button>
                    ]}
                >
                    Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore
                    magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo
                    consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla
                    pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id
                    est laborum.
                </Modal>
            </React.Fragment>
        );
    }

    protected initializeState(): ArtifactsPageState {
        return {
            criteria: {
                sortAscending: true,
                type: "Everything",
                value: "",
            },
            isLoading: true,
            isUploadModalOpen: false,
            paging: {
                page: 1,
                pageSize: 10
            },
            results: null
        };
    }

    protected loadPageData(): void {
        this.search();
    }

    private onUploadArtifact = (): void => {
        this.setSingleState("isUploadModalOpen", true);
    };

    private onUploadModalClose = (): void => {
        this.setSingleState("isUploadModalOpen", false);
    };

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
        this.search(criteria);
    };

    private search(criteria: GetArtifactsCriteria = this.state.criteria, paging: Paging = this.state.paging): void {
        Services.getArtifactsService().getArtifacts(criteria, paging).then(results => {
            this.onArtifactsLoaded(results);
        }).then(error => {
            // TODO handle errors!
        });
    }

    private onSetPage = (event: any, newPage: number, perPage?: number): void => {
        const paging: Paging = {
            page: newPage,
            pageSize: perPage ? perPage : this.state.paging.pageSize
        };
        this.setMultiState({
            isLoading: true,
            paging
        });
        this.search(undefined, paging);
    };

    private onPerPageSelect = (event: any, newPerPage: number): void => {
        const paging: Paging = {
            page: this.state.paging.page,
            pageSize: newPerPage
        };
        this.setMultiState({
            isLoading: true,
            paging
        });
        this.search(undefined, paging);
    }

}
