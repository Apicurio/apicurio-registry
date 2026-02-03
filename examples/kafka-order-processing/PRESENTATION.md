---
marp: true
theme: default
paginate: true
header: 'Schema Registry with Apache Kafka'
footer: 'Apicurio Registry | Apache Kafka Architecture'
---

# Schema Registry with Apache Kafka
## Architectural Patterns for Async Message Systems

Using Apicurio Registry for Schema Management

---

# The Challenge: Distributed Async Systems

**Scenario:** Multiple services producing and consuming messages

```
┌──────────────┐         ┌──────────────┐         ┌──────────────┐
│   Service A  │────────>│  Kafka Topic │────────>│   Service B  │
│  (Producer)  │         │              │         │  (Consumer)  │
└──────────────┘         └──────────────┘         └──────────────┘
```

**Key Problems:**
- How do consumers know the structure of messages?
- What happens when message formats change?
- How do we prevent breaking changes?
- How do we enforce data contracts between teams?

---

# The Traditional Approach: Embedded Schemas

**Option 1: Hard-coded schemas in each service**
```java
// Duplicated in every service
public class Order {
    private String orderId;
    private String customerId;
    // ... more fields
}
```

**Problems:**
- ❌ Schema duplication across services
- ❌ No version control
- ❌ Difficult to coordinate updates
- ❌ No validation at runtime
- ❌ Breaking changes are easy to introduce

---

# The Solution: Centralized Schema Registry

```
┌─────────────┐                    ┌─────────────┐
│  Producer   │◄───── Schema ─────►│  Consumer   │
└──────┬──────┘      Registration  └──────▲──────┘
       │                                  │
       │             ┌──────────────┐     │
       └────────────►│   Schema     │◄────┘
         Messages    │   Registry   │  Schema
         + Schema ID │              │  Lookup
                     └──────┬───────┘
                            │
                     ┌──────▼───────┐
                     │  Kafka Topic │
                     └──────────────┘
```

---

# The Solution: Centralized Schema Registry

**Benefits:**
- ✅ Single source of truth for schemas
- ✅ Version management and evolution
- ✅ Compatibility validation
- ✅ Runtime schema enforcement

---

# Schema Registry: Key Capabilities

## 1. **Schema Registration & Storage**
   - Centralized repository for all message schemas
   - Versioned schema history
   - Metadata and documentation

---

# Schema Registry: Key Capabilities

## 2. **Schema Validation**
   - Validate messages against schemas before sending
   - Ensure compatibility between versions
   - Prevent breaking changes

---

# Schema Registry: Key Capabilities

## 3. **Schema Evolution**
   - Forward compatibility: Old consumers can read new messages
   - Backward compatibility: New consumers can read old messages
   - Full compatibility: Both directions work

---

# Apicurio Registry Architecture

```
┌───────────────────────────────────────────────────────────┐
│                    Apicurio Registry                      │
├───────────────────────────────────────────────────────────┤
│  REST API  │  Web UI  │  Maven Plugin  │  SDKs            │
├───────────────────────────────────────────────────────────┤
│              Schema Storage & Versioning                  │
│              Compatibility Checking                       │
│              Content Rules & Validation                   │
├───────────────────────────────────────────────────────────┤
│  Storage: KafkaSQL | SQL | GitOps                         │
└───────────────────────────────────────────────────────────┘
```

---

# Apicurio Registry Architecture

**Key Features:**
- Multiple storage backends (KafkaSQL, PostgreSQL, SQL Server)
- REST API for all operations
- Web UI for schema browsing
- Support for Avro, Protobuf, JSON Schema, OpenAPI, AsyncAPI

---

# Message Flow with Schema Registry

## Producer Side:
1. **Schema Registration**: Producer registers or retrieves schema
2. **Schema ID**: Registry returns global schema ID
3. **Serialization**: Message serialized with schema ID embedded
4. **Send**: Message + Schema ID sent to Kafka

---

