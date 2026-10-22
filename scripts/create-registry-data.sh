#!/bin/bash

# Apicurio Registry Sample Data Creator (v3 API)
# This script creates realistic groups, artifacts, and versions for testing and demonstration

set -e

# Configuration
REGISTRY_URL="${REGISTRY_URL:-http://localhost:8080}"
API_BASE="$REGISTRY_URL/apis/registry/v3"
TEMP_DIR="/tmp/registry-schemas"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Helper functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

check_registry() {
    log_info "Checking registry connectivity..."
    if curl -s "$REGISTRY_URL/health" > /dev/null; then
        log_success "Registry is accessible at $REGISTRY_URL"
    else
        log_error "Cannot connect to registry at $REGISTRY_URL"
        exit 1
    fi
}

create_group() {
    local group="$1"
    local description="$2"
    local labels="$3"

    log_info "Creating group $group..."

    local group_data="{
        \"groupId\": \"$group\",
        \"description\": \"$description\",
        \"labels\": $labels
    }"

    local response=$(curl -s -w "%{http_code}" \
        -X POST "$API_BASE/groups" \
        -H "Content-Type: application/json" \
        -d "$group_data")

    local http_code="${response: -3}"
    if [[ "$http_code" =~ ^20[0-9]$ ]]; then
        log_success "Created group $group ($description)"
    else
        log_warning "Failed to create group $group (HTTP $http_code) - may already exist"
    fi
}


create_schema_file() {
    local filename="$1"
    local content="$2"
    echo "$content" > "$TEMP_DIR/$filename"
}

create_artifact() {
    local group="$1"
    local artifact_id="$2"
    local schema_file="$3"
    local description="$4"
    local labels="$5"

    log_info "Creating artifact $artifact_id in group $group..."

    # Read the schema content
    local schema_content=$(cat "$TEMP_DIR/$schema_file")

    # Create the v3 API compatible request body
    local artifact_data=$(cat <<EOF
{
  "artifactId": "$artifact_id",
  "artifactType": "JSON",
  "description": "$description",
  "labels": $labels,
  "firstVersion": {
    "content": {
      "content": $(echo "$schema_content" | jq -R -s .),
      "contentType": "application/json"
    },
    "name": "$artifact_id v1.0",
    "description": "Initial version of $description",
    "labels": $labels
  }
}
EOF
    )

    local response=$(curl -s -w "%{http_code}" \
        -X POST "$API_BASE/groups/$group/artifacts" \
        -H "Content-Type: application/json" \
        -d "$artifact_data")

    local http_code="${response: -3}"
    if [[ "$http_code" =~ ^20[0-9]$ ]]; then
        log_success "Created $artifact_id ($description)"
    else
        log_warning "Failed to create $artifact_id (HTTP $http_code)"
    fi
}

create_version() {
    local group="$1"
    local artifact_id="$2"
    local schema_file="$3"
    local version_info="$4"
    local labels="$5"

    log_info "Creating new version for $artifact_id..."

    # Read the schema content
    local schema_content=$(cat "$TEMP_DIR/$schema_file")

    # Create the v3 API compatible request body
    local version_data=$(cat <<EOF
{
  "content": {
    "content": $(echo "$schema_content" | jq -R -s .),
    "contentType": "application/json"
  },
  "name": "$artifact_id v2.0",
  "description": "$version_info",
  "labels": $labels
}
EOF
    )

    local response=$(curl -s -w "%{http_code}" \
        -X POST "$API_BASE/groups/$group/artifacts/$artifact_id/versions" \
        -H "Content-Type: application/json" \
        -d "$version_data")

    local http_code="${response: -3}"
    if [[ "$http_code" =~ ^20[0-9]$ ]]; then
        log_success "Created new version for $artifact_id ($version_info)"
    else
        log_warning "Failed to create version for $artifact_id (HTTP $http_code)"
    fi
}

