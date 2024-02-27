# Apicurio Rest Client example application using your RHOSR instance.

1. Create RHOSR Managed Service instance on cloud.redhat.com and store your instance api url.

2. Create associated Service Account, save client Id and Client Secret.

3. Ensure your service account has at least, manager permissions on your RHOSR instance.

4. Create or use an existing instance of Openshift Streams for Apache Kafka. Get the bootstraps servers for that instance.

5. Create a topic with the name SimpleAvroExample on that Openshift Streams for Apache Kafka instance.

6. Ensure that the previously created service account has permissions on that Kafka instance topic for producing and consuming from that topic.

7. Set the environment variables SERVERS, AUTH_CLIENT_ID, AUTH_CLIENT_SECRET, AUTH_TOKEN_URL and REGISTRY_URL.

8. Execute the java main SimpleAvroExample on this module, it will produce and consume 5 messages, creating and enforcing a schema during the way, proving the functioning of the service with a realistic application.
