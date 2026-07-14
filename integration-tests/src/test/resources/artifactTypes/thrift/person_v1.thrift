namespace java com.example

struct Person {
  1: required string name,
  2: optional i32 age,
  3: optional string email
}

service PersonService {
  Person getPerson(1: string id)
}
