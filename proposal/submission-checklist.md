# Submission checklist

The repository now contains the design/prototype material needed to start Kotlin's language-design process. The current KEEP repository explicitly asks contributors **not** to open unsolicited PRs containing brand-new KEEPs; new language ideas should first be filed as a YouTrack issue in the **Language Design** subsystem with concrete real-world use cases.

Process reference: https://github.com/Kotlin/KEEP#contributing-use-cases-and-specific-enhancement-proposals

## Before filing the Language Design issue

- [ ] Make this prototype repository publicly readable, or move the proposal/prototype to a public repository that Kotlin maintainers can inspect.
- [x] License the prototype for unrestricted reuse. The repository uses the BSD Zero Clause License (`0BSD`).
- [ ] Re-run CI on the exact public revision linked from the issue.
- [ ] Review [`inline-annotation-classes.md`](inline-annotation-classes.md) for any semantics you want to narrow before public discussion.
- [ ] Decide whether parameter forwarding is part of the initial proposal or a follow-up. It is designed in the proposal but not yet executable in the prototype.
- [ ] Decide the initial Java-source interoperability position. The proposal currently treats Java-source use of an inline annotation class as unsupported semantics.
- [ ] Prototype or explicitly defer the remaining annotation targets called out in the evidence table.

## File the Language Design issue

Use [`youtrack-language-design.md`](youtrack-language-design.md) as the paste-ready issue body.

The strongest concrete use cases in the issue are deliberately ecosystem examples rather than hypothetical convenience:

1. AndroidX Compose Preview has a specialized recursive MultiPreview annotation model implemented by Android Studio tooling.
2. Spring Framework has a general composed-annotation runtime model (`@AliasFor`, `MergedAnnotations`) and composed APIs such as `@PostMapping`.
3. Compiler-semantic annotations such as Compose `@Composable` and `@ReadOnlyComposable` cannot be bundled with ordinary Kotlin meta-annotations because they do not target `ANNOTATION_CLASS`.

## After YouTrack creation

- [ ] Replace `Related YouTrack issue: TBD` in the KEEP-shaped proposal with the assigned issue.
- [ ] Put the YouTrack URL in the README.
- [ ] Use the YouTrack issue for initial use-case/design discussion.
- [ ] If the Kotlin language team promotes the idea to a KEEP, add the assigned KEEP number, formal Discussion link, and current KEEP status to the proposal header.
- [ ] Only then reshape/rename the document to the exact assigned `KEEP-xxxx-...md` form requested by maintainers.

## Prototype gaps worth closing during discussion

These are intentionally visible rather than hidden behind prose:

- parameter forwarding;
- full annotation-target coverage;
- multiplatform/KLIB metadata;
- Java-source behavior;
- semantic API representation of direct vs expanded annotation origin;
- deterministic ordering across nested/repeated bundle use;
- distinction between annotations that are part of the inline recipe and annotations intended to describe the inline annotation declaration itself.

The last item deserves explicit design discussion. Built-in declaration annotations such as `@Target`, `@Retention`, `@MustBeDocumented`, and `@Repeatable` clearly configure the bundle rather than expand. User-defined meta-annotations can create an ambiguous case, especially when an annotation is valid on both `ANNOTATION_CLASS` and ordinary declaration targets. The final language design needs a deterministic rule or explicit escape hatch rather than a framework-specific heuristic.
