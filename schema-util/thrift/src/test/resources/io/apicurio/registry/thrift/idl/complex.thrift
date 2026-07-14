namespace java com.example.complex
namespace py complex

include "shared.thrift"

const i32 MAX_RETRIES = 3
const string DEFAULT_HOST = "localhost"

typedef i64 Timestamp
typedef map<string, string> Headers

enum Priority {
  LOW = 1,
  MEDIUM = 2,
  HIGH = 3,
  CRITICAL = 4
}

struct Request {
  1: required string id,
  2: required string method,
  3: optional Headers headers,
  4: optional binary body,
  5: required Timestamp created_at,
  6: optional Priority priority = Priority.MEDIUM
}

struct Response {
  1: required i32 status_code,
  2: optional Headers headers,
  3: optional binary body,
  4: required Timestamp responded_at
}

union Result {
  1: Response success,
  2: string error_message,
  3: i32 error_code
}

exception ServiceException {
  1: required i32 code,
  2: required string message,
  3: optional string details
}

exception TimeoutException {
  1: required i64 timeout_ms,
  2: optional string operation
}

service HttpService {
  Response send(1: Request request) throws (1: ServiceException se, 2: TimeoutException te),
  oneway void notify(1: Request request),
  bool ping()
}

service ExtendedHttpService extends HttpService {
  list<Response> sendBatch(1: list<Request> requests) throws (1: ServiceException se)
}
