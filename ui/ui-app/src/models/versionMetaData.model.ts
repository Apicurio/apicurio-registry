export interface VersionMetaData {

    groupId: string|null;
    id: string;
    name: string|null;
    description: string|null;
    labels: string[]|null;
    type: string;
    version: number;
    owner: string;
    createdOn: Date;
    contentId: number|null;
    globalId: number;
    state: string;

}
