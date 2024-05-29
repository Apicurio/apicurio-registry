import { SearchedGroup } from "@models/searchedGroup.model.ts";

export interface GroupSearchResults {

    groups: SearchedGroup[];
    count: number;

}