# Main execution
main() {
    echo "=========================================="
    echo "  Apicurio Registry Sample Data Creator"
    echo "            (v3 API)"
    echo "=========================================="
    echo

    check_registry

    # Create temporary directory for schema files
    mkdir -p "$TEMP_DIR"

    log_info "Creating schema files..."

    # E-commerce schemas
    create_schema_file "customer-order-v1.json" '{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "type": "object",
  "title": "Customer Order",
  "description": "Schema for customer order events in e-commerce platform",
  "required": ["orderId", "customerId", "orderDate", "items", "totalAmount"],
  "properties": {
    "orderId": {
      "type": "string",
      "pattern": "^ORD-[0-9]{8}$",
      "description": "Unique order identifier"
    },
    "customerId": {
      "type": "string",
      "pattern": "^CUST-[0-9]{6}$",
      "description": "Customer identifier"
    },
    "orderDate": {
      "type": "string",
      "format": "date-time",
      "description": "Order creation timestamp"
    },
    "items": {
      "type": "array",
      "minItems": 1,
      "items": {
        "type": "object",
        "required": ["productId", "quantity", "price"],
        "properties": {
          "productId": { "type": "string" },
          "productName": { "type": "string" },
          "quantity": { "type": "integer", "minimum": 1 },
          "price": { "type": "number", "minimum": 0 }
        }
      }
    },
    "totalAmount": {
      "type": "number",
      "minimum": 0,
      "description": "Total order amount in USD"
    },
    "status": {
      "type": "string",
      "enum": ["pending", "confirmed", "processing", "shipped", "delivered", "cancelled"]
    },
    "shippingAddress": {
      "type": "object",
      "required": ["street", "city", "country", "postalCode"],
      "properties": {
        "street": { "type": "string" },
        "city": { "type": "string" },
        "state": { "type": "string" },
        "country": { "type": "string" },
        "postalCode": { "type": "string" }
      }
    }
  }
}'

    create_schema_file "customer-order-v2.json" '{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "type": "object",
  "title": "Customer Order v2",
  "description": "Enhanced schema for customer order events with payment and tracking info",
  "required": ["orderId", "customerId", "orderDate", "items", "totalAmount"],
  "properties": {
    "orderId": {
      "type": "string",
      "pattern": "^ORD-[0-9]{8}$",
      "description": "Unique order identifier"
    },
    "customerId": {
      "type": "string",
      "pattern": "^CUST-[0-9]{6}$",
      "description": "Customer identifier"
    },
    "orderDate": {
      "type": "string",
      "format": "date-time",
      "description": "Order creation timestamp"
    },
    "items": {
      "type": "array",
      "minItems": 1,
      "items": {
        "type": "object",
        "required": ["productId", "quantity", "price"],
        "properties": {
          "productId": { "type": "string" },
          "productName": { "type": "string" },
          "productSku": { "type": "string", "description": "Product SKU code" },
          "quantity": { "type": "integer", "minimum": 1 },
          "price": { "type": "number", "minimum": 0 },
          "discount": { "type": "number", "minimum": 0, "maximum": 1, "description": "Discount percentage" },
          "category": { "type": "string", "description": "Product category" }
        }
      }
    },
    "totalAmount": {
      "type": "number",
      "minimum": 0,
      "description": "Total order amount in USD"
    },
    "currency": {
      "type": "string",
      "pattern": "^[A-Z]{3}$",
      "default": "USD",
      "description": "Currency code (ISO 4217)"
    },
    "status": {
      "type": "string",
      "enum": ["pending", "confirmed", "processing", "shipped", "delivered", "cancelled", "returned"]
    },
    "paymentInfo": {
      "type": "object",
      "required": ["method", "status"],
      "properties": {
        "method": { "type": "string", "enum": ["credit_card", "debit_card", "paypal", "apple_pay", "google_pay", "bank_transfer"] },
        "status": { "type": "string", "enum": ["pending", "authorized", "captured", "failed", "refunded"] },
        "transactionId": { "type": "string" },
        "authorizationCode": { "type": "string" }
      }
    },
    "shippingAddress": {
      "type": "object",
      "required": ["street", "city", "country", "postalCode"],
      "properties": {
        "street": { "type": "string" },
        "city": { "type": "string" },
        "state": { "type": "string" },
        "country": { "type": "string" },
        "postalCode": { "type": "string" }
      }
    },
    "tracking": {
      "type": "object",
      "properties": {
        "carrier": { "type": "string", "description": "Shipping carrier" },
        "trackingNumber": { "type": "string" },
        "estimatedDelivery": { "type": "string", "format": "date-time" },
        "actualDelivery": { "type": "string", "format": "date-time" }
      }
    },
    "promotions": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "promoCode": { "type": "string" },
          "discountAmount": { "type": "number", "minimum": 0 },
          "type": { "type": "string", "enum": ["percentage", "fixed_amount", "free_shipping"] }
        }
      }
    }
  }
}'

    create_schema_file "customer-profile-v1.json" '{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "type": "object",
  "title": "Customer Profile",
  "description": "Schema for customer profile information in e-commerce platform",
  "required": ["customerId", "email", "firstName", "lastName", "registrationDate"],
  "properties": {
    "customerId": {
      "type": "string",
      "pattern": "^CUST-[0-9]{6}$",
      "description": "Unique customer identifier"
    },
    "email": {
      "type": "string",
      "format": "email",
      "description": "Customer email address"
    },
    "firstName": {
      "type": "string",
      "minLength": 1,
      "maxLength": 50,
      "description": "Customer first name"
    },
    "lastName": {
      "type": "string",
      "minLength": 1,
      "maxLength": 50,
      "description": "Customer last name"
    },
    "phoneNumber": {
      "type": "string",
      "pattern": "^\\\\+?[1-9]\\\\d{1,14}$",
      "description": "Customer phone number in international format"
    },
    "dateOfBirth": {
      "type": "string",
      "format": "date",
      "description": "Customer date of birth"
    },
    "registrationDate": {
      "type": "string",
      "format": "date-time",
      "description": "Customer registration timestamp"
    },
    "preferences": {
      "type": "object",
      "properties": {
        "newsletterSubscribed": { "type": "boolean" },
        "smsNotifications": { "type": "boolean" },
        "preferredLanguage": { "type": "string", "enum": ["en", "es", "fr", "de", "it"] },
        "categories": {
          "type": "array",
          "items": { "type": "string" },
          "description": "Preferred product categories"
        }
      }
    },
    "addresses": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["type", "street", "city", "country", "postalCode"],
        "properties": {
          "type": { "type": "string", "enum": ["billing", "shipping", "both"] },
          "street": { "type": "string" },
          "city": { "type": "string" },
          "state": { "type": "string" },
          "country": { "type": "string" },
          "postalCode": { "type": "string" },
          "isDefault": { "type": "boolean" }
        }
      }
    },
    "loyaltyStatus": {
      "type": "string",
      "enum": ["bronze", "silver", "gold", "platinum"],
      "description": "Customer loyalty tier"
    }
  }
}'

    # IoT Sensor schemas
    create_schema_file "sensor-telemetry-v1.json" '{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "type": "object",
  "title": "IoT Sensor Telemetry",
  "description": "Schema for IoT sensor telemetry data collection",
  "required": ["deviceId", "timestamp", "sensorType", "measurements"],
  "properties": {
    "deviceId": {
      "type": "string",
      "pattern": "^DEVICE-[A-Z0-9]{8}$",
      "description": "Unique device identifier"
    },
    "timestamp": {
      "type": "string",
      "format": "date-time",
      "description": "Measurement timestamp in ISO 8601 format"
    },
    "sensorType": {
      "type": "string",
      "enum": ["temperature", "humidity", "pressure", "light", "motion", "air_quality", "soil_moisture", "ph"],
      "description": "Type of sensor providing the measurement"
    },
    "location": {
      "type": "object",
      "required": ["latitude", "longitude"],
      "properties": {
        "latitude": { "type": "number", "minimum": -90, "maximum": 90 },
        "longitude": { "type": "number", "minimum": -180, "maximum": 180 },
        "altitude": { "type": "number", "description": "Altitude in meters" },
        "zone": { "type": "string", "description": "Location zone or area identifier" }
      }
    },
    "measurements": {
      "type": "object",
      "required": ["value", "unit"],
      "properties": {
        "value": {
          "type": "number",
          "description": "Measured value"
        },
        "unit": {
          "type": "string",
          "description": "Unit of measurement (e.g., celsius, fahrenheit, percent, lux, ppm)"
        },
        "accuracy": {
          "type": "number",
          "minimum": 0,
          "maximum": 100,
          "description": "Measurement accuracy percentage"
        },
        "calibrationDate": {
          "type": "string",
          "format": "date",
          "description": "Last calibration date"
        }
      }
    },
    "batteryLevel": {
      "type": "number",
      "minimum": 0,
      "maximum": 100,
      "description": "Device battery level percentage"
    },
    "signalStrength": {
      "type": "number",
      "minimum": -120,
      "maximum": 0,
      "description": "Signal strength in dBm"
    },
    "quality": {
      "type": "string",
      "enum": ["excellent", "good", "fair", "poor", "error"],
      "description": "Data quality assessment"
    },
    "alertLevel": {
      "type": "string",
      "enum": ["normal", "warning", "critical", "emergency"],
      "description": "Alert level based on measurement thresholds"
    }
  }
}'

    create_schema_file "device-registration-v1.json" '{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "type": "object",
  "title": "IoT Device Registration",
  "description": "Schema for IoT device registration and configuration",
  "required": ["deviceId", "deviceType", "manufacturer", "model", "registrationDate"],
  "properties": {
    "deviceId": {
      "type": "string",
      "pattern": "^DEVICE-[A-Z0-9]{8}$",
      "description": "Unique device identifier"
    },
    "deviceType": {
      "type": "string",
      "enum": ["sensor", "actuator", "gateway", "camera", "tracker"],
      "description": "Type of IoT device"
    },
    "manufacturer": {
      "type": "string",
      "minLength": 1,
      "maxLength": 50,
      "description": "Device manufacturer"
    },
    "model": {
      "type": "string",
      "minLength": 1,
      "maxLength": 50,
      "description": "Device model number"
    },
    "firmwareVersion": {
      "type": "string",
      "pattern": "^\\\\d+\\\\.\\\\d+\\\\.\\\\d+$",
      "description": "Firmware version in semver format"
    },
    "registrationDate": {
      "type": "string",
      "format": "date-time",
      "description": "Device registration timestamp"
    },
    "location": {
      "type": "object",
      "required": ["latitude", "longitude"],
      "properties": {
        "latitude": { "type": "number", "minimum": -90, "maximum": 90 },
        "longitude": { "type": "number", "minimum": -180, "maximum": 180 },
        "altitude": { "type": "number", "description": "Altitude in meters" },
        "description": { "type": "string", "description": "Human-readable location description" }
      }
    },
    "capabilities": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["type", "unit"],
        "properties": {
          "type": { "type": "string", "enum": ["temperature", "humidity", "pressure", "light", "motion", "air_quality"] },
          "unit": { "type": "string" },
          "range": {
            "type": "object",
            "properties": {
              "min": { "type": "number" },
              "max": { "type": "number" }
            }
          }
        }
      }
    },
    "status": {
      "type": "string",
      "enum": ["active", "inactive", "maintenance", "error", "decommissioned"],
      "description": "Current device status"
    }
  }
}'

    # Financial Services schemas
    create_schema_file "financial-transaction-v1.json" '{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "type": "object",
  "title": "Financial Transaction",
  "description": "Schema for financial transaction processing and recording",
  "required": ["transactionId", "accountId", "timestamp", "amount", "currency", "type"],
  "properties": {
    "transactionId": {
      "type": "string",
      "pattern": "^TXN-[A-Z0-9]{12}$",
      "description": "Unique transaction identifier"
    },
    "accountId": {
      "type": "string",
      "pattern": "^ACC-[0-9]{10}$",
      "description": "Account identifier"
    },
    "timestamp": {
      "type": "string",
      "format": "date-time",
      "description": "Transaction timestamp"
    },
    "amount": {
      "type": "number",
      "multipleOf": 0.01,
      "description": "Transaction amount with 2 decimal precision"
    },
    "currency": {
      "type": "string",
      "pattern": "^[A-Z]{3}$",
      "description": "Currency code (ISO 4217)"
    },
    "type": {
      "type": "string",
      "enum": ["debit", "credit", "transfer", "payment", "refund", "fee", "interest", "dividend"],
      "description": "Transaction type"
    },
    "category": {
      "type": "string",
      "enum": ["groceries", "dining", "transportation", "entertainment", "utilities", "healthcare", "education", "shopping", "travel", "investment", "other"],
      "description": "Transaction category"
    },
    "description": {
      "type": "string",
      "maxLength": 255,
      "description": "Transaction description"
    },
    "merchant": {
      "type": "object",
      "properties": {
        "name": { "type": "string", "maxLength": 100 },
        "merchantId": { "type": "string" },
        "category": { "type": "string" }
      }
    },
    "status": {
      "type": "string",
      "enum": ["pending", "processing", "completed", "failed", "cancelled", "disputed"],
      "description": "Transaction status"
    },
    "riskScore": {
      "type": "number",
      "minimum": 0,
      "maximum": 100,
      "description": "Fraud risk score"
    }
  }
}'

    create_schema_file "account-info-v1.json" '{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "type": "object",
  "title": "Bank Account Information",
  "description": "Schema for bank account information and metadata",
  "required": ["accountId", "accountNumber", "accountType", "currency", "status", "openDate"],
  "properties": {
    "accountId": {
      "type": "string",
      "pattern": "^ACC-[0-9]{10}$",
      "description": "Internal account identifier"
    },
    "accountNumber": {
      "type": "string",
      "pattern": "^[0-9]{10,16}$",
      "description": "Bank account number"
    },
    "routingNumber": {
      "type": "string",
      "pattern": "^[0-9]{9}$",
      "description": "Bank routing number"
    },
    "accountType": {
      "type": "string",
      "enum": ["checking", "savings", "money_market", "cd", "credit", "loan", "investment"],
      "description": "Type of account"
    },
    "currency": {
      "type": "string",
      "pattern": "^[A-Z]{3}$",
      "description": "Account currency (ISO 4217)"
    },
    "balance": {
      "type": "object",
      "required": ["available", "current"],
      "properties": {
        "available": { "type": "number", "description": "Available balance" },
        "current": { "type": "number", "description": "Current balance" },
        "pending": { "type": "number", "description": "Pending transactions total" },
        "lastUpdated": { "type": "string", "format": "date-time" }
      }
    },
    "status": {
      "type": "string",
      "enum": ["active", "inactive", "frozen", "closed", "pending_activation"],
      "description": "Account status"
    },
    "openDate": {
      "type": "string",
      "format": "date",
      "description": "Account opening date"
    },
    "primaryHolder": {
      "type": "object",
      "required": ["customerId", "name"],
      "properties": {
        "customerId": { "type": "string", "pattern": "^CUST-[0-9]{8}$" },
        "name": { "type": "string" },
        "phone": { "type": "string" },
        "email": { "type": "string", "format": "email" }
      }
    }
  }
}'

    # User Management schemas
    create_schema_file "user-profile-v1.json" '{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "type": "object",
  "title": "User Profile",
  "description": "Schema for user profile management and personal information",
  "required": ["userId", "username", "email", "createdAt", "status"],
  "properties": {
    "userId": {
      "type": "string",
      "pattern": "^USER-[A-Z0-9]{8}$",
      "description": "Unique user identifier"
    },
    "username": {
      "type": "string",
      "pattern": "^[a-zA-Z0-9_]{3,20}$",
      "description": "Unique username for login"
    },
    "email": {
      "type": "string",
      "format": "email",
      "description": "User email address"
    },
    "emailVerified": {
      "type": "boolean",
      "default": false,
      "description": "Email verification status"
    },
    "personalInfo": {
      "type": "object",
      "required": ["firstName", "lastName"],
      "properties": {
        "firstName": { "type": "string", "minLength": 1, "maxLength": 50 },
        "lastName": { "type": "string", "minLength": 1, "maxLength": 50 },
        "displayName": { "type": "string", "maxLength": 100 },
        "dateOfBirth": { "type": "string", "format": "date" },
        "phoneNumber": { "type": "string", "pattern": "^\\\\+?[1-9]\\\\d{1,14}$" }
      }
    },
    "preferences": {
      "type": "object",
      "properties": {
        "language": { "type": "string", "pattern": "^[a-z]{2}(-[A-Z]{2})?$", "default": "en" },
        "timezone": { "type": "string", "description": "IANA timezone identifier" },
        "theme": { "type": "string", "enum": ["light", "dark", "auto"] }
      }
    },
    "roles": {
      "type": "array",
      "items": { "type": "string", "enum": ["user", "admin", "moderator", "viewer", "editor"] },
      "description": "User roles and permissions"
    },
    "status": {
      "type": "string",
      "enum": ["active", "inactive", "suspended", "pending_verification", "deleted"],
      "description": "User account status"
    },
    "createdAt": {
      "type": "string",
      "format": "date-time",
      "description": "Account creation timestamp"
    },
    "lastLoginAt": {
      "type": "string",
      "format": "date-time",
      "description": "Last login timestamp"
    }
  }
}'

    create_schema_file "authentication-event-v1.json" '{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "type": "object",
  "title": "Authentication Event",
  "description": "Schema for authentication events and security logging",
  "required": ["eventId", "userId", "eventType", "timestamp", "status"],
  "properties": {
    "eventId": {
      "type": "string",
      "pattern": "^AUTH-[A-Z0-9]{12}$",
      "description": "Unique authentication event identifier"
    },
    "userId": {
      "type": "string",
      "pattern": "^USER-[A-Z0-9]{8}$",
      "description": "User identifier attempting authentication"
    },
    "sessionId": {
      "type": "string",
      "pattern": "^SES-[A-Z0-9]{16}$",
      "description": "Session identifier"
    },
    "eventType": {
      "type": "string",
      "enum": ["login", "logout", "password_change", "password_reset", "mfa_challenge", "mfa_verification", "account_lockout", "account_unlock", "registration", "email_verification"],
      "description": "Type of authentication event"
    },
    "timestamp": {
      "type": "string",
      "format": "date-time",
      "description": "Event timestamp"
    },
    "status": {
      "type": "string",
      "enum": ["success", "failure", "pending", "expired", "blocked"],
      "description": "Authentication status"
    },
    "method": {
      "type": "string",
      "enum": ["password", "mfa_totp", "mfa_sms", "oauth", "saml", "biometric", "api_key", "social_login"],
      "description": "Authentication method used"
    },
    "clientInfo": {
      "type": "object",
      "required": ["ipAddress", "userAgent"],
      "properties": {
        "ipAddress": {
          "type": "string",
          "description": "Client IP address"
        },
        "userAgent": { "type": "string", "description": "Client user agent string" },
        "deviceType": { "type": "string", "enum": ["desktop", "mobile", "tablet", "api", "unknown"] },
        "country": { "type": "string", "pattern": "^[A-Z]{2}$", "description": "Country code from IP geolocation" }
      }
    },
    "riskFactors": {
      "type": "object",
      "properties": {
        "riskScore": { "type": "number", "minimum": 0, "maximum": 100, "description": "Overall risk score" },
        "isNewDevice": { "type": "boolean" },
        "isNewLocation": { "type": "boolean" },
        "recentFailedAttempts": { "type": "integer", "minimum": 0 }
      }
    }
  }
}'

    # Inventory Management schemas
    create_schema_file "product-catalog-v1.json" '{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "type": "object",
  "title": "Product Catalog",
  "description": "Schema for product catalog management and inventory tracking",
  "required": ["productId", "sku", "name", "category", "status", "createdAt"],
  "properties": {
    "productId": {
      "type": "string",
      "pattern": "^PROD-[A-Z0-9]{8}$",
      "description": "Unique product identifier"
    },
    "sku": {
      "type": "string",
      "pattern": "^[A-Z0-9-]{6,20}$",
      "description": "Stock Keeping Unit"
    },
    "name": {
      "type": "string",
      "minLength": 1,
      "maxLength": 200,
      "description": "Product name"
    },
    "description": {
      "type": "string",
      "maxLength": 2000,
      "description": "Product description"
    },
    "category": {
      "type": "object",
      "required": ["id", "name"],
      "properties": {
        "id": { "type": "string" },
        "name": { "type": "string" },
        "parentId": { "type": "string" }
      }
    },
    "pricing": {
      "type": "object",
      "required": ["basePrice", "currency"],
      "properties": {
        "basePrice": { "type": "number", "minimum": 0, "multipleOf": 0.01 },
        "currency": { "type": "string", "pattern": "^[A-Z]{3}$" },
        "salePrice": { "type": "number", "minimum": 0, "multipleOf": 0.01 },
        "costPrice": { "type": "number", "minimum": 0, "multipleOf": 0.01 }
      }
    },
    "inventory": {
      "type": "object",
      "required": ["quantityOnHand"],
      "properties": {
        "quantityOnHand": { "type": "integer", "minimum": 0 },
        "quantityReserved": { "type": "integer", "minimum": 0 },
        "quantityAvailable": { "type": "integer", "minimum": 0 },
        "reorderPoint": { "type": "integer", "minimum": 0 }
      }
    },
    "attributes": {
      "type": "object",
      "properties": {
        "weight": { "type": "number", "minimum": 0, "description": "Weight in grams" },
        "color": { "type": "string" },
        "size": { "type": "string" },
        "material": { "type": "string" }
      }
    },
    "status": {
      "type": "string",
      "enum": ["active", "inactive", "discontinued", "out_of_stock", "pending_approval"],
      "description": "Product status"
    },
    "visibility": {
      "type": "string",
      "enum": ["public", "private", "catalog_only", "search_hidden"],
      "description": "Product visibility settings"
    },
    "tags": {
      "type": "array",
      "items": { "type": "string" },
      "description": "Product tags for search and categorization"
    },
    "createdAt": {
      "type": "string",
      "format": "date-time",
      "description": "Product creation timestamp"
    },
    "createdBy": {
      "type": "string",
      "description": "User who created the product"
    }
  }
}'

    create_schema_file "stock-movement-v1.json" '{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "type": "object",
  "title": "Stock Movement",
  "description": "Schema for tracking inventory stock movements and transactions",
  "required": ["movementId", "productId", "movementType", "quantity", "timestamp", "location"],
  "properties": {
    "movementId": {
      "type": "string",
      "pattern": "^MOV-[A-Z0-9]{10}$",
      "description": "Unique stock movement identifier"
    },
    "productId": {
      "type": "string",
      "pattern": "^PROD-[A-Z0-9]{8}$",
      "description": "Product identifier"
    },
    "sku": {
      "type": "string",
      "pattern": "^[A-Z0-9-]{6,20}$",
      "description": "Stock Keeping Unit"
    },
    "movementType": {
      "type": "string",
      "enum": ["inbound", "outbound", "transfer", "adjustment", "return", "damaged", "expired", "promotional"],
      "description": "Type of stock movement"
    },
    "subType": {
      "type": "string",
      "enum": ["purchase", "sale", "warehouse_transfer", "cycle_count", "customer_return", "supplier_return", "damage_write_off", "expiry_write_off"],
      "description": "Detailed movement subtype"
    },
    "quantity": {
      "type": "integer",
      "description": "Quantity moved (positive for inbound, negative for outbound)"
    },
    "unitCost": {
      "type": "number",
      "minimum": 0,
      "multipleOf": 0.01,
      "description": "Cost per unit at time of movement"
    },
    "currency": {
      "type": "string",
      "pattern": "^[A-Z]{3}$",
      "description": "Currency code (ISO 4217)"
    },
    "timestamp": {
      "type": "string",
      "format": "date-time",
      "description": "Movement timestamp"
    },
    "location": {
      "type": "object",
      "required": ["warehouseId"],
      "properties": {
        "warehouseId": { "type": "string", "pattern": "^WH-[0-9]{4}$" },
        "warehouseName": { "type": "string" },
        "zone": { "type": "string", "description": "Warehouse zone or section" },
        "aisle": { "type": "string" },
        "shelf": { "type": "string" }
      }
    },
    "balanceBefore": {
      "type": "integer",
      "minimum": 0,
      "description": "Stock balance before this movement"
    },
    "balanceAfter": {
      "type": "integer",
      "minimum": 0,
      "description": "Stock balance after this movement"
    },
    "performedBy": {
      "type": "object",
      "required": ["userId"],
      "properties": {
        "userId": { "type": "string" },
        "username": { "type": "string" },
        "role": { "type": "string" }
      }
    },
    "reason": {
      "type": "string",
      "maxLength": 500,
      "description": "Reason for the stock movement"
    }
  }
}'

    log_success "Schema files created successfully"

    # Create groups with labels
    echo
    log_info "Creating registry groups with labels..."

    create_group "ecommerce" "E-commerce domain schemas for customer orders and profiles" '{
        "domain": "ecommerce",
        "environment": "demo",
        "team": "customer-experience",
        "criticality": "high",
        "purpose": "order-processing"
    }'

    create_group "iot-sensors" "IoT sensor data schemas for telemetry and device management" '{
        "domain": "iot",
        "environment": "demo",
        "team": "platform-engineering",
        "criticality": "medium",
        "purpose": "telemetry"
    }'

    create_group "financial-services" "Financial services schemas for transactions and accounts" '{
        "domain": "finance",
        "environment": "demo",
        "team": "payments",
        "criticality": "critical",
        "purpose": "transaction-processing"
    }'

    create_group "user-management" "User management schemas for profiles and authentication" '{
        "domain": "identity",
        "environment": "demo",
        "team": "security",
        "criticality": "critical",
        "purpose": "authentication"
    }'

    create_group "inventory-management" "Inventory management schemas for products and stock" '{
        "domain": "inventory",
        "environment": "demo",
        "team": "supply-chain",
        "criticality": "high",
        "purpose": "stock-management"
    }'

    # Create artifacts in registry
    echo
    log_info "Creating registry artifacts with labels..."

    # E-commerce group
    echo "📦 E-commerce Group"
    create_artifact "ecommerce" "customer-order-v1" "customer-order-v1.json" "Customer order processing schema" '{
        "schema-type": "order",
        "data-classification": "internal",
        "version": "1.0",
        "breaking-change": "false",
        "owner": "customer-experience-team"
    }'
    create_artifact "ecommerce" "customer-profile-v1" "customer-profile-v1.json" "Customer profile information schema" '{
        "schema-type": "profile",
        "data-classification": "confidential",
        "version": "1.0",
        "breaking-change": "false",
        "owner": "customer-experience-team"
    }'

    # Create evolved version of customer order
    create_version "ecommerce" "customer-order-v1" "customer-order-v2.json" "Enhanced with payment and tracking" '{
        "schema-type": "order",
        "data-classification": "internal",
        "version": "2.0",
        "breaking-change": "false",
        "enhancement": "payment-tracking",
        "migration-guide": "available"
    }'

    # IoT sensors group
    echo "🌡️  IoT Sensors Group"
    create_artifact "iot-sensors" "sensor-telemetry-v1" "sensor-telemetry-v1.json" "IoT sensor telemetry data schema" '{
        "schema-type": "telemetry",
        "data-classification": "internal",
        "version": "1.0",
        "breaking-change": "false",
        "sensor-types": "temperature,humidity,pressure",
        "owner": "platform-engineering-team"
    }'
    create_artifact "iot-sensors" "device-registration-v1" "device-registration-v1.json" "IoT device registration schema" '{
        "schema-type": "registration",
        "data-classification": "internal",
        "version": "1.0",
        "breaking-change": "false",
        "device-types": "sensor,actuator,gateway",
        "owner": "platform-engineering-team"
    }'

    # Financial services group
    echo "💰 Financial Services Group"
    create_artifact "financial-services" "financial-transaction-v1" "financial-transaction-v1.json" "Financial transaction processing schema" '{
        "schema-type": "transaction",
        "data-classification": "restricted",
        "version": "1.0",
        "breaking-change": "false",
        "compliance": "pci-dss",
        "owner": "payments-team"
    }'
    create_artifact "financial-services" "account-info-v1" "account-info-v1.json" "Bank account information schema" '{
        "schema-type": "account",
        "data-classification": "restricted",
        "version": "1.0",
        "breaking-change": "false",
        "compliance": "pci-dss,gdpr",
        "owner": "payments-team"
    }'

    # User management group
    echo "👤 User Management Group"
    create_artifact "user-management" "user-profile-v1" "user-profile-v1.json" "User profile management schema" '{
        "schema-type": "profile",
        "data-classification": "confidential",
        "version": "1.0",
        "breaking-change": "false",
        "compliance": "gdpr",
        "owner": "security-team"
    }'
    create_artifact "user-management" "authentication-event-v1" "authentication-event-v1.json" "Authentication event logging schema" '{
        "schema-type": "event",
        "data-classification": "confidential",
        "version": "1.0",
        "breaking-change": "false",
        "compliance": "gdpr,sox",
        "retention": "7-years",
        "owner": "security-team"
    }'

    # Inventory management group
    echo "📋 Inventory Management Group"
    create_artifact "inventory-management" "product-catalog-v1" "product-catalog-v1.json" "Product catalog management schema" '{
        "schema-type": "catalog",
        "data-classification": "internal",
        "version": "1.0",
        "breaking-change": "false",
        "business-function": "product-management",
        "owner": "supply-chain-team"
    }'
    create_artifact "inventory-management" "stock-movement-v1" "stock-movement-v1.json" "Stock movement tracking schema" '{
        "schema-type": "movement",
        "data-classification": "internal",
        "version": "1.0",
        "breaking-change": "false",
        "business-function": "inventory-tracking",
        "owner": "supply-chain-team"
    }'

    # Clean up temporary files
    rm -rf "$TEMP_DIR"

    echo
    echo "=========================================="
    log_success "Registry sample data creation completed!"
    echo "=========================================="
    echo
    echo "📊 Summary:"
    echo "   • 5 Groups created with comprehensive labels"
    echo "   • 10 Artifacts created with contextual labels"
    echo "   • 1 Schema evolution demonstrated with version labels"
    echo "   • Labels include: domain, team, criticality, data classification, compliance, and ownership"
    echo
    echo "🌐 Access your registry at: $REGISTRY_URL"
    echo "📋 List groups: curl $API_BASE/groups"
    echo "🏷️  View group metadata: curl $API_BASE/groups/{groupId}"
    echo
    echo "Groups created with labels:"
    echo "   • ecommerce - Customer orders and profiles (high criticality, customer-experience team)"
    echo "   • iot-sensors - Device telemetry and registration (medium criticality, platform-engineering team)"
    echo "   • financial-services - Transactions and accounts (critical, payments team, PCI-DSS compliant)"
    echo "   • user-management - User profiles and authentication (critical, security team, GDPR compliant)"
    echo "   • inventory-management - Products and stock movements (high criticality, supply-chain team)"
    echo
}

# Script execution
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi