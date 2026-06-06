# Moka Git AI Commit

IntelliJ IDEA plugin that generates git commit messages using OpenAI API.

The plugin reads your staged changes, combines them with a prompt you write yourself,
and fills the commit message field — no opinionated defaults, no built-in prompts.

## Features

- One-click commit message generation from the commit dialog
- Fully user-defined prompt template (you decide the format, language, style)
- Works with any OpenAI-compatible endpoint (OpenAI, Azure OpenAI, local Ollama, etc.)
- API key stored in system keychain (not plain text)
- Configurable diff size limit to control token usage

## Requirements

- IntelliJ IDEA 2024.2 or later (Community or Ultimate)
- OpenAI API key (or compatible endpoint)
- JDK 21+ for building from source

## Installation

### From JetBrains Marketplace

1. Open IntelliJ IDEA
2. Go to Settings -> Plugins -> Marketplace
3. Search for "Moka Git AI Commit"
4. Install and restart

### From ZIP (manual)

1. Download the latest ZIP from Releases
2. Settings -> Plugins -> gear icon -> Install Plugin from Disk
3. Select the ZIP file and restart

## Configuration

1. Open Settings -> Tools -> Moka Git AI Commit
2. Enter your OpenAI API key
3. Write your prompt template in the text area

### Prompt Template

The prompt template is entirely yours. The plugin appends the git diff after your prompt.

Example:
```
Write a concise git commit message in Korean.
Use conventional commit format: type(scope): description
Keep it under 72 characters.

Changes:
```

You can instruct the model to use any language, format, or style you prefer.

### Custom Endpoint

To use Azure OpenAI or a local model (e.g. Ollama):
1. Set Base URL to your endpoint
2. Set Model to the model name your endpoint expects

## Usage

1. Stage your changes in git
2. Open the Commit dialog (Ctrl+K / Cmd+K)
3. Click **Generate AI Commit Message** or press Ctrl+Alt+C
4. Review and edit the generated message
5. Commit

## Building from Source

```bash
git clone https://github.com/icemokacat/moka-git-ai-commit
cd moka-git-ai-commit
./gradlew runIde          # Run in test IDE instance
./gradlew buildPlugin     # Build distributable ZIP
```

See `CLAUDE.md` for architecture notes and `.claude/skills/build.md` for build details.

## License

MIT
