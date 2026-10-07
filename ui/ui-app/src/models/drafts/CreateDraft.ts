import { ArtifactReference } from "@sdk/lib/generated-client/models";

export interface CreateDraft {

    groupId: string;
    draftId: string;
    version: string;
    type: string;
    name: string;
    description: string | undefined;
    labels: any;
    content: string | undefined;
    contentType: string;
    references?: ArtifactReference[];

}
