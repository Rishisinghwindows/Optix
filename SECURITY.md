# Security Policy

## Reporting a Vulnerability

If you discover a security vulnerability in Optix, please report it responsibly.

**Do NOT open a public issue for security vulnerabilities.**

Instead, please report via one of these methods:

1. **GitHub Private Vulnerability Reporting**: Use the [Security tab](https://github.com/Rishisinghwindows/Optix/security/advisories/new) to submit a private advisory
2. **Email**: Contact the repository owner directly through their GitHub profile

### What to Include

- Description of the vulnerability
- Steps to reproduce
- Potential impact
- Suggested fix (if any)

### Response Timeline

- **Acknowledgment**: Within 48 hours
- **Assessment**: Within 7 days
- **Fix**: Depends on severity, typically within 14 days for critical issues

## Supported Versions

| Version | Supported |
|---------|-----------|
| Latest (main branch) | Yes |
| Older versions | No |

## Scope

The following are in scope for security reports:

- Authentication bypass or token leakage
- SQL injection or NoSQL injection
- Cross-site scripting (XSS)
- Server-side request forgery (SSRF)
- Sensitive data exposure (API keys, credentials)
- Authorization flaws (accessing other users' data)
- Remote code execution

## Out of Scope

- Denial of service attacks
- Social engineering
- Issues in third-party dependencies (report to upstream)
- Issues requiring physical access to a device
