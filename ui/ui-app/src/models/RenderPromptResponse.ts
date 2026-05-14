export interface RenderPromptValidationError {
    path?: string;
    message: string;
}

export interface RenderPromptResponse {
    rendered: string;
    groupId?: string;
    artifactId?: string;
    version?: string;
    validationErrors?: RenderPromptValidationError[];
}
