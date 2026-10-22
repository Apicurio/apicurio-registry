export function buildNonDraftVersionLink(groupId: string, artifactId: string, version: string): string {
    return `/explore/${groupId}/${artifactId}/versions/${version}`;
}
