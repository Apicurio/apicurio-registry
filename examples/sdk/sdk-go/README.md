# Go SDK Example

1. Start Apicurio Registry 3 server, for example:

```
docker run --rm -it -p 8080:8080 quay.io/apicurio/apicurio-registry:latest
```

2. Build the example application (golang 1.23+ required):

```
make build
```

3. Run the example:

```
bin/example
```

Results in:

```
Server name: Apicurio Registry (In Memory)
Server version: 3.0.6
Created version 1 of artifact e98c1564-06a9-47fb-b551-ec4ec0cdf235
```
