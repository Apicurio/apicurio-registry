var ApicurioRegistryConfig = {
    artifacts: {
        url: "http://localhost:8080/apis/registry/v3"
    },
    ui: {
        contextPath: "/",
        navPrefixPath: "/",
        oaiDocsUrl: "http://localhost:8889"
    },
    auth: {
        type: "oidc",
        rbacEnabled: true,
        obacEnabled: false,
        options: {
            url: "https://auth.apicur.io/auth/realms/apicurio-local",
            redirectUri: "http://localhost:8888",
            clientId: "apicurio-registry-ui"
        }
    },
    features: {
        readOnly: false,
        breadcrumbs: true,
        roleManagement: true,
        settings: true
    },
    principals: [
        {
            "principalType": "USER_ACCOUNT",
            "id": "ewittman",
            "displayName": "Eric Wittmann",
            "emailAddress": "ewittman@examplecom"
        },
        {
            "principalType": "USER_ACCOUNT",
            "id": "carnal",
            "displayName": "Carles Arnal",
            "emailAddress": "carnal@example.com"
        },
        {
            "principalType": "USER_ACCOUNT",
            "id": "jsenko",
            "displayName": "Jakub Senko",
            "emailAddress": "jsenko@examplecom"
        },
        {
            "principalType": "USER_ACCOUNT",
            "id": "famartin",
            "displayName": "Fabian Martinez Gonzalez",
            "emailAddress": "famartin@examplecom"
        },
        {
            "principalType": "SERVICE_ACCOUNT",
            "id": "svc_account_user-643243-89435625",
            "displayName": "Service Account User 1"
        },
        {
            "principalType": "SERVICE_ACCOUNT",
            "id": "svc_account_kafka-12345",
            "displayName": "Kafka Service Account"
        },
    ]
};
