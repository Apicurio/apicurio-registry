// Convert 'any' to type once keycloak.js is converted to typescript.
export type ConfigType =  {
    artifacts: {
      url: string,
      type: string
    },
    auth: any,
    features: any,
    mode: string,
    ui: any,
    user: any
  };