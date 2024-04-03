# Apicurio Rest Client example application using your RHOSR instance.

1. Create RHOSR Managed Service instance on cloud.redhat.com and store your instance api url.

2. Create associated Service Account, save client Id and Client Secret.

3. Ensure your service account has at least, manager permissions on your RHOSR instance.

4. Set the environment variables AUTH_CLIENT_ID, AUTH_CLIENT_SECRET, AUTH_TOKEN_URL and REGISTRY_URL.

5. Execute the java main SimpleRegistryDemo on this module, it will create, get and delete a schema in your instance, proving the functioning of the service.
