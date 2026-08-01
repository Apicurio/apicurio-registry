export interface RenderPromptValidationError {
    variableName?: string;
    message: string;
    expectedType?: string;
    actualType?: string;
}

export interface RenderPromptResponse {
    rendered: string;
    groupId?: string;
    artifactId?: string;
    version?: string;
    validationErrors?: RenderPromptValidationError[];
}
