var ApicurioRegistryConfig = {
    mode: "prod",
    artifacts: {
        type: "rest",
        url: "http://localhost:8080/api"
    },
    ui: {
        contextPath: null,
        url: "http://localhost:8888/ui"
    },
    auth: {
        url: 'http://localhost:8090/auth',
        realm: 'registry',
        clientId:'registry-ui',
        onLoad: 'login-required'
    },
    features: {
        readOnly: false
    }
};
