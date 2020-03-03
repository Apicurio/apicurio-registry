// Convert 'any' to type once keycloak.js is converted to typescript.
export type ConfigType =  {
    mode: string,
    artifacts: {
        url: string,
        type: string
    },
    features: any,
    ui: any
};