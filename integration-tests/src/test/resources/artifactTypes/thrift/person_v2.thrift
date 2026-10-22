namespace java com.example

struct Person {
  1: required string name,
  2: optional i32 age,
  3: optional string email,
  4: optional string phone
}

struct Address {
  1: required string street,
  2: required string city,
  3: optional string zip
}

service PersonService {
  Person getPerson(1: string id),
  list<Person> listPersons()
}
