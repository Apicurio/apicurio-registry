var ApicurioStudioConfig = {
    mode: "dev",
    auth: {
        type: "keycloakjs"
    },
    apis: {
        type: "hub",
        hubUrl: "https://studio-api.apicur.io/",
        editingUrl: "wss://studio-ws.apicur.io/"
    },
    ui: {
        url: "http://localhost:8888/"
    },
    features: {
        "shareWithEveryone": false,
        "microcks": false,
        "asyncapi": true,
        "graphql": true
    }
};
