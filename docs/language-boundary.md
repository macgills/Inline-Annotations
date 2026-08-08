# Language boundaries proven by the prototype

The intended language syntax is:

```kotlin
@First("expanded")
@Second(7)
inline annotation class Bundle
```

The executable prototype now compiles that **actual declaration shape** in ordinary `.kt` source. It still uses explicit compiler-boundary scaffolding where today's Kotlin language and metadata model cannot express the proposal cleanly.

## 1. `inline annotation class` is parsed and executable under the prototype

Kotlin 2.4.10 parses `inline annotation class` far enough for FIR to expose `status.isInline`, but the built-in modifier applicability checker reports:

```text
Modifier 'inline' is not applicable to 'annotation class'.
```

The executable fixtures therefore use a file-level prototype concession:

```kotlin
@file:Suppress("WRONG_MODIFIER_TARGET")
```

The declarations themselves use the proposed syntax directly:

```kotlin
@First("expanded")
@Second(7)
inline annotation class Bundle
```

The FIR status transformer observes `status.isInline`, records the declaration as an inline-annotation recipe, expands uses in the frontend, and then normalizes the otherwise-invalid inline/value status before later compiler phases.

This is materially stronger than a marker-only syntax simulation: the parser and FIR plugin are executing the proposed source shape. It still does **not** mean stock Kotlin has made the modifier legal. A real language implementation removes the need for `WRONG_MODIFIER_TARGET` suppression by making `inline` applicable to annotation declarations.

[`sample/src/main/kotlin/dev/inlineannotations/sample/Fixtures.kt`](../sample/src/main/kotlin/dev/inlineannotations/sample/Fixtures.kt) is the direct executable proof.

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

Today's Kotlin compiler instead initially treats it as an ordinary annotation directly applied to an annotation class and reports `WRONG_ANNOTATION_TARGET` before the plugin can redefine that language rule cleanly.

The strongest cross-module fixture therefore contains a second file-level prototype concession:

```kotlin
@file:Suppress("WRONG_MODIFIER_TARGET", "WRONG_ANNOTATION_TARGET")
```

and the declaration itself remains the desired shape:

```kotlin
@CrossModuleFirst("library") // CrossModuleFirst targets FUNCTION only
@CrossModuleSecond(42)
inline annotation class LibraryBundle
```

The production feature must make recipe position a distinct semantic context and validate constituents after expansion.

## 3. Cross-module recipe storage is still prototype scaffolding

The plugin needs some way to recover a recipe from a separately compiled dependency.

For same-module source declarations, the `inline` modifier itself identifies the recipe. For a compiled dependency, today's Kotlin metadata has no representation for this new declaration semantic, and the prototype intentionally normalizes the invalid `inline` class status before later phases.

The cross-module fixture therefore retains one prototype-only annotation:

```kotlin
@InlineAnnotations // binary discovery marker only
@CrossModuleFirst("library")
@CrossModuleSecond(42)
inline annotation class LibraryBundle
```

This marker is **not** a source-syntax substitute anymore. `LibraryBundle` is a real inline annotation class and is compiled with the plugin. The marker exists only so the consumer compiler can identify the already-compiled recipe.

The prototype also writes the recipe constituents as ordinary annotations on the bundle declaration and reads those annotations back from the compiled symbol.

That demonstrates **cross-module recoverability**, but it is not the desired language-level artifact representation.

Semantically, recipe constituents do not annotate the inline annotation declaration itself. A production implementation should therefore store the fixed recipe in dedicated Kotlin compile-time metadata rather than expose recipe constituents to Java or runtime reflection as ordinary meta-annotations on the bundle declaration.

Dedicated recipe metadata is also required to preserve recipes containing `SOURCE`-retained constituents, whose ordinary annotation representation is intentionally unavailable after compilation, and to support common/KLIB metadata consistently.

The retention of an inline annotation use itself is consequently not what keeps the recipe available to downstream compilation. The exact meaning—or usefulness—of applying declaration controls such as `@Retention` to an inline annotation class remains a language-design question.

## What the prototype actually establishes

The experiment now separates three different questions that were previously conflated:

1. Can Kotlin's parser and K2/FIR compiler-plugin pipeline compile source that literally says `inline annotation class`? **Yes.** The executable fixtures do so, with the current built-in modifier diagnostic explicitly suppressed at file level.
2. Can K2/FIR perform fixed source-level annotation substitution from those real inline annotation declarations? **Yes; the executable tests demonstrate it.**
3. Can a fixed recipe be recovered and expanded from a separately compiled JVM module whose declaration also uses real `inline annotation class` syntax? **Yes, using the prototype's binary marker and retained-annotation encoding.**
4. Can a recipe constituent lack `ANNOTATION_CLASS` and still become present on the eventual consumer? **Yes; the cross-module test demonstrates it, using the explicit target-checker concession above.**
5. Does the prototype change Kotlin's built-in modifier/target language rules themselves? **No.** Those diagnostics are suppressed as explicit prototype boundaries rather than redefined cleanly.
6. Does the prototype prove the final retention-independent recipe metadata, KLIB representation, every annotation target, Java-source behavior, or polished cycle diagnostics? **No; those remain explicit language/compiler design work.**

These boundaries are part of the evidence for the Language Design proposal rather than limitations to disguise.
