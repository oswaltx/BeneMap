# Contributing to Benemap

Thanks for your interest in contributing! Benemap is a small, open, non-commercial
project, and contributions of any size — bug reports, docs fixes, features — are welcome.

## Ways to contribute

- **Report bugs**: open an issue with steps to reproduce, what you expected, and what
  happened instead.
- **Suggest features**: open an issue describing the problem you're trying to solve, not
  just the solution — it makes discussion easier.
- **Fix bugs / build features**: see below for the workflow.
- **Improve docs**: README, code comments, this file — all fair game.

## Project structure

```
/frontend    // Svelte 5 SPA
/backend     // Spring Boot (Kotlin) REST API + H2 database
/orga        // planning docs, roadmap, kanban board
```

See [README.md](README.md) for how to run the project locally.

## Development workflow

1. Fork the repo and create a branch off `main` (`git checkout -b fix/short-description`).
2. Make your change. Keep commits focused — one logical change per commit.
3. Add or update tests for backend changes (`backend/src/test/...`). The backend has an
   existing suite of Spring MockMvc / unit tests to follow as examples.
4. Make sure things still build and pass:

   ```bash
   cd backend && ./gradlew test
   cd frontend && npm run check
   ```

5. Open a pull request against `main` with a clear description of what changed and why.
   Reference any related issue.

## Code style

There's no enforced linter/formatter yet — please just match the style of the
surrounding code:

- **Kotlin**: standard Kotlin conventions, constructor-injected dependencies,
  `@RestController` classes per resource (see `backend/src/main/kotlin/.../server/`).
- **Svelte/TypeScript**: components in `frontend/src/lib` and `frontend/src/pages`,
  4-space indentation, no unused imports.

## Commit messages

Short, imperative summary line (`fix: ...`, `feat: ...`, `chore: ...`), optionally
followed by a blank line and more detail if the change isn't self-explanatory.

## Reporting security issues

Please **don't** open a public issue for security vulnerabilities. See
[SECURITY.md](SECURITY.md) instead.

## License

Benemap is licensed under the [AGPLv3](LICENSE). By submitting a pull request, you agree
your contribution is offered under the same license.
