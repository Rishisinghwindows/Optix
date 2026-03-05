# Contributing to Optix

Thank you for your interest in Optix! This project is currently **source-available** under an All Rights Reserved license. Contributions are welcome under the following guidelines.

## How to Contribute

### Reporting Bugs

1. Check existing [issues](https://github.com/Rishisinghwindows/Optix/issues) to avoid duplicates
2. Use the **Bug Report** issue template
3. Include:
   - Steps to reproduce
   - Expected vs actual behavior
   - Platform (Web/Android/iOS/Backend)
   - Screenshots or logs if applicable

### Suggesting Features

1. Open an issue using the **Feature Request** template
2. Describe the use case and expected behavior
3. Indicate which platform(s) it applies to

### Submitting Code

1. Fork the repository
2. Create a feature branch from `main`:
   ```bash
   git checkout -b feature/your-feature-name
   ```
3. Make your changes following the code style of the existing codebase
4. Test your changes locally
5. Commit with a clear message:
   ```bash
   git commit -m "Add: brief description of change"
   ```
6. Push and open a Pull Request against `main`

## Code Style

| Platform | Style |
|----------|-------|
| **Python (Backend)** | PEP 8, type hints encouraged, use `logging` (not `print`) |
| **JavaScript (React)** | ESLint defaults, functional components with hooks |
| **Kotlin (Android)** | Kotlin coding conventions, Compose best practices |
| **Swift (iOS)** | Swift API Design Guidelines, SwiftUI patterns |

## Development Setup

See [README.md](README.md) for setup instructions for each platform.

## Commit Message Format

```
<type>: <short description>

Types: Add, Fix, Update, Remove, Refactor, Docs, Test
```

## What NOT to Submit

- Changes to `.env` files or any secrets/credentials
- Unrelated formatting or linting changes
- Large refactors without prior discussion

## Questions?

Open an issue with the **Question** label or reach out to the repository owner.
