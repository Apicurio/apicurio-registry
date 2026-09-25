# Health & Metrics

## Health

### Definitions

 - **Readiness** - Service is prepared to serve requests. 
   (On failure: Stop sending requests).
 - **Liveness** - Service is ready and able to make progress
   (On failure: Restart the service instance).

### Transitions

Readiness status must eventually transition from **NOT Ready** to **Ready**, if the service is **live**.

### Components that provide health info

#### Storage

 - **Ready if** - A connection to the storage has been made, and a test query succeeds.
 - **NOT Ready if** - A test storage query times-out, but does not fail.
 - **Live if** - There are no *unexpected* errors while processing queries.
 - **NOT Live if** - There are *unexpected* errors while processing queries. 
   *TODO Over a short period of time?*
   
#### REST

 - **Ready if** - Implementing bean is constructed.
 - **NOT Ready if** - The requests take too much time.
 - **Live if** - There are no HTTP 5xx errors.
 - **NOT Live if** - There has been an HTTP 5xx error.

## Metrics

The *application* metrics are collected in the following areas:

 - REST
    - Request-Response time
    - No. of requests total
    - No. of concurrent requests
 - Persistence 
    - Storage operation time
    - No. of storage operations total
    - No. of concurrent operations
    - (TODO) Operation type histagram
