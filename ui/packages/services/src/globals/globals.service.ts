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

import {Rule} from "@apicurio/registry-models";
import {LoggerService} from "../logger";


/**
 * The globals service.  Used to get global/settings information from the back-end.
 */
export class GlobalsService {

    private logger: LoggerService = null;

    private readonly rules: Rule[];

    constructor() {
        this.rules = [
            Rule.create("VALIDITY", "FULL"),
            Rule.create("COMPATIBILITY", "BACKWARD")
        ];
    }

    public getRules(): Promise<Rule[]> {
        this.logger.info("[GlobalsService] Getting the global list of rules.");
        return new Promise<Rule[]>(resolve => {
            setTimeout(() => {
                resolve(this.rules);
            }, 200);
        });
    }

    public updateRule(type: string, config: string|null): Promise<Rule|null> {
        this.logger.info("[GlobalsService] Updating rule:", type);
        return new Promise<Rule>(resolve => {
            setTimeout(() => {
                const frules: Rule[] = this.rules.filter(rule => rule.type === type);
                // If config is null, we're disabling/removing the rule.
                if (!config) {
                    if (frules.length === 1) {
                        // Disable by removing the rule from the list.
                        this.rules.splice(this.rules.indexOf(frules[0]), 1);
                        resolve(null);
                    } else {
                        // It's already disabled
                        resolve(null);
                    }
                } else {
                    if (frules.length === 1) {
                        // Modify the rule
                        const rval: Rule = this.rules.filter(rule => rule.type === type)[0];
                        rval.config = config;
                        resolve(rval);
                    } else {
                        // Add the rule
                        const rule: Rule = Rule.create(type, config);
                        this.rules.push(rule);
                        resolve(rule);
                    }
                }
            }, 200);
        });
    }

}
