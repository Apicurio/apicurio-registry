export interface VersionMetaData {

    groupId: string|null;
    artifactId: string;
    name: string|null;
    description: string|null;
    labels: { [key: string]: string };
    version: string;
    owner: string;
    createdOn: Date;
    contentId: number;
    globalId: number;
    state: string;
    type: string;

}
