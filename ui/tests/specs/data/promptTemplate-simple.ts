export const PROMPT_TEMPLATE_DATA = {
    templateId: "test-support-prompt",
    name: "Test Support Prompt",
    description: "A minimal prompt template used to exercise the viewer and test panel",
    version: "1.0",
    template: "Answer the following question: {{question}}",
    variables: {
        question: {
            type: "string",
            required: true,
            description: "The user's question"
        },
        include_examples: {
            type: "boolean",
            default: true,
            description: "Whether to include code examples in the answer"
        }
    }
};
