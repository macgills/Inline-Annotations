# Language boundary: `inline annotation class`

The intended language syntax is:

```kotlin
@First("expanded")
@Second(7)
inline annotation class Bundle
```

Kotlin 2.4.10 already parses this form far enough for a compiler plugin to observe the `inline` status in FIR, but the built-in modifier applicability checker later reports:

```text
Modifier 'inline' is not applicable to 'annotation class'.
```

The prototype deliberately does **not** suppress or hide that diagnostic. The executable proof therefore uses `@InlineAnnotations` as a bootstrap marker while implementing the proposed semantics in FIR.

This separates two questions cleanly:

1. Can source-level annotation substitution work with Kotlin's frontend model? **Yes; the tests prove the semantics.**
2. Can an ordinary compiler plugin make `inline` a legal modifier on an annotation declaration? **No; that requires a language/compiler change.**

That boundary is part of the evidence for a KEEP rather than a limitation to disguise.
