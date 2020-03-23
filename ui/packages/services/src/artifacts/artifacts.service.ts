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

import {Artifact} from "@apicurio/registry-models";

export interface GetArtifactsCriteria {
    type: string;
    value: string;
    sortAscending: boolean;
}

export interface Paging {
    page: number;
    pageSize: number;
}

export interface ArtifactsSearchResults {
    artifacts: Artifact[];
    count: number;
    firstPage: boolean;
    lastPage: boolean;
    page: number;
    pageSize: number;
}

/**
 * The artifacts service.  Used to query the backend search API to fetch lists of
 * artifacts and also details about individual artifacts.
 */
export class ArtifactsService {

    public getArtifacts(criteria: GetArtifactsCriteria, paging: Paging): Promise<ArtifactsSearchResults> {
        return new Promise<ArtifactsSearchResults>(resolve => {
            setTimeout(() => {
                const results: ArtifactsSearchResults = {
                    artifacts: [
                        Artifact.create("1", "OPENAPI", "My First API", "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.", [ "horse", "donkey" ]),
                        Artifact.create("2", "ASYNCAPI", "Eventing API #1", "Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat.", [ "light", "heat", "water", "fuel"] ),
                        Artifact.create("3", "AVRO", "Invoice Type", "Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur.")
                    ],
                    count: 3,
                    firstPage: true,
                    lastPage: false,
                    page: paging.page,
                    pageSize: paging.pageSize
                };
                resolve(results);
            }, 500);
        });
    }

}
