var ApicurioRegistryConfig = {
    artifacts: {
        url: "http://localhost:8080/apis/registry"
    },
    ui: {
        contextPath: "/",
        navPrefixPath: "/"
    },
    auth: {
        type: "keycloakjs",
        rbacEnabled: true,
        obacEnabled: false,
        options: {
            url: "https://studio-auth.apicur.io/auth",
            realm: "apicurio-local",
            clientId:"apicurio-registry",
            onLoad: "login-required",
            checkLoginIframe: false
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
