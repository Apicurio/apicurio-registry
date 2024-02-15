import { RoleMapping } from "@models/roleMapping.model.ts";

export interface RoleMappingSearchResults {

    roleMappings: RoleMapping[];
    count: number;

}
