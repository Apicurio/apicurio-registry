# Compatibility Notes

1. The API should follow [Confluent spec](https://docs.confluent.io/5.5.0/schema-registry/develop/api.html)

1. We expect only Avro schemas are supported

1. Schema deleted using `SubjectVersionsResource#deleteSchemaVersion(java.lang.String, java.lang.String)`
   should still be accessible using global ID. This guarantee can not be kept in our implementation, 
   since any schema must be part of an artifact.
   
1. Some endpoints expect raising error `422` if the schema is invalid, and error `409` if the schema is incompatible.
   Schema format validation is done only if such validation rule is configured. 
   Therefore when creating a new subject, validation rule for it is also configured 
   to possibly return `422` when a new version is added. If the rule is removed by another means
   (registry API), this check will not work.

**TODO:**

1. Registry provides a state API. If the state of the artifact (or version) is `DISABLED` or `DELETED`,
   it takes precedence and is not returned.
