# Language boundaries proven by the prototype

The intended language syntax is:

```kotlin
@First("expanded")
@Second(7)
inline annotation class Bundle
```

The executable prototype deliberately uses compiler-plugin scaffolding at two places where today's Kotlin language rules cannot express the proposal directly.

## 1. `inline` is not an applicable modifier on annotation classes

Kotlin 2.4.10 parses `inline annotation class` far enough for a compiler plugin to observe the `inline` status in FIR, but the built-in modifier applicability checker reports:

```text
Modifier 'inline' is not applicable to 'annotation class'.
```

The prototype does **not** pretend a compiler plugin can legalize that syntax. Executable fixtures therefore use `@InlineAnnotations` as a bootstrap marker, while [`prototype/desired-syntax.kt.txt`](../prototype/desired-syntax.kt.txt) records the intended source form.

A real implementation requires the Kotlin compiler/language to make `inline` applicable to annotation declarations.

## 2. Recipe constituents are currently target-checked as meta-annotations

A core property of the proposal is that a recipe constituent should be validated at the eventual expanded use site, not against the inline annotation declaration.

For example:

```kotlin
@Target(AnnotationTarget.FUNCTION)
annotation class FunctionOnly

@FunctionOnly
inline annotation class FunctionFeature
```

`FunctionOnly` intentionally does not target `ANNOTATION_CLASS`. Under the proposed semantics that is valid because `@FunctionOnly` is an annotation application stored in a recipe for later substitution onto functions.

Today's Kotlin compiler instead treats it as an ordinary annotation directly applied to an annotation class and reports `WRONG_ANNOTATION_TARGET` before a compiler plugin can redefine that language rule.

The strongest cross-module fixture therefore contains one explicit prototype concession:

```kotlin
@Suppress("WRONG_ANNOTATION_TARGET")
@InlineAnnotations
@CrossModuleFirst("library") // CrossModuleFirst targets FUNCTION only
annotation class LibraryBundle
```

Kotlin itself warns that suppressing this error relies on unspecified behavior. That warning is useful evidence of the precise language boundary; it is not proposed as an implementation technique.

A real language implementation must make recipe position a distinct semantic context and validate constituents after expansion.

## What the prototype actually establishes

The experiment cleanly separates feasibility from the language changes required to expose it:

1. Can K2/FIR perform fixed source-level annotation substitution? **Yes; the executable tests demonstrate it.**
2. Can the recipe be consumed from a separately compiled JVM module? **Yes, for recipe annotations available in the compiled symbol metadata; the cross-module test demonstrates it.**
3. Can an ordinary plugin make `inline` legal on annotation classes? **No.**
4. Can an ordinary plugin redefine Kotlin's annotation-target applicability rules cleanly? **No.**
5. Does the prototype prove the final retention-independent/KLIB metadata design, every annotation target, Java-source behavior, or polished cycle diagnostics? **No; those remain explicit language/compiler design work.**

These boundaries are part of the evidence for the Language Design proposal rather than limitations to disguise.
