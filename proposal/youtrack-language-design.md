# YouTrack draft: inline annotation classes

## Suggested title

Language support for inline annotation classes (compile-time annotation composition)

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

The important part is that this is **frontend annotation substitution**, not runtime meta-annotation lookup. The bundle annotation disappears from the effective annotation set and its constituent annotations are visible to target checking, compiler plugins, Analysis API/IDE tooling, backends, and runtime reflection according to each constituent's normal retention.

## Deliberately narrow scope

This proposal is only for **fixed annotation recipes**.

It does **not** propose:

* parameters on the inline annotation that are forwarded into constituent annotations;
* aliases between outer and constituent annotation parameters;
* merging/amalgamating multiple constituent parameter sets into one annotation API;
* Spring-style merged-annotation or attribute-override semantics;
* arbitrary expressions that derive constituent arguments from bundle arguments.

For example, this is deliberately out of scope:

```kotlin
// NOT PROPOSED
@RequestMapping(path = [path])
inline annotation class Route(val path: String)

@Route("/users")
fun users() = Unit
```

The proposed feature supports the fixed-preset form:

```kotlin
@RequestMapping(path = ["/users"])
inline annotation class UsersRoute

@UsersRoute
fun users() = Unit
```

Parameterized annotation composition is a separable language-design problem and is not required for this feature to be useful.

## Real-world use cases

### AndroidX Compose Preview already implements a specialized version

AndroidX `@Preview` deliberately targets both `FUNCTION` and `ANNOTATION_CLASS`. Its API documentation says that annotation classes carrying `@Preview` can themselves annotate composable functions, which are then considered *indirectly annotated* with that Preview.

Android's MultiPreview feature builds directly on this behavior. AndroidX ships reusable presets such as `@PreviewLightDark` where one annotation represents multiple fixed `@Preview` applications.

References:

* https://developer.android.com/reference/kotlin/androidx/compose/ui/tooling/preview/Preview
* https://developer.android.com/develop/ui/compose/tooling/previews#preview-multipreview
* https://developer.android.com/reference/kotlin/androidx/compose/ui/tooling/preview/PreviewLightDark

This is effectively fixed annotation composition implemented specifically by Android Studio Preview tooling. Other annotations and other Kotlin tooling do not inherit those semantics.

### Spring Framework independently built a broader composition system

Spring has a much broader concept of **composed annotations**. `@GetMapping`, `@PostMapping`, etc. are composed over `@RequestMapping`, and Spring additionally provides `@AliasFor` and `MergedAnnotations` for parameter aliasing, merging and synthesis.

References:

* https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-requestmapping.html
* https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/core/annotation/AliasFor.html
* https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/core/annotation/MergedAnnotations.html

Spring is motivating evidence that ecosystems repeatedly need annotation composition, but this proposal does **not** attempt to standardize Spring's richer parameter-merging model. The proposed Kotlin feature is the simpler fixed-recipe subset.

### Ordinary meta-annotations cannot cover compiler-semantic annotations

A constituent annotation should not have to allow `ANNOTATION_CLASS` merely because it is stored in a recipe.

For example, AndroidX `@Composable` currently targets `FUNCTION`, `TYPE`, `TYPE_PARAMETER`, and `PROPERTY_GETTER`; `@ReadOnlyComposable` targets `FUNCTION` and `PROPERTY_GETTER`. Neither targets `ANNOTATION_CLASS`.

So this cannot be written today:

```kotlin
@Composable
@ReadOnlyComposable
annotation class ReadOnlyUi
```

But this fixed bundle is meaningful:

```kotlin
@Composable
@ReadOnlyComposable
inline annotation class ReadOnlyUi

@ReadOnlyUi
fun currentTheme(): Theme = LocalTheme.current
```

The second form should behave exactly as though both compiler annotations were written directly on `currentTheme`.

References:

* https://developer.android.com/reference/kotlin/androidx/compose/runtime/Composable
* https://developer.android.com/reference/kotlin/androidx/compose/runtime/ReadOnlyComposable

## Proposed core semantics

* `inline annotation class` declares a fixed annotation recipe.
* Constituent annotation applications and their arguments are fixed at the recipe declaration.
* Constituents do not need `AnnotationTarget.ANNOTATION_CLASS`; they are target-checked at the expanded use site.
* Expansion is recursive and cycles are compile-time errors.
* The inline annotation use itself is absent from the effective/emitted annotation set.
* Constituent retention is unchanged.
* Expansion happens in the frontend before annotation-sensitive compiler plugins consume the declaration.
* Explicit use-site targets propagate to constituents; without one, normal target defaulting applies to each expanded annotation.
* A directly written non-repeatable annotation overrides the same annotation contributed by a bundle.
* Repeatable annotations accumulate.
* Recipes must work across module boundaries through Kotlin metadata.
* There is no parameter forwarding or synthesized amalgamated parameter surface in this proposal.

## Prototype

There is an executable K2 / Kotlin 2.4.10 proof of concept:

https://github.com/macgills/Inline-Annotations-

The prototype uses a temporary `@InlineAnnotations` marker because a compiler plugin cannot make `inline` a legal modifier for annotation classes. Kotlin already parses `inline annotation class` far enough for FIR to observe it, but the built-in modifier checker rejects it.

The current tests prove:

* FIR-level annotation expansion;
* fixed constituent arguments;
* nested bundles;
* bundle removal from emitted JVM declarations;
* class, constructor, function, field, getter, value-parameter and type-parameter targets;
* constituents that do not target `ANNOTATION_CLASS`;
* direct non-repeatable annotation precedence;
* repeatable accumulation;
* cross-module expansion from a separately compiled library.

The strongest proof is cross-module: the library declares a bundle containing `@CrossModuleFirst("library")` and `@CrossModuleSecond(42)`, the consumer writes only `@LibraryBundle`, and reflection verifies that the compiled consumer contains both constituent annotations with those fixed values while `LibraryBundle` itself is absent.

Known limitations / remaining work include the full annotation-target matrix, multiplatform metadata/backends, Java-source consumption, and deterministic distinction between annotations that describe the inline annotation declaration itself versus annotations that belong to its recipe.

Parameter forwarding is not "remaining work" for this proposal; it is explicitly out of scope.

## Why this should be a language feature rather than a library/compiler-plugin convention

A runtime or framework convention cannot make all annotation consumers agree on the effective annotations. A late compiler transform is also too late for compiler-semantic annotations such as `@Composable`.

For predictable semantics, expansion needs to be part of the Kotlin frontend so that the language, compiler plugins, Analysis API, IDE, symbol tooling and backends share one effective annotation model.

## Full proposal draft

The repository contains a KEEP-shaped design document with detailed semantics, compatibility, prototype evidence, non-goals and open questions:

https://github.com/macgills/Inline-Annotations-/blob/main/proposal/inline-annotation-classes.md
