namespace java com.example.thrift.refs

include "shared/common.thrift"
include "shared/other.thrift"
cpp_include "my_header.h"

struct Message {
  1: required string id,
  2: optional string body
}
