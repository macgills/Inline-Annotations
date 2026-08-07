# Submission checklist

The repository contains the design/prototype material needed to start Kotlin's language-design process. The current KEEP repository asks contributors not to open unsolicited PRs containing brand-new KEEPs; new language ideas should first be filed as a YouTrack issue in the **Language Design** subsystem with concrete real-world use cases.

Process reference: https://github.com/Kotlin/KEEP#contributing-use-cases-and-specific-enhancement-proposals

## Scope locked for initial proposal

The initial proposal is **fixed annotation substitution only**.

- [x] Constituent annotation applications and their arguments are fixed at the inline annotation declaration.
- [x] No forwarding of inline-annotation parameters into constituents.
- [x] No amalgamation of constituent parameter sets into a synthesized outer annotation API.
- [x] No Spring-style aliasing, merging, attribute override or synthesized annotation semantics.
- [x] Spring remains a motivating example of independently reinvented composition, not a claim of feature parity.
- [x] Parameterized annotation composition is explicitly described as a possible separate future proposal, not an unfinished part of this one.

## Before filing the Language Design issue

- [ ] Make this prototype repository publicly readable, or move the proposal/prototype to a public repository that Kotlin maintainers can inspect.
- [x] License the prototype under a highly permissive open-source license (`0BSD`).
- [ ] Re-run CI on the exact public revision linked from the issue.
- [ ] Review [`inline-annotation-classes.md`](inline-annotation-classes.md) one final time after the repository becomes public.
- [ ] Decide the initial Java-source interoperability wording. The proposal currently treats direct Java-source use as an unresolved interoperability limitation.
- [ ] Prototype or explicitly defer remaining Kotlin annotation targets not covered by the current executable matrix.

## File the Language Design issue

Use [`youtrack-language-design.md`](youtrack-language-design.md) as the submission body.

The strongest concrete motivations are:

1. AndroidX Compose Preview has a specialized recursive MultiPreview model for fixed preview presets.
2. Spring Framework independently built a broader composed-annotation system, demonstrating repeated ecosystem demand while also illustrating the larger parameter-merging problem this proposal deliberately does not solve.
3. Compiler-semantic annotations such as Compose `@Composable` and `@ReadOnlyComposable` cannot be bundled with ordinary Kotlin meta-annotations because they do not target `ANNOTATION_CLASS`.
4. The repository contains an executable cross-module proof that fixed constituent annotations and their arguments are substituted into a separately compiled consumer while the bundle annotation itself is absent.

## After YouTrack creation

- [ ] Replace `Related YouTrack issue: TBD` in the KEEP-shaped proposal with the assigned issue.
- [ ] Put the YouTrack URL in the README.
- [ ] Use the YouTrack issue for initial use-case/design discussion.
- [ ] If the Kotlin language team promotes the idea to a KEEP, add the assigned KEEP number, formal Discussion link, and current KEEP status to the proposal header.
- [ ] Only then rename/reshape the document to the exact assigned `KEEP-xxxx-...md` form requested by maintainers.

## Remaining prototype/design limitations worth acknowledging

These remain visible rather than being hidden behind the proof:

- incomplete annotation-target coverage;
- multiplatform/KLIB metadata and backend behavior;
- Java-source usage;
- semantic API representation of direct versus expanded annotation origin;
- deterministic ordering across nested/repeated bundle use;
- deterministic distinction between annotations that belong to the inline recipe and annotations that describe the inline annotation declaration itself.

Parameter forwarding is intentionally **not** on this list. It is outside the proposal rather than an implementation gap.
