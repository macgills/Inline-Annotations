# Language boundaries proven by the prototype

The intended language syntax is:

```kotlin
@First("expanded")
@Second(7)
inline annotation class Bundle
```

The executable prototype deliberately uses compiler-plugin scaffolding where today's Kotlin language and metadata model cannot express the proposal directly.

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

Kotlin itself warns that suppressing this error relies on unspecified behavior and asks for the use case to be reported to the Kotlin issue tracker. That warning is useful evidence of the precise language boundary; it is not proposed as an implementation technique.

A real language implementation must make recipe position a distinct semantic context and validate constituents after expansion.

## 3. Cross-module recipe storage is prototype scaffolding

The plugin needs some way to recover a recipe from a separately compiled dependency. The prototype therefore writes the recipe using ordinary annotations on the bundle annotation declaration and reads those annotations back from the compiled symbol.

That demonstrates **cross-module recoverability**, but it is not the desired language-level artifact representation.

Semantically, recipe constituents do not annotate the inline annotation declaration itself. A production implementation should therefore store the fixed recipe in dedicated Kotlin compile-time metadata rather than expose recipe constituents to Java or runtime reflection as ordinary meta-annotations on the bundle declaration.

Dedicated recipe metadata is also required to preserve recipes containing `SOURCE`-retained constituents, whose ordinary annotation representation is intentionally unavailable after compilation, and to support common/KLIB metadata consistently.

The retention of an inline annotation use itself is consequently not what keeps the recipe available to downstream compilation. The exact meaning—or usefulness—of applying declaration controls such as `@Retention` to an inline annotation class remains a language-design question.

## What the prototype actually establishes

The experiment cleanly separates feasibility from the language changes required to expose it:

1. Can K2/FIR perform fixed source-level annotation substitution? **Yes; the executable tests demonstrate it.**
2. Can a fixed recipe be recovered and expanded from a separately compiled JVM module? **Yes, using the prototype's retained-annotation encoding.**
3. Can a recipe constituent lack `ANNOTATION_CLASS` and still become present on the eventual consumer? **Yes; the cross-module test demonstrates it, using the explicit target-checker concession above.**
4. Can an ordinary plugin make `inline` legal on annotation classes? **No.**
5. Can an ordinary plugin redefine Kotlin's annotation-target applicability rules cleanly? **No.**
6. Does the prototype prove the final retention-independent recipe metadata, KLIB representation, every annotation target, Java-source behavior, or polished cycle diagnostics? **No; those remain explicit language/compiler design work.**

These boundaries are part of the evidence for the Language Design proposal rather than limitations to disguise.
