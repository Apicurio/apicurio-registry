import { ArtifactReference } from "@sdk/lib/generated-client/models";

export interface DraftContent {
    content: string;
    contentType: string;
    references?: ArtifactReference[];
}
