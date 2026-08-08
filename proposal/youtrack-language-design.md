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

The prototype now compiles the **literal proposed declaration syntax** in ordinary `.kt` files:

```kotlin
@First("expanded")
@Second(7)
inline annotation class Bundle

@Bundle
inline annotation class NestedBundle
```

Kotlin 2.4.10 already parses that declaration shape and exposes the `inline` status to FIR, but the stock modifier-applicability checker reports `WRONG_MODIFIER_TARGET`. The executable fixture suppresses that existing diagnostic at file level. The FIR plugin then observes `status.isInline`, treats the annotation class as a recipe, performs expansion, and normalizes the invalid class status before later compiler phases.

This means the prototype is no longer using `@InlineAnnotations` as a substitute for the proposed source syntax. Same-module bundles use `inline annotation class` directly.

The implementation performs the meaningful expansion in FIR so the experiment exercises the required frontend model rather than only a late JVM transformation.

The current tests prove:

* compilation of real `inline annotation class` source under the prototype plugin;
* FIR-level fixed annotation expansion;
* fixed constituent arguments;
* nested inline annotation classes;
* bundle removal from emitted JVM declarations;
* class, constructor, function, field, getter, value-parameter, and type-parameter targets;
* repeatable accumulation after expansion;
* a constituent that does not target `ANNOTATION_CLASS`;
* cross-module expansion from a separately compiled inline annotation class.

The strongest test combines the last two claims. `:bundle-library` is compiled with the plugin and declares a real `inline annotation class LibraryBundle` containing `@CrossModuleFirst("library")`, whose target is **only `FUNCTION`**, plus `@CrossModuleSecond(42)`. `:sample` uses only `@LibraryBundle`; reflection then verifies that the compiled consumer contains `@CrossModuleFirst("library")` and `@CrossModuleSecond(42)`, while `LibraryBundle` itself is absent.

For that compiled-library case only, the prototype retains `@InlineAnnotations` as a **binary discovery marker**. Today's Kotlin metadata has no representation for the new recipe declaration semantic, so a downstream compilation needs temporary metadata telling it which compiled annotation class owns a recipe. The source declaration still uses the proposed `inline annotation class` syntax.

## Prototype limitations that are not being presented as solved

* Stock Kotlin still reports `WRONG_MODIFIER_TARGET` for `inline annotation class`; executable fixtures suppress it at file level so the plugin can exercise the already-parsed FIR shape. A real language feature must make the modifier applicable directly.
* A `FUNCTION`-only recipe constituent is still initially target-checked as though it were an ordinary meta-annotation, so the cross-module fixture also suppresses `WRONG_ANNOTATION_TARGET` at file level. A real language feature must make recipe position legal directly.
* Cross-module recipe identity currently uses the prototype-only `@InlineAnnotations` binary marker. That is artifact scaffolding, not part of the proposed source syntax.
* The prototype encodes a cross-module recipe using ordinary annotations on the bundle declaration because those are what a compiler plugin can recover from a dependency. **That is prototype scaffolding, not the desired artifact semantics.** Recipe constituents should be stored in dedicated Kotlin compile-time metadata, not exposed to Java/reflection as though they semantically annotated the inline annotation declaration itself.
* Cross-module JVM expansion is therefore proven only where the recipe annotations remain available in compiled symbol metadata. A real implementation needs dedicated recipe metadata independent of constituent retention, including `SOURCE`, and corresponding KLIB/common metadata.
* The prototype detects cycles with an internal assertion, not a polished compiler diagnostic.
* The complete target/use-site-target matrix is not tested.
* Java-source use of an inline annotation class cannot inherit Kotlin compiler semantics automatically and needs an interoperability rule.
* The final language design must distinguish recipe annotations from annotations intended to describe the inline annotation declaration itself. The meaning, if any, of declaration controls such as `@Retention` on an annotation whose uses are themselves erased also needs to be specified.

Parameter forwarding is **not** a prototype limitation or future requirement of this proposal; it is explicitly out of scope.

## Related Kotlin design history

KT-14652 (`constexpr` / compile-time functions) contains an older suggestion that compile-time annotation factories could return annotations, including multiple annotations at once. That is related motivation, but it requires the much larger `constexpr` facility and naturally leads toward parameterized annotation factories.

This proposal is intentionally smaller: it asks only for a fixed annotation recipe attached to an annotation declaration and frontend substitution of that recipe.

Reference: https://youtrack.jetbrains.com/issue/KT-14652

## Why a language feature rather than a library/compiler-plugin convention?

A framework convention cannot make all annotation consumers agree on one effective annotation set. A late compiler transformation is also too late for compiler-semantic annotations such as `@Composable`.

For predictable semantics, expansion needs to be part of the Kotlin frontend so the language, compiler plugins, Analysis API/IDE, symbol tooling, and backends share the same effective annotations.

## Full design draft

The repository contains a KEEP-shaped design document with detailed semantics, compatibility, executable evidence, non-goals, and open questions:

https://github.com/macgills/Inline-Annotations/blob/main/proposal/inline-annotation-classes.md
