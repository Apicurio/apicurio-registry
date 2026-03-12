# Securing Apicurio Registry 3.1 with Microsoft Entra ID External Tenants

- Date: December 16, 2025
- Topic: Configuring Microsoft Entra ID External Tenant as OIDC Provider for Apicurio Registry 3.1

---

## Executive Summary

This report provides comprehensive guidance on configuring Microsoft Entra ID External tenants to secure
Apicurio Registry 3.1.0 using OpenID Connect (OIDC). The research covers tenant creation, application
registration, user management, role configuration, and the critical adaptations needed to replace Keycloak with
Entra ID as the identity provider.

**Key Findings:**
- Microsoft Entra ID External tenants support OIDC and can successfully replace Keycloak for Apicurio Registry
  authentication
- Critical configuration difference: Entra ID uses a flat `roles` claim array vs. Keycloak's nested
  `realm_access.roles` structure
- Apicurio Registry 3.x requires specific Quarkus OIDC configuration properties
- Role-based authorization works with Entra ID app roles mapped to Registry roles (sr-admin, sr-developer,
  sr-readonly)
- **CRITICAL:** Must expose an API scope (`api://{client-id}/access_as_user`) and include it in UI scopes to
  ensure app roles appear in JWT tokens
- **CRITICAL:** Must set `REGISTRY_AUTH_LOAD_USER_INFO=false` to disable userinfo endpoint calls due to Azure AD
  audience mismatch incompatibility

Based on official Microsoft documentation, Apicurio official docs, and verified integration examples

---

## Table of Contents

