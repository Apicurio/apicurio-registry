# Security Policy

The Apicurio Registry community takes security seriously. We appreciate your efforts to responsibly disclose any security vulnerabilities you find.

## Supported Versions

| Version | Supported          |
|---------|--------------------|
| 3.1.x   | Yes                |
| 3.0.x   | Security fixes only|
| 2.x     | No                 |
| 1.x     | No                 |

## Reporting a Vulnerability

If you discover a security vulnerability in Apicurio Registry, please report it responsibly. **Do not open a public GitHub issue for security vulnerabilities.**

### How to report

Send an email to **[apicurio-registry-security@redhat.com](mailto:apicurio-registry-security@redhat.com)** with the following information:

- A description of the vulnerability
- Steps to reproduce the issue (include screenshots or proof-of-concept code if possible)
- Affected versions
- Any potential mitigations you have identified

### What to expect

- **Acknowledgment**: We will acknowledge receipt of your report within **3 business days**.
- **Assessment**: A maintainer will evaluate the report and may contact you for additional details.
- **Fix timeline**: We aim to provide a fix or mitigation for confirmed vulnerabilities within **30 days** of acknowledgment, depending on complexity.
- **Disclosure**: We will coordinate with you on public disclosure timing. We follow a responsible disclosure process and request that you do not disclose the vulnerability publicly until a fix is available.

### Credit

We are happy to credit reporters in release notes and security advisories unless you prefer to remain anonymous.

## Security Advisories

Published security advisories are available on the [GitHub Security Advisories page](https://github.com/Apicurio/apicurio-registry/security/advisories).

## Security Best Practices for Deployers

- Always run the latest supported version of Apicurio Registry.
- Enable authentication and authorization (OIDC) in production deployments.
- Use TLS for all network communication.
- Follow the principle of least privilege when configuring role-based access control.
- Review the [Apicurio Registry documentation](https://www.apicur.io/registry/docs/) for security configuration guidance.
