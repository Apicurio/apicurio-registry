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


export class Artifact {

    public static create(id: string, type: string, name: string, description: string): Artifact {
        const artifact: Artifact = new Artifact();
        artifact.id = id;
        artifact.type = type;
        artifact.name = name;
        artifact.description = description;
        return artifact;
    }

    public id: string;
    public name: string;
    public description: string;
    public createdOn: Date;
    public createdBy: string;
    public type: string;

    constructor() {
        this.id = "";
        this.name = "";
        this.description = "";
        this.createdOn = new Date();
        this.createdBy = "";
        this.type = "";
    }

}
