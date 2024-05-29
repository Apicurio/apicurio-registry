export interface CreateGroup {
    groupId: string;
    description?: string;
    labels?: { [key: string]: string };
}
