export interface PromptVariable {
    name?: string;
    type?: string;
    description?: string;
    required?: boolean;
    default?: any;
    enum?: string[];
    constraints?: any;
}

export interface PromptTemplateMetadata {
    author?: string;
    createdAt?: string;
    tags?: string[];
    recommendedModels?: string[];
    useCases?: string[];
    estimatedTokens?: {
        input?: number;
        variableOverhead?: number;
    };
}

export interface PromptTemplate {
    templateId?: string;
    name?: string;
    description?: string;
    version?: string;
    template?: string;
    variables?: Record<string, PromptVariable> | PromptVariable[];
    outputSchema?: any;
    metadata?: PromptTemplateMetadata;
    mcp?: {
        enabled?: boolean;
        name?: string;
        description?: string;
        arguments?: any[];
    };
}
