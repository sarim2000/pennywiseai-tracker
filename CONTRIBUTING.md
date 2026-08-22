# Contributing to PennyWise AI

Thank you for your interest in contributing to PennyWise AI! We welcome contributions from the community.

## How to Contribute

### 🐛 Reporting Bugs

1. Check if the bug has already been reported in [Issues](https://github.com/sarim2000/pennywiseai-tracker/issues)
2. If not, create a new issue using the bug report template
3. Include:
   - Device model and Android version
   - Steps to reproduce
   - Expected vs actual behavior
   - Screenshots if applicable
   - Bank name (if SMS parsing related)

### 💡 Suggesting Features

1. Check [existing issues](https://github.com/sarim2000/pennywiseai-tracker/issues) for similar suggestions
2. Create a new issue using the feature request template
3. Describe the problem it solves and how it would work

### 🏦 Adding Bank Support

To add support for a new bank:

1. Create a new parser class in `parser-core/src/main/kotlin/com/pennywiseai/parser/core/bank/`
2. Extend the right base class: `BaseIndianBankParser` for Indian banks, `UAEBankParser` for UAE banks, `BaseIranianBankParser` / `BaseThailandBankParser` for those markets, plain `BankParser` otherwise
3. Implement the required methods (`parse()` has a default implementation you only override when the bank needs custom parsing):
   ```kotlin
   override fun getBankName(): String
   override fun canHandle(sender: String): Boolean
   ```
4. Register your parser in `BankParserFactory` — registration order matters, so place it **above** any broader parser whose `canHandle()` could also match your sender IDs
5. Test with real SMS samples ([parser test conventions](docs/parser-test-standards.md))
6. Run `./scripts/update-supported-banks.sh` so the generated bank list stays in sync (CI fails otherwise)

Full walkthrough: [docs/adding-bank-parsers.md](docs/adding-bank-parsers.md)

### 💻 Code Contributions

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Make your changes
4. Run tests: `./gradlew test`
5. Check code style: `./gradlew lint`
6. Commit using conventional commits:
   - `feat:` New feature
   - `fix:` Bug fix
   - `docs:` Documentation changes
   - `style:` Code style changes
   - `refactor:` Code refactoring
   - `test:` Test additions/changes
   - `chore:` Build/dependency updates
7. Push to your fork
8. Open a Pull Request

### 📋 Pull Request Guidelines

- Keep PRs focused on a single feature or fix
- Include tests for new functionality
- Update documentation if needed
- Ensure all tests pass
- Follow existing code style and patterns
- Add screenshots for UI changes

## Development Setup

### Prerequisites

- Android Studio Ladybug or newer
- JDK 21
- Android SDK (installed through Android Studio; compileSdk/targetSdk come from Gradle)

### Building the Project

```bash
# Clone the repo
git clone https://github.com/sarim2000/pennywiseai-tracker.git
cd pennywiseai-tracker

# Build debug APK
./gradlew assembleDebug

# Run tests
./gradlew test

# Check code style
./gradlew lint
```

### Project Structure

PennyWise is a multi-module project (`settings.gradle.kts` includes `:app`, `:parser-core`, `:shared`, and `:iosApp`; the tree below also shows non-module directories):

```
app/                  # Android app (Compose UI, Room, Hilt)
└── src/main/java/com/pennywiseai/tracker/
    ├── data/             # Database, repositories, managers
    ├── domain/           # Use cases and services
    ├── presentation/     # Feature screens + ViewModels
    ├── ui/               # Shared UI components, theme, remaining screens
    ├── navigation/       # Navigation graph
    ├── widget/ worker/ receiver/   # Widgets, WorkManager jobs, SMS receiver
    └── billing/ backup/ core/ utils/
parser-core/          # Kotlin Multiplatform bank parsers (shared with iOS)
shared/               # Shared Kotlin code for the iOS app
iosApp/               # Swift iOS app
pennywise-web/        # Web deployment (Cloudflare Worker + Ktor server)
```

## Testing

- Test with real SMS messages from supported banks
- Test both light and dark themes
- Test on different screen sizes
- Verify offline functionality

## Community

- Join our [Discord](https://discord.gg/H3xWeMWjKQ) for discussions
- Follow development updates on [GitHub](https://github.com/sarim2000/pennywiseai-tracker)

## Code of Conduct

By participating, you agree to follow our [Code of Conduct](CODE_OF_CONDUCT.md).

Reporting concerns:
- Prefer a private report via Discord DM to a maintainer/moderator
- Or open a GitHub issue labeled `conduct` (maintainers will move details to a private channel)

## Recognition

All contributors will be recognized in our README following the [all-contributors](https://github.com/all-contributors/all-contributors) specification.

## Questions?

Feel free to:
- Open an issue for clarification
- Ask in our [Discord](https://discord.gg/H3xWeMWjKQ)
- Reach out to maintainers

Thank you for helping make PennyWise AI better! 🚀