# Message Flow with Schema Registry

## Consumer Side:
1. **Receive**: Message received from Kafka
2. **Extract ID**: Schema ID extracted from message
3. **Schema Lookup**: Schema retrieved from registry (cached)
4. **Deserialize**: Message deserialized using retrieved schema

**Result:** Decoupled schema management with guaranteed compatibility

---

# Demo Architecture: Order Processing System

```
┌──────────────────┐    orders     ┌──────────────────┐    orders     ┌──────────────────┐
│ Order Producer   │   (Avro +     │  Apache Kafka    │   (Avro +     │ Order Consumer   │
│  (Quarkus App)   │   Schema ID)  │  Topic: orders   │   Schema ID)  │  (Quarkus App)   │
│                  │──────────────>│                  │──────────────>│                  │
│ - Generates      │               └──────────────────┘               │ - Consumes       │
│   orders         │                                                  │   orders         │
│ - Auto-registers │                                                  │ - Fetches schema │
│   schema         │                                                  │ - Processes data │
└────────┬─────────┘                                                  └────────┬─────────┘
         │                                                                     │
         │ Register/Update                                       Lookup Schema │
         │ Schema (Avro)                                          by Schema ID │
         │                        ┌──────────────────┐                         │
         └───────────────────────>│  Apicurio        │<────────────────────────┘
                                  │  Registry        │
                                  │  (Schema Store)  │
                                  └──────────────────┘
```

---

# Avro Schema: Order

```json
{
  "type": "record",
  "name": "Order",
  "namespace": "io.apicurio.registry.examples.kafka.order",
  "fields": [
    { "name": "orderId", "type": "string" },
    { "name": "customerId", "type": "string" },
    { "name": "customerName", "type": "string" },
    { "name": "orderTimestamp", "type": "long" },
    {
      "name": "items",
      "type": { "type": "array", "items": "OrderItem" }
    },
    { "name": "totalAmount", "type": "double" },
    { "name": "status", "type": "OrderStatus", "default": "PENDING" }
  ]
}
```

**Schema features:**
- Strongly typed fields
- Nested types (OrderItem)
- Enums (OrderStatus)
- Default values

---

# Producer Configuration

```java
Properties props = new Properties();

// Kafka configuration
props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
          AvroKafkaSerializer.class.getName());

// Apicurio Registry configuration
props.put(SerdeConfig.REGISTRY_URL,
          "http://localhost:8080/apis/registry/v3");
props.put(SerdeConfig.AUTO_REGISTER_ARTIFACT, Boolean.TRUE);
props.put(SerdeConfig.AUTO_REGISTER_ARTIFACT_IF_EXISTS,
          "FIND_OR_CREATE_VERSION");

// Use reflection for Avro serialization
props.put(AvroSerdeConfig.AVRO_DATUM_PROVIDER,
          ReflectAvroDatumProvider.class.getName());

Producer<String, Order> producer = new KafkaProducer<>(props);
```

---

# Consumer Configuration

```java
Properties props = new Properties();

// Kafka configuration
props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
          AvroKafkaDeserializer.class.getName());

// Apicurio Registry configuration
props.put(SerdeConfig.REGISTRY_URL,
          "http://localhost:8080/apis/registry/v3");

// Use reflection for Avro deserialization
props.put(AvroSerdeConfig.AVRO_DATUM_PROVIDER,
          ReflectAvroDatumProvider.class.getName());

KafkaConsumer<String, Order> consumer = new KafkaConsumer<>(props);
```

**Note:** No schema configuration needed on consumer side!
Schema ID is embedded in the message payload.

---

# Schema Evolution in Action

## Scenario: Add a new optional field

**Original Schema (v1):**
```json
{
  "name": "Order",
  "fields": [
    { "name": "orderId", "type": "string" },
    { "name": "totalAmount", "type": "double" }
  ]
}
```

