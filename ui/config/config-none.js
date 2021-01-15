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
        type: "none"
    },
    features: {
        readOnly: false
    }
};
