/**
 * @license
 * Copyright 2022 Red Hat
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

import {ChangeDetectionStrategy, ChangeDetectorRef, Component, Input, ViewEncapsulation} from "@angular/core";
import {CommandFactory, ICommand, Oas20SchemaDefinition, Oas30SchemaDefinition} from "@apicurio/data-models";
import {CommandService} from "../../../_services/command.service";
import {AbstractBaseComponent} from "../../common/base-component";
import {DocumentService} from "../../../_services/document.service";
import {SelectionService} from "../../../_services/selection.service";
import {ExampleTextValue, ModelUtils} from "../../../_util/model.util";


@Component({
    selector: "definition-example-section",
    templateUrl: "example-section.component.html",
    encapsulation: ViewEncapsulation.None,
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class DefinitionExampleSectionComponent extends AbstractBaseComponent {

    @Input() definition: Oas20SchemaDefinition | Oas30SchemaDefinition;

    /**
     * C'tor.
     * @param changeDetectorRef
     * @param documentService
     * @param commandService
     * @param selectionService
     */
    constructor(private changeDetectorRef: ChangeDetectorRef, private documentService: DocumentService,
                private commandService: CommandService, private selectionService: SelectionService) {
        super(changeDetectorRef, documentService, selectionService);
    }

    /**
     * Returns the example formatted for the UI editor:
     * plain strings are preserved as-is, while non-string values are JSON-stringified.
     */
    public example(): ExampleTextValue {
        return ModelUtils.stringifyExampleValue(this.definition.example);
    }

    /**
     * Called when the user changes the example.
     */
    public onExampleChange(newExample: string): void {
        console.info("[DefinitionExampleSectionComponent] User changed the data type example.");
        const newValue = ModelUtils.parseExampleValue(this.definition.type, newExample);
        const command = CommandFactory.createChangePropertyCommand(this.definition, "example", newValue);
        this.commandService.emit(command);
    }

    public exampleNodePath(): string {
        return ModelUtils.nodeToPath(this.definition) + "/example";
    }

}
