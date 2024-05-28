# Camel Quarkus Kafka Example involving the Service Registry Managed Service

1. Create Kafka Managed Service instance on cloud.redhat.com

2. Create associated Service Account, save client Id and Client Secret

3. Create Service Registry Managed instance on cloud.redhat.com

4. Populate correctly the producer application.properties file with the missing parameters

5. Populate correctly the consumer application.properties file with the missing parameters

6. From the Service Registry Managed Instance UI load the user.avsc as schema named 'test-value' with no group

7. From the producer folder run

   mvn clean compile package
   java -jar target/quarkus-app/quarkus-run.jar

8. From the consumer folder run

   mvn clean compile package
   java -Dquarkus.http.port=8081 -jar target/quarkus-app/quarkus-run.jar

Notes:
- The class User has been generated starting from the avsc user schema, through the avro tools
