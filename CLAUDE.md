# CLAUDE.md

Rules for working in this repository (RS Mod — a Kotlin/Gradle RuneScape game-server emulator).

## Core Rules

- Never guess. If something is unclear, inspect the code or ask rather than assuming.
- Always inspect existing code first, before writing anything new.
- Never duplicate functionality — search the codebase for an existing system before adding one.
- Reuse existing systems whenever possible.
- Compile after every feature.
- Fix compile errors immediately.
- Run tests after each change.
- Never leave TODOs.
- Document every public class.
- Update docs whenever architecture changes.
- Create one Git commit per completed feature.
- Do not implement multiple unrelated systems in a single change.
- Favor modular, maintainable code over quick fixes.

## Project Commands

- Compile: `gradlew compileKotlin` (or `gradlew build` for a full build)
- Format: `gradlew spotlessKotlinApply`
- Unit tests: `gradlew test`
- Integration tests: `gradlew integration`
- Meta tests (Konsist architecture checks + doc tests): `gradlew konsistTest docTest`
- Run the game server: `gradlew run`
