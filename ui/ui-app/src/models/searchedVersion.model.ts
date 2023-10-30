export interface SearchedVersion {

    globalId: number;
    contentId: number|null;
    version: number;
    type: string;
    state: string;
    name: string;
    description: string;
    labels: string[];
    createdOn: string;
    createdBy: string;

}
