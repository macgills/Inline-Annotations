# Submission checklist

The repository contains the design/prototype material needed to start Kotlin's language-design process. The current KEEP repository asks contributors not to open unsolicited PRs containing brand-new KEEPs; new language ideas should first be filed as a YouTrack issue in the **Language Design** subsystem with concrete real-world use cases.

Process reference: https://github.com/Kotlin/KEEP#contributing-use-cases-and-specific-enhancement-proposals

## Scope locked for initial proposal

The initial proposal is **fixed annotation substitution only**.

- [x] Constituent annotation applications and their arguments are fixed at the inline annotation declaration.
- [x] No forwarding of inline-annotation parameters into constituents.
- [x] No amalgamation of constituent parameter sets into a synthesized outer annotation API.
- [x] No Spring-style aliasing, merging, attribute override, or synthesized annotation semantics.
- [x] Spring remains motivating evidence of independently reinvented composition, not a claim of feature parity.
- [x] After expansion, ordinary Kotlin duplicate/repeatable semantics apply; the proposal adds no bundle-specific precedence rules.

## Pre-public review

- [x] Repository renamed to the clean canonical slug `macgills/Inline-Annotations`.
- [x] Licensed under the highly permissive BSD Zero Clause License (`0BSD`).
- [x] Cross-module proof strengthened so one constituent targets only `FUNCTION` and therefore cannot legally be an ordinary meta-annotation today.
- [x] Prototype-specific direct-annotation precedence behavior removed; implementation now models pure substitution.
- [x] Prototype limitations separated from proposed language semantics.
- [x] CI action versions refreshed and permissions made explicit.
- [x] No parameter-forwarding design is presented as unfinished scope.

## Before filing the Language Design issue

- [ ] Make this repository publicly readable so Kotlin maintainers can inspect the linked proof.
- [ ] Re-run/verify CI on the exact public revision linked from the issue.
- [ ] Read [`youtrack-language-design.md`](youtrack-language-design.md) once after publication to ensure all GitHub links resolve anonymously.
- [ ] Replace any remaining private-repository assumptions with public wording if GitHub rendering exposes one.

## File the Language Design issue

Use [`youtrack-language-design.md`](youtrack-language-design.md) as the submission body.

The strongest concrete motivations are:

1. AndroidX Compose Preview has a specialized recursive MultiPreview model for fixed preview presets.
2. Spring Framework independently built a broader composed-annotation system, demonstrating repeated ecosystem demand while also illustrating the larger parameter-merging problem this proposal deliberately does not solve.
3. Compiler-semantic annotations such as Compose `@Composable` and `@ReadOnlyComposable` cannot be bundled with ordinary Kotlin meta-annotations because they do not target `ANNOTATION_CLASS`.
4. The repository contains an executable cross-module proof where a `FUNCTION`-only constituent is recovered in a separately compiled consumer while the bundle annotation itself is absent.

## After YouTrack creation

- [ ] Replace `Related YouTrack issue: TBD` in the KEEP-shaped proposal with the assigned issue.
- [ ] Put the YouTrack URL in the README.
- [ ] Use the YouTrack issue for initial use-case/design discussion.
- [ ] If the Kotlin language team promotes the idea to a KEEP, add the assigned KEEP number, formal Discussion link, and current KEEP status to the proposal header.
- [ ] Only then rename/reshape the document to the exact assigned `KEEP-xxxx-...md` form requested by maintainers.

## Acknowledged design / implementation limitations

These are not submission blockers for a use-case-led Language Design ticket, but they must remain visible:

- recipe-position target legality is currently proven using one intentionally suppressed `WRONG_ANNOTATION_TARGET` diagnostic;
- cycle detection in the prototype is an internal assertion, not a user-facing compiler diagnostic;
- incomplete annotation-target/use-site-target coverage;
- dedicated recipe metadata independent of constituent retention, including `SOURCE` recipes;
- multiplatform/KLIB metadata and backend behavior;
- Java-source usage;
- semantic API representation of direct versus expanded annotation origin;
- deterministic distinction between annotations that belong to the inline recipe and annotations that describe the inline annotation declaration itself.

Parameter forwarding is intentionally **not** on this list. It is outside the proposal rather than an implementation gap.
