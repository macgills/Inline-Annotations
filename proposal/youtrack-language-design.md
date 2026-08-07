# YouTrack draft: inline annotation classes

## Suggested title

Language support for inline annotation classes (fixed compile-time annotation composition)

## Subsystem

Language Design

## Description

Kotlin has no general way to define one annotation as a compile-time bundle of other annotation applications.

I propose allowing an annotation class to be marked `inline`:

```kotlin
@A("default")
@B
inline annotation class Feature

@Feature
fun operation() = Unit
```

with the effective semantics of:

```kotlin
@A("default")
@B
fun operation() = Unit
```

This is **frontend annotation substitution**, not runtime meta-annotation lookup. The bundle annotation disappears from the effective annotation set, and the expanded constituents are then handled by Kotlin exactly as directly written annotations would be.

## Deliberately narrow scope

This proposal is only for **fixed annotation recipes**.

It does **not** propose:

* parameters on the inline annotation that are forwarded into constituent annotations;
* aliases between outer and constituent annotation parameters;
* merging/amalgamating multiple constituent parameter sets into one annotation API;
* Spring-style merged-annotation or attribute-override semantics;
* arbitrary expressions that derive constituent arguments from bundle arguments.

This is deliberately out of scope:

```kotlin
// NOT PROPOSED
@RequestMapping(path = [path])
inline annotation class Route(val path: String)

@Route("/users")
fun users() = Unit
```

The proposed feature supports fixed presets:

```kotlin
@RequestMapping(path = ["/users"])
inline annotation class UsersRoute

@UsersRoute
fun users() = Unit
```

Parameterized annotation composition is a separable language-design problem.

## Real-world use cases

### AndroidX Compose Preview already implements a specialized version

AndroidX Compose MultiPreview lets an annotation class contain multiple fixed `@Preview` applications. Applying the custom annotation to a composable causes Android Studio to render the contained previews. AndroidX itself ships presets such as `@PreviewLightDark`, `@PreviewScreenSizes`, `@PreviewFontScales`, and `@PreviewDynamicColors`.

References:

* https://developer.android.com/reference/kotlin/androidx/compose/ui/tooling/preview/Preview
* https://developer.android.com/develop/ui/compose/tooling/previews#preview-multipreview
* https://developer.android.com/reference/kotlin/androidx/compose/ui/tooling/preview/PreviewLightDark

This is effectively fixed annotation composition implemented specifically by Preview tooling. Other Kotlin annotation consumers do not inherit those semantics.

### Spring independently built a broader composition system

Spring has a much broader concept of composed annotations. It provides composed APIs such as `@PostMapping` and infrastructure including `@AliasFor` and `MergedAnnotations` for recursive meta-annotation discovery, aliasing, merging, and synthesis.

References:

* https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-requestmapping.html
* https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/core/annotation/AliasFor.html
* https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/core/annotation/MergedAnnotations.html

Spring is evidence that ecosystems repeatedly need annotation composition, but its parameter-merging model is explicitly outside this proposal.

### Ordinary meta-annotations cannot cover compiler-semantic annotations

A recipe constituent should not have to allow `ANNOTATION_CLASS` merely because it participates in composition.

For example, AndroidX `@Composable` and `@ReadOnlyComposable` do not target `ANNOTATION_CLASS`, so this is invalid today:

```kotlin
@Composable
@ReadOnlyComposable
annotation class ReadOnlyUi
```

But the fixed bundle is meaningful:

```kotlin
@Composable
@ReadOnlyComposable
inline annotation class ReadOnlyUi

@ReadOnlyUi
fun currentTheme(): Theme = LocalTheme.current
```

The second form should be checked as though both compiler-semantic annotations were written directly on `currentTheme`.

References:

* https://developer.android.com/reference/kotlin/androidx/compose/runtime/Composable
* https://developer.android.com/reference/kotlin/androidx/compose/runtime/ReadOnlyComposable

## Proposed core semantics

* `inline annotation class` declares a fixed annotation recipe.
* Constituent applications and arguments are fixed at the recipe declaration.
* Constituents do not need `AnnotationTarget.ANNOTATION_CLASS`; they are target-checked at the expanded use site.
* Expansion is recursive; cycles are compile-time errors.
* The inline annotation use itself is absent from the effective/emitted annotation set.
* Constituent retention is unchanged.
* Expansion happens in the frontend before annotation-sensitive compiler plugins consume the declaration.
* After expansion, **ordinary Kotlin annotation semantics apply**. The feature adds no special bundle precedence; ordinary duplicate/repeatable behavior applies to the resulting annotation set.
* Recipes must be consumable across module boundaries through compiler metadata.
* There is no parameter forwarding or synthesized amalgamated parameter surface.

Use-site targets and the distinction between recipe annotations versus annotations describing the inline annotation declaration itself need precise language-design rules; those are intentionally not hidden by the prototype.

## Executable prototype

There is a Kotlin 2.4.10 / K2 proof of concept:

https://github.com/macgills/Inline-Annotations

An ordinary compiler plugin cannot make `inline` a legal modifier on annotation classes or change Kotlin's built-in annotation-target applicability, so the prototype uses a temporary `@InlineAnnotations` marker and one intentionally suppressed `WRONG_ANNOTATION_TARGET` diagnostic as scaffolding.

The implementation performs the meaningful expansion in FIR so the experiment exercises the required frontend model rather than only a late JVM transformation.

The current tests prove:

* FIR-level fixed annotation expansion;
* fixed constituent arguments;
* nested bundles;
* bundle removal from emitted JVM declarations;
* class, constructor, function, field, getter, value-parameter, and type-parameter targets;
* repeatable accumulation after expansion;
* a constituent that does not target `ANNOTATION_CLASS`;
* cross-module expansion from a separately compiled JVM library.

The strongest test combines the last two claims. `:bundle-library` declares `@CrossModuleFirst("library")`, whose target is **only `FUNCTION`**, inside `LibraryBundle`. `:sample` uses only `@LibraryBundle`; reflection then verifies that the compiled consumer contains `@CrossModuleFirst("library")` and `@CrossModuleSecond(42)`, while `LibraryBundle` itself is absent.

## Prototype limitations that are not being presented as solved

* The target-checking concession is implemented with `@Suppress("WRONG_ANNOTATION_TARGET")`; a real language feature must make recipe position legal directly.
* Cross-module JVM expansion is proven only where the recipe annotations remain available in compiled symbol metadata. A real implementation needs dedicated recipe metadata independent of constituent retention, including `SOURCE`, and corresponding KLIB/common metadata.
* The prototype detects cycles with an internal assertion, not a polished compiler diagnostic.
* The complete target/use-site-target matrix is not tested.
* Java-source use of an inline annotation class cannot inherit Kotlin compiler semantics automatically and needs an interoperability rule.
* The final language design must distinguish recipe annotations from annotations intended to describe the inline annotation declaration itself.

Parameter forwarding is **not** a prototype limitation or future requirement of this proposal; it is explicitly out of scope.

## Why a language feature rather than a library/compiler-plugin convention?

A framework convention cannot make all annotation consumers agree on one effective annotation set. A late compiler transformation is also too late for compiler-semantic annotations such as `@Composable`.

For predictable semantics, expansion needs to be part of the Kotlin frontend so the language, compiler plugins, Analysis API/IDE, symbol tooling, and backends share the same effective annotations.

## Full design draft

The repository contains a KEEP-shaped design document with detailed semantics, compatibility, executable evidence, non-goals, and open questions:

https://github.com/macgills/Inline-Annotations/blob/main/proposal/inline-annotation-classes.md
