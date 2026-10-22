This collection of OpenShift templates provides examples of how to configure Red Hat Integration - Service Registry to use HTTPS. Each template contains comments with instructions and details.

We suggest you read the examples in the following order of increasing complexity:

Edge termination:

- registry-default-edge
- registry-certmanager-edge
- registry-certmanager-letsencrypt-edge
- registry-certmanager-letsencrypt-custom-domain-edge

Passthrough termination:

- registry-certmanager-passthrough

In addition, there is a collection of examples on how to configure Red Hat Single Sign-On (Keycloak) to use HTTPS, and integrate it with Service Registry:

- registry-keycloak-default-edge
- registry-keycloak-certmanager-letsencrypt-edge

You can merge multiple examples together to configure HTTPS for both Service Registry and Red Hat Single Sign-On.
