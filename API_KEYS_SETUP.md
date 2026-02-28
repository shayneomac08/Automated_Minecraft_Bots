# API Keys Setup

This project requires API keys for LLM integration. These are NOT included in the repository for security reasons.

## Setup Instructions

1. Create a file named `Automate_Minecraft_Bots_APIs.txt` in the project root (this file is gitignored)
2. Add your API keys to this file
3. Alternatively, configure API keys in-game via Mod Menu > Automated Minecraft Bots

## Config File Location

API keys are stored in:
- `config/automated_minecraft_bots-common.toml` (when running the mod)

Both of these files are automatically ignored by git to protect your API keys.

## Required API Keys

- OpenAI API Key (for GPT models)
- Gemini API Key (for Google Gemini models)
- Grok API Key (for xAI Grok models)

You only need the API key for the LLM provider you plan to use.