1. [Understanding Apicurio Registry OIDC Requirements](#1-understanding-apicurio-registry-oidc-requirements)
2. [Microsoft Entra ID External Tenants Overview](#2-microsoft-entra-id-external-tenants-overview)
3. [Keycloak vs. Entra ID Concept Mapping](#3-keycloak-vs-entra-id-concept-mapping)
4. [Step-by-Step Configuration Guide](#4-step-by-step-configuration-guide)
5. [Role Configuration and JWT Claims](#5-role-configuration-and-jwt-claims)
6. [Environment Variables and Configuration](#6-environment-variables-and-configuration)
7. [Machine-to-Machine Authentication (Client Credentials Grant)](#7-machine-to-machine-authentication-client-credentials-grant)
8. [Common Issues and Solutions](#8-common-issues-and-solutions)
9. [Sources and References](#9-sources-and-references)

---

## 1. Understanding Apicurio Registry OIDC Requirements

### 1.1 Apicurio Registry 3.x OIDC Architecture

Apicurio Registry 3.x is built on Quarkus and uses Quarkus OIDC capabilities for authentication. The
application consists of two main components that require separate OIDC configuration:

- **REST API**: Backend service that handles schema/API management
- **UI**: Frontend application that provides the web interface

### 1.2 Required OIDC Configuration

**For the REST API:**
- `QUARKUS_OIDC_TENANT_ENABLED=true` - Enable OIDC authentication
- `QUARKUS_OIDC_AUTH_SERVER_URL` - OIDC provider URL
- `QUARKUS_OIDC_CLIENT_ID` - Client ID for the API

**For the UI:**
- `APICURIO_AUTH_TYPE=oidc` - Set authentication type
- `APICURIO_AUTH_URL` - OIDC auth URL
- `APICURIO_AUTH_REDIRECT_URL` - Redirect URL after authentication
- `APICURIO_AUTH_CLIENT_ID` - Client ID for the UI

### 1.3 Default Role Expectations

Apicurio Registry expects three default roles:
- `sr-admin` - Full administrative access
- `sr-developer` - Create, read, update, delete artifacts
- `sr-readonly` - Read-only access

These roles must be created in your identity provider and included in the JWT token's `roles` claim.

**Sources:**
- [Apicurio Registry 3.0.x Security Configuration](https://www.apicur.io/registry/docs/apicurio-registry/3.0.x/getting-started/assembly-configuring-registry-security.html)
- [Apicurio Registry Configuration Reference](https://www.apicur.io/registry/docs/apicurio-registry/3.0.x/getting-started/assembly-config-reference.html)

---

## 2. Microsoft Entra ID External Tenants Overview

### 2.1 What is an External Tenant?

Microsoft Entra ID External tenants are a specialized tenant configuration designed exclusively for customer
identity and access management (CIAM) scenarios. They are separate from workforce tenants and optimized for
publishing applications to consumers or business customers.

**Key Characteristics:**
- Separate tenant configuration from employee/organizational resources
- Designed for consumer and business customer applications
- Support for self-service account management
- Multiple authentication methods (email/password, email/OTP, social providers, custom OIDC)

### 2.2 External Tenant vs. Workforce Tenant

| Feature | External Tenant | Workforce Tenant |
|---------|----------------|------------------|
| Purpose | Consumer/customer apps | Employee/internal apps |
| User Management | Self-service registration | Admin-managed |
| Authentication | Email, social, custom OIDC | Typically corporate SSO |
| Use Case | Public-facing apps | Internal business apps |

### 2.3 OIDC Support in External Tenants

External tenants provide full OpenID Connect support with:
- Standard OIDC discovery endpoints
- OAuth 2.0 authorization flows
- JWT token issuance with customizable claims
- Support for both v1.0 and v2.0 endpoints

**Important Update (2024):** Microsoft announced Public Preview (and later General Availability) of OpenID
Connect external identity provider support in External ID, allowing federation with any OIDC-compliant
provider.

**Sources:**
- [Microsoft Entra External ID Overview](https://learn.microsoft.com/en-us/entra/external-id/external-identities-overview)
- [External Tenant Overview](https://learn.microsoft.com/en-us/entra/external-id/customers/overview-customers-ciam)
- [OpenID Connect on Microsoft Identity Platform](https://learn.microsoft.com/en-us/entra/identity-platform/v2-protocols-oidc)

---

## 3. Keycloak vs. Entra ID Concept Mapping

### 3.1 Core Concept Translation

| Keycloak Concept | Entra ID Equivalent | Notes |
|------------------|---------------------|-------|
| Realm | Tenant | Top-level organizational container |
| Client | Application Registration | Represents an application that can authenticate |
| Realm Roles | App Roles | Roles defined for authorization |
| Users | Users | Individual user accounts |
| Client Secret | Client Secret | Credential for confidential clients |
| Protocol Mappers | Token Configuration / Claims Mapping | Controls what goes into tokens |

### 3.2 JWT Token Structure Differences

**CRITICAL DIFFERENCE:** The structure of roles in JWT tokens differs significantly between Keycloak and Entra
ID.

**Keycloak JWT Structure:**
```json
{
  "sub": "user-id",
  "realm_access": {
    "roles": ["sr-admin", "sr-developer"]
  },
  "resource_access": {
    "apicurio-registry": {
      "roles": ["sr-admin"]
    }
  }
}
```

**Entra ID JWT Structure:**
```json
{
  "sub": "user-id",
  "roles": ["sr-admin", "sr-developer"]
}
```

**Impact:** Apicurio Registry expects roles in a flat array at the top level of the JWT. Entra ID provides
this by default, but Keycloak requires additional configuration or claim mapping.

### 3.3 Authentication Flow Comparison

Both Keycloak and Entra ID support the same OIDC flows:
- Authorization Code Flow (recommended for web apps)
- Implicit Flow (legacy, not recommended)
- Client Credentials Flow (for service-to-service)

**Sources:**
- [Configure Azure Entra ID as IdP on Keycloak](https://blog.ght1pc9kc.fr/en/2023/configure-azure-entra-id-as-idp-on-keycloak/)
- [Using Keycloak with Azure AD](https://techcommunity.microsoft.com/blog/azuredevcommunityblog/using-keycloak-with-azure-ad-to-integrate-aks-cluster-authentication-process/4174238)
- [Access Token Claims Reference](https://learn.microsoft.com/en-us/entra/identity-platform/access-token-claims-reference)

---

## 4. Step-by-Step Configuration Guide

### 4.1 Create Microsoft Entra External Tenant

**Prerequisites:**
- Azure subscription (or use 30-day free trial)
- Azure account with at least Tenant Creator role

**Steps:**

1. **Sign in to Microsoft Entra Admin Center**
   - Navigate to [https://entra.microsoft.com](https://entra.microsoft.com)
   - Sign in with your Azure account

2. **Create External Tenant**
   - Browse to: **Entra ID > Overview > Manage tenants**
   - Click **Create**
   - Select **External** and click **Continue**

3. **Configure Basics**
   - **Tenant Name:** Enter a descriptive name (e.g., "Apicurio Registry Users")
   - **Domain Name:** Choose a unique domain (e.g., "apicurioregistry") - this becomes
     `apicurioregistry.onmicrosoft.com`
   - **Country/Region:** Select your region (cannot be changed later)
   - Click **Review + Create**

4. **Complete Setup**
   - Review the configuration
   - Click **Create**
   - Wait for tenant creation (typically 1-2 minutes)

**Sources:**
- [Create an External Tenant](https://learn.microsoft.com/en-us/entra/external-id/customers/how-to-create-external-tenant-portal)
- [Quickstart: Tenant Setup](https://learn.microsoft.com/en-us/entra/external-id/customers/quickstart-tenant-setup)

### 4.2 Register Application in Entra ID

**IMPORTANT - Single App Registration Approach:**

We use a **single app registration** for both the API and UI components. This is the recommended approach
because:
- App roles defined in the app registration are automatically included in JWT tokens
- Simpler configuration and management
- Common pattern for SPAs calling their own backend API
- Avoids token audience mismatch issues

**Why Not Separate Apps?**

If you use separate app registrations for API and UI:
- UI gets tokens with `aud` = UI client ID
- API expects tokens with `aud` = API client ID
- App roles from API registration won't appear in UI tokens
- Complex workaround needed using API permissions and scopes

NOTE: The "aud" mismatch issue is low risk because Quarkus (by default) does not check "aud" when validating a JWT.

**Steps for Application Registration:**

1. **Navigate to App Registrations**
   - In your External tenant, browse to: **Entra ID > App registrations**
   - Click **New registration**

2. **Configure Registration**
   - **Name:** `apicurio-registry`
   - **Supported account types:** Select "Accounts in this organizational directory only"
   - **Redirect URI:**
     - Platform: **Single-page application**
     - URI: `http://localhost:8080` (or your actual UI URL)
   - Click **Register**

3. **Note the Application Details**
   - Copy the **Application (client) ID** - you'll use this for BOTH `QUARKUS_OIDC_CLIENT_ID` and
     `REGISTRY_AUTH_CLIENT_ID`
   - Copy the **Directory (tenant) ID**

4. **Configure Authentication**
   - Navigate to: **Authentication**
   - Verify redirect URI is configured as **Single-page application** platform
   - Under **Settings > Implicit grant and hybrid flows**, enable:
     - ☑ Access tokens
     - ☑ ID tokens
   - Click **Save**

5. **Create Client Secret**
   - Navigate to: **Certificates & secrets > Client secrets**
   - Click **New client secret**
   - **Description:** `apicurio-registry-secret`
   - **Expires:** Choose appropriate expiration (24 months max)
   - Click **Add**
   - **IMPORTANT:** Copy the secret **Value** immediately (it won't be shown again)

6. **Expose an API Scope** (Required for Azure AD)
   - Navigate to: **Expose an API**
   - Click **Add a scope**
   - **Application ID URI:** (keep the default)
   - Click **Save and continue**
   - **Scope name:** `access_as_user`
   - **Who can consent:** Admins and users
   - **Admin consent display name:** `Access Apicurio Registry as a user`
   - **Admin consent description:** `Allows the app to access Apicurio Registry on behalf of the signed-in user`
   - **User consent display name:** `Access Apicurio Registry`
   - **User consent description:** `Allows the app to access Apicurio Registry on your behalf`
   - **State:** Enabled
   - Click **Add scope**
   - Note the full scope value: `api://{id}/access_as_user`

   **Why this is required:** When using Azure AD with app roles, you must request a custom API scope instead of
   just the default OIDC scopes. This ensures the JWT token has the correct audience (`aud`) set to your
   application's client ID, which allows app roles to be included in the token. Without this scope, the token
   will have a Microsoft Graph audience and won't contain your custom app roles.

7. **Get OIDC Endpoints**
   - Navigate to: **Overview > Endpoints**
   - Copy the **OpenID Connect metadata document** URL:
     ```
     https://login.microsoftonline.com/{tenant-id}/v2.0/.well-known/openid-configuration
     ```
   - The auth server URL for both API and UI is:
     ```
     https://login.microsoftonline.com/{tenant-id}/v2.0
     ```

**Sources:**
- [How to Register an App in Entra ID](https://learn.microsoft.com/en-us/entra/identity-platform/quickstart-register-app)
- [Add and Manage App Credentials](https://learn.microsoft.com/en-us/entra/identity-platform/how-to-add-credentials)
- [OIDC Endpoints Discovery](https://learn.microsoft.com/en-us/entra/identity-platform/v2-protocols-oidc)
- [Expose a Web API](https://learn.microsoft.com/en-us/entra/identity-platform/quickstart-configure-app-expose-web-apis)

### 4.3 Configure User Management

**Authentication Method Configuration:**

1. **Create User Flow**
   - Browse to: **Entra ID > External Identities > Configure user flows**
   - Click **New user flow**
   - **Name:** `apicurio-signin-signup`
   - **Identity providers:** Select authentication methods:
     - **Email with password** (recommended for most cases)
     - **Email with one-time passcode** (alternative)
     - **Social accounts** (Google, Facebook, Apple) - optional

2. **Configure User Attributes**
   - Select which attributes to collect during signup:
     - Email Address (required)
     - Display Name (recommended)
     - Given Name, Surname (optional)

**Creating Test Users:**

**Option 1: Self-Service Registration (Recommended)**
- Users register themselves through the application
- Navigate to your application's login page
- Users will see "Sign up" option
- After signup, user objects are created in the tenant

**Option 2: Admin-Created Users**
- Browse to: **Entra ID > Users > All users**
- Click **New user > Create new user**
- **User principal name:** e.g., `testuser@apicurioregistry.onmicrosoft.com`
- **Display name:** User's display name
- **Password:** Set initial password
- Click **Create**

**IMPORTANT LIMITATION:** Users created via the portal or Graph API cannot directly set passwords through
email flows. They must be created through user flows for full self-service experience.

**Sources:**
- [Identity Providers for External Tenants](https://learn.microsoft.com/en-us/entra/external-id/customers/concept-authentication-methods-customers)
- [Create a User Flow](https://learn.microsoft.com/en-us/entra/external-id/customers/how-to-user-flow-sign-up-sign-in-customers)

### 4.4 Configure App Roles for Authorization

**Creating App Roles:**

1. **Navigate to App Roles**
   - Go to your **API application** registration (in the **App registrations** section)
   - Browse to: **App roles** then click **Create app role**

2. **Create sr-admin Role**
   - **Display name:** `sr-admin`
   - **Allowed member types:** **Both (Users/Groups + Applications)**
   - **Value:** `sr-admin` (this exact value appears in the JWT)
   - **Description:** `Apicurio Registry Administrator - Full access`
   - **Enable this app role:** Checked
   - Click **Apply**

3. **Create sr-developer Role**
   - **Display name:** `sr-developer`
   - **Allowed member types:** **Both (Users/Groups + Applications)**
   - **Value:** `sr-developer`
   - **Description:** `Apicurio Registry Developer - Create, read, update, delete artifacts`
   - **Enable this app role:** Checked
   - Click **Apply**

4. **Create sr-readonly Role**
   - **Display name:** `sr-readonly`
   - **Allowed member types:** **Both (Users/Groups + Applications)**
   - **Value:** `sr-readonly`
   - **Description:** `Apicurio Registry Read-Only - View artifacts only`
   - **Enable this app role:** Checked
   - Click **Apply**

**IMPORTANT:** Setting "Allowed member types" to **Both** enables the roles to be used for:
- **Users/Groups:** For interactive user authentication (Authorization Code Flow)
- **Applications:** For service account authentication (Client Credentials Grant)

If you only select "Users/Groups", the roles won't be available as Application permissions for service accounts.

**Assigning Roles to Users:**

1. **Navigate to Enterprise Applications**
   - Browse to: **Entra ID > Enterprise applications**
   - Find and select `apicurio-registry`

2. **Assign Users and Roles**
   - Navigate to: **Users and groups**
   - Click **Add user/group**
   - **Users:** Select a user
   - **Select a role:** Choose one of your app roles (sr-admin, sr-developer, sr-readonly)
   - Click **Assign**

NOTE: it may be the case that users must login for the first time before they appear in the list of users when assigning roles.

3. **Verify Role Assignment**
   - The user should now appear in the "Users and groups" list with their assigned role
   - When this user authenticates, the `roles` claim in their JWT will contain the assigned role

**Sources:**
- [Add App Roles and Get Them from a Token](https://learn.microsoft.com/en-us/entra/identity-platform/howto-add-app-roles-in-apps)
- [Using Role-Based Access Control for Apps](https://learn.microsoft.com/en-us/entra/external-id/customers/how-to-use-app-roles-customers)
- [Manage Users and Groups Assignment](https://learn.microsoft.com/en-us/entra/identity/enterprise-apps/assign-user-or-group-access-portal)

---

## 5. Role Configuration and JWT Claims

### 5.1 Understanding the Roles Claim

When a user with assigned app roles authenticates, Microsoft Entra ID includes those roles in the JWT token:

**ID Token Example:**
```json
{
  "aud": "a1b2c3d4-...",
  "iss": "https://login.microsoftonline.com/{tenant-id}/v2.0",
  "sub": "user-object-id",
  "name": "Test User",
  "preferred_username": "testuser@apicurioregistry.onmicrosoft.com",
  "roles": [
    "sr-admin"
  ]
}
```

**Access Token Example:**
```json
{
  "aud": "api://apicurio-registry",
  "iss": "https://login.microsoftonline.com/{tenant-id}/v2.0",
  "sub": "user-object-id",
  "roles": [
    "sr-admin",
    "sr-developer"
  ],
  "scp": "user_impersonation"
}
```

### 5.2 Critical Configuration for Apicurio Registry

**MOST IMPORTANT:** When using Azure AD/Entra ID with Apicurio Registry, you MUST set:

```
QUARKUS_OIDC_ROLES_ROLE_CLAIM_PATH=roles
```

**Why?** By default, Quarkus may look for roles in nested claims (like Keycloak's `realm_access.roles`). This
configuration tells Quarkus to look for roles in the top-level `roles` claim where Entra ID puts them.

### 5.3 Role Claim Customization (Advanced)

If you need to customize the claim name:

1. **Navigate to Token Configuration**
   - Go to your application registration
   - Browse to: **Token configuration**
   - Click **Add optional claim**

2. **Configure Role Claim**
   - **Token type:** ID, Access (select both)
   - Select the claims you want to include
   - For roles, the default `roles` claim is already configured when you create app roles

3. **Custom Claim Mapping (If Needed)**
   - For advanced scenarios, you can use the **Attributes & Claims** section in Enterprise Applications
   - Typically not needed for Apicurio Registry

### 5.4 Testing JWT Tokens

**Using jwt.ms for Verification:**

1. **Get a Token**
   - Authenticate to your application
   - Capture the JWT token (use browser developer tools, network tab)

2. **Decode and Verify**
   - Navigate to [https://jwt.ms](https://jwt.ms)
   - Paste your token
   - Verify:
     - `roles` claim is present
     - Role values match your app roles (sr-admin, etc.)
     - `aud` (audience) matches your client ID

**Sources:**
- [Configure Group Claims and App Roles in Tokens](https://learn.microsoft.com/en-us/security/zero-trust/develop/configure-tokens-group-claims-app-roles)
- [Customize JWT Claims](https://learn.microsoft.com/en-us/entra/identity-platform/jwt-claims-customization)
- [Testing Entra ID OIDC Apps with JWT.ms](https://c7solutions.com/2024/11/testing-entra-id-saas-oidc-apps-with-jwt-ms)

---

## 6. Environment Variables and Configuration

### 6.1 Complete Environment Variable Reference

**For Apicurio Registry with Entra ID External Tenant:**

```bash
# ======================
# OIDC Authentication
# ======================

# Enable OIDC
QUARKUS_OIDC_TENANT_ENABLED=true

# Entra ID Auth Server URL (v2.0 endpoint)
# Format: https://login.microsoftonline.com/{tenant-id}/v2.0
QUARKUS_OIDC_AUTH_SERVER_URL=https://login.microsoftonline.com/12345678-1234-1234-1234-123456789abc/v2.0

# API Client ID (from app registration)
QUARKUS_OIDC_CLIENT_ID=a1b2c3d4-e5f6-7890-abcd-ef1234567890

# Client Secret (from app registration)
QUARKUS_OIDC_CREDENTIALS_SECRET=your-client-secret-value-here

# CRITICAL: Configure role claim path for Entra ID
QUARKUS_OIDC_ROLES_ROLE_CLAIM_PATH=roles

# ======================
# UI Authentication
# ======================

# Set UI auth type to OIDC
APICURIO_AUTH_TYPE=oidc

# OIDC Auth URL (same as auth server URL)
APICURIO_AUTH_URL=https://login.microsoftonline.com/12345678-1234-1234-1234-123456789abc/v2.0

# UI Client ID (can be same as API or separate)
APICURIO_AUTH_CLIENT_ID=a1b2c3d4-e5f6-7890-abcd-ef1234567890

# Redirect URL after authentication
APICURIO_AUTH_REDIRECT_URL=http://localhost:8080

# CRITICAL: Specify OIDC scopes including custom API scope
# This ensures app roles are included in the JWT token
REGISTRY_AUTH_CLIENT_SCOPES=openid profile email api://{client-id}/access_as_user

# CRITICAL: Disable userinfo endpoint call (Azure AD incompatibility)
# Azure AD's userinfo endpoint requires a Graph token, but our API-scoped token
# has a different audience, causing authentication to fail
REGISTRY_AUTH_LOAD_USER_INFO=false

# ======================
# Role Configuration
# ======================

# Role source (use token roles)
APICURIO_AUTH_ROLE_SOURCE=token

# Default role names (must match app roles in Entra ID)
APICURIO_AUTH_ROLES_ADMIN=sr-admin
APICURIO_AUTH_ROLES_DEVELOPER=sr-developer
APICURIO_AUTH_ROLES_READONLY=sr-readonly

# Optional: Admin override (assign admin to specific user)
APICURIO_AUTH_ADMIN_OVERRIDE_ENABLED=false
APICURIO_AUTH_ADMIN_OVERRIDE_FROM=token
APICURIO_AUTH_ADMIN_OVERRIDE_TYPE=sub
APICURIO_AUTH_ADMIN_OVERRIDE_CLAIM=sub
APICURIO_AUTH_ADMIN_OVERRIDE_ROLE=sr-admin

# ======================
# Database (example with PostgreSQL)
# ======================
APICURIO_DATASOURCE_URL=jdbc:postgresql://postgres:5432/apicurio
APICURIO_DATASOURCE_USERNAME=apicurio
APICURIO_DATASOURCE_PASSWORD=password
```

**Sources:**
- [Apicurio Registry 3.0 Migration Guide](https://www.apicur.io/blog/2025/04/02/application-configuration-migration)
- [Securing Apicurio Registry with Azure Entra ID](https://www.apicur.io/blog/2023/07/13/registry-azure-ad)
- [Quarkus OIDC Configuration](https://quarkus.io/guides/security-oidc-configuration-properties-reference)

---

## 7. Machine-to-Machine Authentication (Client Credentials Grant)

For automation scenarios, CI/CD pipelines, and service-to-service integration, you need machine-to-machine (M2M)
authentication without user interaction. Azure Entra ID supports this through the OAuth 2.0 Client Credentials Grant
flow.

### 7.1 Overview of Client Credentials Flow

**What is Client Credentials Grant?**

Client Credentials Grant is an OAuth 2.0 flow where an application authenticates directly with the authorization
server using its own credentials (client ID and secret), rather than on behalf of a user. This is ideal for:
- Automated scripts and tools
- CI/CD pipelines
- Service-to-service communication
- Background processes
- API integrations

**Key Differences from User Authentication:**

| Aspect | User Authentication (Auth Code Flow) | Service Authentication (Client Credentials) |
|--------|--------------------------------------|-------------------------------------------|
| Flow Type | Authorization Code Flow | Client Credentials Grant |
| User Context | Yes - acts on behalf of a user | No - acts as the application itself |
| Token Claims | `sub` (user ID), `preferred_username` | `oid` (service principal object ID) |
| Permissions Type | Delegated Permissions | Application Permissions (App Roles) |
| Interactive Login | Required | Not required |
| Scope Format | `openid profile email api://...` | `api://{client-id}/.default` |

### 7.2 Azure Entra ID Configuration

**Step 1: Create App Registration for Service Account**

**IMPORTANT:** Create a **separate app registration** specifically for service account authentication. This is the
recommended approach for production environments because it:
- Provides better security isolation between user and service authentication
- Allows independent credential rotation and lifecycle management
- Enables granular audit logging of service account activities
- Prevents credential exposure through client-side applications

**Creating the Service Account App Registration:**

1. **Navigate to App Registrations**
   - In your External tenant: **Entra ID > App registrations**
   - Click **New registration**

2. **Configure Registration**
   - **Name:** `apicurio-registry-service-account`
   - **Supported account types:** "Accounts in this organizational directory only"
   - **Redirect URI:** Leave blank (not needed for client credentials flow)
   - Click **Register**

3. **Note the Application Details**
   - Copy the **Application (client) ID** - this is your service account client ID
   - Copy the **Directory (tenant) ID**
   - Keep these values secure - they will be used in automation scripts

4. **Create Client Secret**
   - Navigate to: **Certificates & secrets > Client secrets**
   - Click **New client secret**
   - **Description:** `service-account-secret`
   - **Expires:** Choose appropriate expiration (24 months maximum)
   - Click **Add**
   - **CRITICAL:** Copy the secret **Value** immediately - it won't be shown again
   - Store the secret securely (use Azure Key Vault, HashiCorp Vault, or similar for production)

**Step 2: Configure API Permissions (App Roles)**

For client credentials flow, you must use **Application Permissions** (app roles), not delegated permissions.

**TROUBLESHOOTING:** If "Application permissions" is greyed out or shows no roles, your app roles were created with
"Allowed member types" set to only "Users/Groups". You need to update them to "Both (Users/Groups + Applications)".

To fix existing app roles:
1. Go to your main Apicurio Registry app registration
2. Navigate to **App roles**
3. Click on each role (sr-admin, sr-developer, sr-readonly)
4. Click **Edit**
5. Change **Allowed member types** to **Both**
6. Click **Apply**
7. Return to your service account app registration and continue with the steps below

**Configuring API Permissions:**

1. **Navigate to API Permissions**
   - In your service account app registration: **API permissions**
   - Click **Add a permission**

2. **Add Permission to Your API**
   - Click **My APIs** tab or **APIs my organization uses** (apicurio-registry will be in one or the other)
   - Select your main Apicurio Registry app registration (the one with the app roles)
   - Select **Application permissions**
   - You should now see the app roles (sr-admin, sr-developer, sr-readonly)
   - Check the roles you want to grant (e.g., `sr-admin`, `sr-developer`)
   - Click **Add permissions**

3. **Grant Admin Consent** (CRITICAL)
   - Back in the **API permissions** page
   - Click **Grant admin consent for [Your Tenant]**
   - Confirm by clicking **Yes**
   - Verify all permissions show "Granted for [Your Tenant]" status

**Why Admin Consent is Required:**

Application permissions always require admin consent because they grant the application direct access to resources
without user interaction. This is a security measure to prevent unauthorized service accounts.

**Step 3: Verify App Role Assignment**

After granting consent, the service principal is automatically created and the app roles are assigned to it.

To verify:
1. Navigate to: **Enterprise applications**
2. Find your main Apicurio Registry app
3. Go to: **Users and groups**
4. You should see your service account app listed with the assigned roles

### 7.3 Obtaining Access Tokens

**Using curl to Get Token:**

```bash
# Set your values
TENANT_ID="your-tenant-id"
CLIENT_ID="your-service-account-client-id"
CLIENT_SECRET="your-service-account-secret"
API_CLIENT_ID="your-main-apicurio-app-client-id"

# Request token using client credentials
curl -X POST "https://login.microsoftonline.com/${TENANT_ID}/oauth2/v2.0/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=${CLIENT_ID}" \
  -d "client_secret=${CLIENT_SECRET}" \
  -d "scope=api://${API_CLIENT_ID}/.default" \
  -d "grant_type=client_credentials"
```

**Understanding the `.default` Scope:**

- For client credentials, you **must** use `api://{client-id}/.default`
- The `.default` suffix tells Azure AD to include all app roles that have been granted to the service principal
- You **cannot** use specific scopes like `api://{client-id}/access_as_user` in client credentials flow
- The resulting token will have a `roles` claim containing all assigned app roles

**Example Token Response:**

```json
{
  "token_type": "Bearer",
  "expires_in": 3599,
  "ext_expires_in": 3599,
  "access_token": "eyJ0eXAiOiJKV1QiLCJhbGc..."
}
```

**Example Decoded JWT:**

```json
{
  "aud": "api://a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "iss": "https://login.microsoftonline.com/{tenant-id}/v2.0",
  "iat": 1234567890,
  "nbf": 1234567890,
  "exp": 1234571490,
  "aio": "...",
  "appid": "service-account-client-id",
  "appidacr": "1",
  "idp": "https://sts.windows.net/{tenant-id}/",
  "oid": "service-principal-object-id",
  "roles": [
    "sr-admin"
  ],
  "sub": "service-principal-object-id",
  "tid": "{tenant-id}",
  "uti": "...",
  "ver": "2.0"
}
```

**Key Claims:**
- `aud`: Your API's client ID (same as user tokens)
- `roles`: App roles assigned to the service principal
- `oid`/`sub`: Service principal's object ID (not a user ID)
- `appid`: The service account's client ID
- No `scp` claim (scopes are only for delegated permissions)

### 7.4 Using Tokens with Apicurio Registry API

**Example: List Artifacts**

```bash
# Extract access token from previous response
ACCESS_TOKEN="eyJ0eXAiOiJKV1QiLCJhbGc..."

# Call Apicurio Registry API
curl -X GET "http://localhost:8081/apis/registry/v3/search/artifacts" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json"
```

**Example: Create New Artifact**

```bash
curl -X POST "http://localhost:8081/apis/registry/v3/groups/default/artifacts" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -H "X-Registry-ArtifactId: my-schema" \
  -H "X-Registry-ArtifactType: AVRO" \
  -d '{
    "schema": "{\"type\":\"record\",\"name\":\"User\",\"fields\":[{\"name\":\"id\",\"type\":\"int\"}]}"
  }'
```

**Example: Complete Script**

```bash
#!/bin/bash

# Configuration
TENANT_ID="tenant-id"
CLIENT_ID="service-account-client-id"
CLIENT_SECRET="service-account-secret"
API_CLIENT_ID="apicurio-registry-app-client-id"
REGISTRY_URL="http://localhost:8081"

# Get access token
TOKEN_RESPONSE=$(curl -s -X POST \
  "https://login.microsoftonline.com/${TENANT_ID}/oauth2/v2.0/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=${CLIENT_ID}" \
  -d "client_secret=${CLIENT_SECRET}" \
  -d "scope=api://${API_CLIENT_ID}/.default" \
  -d "grant_type=client_credentials")

# Extract access token (requires jq)
ACCESS_TOKEN=$(echo "$TOKEN_RESPONSE" | jq -r '.access_token')

# Verify token was obtained
if [ "$ACCESS_TOKEN" == "null" ] || [ -z "$ACCESS_TOKEN" ]; then
  echo "Failed to obtain access token"
  echo "$TOKEN_RESPONSE"
  exit 1
fi

echo "Access token obtained successfully"

# Call Apicurio Registry API
curl -X GET "${REGISTRY_URL}/apis/registry/v3/groups/default/artifacts" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json"
```

### 7.5 Apicurio Registry Configuration

**No Special Configuration Required**

Apicurio Registry (Quarkus OIDC) automatically supports both user tokens and service account tokens from the same
OIDC provider. The existing configuration works for both:

```bash
# These settings support both user and service account authentication
QUARKUS_OIDC_TENANT_ENABLED=true
QUARKUS_OIDC_AUTH_SERVER_URL=https://login.microsoftonline.com/{tenant-id}/v2.0
QUARKUS_OIDC_CLIENT_ID={api-client-id}
QUARKUS_OIDC_CREDENTIALS_SECRET={client-secret}
QUARKUS_OIDC_ROLES_ROLE_CLAIM_PATH=roles

# Role-based authorization works the same way
APICURIO_AUTH_ROLE_SOURCE=token
APICURIO_AUTH_ROLES_ADMIN=sr-admin
APICURIO_AUTH_ROLES_DEVELOPER=sr-developer
APICURIO_AUTH_ROLES_READONLY=sr-readonly
```

**How Quarkus Handles Both Token Types:**

- Quarkus OIDC validates tokens from the same issuer (`iss` claim)
- Extracts roles from the `roles` claim (configured via `QUARKUS_OIDC_ROLES_ROLE_CLAIM_PATH`)
- Applies the same role-based authorization rules
- Doesn't distinguish between user and service principal identities for authorization

### 7.6 Testing and Troubleshooting

**Test 1: Verify Token Issuance**

```bash
# Get token and decode it
curl -X POST "https://login.microsoftonline.com/${TENANT_ID}/oauth2/v2.0/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=${CLIENT_ID}" \
  -d "client_secret=${CLIENT_SECRET}" \
  -d "scope=api://${API_CLIENT_ID}/.default" \
  -d "grant_type=client_credentials" | jq -r '.access_token' | \
  cut -d'.' -f2 | base64 -d 2>/dev/null | jq .
```

**Test 2: Verify Roles in Token**

```bash
# Extract and check roles claim
ACCESS_TOKEN=$(curl -s -X POST \
  "https://login.microsoftonline.com/${TENANT_ID}/oauth2/v2.0/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=${CLIENT_ID}" \
  -d "client_secret=${CLIENT_SECRET}" \
  -d "scope=api://${API_CLIENT_ID}/.default" \
  -d "grant_type=client_credentials" | jq -r '.access_token')

# Decode and check roles
echo "$ACCESS_TOKEN" | cut -d'.' -f2 | base64 -d 2>/dev/null | jq '.roles'
```

Expected output:
```json
[
  "sr-admin"
]
```

**Test 3: Verify API Access**

```bash
# Test with valid token
curl -v -X GET "http://localhost:8081/apis/registry/v3/groups/default/artifacts" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"

# Expected: 200 OK with artifact list

# Test without token
curl -v -X GET "http://localhost:8081/apis/registry/v3/groups/default/artifacts"

# Expected: 401 Unauthorized
```

**Common Issues:**

1. **"Invalid client" error**
   - Verify client ID and secret are correct
   - Check that secret hasn't expired

2. **"Invalid scope" error**
   - Ensure you're using `api://{client-id}/.default` format
   - Cannot use specific scopes with client credentials

3. **Empty roles claim**
   - Verify admin consent was granted for API permissions
   - Check that app roles are assigned to the service principal
   - Navigate to Enterprise applications and verify role assignment

4. **401 Unauthorized from Apicurio**
   - Verify token audience (`aud`) matches `QUARKUS_OIDC_CLIENT_ID`
   - Check that `QUARKUS_OIDC_ROLES_ROLE_CLAIM_PATH=roles` is set
   - Verify roles in token match configured role names

**Sources:**
- [OAuth 2.0 client credentials flow on Microsoft identity platform](https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-client-creds-grant-flow)
- [Register a Microsoft Entra app and create a service principal](https://learn.microsoft.com/en-us/entra/identity-platform/howto-create-service-principal-portal)
- [Scopes and permissions in the Microsoft identity platform](https://learn.microsoft.com/en-us/entra/identity-platform/scopes-oidc)
- [Quarkus OIDC Bearer token authentication](https://quarkus.io/guides/security-oidc-bearer-token-authentication)
- [Client authentication best practices on connecting to the registry](https://github.com/Apicurio/apicurio-registry/discussions/2824)

---

## 8. Common Issues and Solutions

### 8.1 Issue: 401 Unauthorized Despite Valid Token

**Symptoms:**
- User can authenticate successfully
- JWT token contains roles
- API returns 401 Unauthorized

**Root Cause:**
Missing or incorrect `QUARKUS_OIDC_ROLES_ROLE_CLAIM_PATH` configuration

**Solution:**
```bash
QUARKUS_OIDC_ROLES_ROLE_CLAIM_PATH=roles
```

Without this, Quarkus looks for roles in `realm_access.roles` (Keycloak format) instead of the top-level
`roles` claim (Entra ID format).

**Source:**
[OIDC 401 Error with Keycloak or Azure AD](https://github.com/Apicurio/apicurio-registry/issues/5478)

### 8.2 Issue: Roles Not Appearing in JWT Token

**Symptoms:**
- User has been assigned app roles in Entra ID
- JWT token doesn't contain `roles` claim

**Possible Causes & Solutions:**

1. **User Not Assigned to Enterprise Application**
   - Go to: **Enterprise applications > apicurio-registry > Users and groups**
   - Verify user is listed with a role assignment
   - If not, click **Add user/group** and assign

2. **Token Type Mismatch**
   - App roles appear in **access tokens** for APIs
   - May not appear in **ID tokens** unless explicitly configured
   - Use access tokens for API authorization

3. **Group Overage Limit**
   - If user is in more than 200 groups, roles may be omitted
   - Solution: Use app roles instead of groups, or query Microsoft Graph API

### 8.3 Issue: Cannot Create Users via Portal/API

**Symptoms:**
- Users created in portal or via Graph API cannot set passwords
- Users cannot complete authentication flow

**Root Cause:**
Entra External ID limitation - users created via API/portal don't receive activation emails

**Solution:**
Use self-service user flows for user registration:
1. Create a user flow with email/password or email/OTP
2. Users register through the application's sign-up page
3. Users complete registration and set their own passwords

**Source:**
[Entra External ID User Creation Limitation](https://learn.microsoft.com/en-us/answers/questions/2142157/entra-external-id-(external-tenant)-how-to-send-an)

### 8.4 Issue: Client Secret Expiration

**Symptoms:**
- Authentication suddenly stops working
- Error: "invalid_client" or "expired credentials"

**Root Cause:**
Client secrets in Entra ID expire (max 24 months)

**Solution:**
1. Create a new client secret before the old one expires
2. Update the `QUARKUS_OIDC_CREDENTIALS_SECRET` environment variable
3. Restart Apicurio Registry
4. Delete the old secret after verifying the new one works

**Best Practice:**
- Use certificate-based authentication for production instead of client secrets
- Set calendar reminders for secret expiration dates
- Consider using Azure Key Vault for secret rotation

**Source:**
[Add and Manage App Credentials](https://learn.microsoft.com/en-us/entra/identity-platform/how-to-add-credentials)

### 8.5 Issue: CORS Errors in UI

**Symptoms:**
- UI loads but authentication fails
- Browser console shows CORS errors

**Root Cause:**
Redirect URI not properly configured in Entra ID

**Solution:**
1. Go to app registration: **Authentication > Platform configurations**
2. Verify redirect URI exactly matches your application URL
3. For SPA: Use "Single-page application" platform type
4. Enable "Access tokens" and "ID tokens" under Implicit grant

### 8.6 Issue: UserInfo Endpoint "Invalid Audience" Error

**Symptoms:**
- Authentication redirects successfully from Azure AD
- Browser console shows error: "Invalid Authentication Token. Invalid audience"
- Error occurs when calling `https://graph.microsoft.com/oidc/userinfo`
- Login flow fails to complete

**Root Cause:**
Apicurio Registry UI uses oidc-client-ts library with `loadUserInfo: true` by default, which causes it to call
the userinfo endpoint. Azure AD's userinfo endpoint (`https://graph.microsoft.com/oidc/userinfo`) is part of
Microsoft Graph and requires a token with `aud: 00000003-0000-0000-c000-000000000000` (Microsoft Graph API).
However, when requesting the custom API scope `api://{client-id}/access_as_user` to get app roles in the token,
Azure AD issues a token with `aud: {client-id}`, causing an audience mismatch.

**Solution:**
Add the `REGISTRY_AUTH_LOAD_USER_INFO` environment variable to disable the userinfo endpoint call:

```bash
REGISTRY_AUTH_LOAD_USER_INFO=false
```

**Why this works:**
- The oidc-client-ts library's `loadUserInfo` setting controls whether it calls the userinfo endpoint
- With `loadUserInfo: false`, the library uses claims from the ID token instead
- The ID token already contains all necessary user information including app roles
- This is Microsoft's recommended approach (they suggest using ID token claims over userinfo endpoint)

NOTE: the configurable `loadUserInfo` feature was introduced in Apicurio Registry 3.1.6.

**Complete Working Configuration:**
```bash
# UI Configuration
REGISTRY_AUTH_TYPE=oidc
REGISTRY_AUTH_URL=https://login.microsoftonline.com/{tenant-id}/v2.0
REGISTRY_AUTH_CLIENT_ID={client-id}
REGISTRY_AUTH_REDIRECT_URL=http://localhost:8080
REGISTRY_AUTH_CLIENT_SCOPES=openid profile email api://{client-id}/access_as_user
REGISTRY_AUTH_LOAD_USER_INFO=false  # Critical for Azure AD
```

**Sources:**
- [Microsoft identity platform UserInfo endpoint](https://learn.microsoft.com/en-us/entra/identity-platform/userinfo)
- [oidc-client-ts loadUserInfo documentation](https://authts.github.io/oidc-client-ts/interfaces/UserManagerSettings.html)
- [Apicurio Registry Issue #7051](https://github.com/Apicurio/apicurio-registry/issues/7051)

---

## 9. Sources and References

### Official Microsoft Documentation

1. [Microsoft Entra External ID Overview](https://learn.microsoft.com/en-us/entra/external-id/external-identities-overview)
2. [Create an External Tenant](https://learn.microsoft.com/en-us/entra/external-id/customers/how-to-create-external-tenant-portal)
3. [OpenID Connect on Microsoft Identity Platform](https://learn.microsoft.com/en-us/entra/identity-platform/v2-protocols-oidc)
4. [Add App Roles and Get Them from a Token](https://learn.microsoft.com/en-us/entra/identity-platform/howto-add-app-roles-in-apps)
5. [Configure Group Claims and App Roles in Tokens](https://learn.microsoft.com/en-us/security/zero-trust/develop/configure-tokens-group-claims-app-roles)
6. [Customize JWT Claims](https://learn.microsoft.com/en-us/entra/identity-platform/jwt-claims-customization)
7. [Access Token Claims Reference](https://learn.microsoft.com/en-us/entra/identity-platform/access-token-claims-reference)
8. [Using Role-Based Access Control for Apps](https://learn.microsoft.com/en-us/entra/external-id/customers/how-to-use-app-roles-customers)
9. [Identity Providers for External Tenants](https://learn.microsoft.com/en-us/entra/external-id/customers/concept-authentication-methods-customers)
10. [Add and Manage App Credentials](https://learn.microsoft.com/en-us/entra/identity-platform/how-to-add-credentials)

### Apicurio Registry Documentation

11. [Apicurio Registry 3.0.x Security Configuration](https://www.apicur.io/registry/docs/apicurio-registry/3.0.x/getting-started/assembly-configuring-registry-security.html)
12. [Apicurio Registry Configuration Reference](https://www.apicur.io/registry/docs/apicurio-registry/3.0.x/getting-started/assembly-config-reference.html)
13. [Apicurio Registry 3.0 Migration Guide](https://www.apicur.io/blog/2025/04/02/application-configuration-migration)
14. [Securing Apicurio Registry with Azure Entra ID](https://www.apicur.io/blog/2023/07/13/registry-azure-ad)
15. [Securing Apicurio Registry with Keycloak](https://www.apicur.io/blog/2021/05/28/registry-security)

### Community Resources and Blog Posts

16. [Testing Entra ID OIDC Apps with JWT.ms](https://c7solutions.com/2024/11/testing-entra-id-saas-oidc-apps-with-jwt-ms)
17. [Configure Azure Entra ID as IdP on Keycloak](https://blog.ght1pc9kc.fr/en/2023/configure-azure-entra-id-as-idp-on-keycloak/)
18. [Using Keycloak with Azure AD](https://techcommunity.microsoft.com/blog/azuredevcommunityblog/using-keycloak-with-azure-ad-to-integrate-aks-cluster-authentication-process/4174238)
19. [How to Implement OIDC with Microsoft Entra ID](https://supertokens.com/blog/how-to-implement-oidc-with-microsoft-entra-id)

### GitHub Resources

20. [Apicurio Registry GitHub Repository](https://github.com/Apicurio/apicurio-registry)
21. [OIDC 401 Error Issue #5478](https://github.com/Apicurio/apicurio-registry/issues/5478)
22. [Azure Entra ID UserInfo Endpoint Issue #7051](https://github.com/Apicurio/apicurio-registry/issues/7051)
23. [Apicurio Common UI Components](https://github.com/Apicurio/apicurio-common-ui-components)

### OIDC Client Library Resources

24. [oidc-client-ts Documentation](https://authts.github.io/oidc-client-ts/)
25. [oidc-client-ts UserManagerSettings](https://authts.github.io/oidc-client-ts/interfaces/UserManagerSettings.html)