**Updated Schema (v2):**
```json
{
  "name": "Order",
  "fields": [
    { "name": "orderId", "type": "string" },
    { "name": "totalAmount", "type": "double" },
    { "name": "discount", "type": "double", "default": 0.0 }  // NEW!
  ]
}
```

---

# Compatibility Checking

```
┌──────────────────┐
│  New Producer    │
│  (Schema v2)     │
└────────┬─────────┘
         │
         │ Register v2?
         ▼
┌────────────────────────┐
│  Apicurio Registry     │
│  Compatibility Check:  │
│  ✓ Backward compatible │  ← Old consumers can still read
│  ✓ Forward compatible  │  ← New consumers can read old msgs
└────────┬───────────────┘
         │
         │ ✅ Approved!
         ▼
┌────────────────────────┐
│  v2 Registered         │
│  Schema ID: 42         │
└────────────────────────┘
```

**Registry validates compatibility before allowing registration!**

---

# Benefits: Contract-First Development

## Traditional Approach:
1. Write code
2. Hope schemas match
3. Integration testing reveals issues
4. Fix and repeat

## Schema Registry Approach:
1. Define schema (contract)
2. Register in registry
3. Generate code from schema
4. Implement services
5. Guaranteed compatibility

**Result:** Shift left on integration issues!

---

# Benefits: Reduced Payload Size

**Without Schema Registry:**
```json
{
  "orderId": "123",
  "customer": "John Smith",
  "total": 99.99,
  // Repeated for EVERY message
  "$schema": { /* full schema definition */ }
}
```
**Size:** ~2KB per message

**With Schema Registry:**
```
[Schema ID: 42][Binary Avro Data]
```
**Size:** ~200 bytes per message

**Savings:** 90% reduction in message size!

---

# Benefits: Multi-Language Support

```
┌─────────────────┐
│  Java Producer  │──┐
└─────────────────┘  │
                     │
┌─────────────────┐  │    ┌──────────────────┐
│  Python Service │──┼───>│  Apicurio        │
└─────────────────┘  │    │  Registry        │
                     │    │  (Avro Schema)   │
┌─────────────────┐  │    └──────────────────┘
│  Go Consumer    │──┘
└─────────────────┘
```

**Same schema, different languages!**
- Avro, Protobuf support multiple languages
- Schema registry provides language-agnostic contracts
- SDKs available for Java, Python, Go, JavaScript, etc.

---

# Best Practices: Schema Design

## 1. **Use Optional Fields for Extensions**
```json
{ "name": "newField", "type": ["null", "string"], "default": null }
```

## 2. **Never Remove Required Fields**
- Add new fields as optional
- Deprecate old fields but keep them

## 3. **Use Semantic Versioning**
- Major: Breaking changes
- Minor: Backward-compatible additions
- Patch: Documentation/fixes

## 4. **Document Your Schemas**
```json
{ "name": "totalAmount", "type": "double",
  "doc": "Total order amount in USD" }
```

---

# Best Practices: Registry Operations

## 1. **Automate Schema Registration**
- Use Maven/Gradle plugins
- CI/CD pipeline integration
- Fail builds on incompatible changes

## 2. **Configure Compatibility Rules**
```java
// Enforce backward compatibility
props.put(SerdeConfig.COMPATIBILITY_MODE, "BACKWARD");
```

## 3. **Use Schema References**
- Reuse common types
- Avoid duplication
- Maintain consistency

## 4. **Monitor Registry Health**
- Track schema versions
- Monitor API latency
- Alert on compatibility failures

---

# Production Considerations

## High Availability
- Run multiple registry instances
- Use external storage (PostgreSQL, Kafka)
- Configure health checks

## Security
- Enable authentication (OAuth2, OIDC)
- Configure TLS/SSL
- Role-based access control

## Performance
- Enable client-side caching
- Configure appropriate timeouts
- Monitor registry response times

## Disaster Recovery
- Backup schema data
- Document recovery procedures
- Test failover scenarios

---

# Apicurio Registry vs. Confluent Schema Registry

| Feature | Apicurio Registry | Confluent Schema Registry |
|---------|-------------------|---------------------------|
| **License** | Apache 2.0 (Open Source) | Confluent Community License |
| **Storage** | KafkaSQL, SQL, GitOps | Kafka only |
| **Formats** | Avro, Protobuf, JSON Schema, OpenAPI, AsyncAPI | Avro, Protobuf, JSON Schema |
| **UI** | Built-in Web UI | Separate commercial product |
| **Deployment** | Standalone, containers, Kubernetes | Confluent Platform |
| **Cost** | Free | Free (basic), paid (advanced) |

**Both are excellent choices - select based on your ecosystem!**

---

# Demo: Running the Example

```bash
# 1. Start infrastructure
cd examples/kafka-order-processing
docker-compose up -d

# 2. Build applications
mvn clean package

# 3. Run producer (generates orders every 5 seconds)
cd order-producer
mvn quarkus:dev

# 4. Run consumer (processes orders)
cd order-consumer
mvn quarkus:dev

# 5. View schemas in UI
open http://localhost:8888
```

**Live demonstration of:**
- Automatic schema registration
- Real-time message flow
- Schema versioning
- Web UI exploration

---

# Real-World Use Cases

## 1. **E-Commerce Platform**
- Order events, inventory updates, payment notifications
- Multiple microservices, different teams
- Schema evolution as business requirements change

## 2. **IoT Data Pipeline**
- Sensor data from thousands of devices
- Multiple consumers (analytics, alerting, storage)
- Evolving sensor capabilities over time

## 3. **Financial Services**
- Transaction events, compliance logging
- Strict data contracts and audit requirements
- Multi-region deployment with consistency

## 4. **Event-Driven Microservices**
- Domain events across bounded contexts
- Cross-team communication contracts
- Independent service deployment with compatibility

---

# Anti-Patterns to Avoid

## ❌ **Frequent Breaking Changes**
- Indicates poor schema design
- Use optional fields and deprecation

## ❌ **Bypassing the Registry**
- Defeats the purpose
- Leads to compatibility issues

## ❌ **Overly Complex Schemas**
- Keep schemas focused and simple
- Use schema references for reusability

## ❌ **No Versioning Strategy**
- Always plan for evolution
- Document compatibility requirements

## ❌ **Ignoring Compatibility Checks**
- Registry warnings are there for a reason
- Fix issues before production

---

# Key Takeaways

1. **Schema Registry is Essential** for production Kafka architectures
   - Provides contract enforcement
   - Enables safe schema evolution
   - Reduces payload size

2. **Choose the Right Format** (Avro, Protobuf, JSON Schema)
   - Based on language support needs
   - Performance requirements
   - Existing ecosystem

3. **Design for Evolution** from day one
   - Use optional fields
   - Plan compatibility strategy
   - Document schemas well

4. **Automate and Monitor**
   - CI/CD integration
   - Compatibility validation
   - Registry health monitoring

---

# Resources

**Apicurio Registry:**
- Documentation: https://www.apicur.io/registry/docs/
- GitHub: https://github.com/Apicurio/apicurio-registry
- Examples: https://github.com/Apicurio/apicurio-registry/tree/main/examples

**Apache Kafka:**
- Documentation: https://kafka.apache.org/documentation/

**Apache Avro:**
- Documentation: https://avro.apache.org/docs/

**Order Processing Example:**
- `examples/kafka-order-processing/`
- Complete working demo with Docker Compose
- Producer, Consumer, and Schema modules

---

# Thank You!

## Questions?

**Try the example:**
```bash
git clone https://github.com/Apicurio/apicurio-registry
cd apicurio-registry/examples/kafka-order-processing
docker-compose up -d
mvn clean package
# Follow README.md for next steps
```

**Connect:**
- Apicurio Community: https://apicurio.zulipchat.com/
- GitHub Discussions: https://github.com/Apicurio/apicurio-registry/discussions

---
