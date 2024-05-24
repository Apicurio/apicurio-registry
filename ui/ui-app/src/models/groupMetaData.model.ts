
export interface GroupMetaData {

    groupId: string;
    description?: string;
    labels?: { [key: string]: string | undefined };
    owner: string;
    createdOn: string;
    modifiedBy: string;
    modifiedOn: string;

}
