# Inline annotation classes

* Type: Design proposal
* Author: Seán Mac Gillicuddy
* Status: Draft
* Discussion: TBD
* Related YouTrack issue: TBD
* Prototype: https://github.com/macgills/Inline-Annotations-

## Abstract

Kotlin annotations cannot currently be composed into a reusable annotation that has the same semantics as writing several annotations directly at a use site.

This proposal introduces **inline annotation classes**. An inline annotation class declares a reusable annotation recipe. Applying it causes the compiler to expand its constituent annotations at the use site early in the frontend, before annotation target checking and annotation-sensitive compiler plugins or tooling observe the declaration.

```kotlin
@A("default")
@B
inline annotation class Feature

@Feature
fun operation() = Unit
```

is semantically equivalent to:

```kotlin
@A("default")
@B
fun operation() = Unit
```

The inline annotation itself is not emitted on the annotated declaration. The constituent annotations behave as direct annotations, including their own retention, targets, compiler-plugin semantics, and backend representation.

The feature generalizes a pattern that major frameworks have already had to implement independently. AndroidX Compose tooling has a specialized MultiPreview model in which `@Preview` can annotate another annotation class and Android Studio treats consumers as indirectly annotated with those previews. Spring Framework has a much broader composed-annotation system, including `@AliasFor` and `MergedAnnotations`, to synthesize and merge annotation semantics at runtime. Both are useful local solutions to the same missing language primitive: reusable annotation composition.

## Table of contents

* [Motivation](#motivation)
  * [Meta-annotations are not annotation composition](#meta-annotations-are-not-annotation-composition)
  * [AndroidX Compose Preview reinvented composition in tooling](#androidx-compose-preview-reinvented-composition-in-tooling)
  * [Spring reinvented composition in a runtime framework](#spring-reinvented-composition-in-a-runtime-framework)
  * [Annotations that cannot be meta-annotated](#annotations-that-cannot-be-meta-annotated)
* [Proposal](#proposal)
  * [Syntax](#syntax)
  * [Recipe annotations versus declaration annotations](#recipe-annotations-versus-declaration-annotations)
  * [Expansion semantics](#expansion-semantics)
  * [Targets and use-site targets](#targets-and-use-site-targets)
  * [Nested inline annotations](#nested-inline-annotations)
  * [Duplicates and repeatable annotations](#duplicates-and-repeatable-annotations)
  * [Parameters and argument forwarding](#parameters-and-argument-forwarding)
  * [Retention and generated artifacts](#retention-and-generated-artifacts)
  * [Cross-module behavior and metadata](#cross-module-behavior-and-metadata)
  * [Compiler and tooling visibility](#compiler-and-tooling-visibility)
  * [Diagnostics](#diagnostics)
* [Examples](#examples)
* [Compatibility and interoperability](#compatibility-and-interoperability)
* [Prototype](#prototype)
* [Alternatives considered](#alternatives-considered)
* [Open design questions](#open-design-questions)
* [References](#references)

## Motivation

Annotations are frequently used as a declarative API between user code and compilers, IDEs, static analyzers, frameworks, test engines, dependency injection systems, serializers, and runtime reflection.

As annotation-driven APIs grow, the same groups of annotations are repeated across many declarations. The natural abstraction is another annotation representing that group. Kotlin can express an annotation *on an annotation class*, but that is only a meta-annotation relationship. It does not mean that consumers of the outer annotation are annotated with the inner annotations.

Frameworks that need this behavior therefore have to implement their own recursive annotation model. Different tools then observe different semantics: Kotlin itself sees one set of annotations, a compiler plugin may see another, an IDE may special-case another, and a runtime framework may synthesize a merged view of its own.

A language-level annotation composition primitive would make the expanded annotations part of Kotlin semantics instead of a convention that every annotation consumer must independently discover and implement.

### Meta-annotations are not annotation composition

Consider:

```kotlin
@Target(AnnotationTarget.FUNCTION)
annotation class A

@A // currently illegal: A does not target ANNOTATION_CLASS
annotation class Feature
```

Even if `A` also targeted `ANNOTATION_CLASS`, applying `@Feature` to a function would not make `A` directly present on that function. A tool has to know that `Feature` is intended as a composed annotation and recursively interpret it.

That distinction matters for compiler-semantic annotations. An annotation can change type checking, code generation, diagnostics, or plugin behavior. Discovering it only through runtime reflection or framework-specific meta-annotation traversal is too late.

Inline annotations are intentionally **substitution**, not a new kind of reflective meta-annotation lookup.

### AndroidX Compose Preview reinvented composition in tooling

AndroidX Compose Preview demonstrates direct demand for reusable annotation composition.

`androidx.compose.ui.tooling.preview.Preview` explicitly targets both `FUNCTION` and `ANNOTATION_CLASS`. Its API documentation states that when `@Preview` is applied to an annotation class, functions annotated with that annotation are considered **indirectly annotated** with the preview. Android Studio then recursively interprets those annotations when rendering previews.

The Android documentation calls this feature **MultiPreview** and recommends patterns such as:

```kotlin
@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class PreviewLightDark

@PreviewLightDark
@Composable
fun GreetingPreview() {
    Greeting()
}
```

AndroidX itself publishes annotations such as `PreviewLightDark`, `PreviewFontScale`, `PreviewScreenSizes`, and `PreviewDynamicColors` using this model.

This is effectively domain-specific annotation inlining implemented by Android Studio tooling. It works because `@Preview` was designed to permit `ANNOTATION_CLASS`, and because the Preview tooling explicitly understands recursive annotation composition. Kotlin, other compiler plugins, reflection consumers, and arbitrary annotations do not acquire those semantics.

With inline annotation classes, the same concept is a language feature rather than a Preview-specific convention:

```kotlin
@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
inline annotation class PreviewLightDark
```

The Preview implementation would no longer need a unique concept of "indirectly annotated" previews to provide basic composition: after frontend expansion, it would simply observe two ordinary `@Preview` annotations.

### Spring reinvented composition in a runtime framework

Spring Framework independently built a significantly more general annotation composition model.

Spring MVC's `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping`, and `@PatchMapping` are documented as **composed annotations** over `@RequestMapping`. Spring also supports user-defined composed annotations.

Composition becomes harder when the outer annotation needs to expose or override attributes of an inner annotation. Spring therefore provides `@AliasFor`. For example, an attribute on a composed annotation can be declared as an alias for an attribute on a meta-annotation.

The Javadoc for `@AliasFor` explicitly notes that the annotation alone does not enforce those semantics: annotations must be loaded through Spring's `MergedAnnotations` model. `MergedAnnotations` recursively discovers meta-annotations, applies explicit and implicit aliases, merges values, tracks annotation distance, and can synthesize annotation instances.

That is substantial framework machinery implementing a semantic annotation layer above Java/Kotlin reflection.

Spring's design is valuable and has more runtime-specific capabilities than this proposal needs. The language-design observation is that a mainstream framework had to invent recursive composed annotations, value forwarding, precedence, and a custom annotation view because the underlying language model exposes no general annotation composition primitive.

Inline annotation classes move the simplest and most broadly useful form of that abstraction into the compiler. Framework-specific merging rules can still exist where necessary, but a reusable bundle of annotations should not require every framework to build a second annotation system.

### Annotations that cannot be meta-annotated

Framework-specific meta-annotation systems are also limited by annotation targets.

For example, `androidx.compose.runtime.Composable` currently targets `FUNCTION`, `TYPE`, `TYPE_PARAMETER`, and `PROPERTY_GETTER`; it does **not** target `ANNOTATION_CLASS`. `ReadOnlyComposable` targets only `FUNCTION` and `PROPERTY_GETTER`.

Therefore this intuitive abstraction is impossible today:

```kotlin
@Composable
@ReadOnlyComposable
annotation class ReadOnlyUi // invalid today
```

Adding `ANNOTATION_CLASS` to every annotation merely to enable composition is not equivalent. `ANNOTATION_CLASS` means the annotation is valid *on an annotation declaration*. The desired semantics here are that the annotation declaration contains a recipe whose annotations are applied somewhere else.

With this proposal:

```kotlin
@Composable
@ReadOnlyComposable
inline annotation class ReadOnlyUi

@ReadOnlyUi
fun currentTheme(): Theme = LocalTheme.current
```

is checked as though the function had been written with `@Composable` and `@ReadOnlyComposable` directly. Neither constituent annotation needs to add `ANNOTATION_CLASS` to its target set.

This distinction is a core property of the proposal and is exercised by the prototype.

## Proposal

### Syntax

An annotation class may be marked `inline`:

```kotlin
@A
@B
inline annotation class Feature
```

The `inline` modifier communicates the same central idea as inline functions: the declaration is an abstraction whose recipe is substituted at use sites rather than represented there as an additional semantic annotation layer.

Kotlin's parser already accepts this modifier/declaration shape sufficiently for FIR to observe it. The current compiler rejects it later because `inline` is not an applicable modifier for annotation classes. Making the modifier legal is therefore a language/frontend change rather than a grammar invention.

An inline annotation class may still need annotations that describe the annotation declaration itself, such as `@Target`, `@Retention`, `@MustBeDocumented`, `@Repeatable`, `@RequiresOptIn`, `@DslMarker`, or framework/compiler meta-markers. These are distinct from annotations that form the inline recipe.

### Recipe annotations versus declaration annotations

A formal design must distinguish two roles for annotations written on an inline annotation class:

1. **recipe annotations**, which are substituted at use sites; and
2. **declaration annotations**, which describe the inline annotation class itself and are not substituted.

The following is a proposed deterministic baseline:

| Annotation on an inline annotation declaration | Default role |
| --- | --- |
| Kotlin language declaration-control annotations such as `@Target`, `@Retention`, `@MustBeDocumented`, `@Repeatable` | Declaration annotation |
| Annotation whose allowed target is only `ANNOTATION_CLASS` | Declaration annotation |
| Annotation that does not allow `ANNOTATION_CLASS` | Recipe annotation; newly legal because it is validated at expanded use sites |
| Annotation that allows `ANNOTATION_CLASS` **and** other declaration targets | Recipe annotation by default |

The last row is important for existing APIs such as AndroidX `@Preview`, which deliberately supports both annotation classes and functions and should naturally participate in an inline recipe.

The rule also leaves a genuine ambiguous case: a user-defined annotation may allow both `ANNOTATION_CLASS` and ordinary targets while being intended to describe the inline annotation declaration rather than be expanded. The final design needs an explicit escape hatch for that case instead of relying on framework heuristics. A dedicated use-site marker such as a future `@meta:` form is one possible design, but this proposal does **not** commit to that spelling.

Conversely, a rare annotation that targets only `ANNOTATION_CLASS` might intentionally be wanted as a constituent for an inline bundle whose consumers are annotation classes. That also requires an explicit opt-in if the target-based default above is adopted.

This distinction should be resolved as part of language design; it should not be hidden inside the implementation as a hardcoded list of third-party annotations.

### Expansion semantics

Given:

```kotlin
@A("a")
@B(2)
inline annotation class Feature

@Feature
fun operation() = Unit
```

Kotlin behaves as if the source declaration were:

```kotlin
@A("a")
@B(2)
fun operation() = Unit
```

The expansion is semantic rather than a literal text rewrite. Source PSI still contains `@Feature`; tooling should be able to report that expanded annotations originated from it.

The following rules apply:

1. Resolve the inline annotation use and its arguments.
2. Expand its recipe annotations recursively.
3. Remove the inline annotation use from the declaration's effective annotation set.
4. Apply ordinary annotation resolution, target validation, compiler-plugin semantics, retention, and backend lowering to the expanded annotations.
5. Preserve origin information so diagnostics and IDE navigation can point back through the inline annotation use and recipe declaration.

Expansion must happen early enough that the rest of the frontend and annotation-sensitive compiler plugins observe the constituents as if they were directly written.

This timing is essential. An IR-only transform would be insufficient for annotations such as `@Composable`, whose meaning is consumed by a compiler plugin during compilation.

### Targets and use-site targets

Recipe annotations do **not** need `AnnotationTarget.ANNOTATION_CLASS`.

They are validated against the actual expanded use site.

```kotlin
@Target(AnnotationTarget.FUNCTION)
annotation class FunctionOnly

@FunctionOnly
@Target(AnnotationTarget.FUNCTION)
inline annotation class FunctionFeature

@FunctionFeature
fun valid() = Unit
```

`FunctionOnly` is legal in the recipe because it is not semantically being applied to `FunctionFeature`; it is stored as an annotation expression to be substituted at uses of `FunctionFeature`.

The `@Target` on an inline annotation class constrains where the bundle itself may be written. After expansion, every constituent annotation is independently checked at the effective use site.

When no explicit use-site target is present, expansion happens first and Kotlin's ordinary default-target rules are applied to each constituent as if each had been written directly.

An explicit use-site target is propagated to the expanded annotations:

```kotlin
@get:Feature
val value: String
```

behaves as if each constituent annotation had been written with `@get:` at that position. A constituent that is not valid for that target produces the ordinary target diagnostic, augmented with its inline-annotation origin.

The same principle applies to the `all` target introduced by KEEP-0402: after expansion, the result should behave like separately writing `@all:A @all:B`, subject to the normal applicability rules for each annotation.

### Nested inline annotations

Inline annotations may contain other inline annotations:

```kotlin
@A
inline annotation class First

@First
@B
inline annotation class Second

@Second
fun operation() = Unit
```

expands recursively to:

```kotlin
@A
@B
fun operation() = Unit
```

The expansion graph must be acyclic. A cycle is a compile-time error and should report the chain of inline annotations involved.

Libraries may flatten recipes in metadata as an implementation detail, but source semantics are recursive composition.

### Duplicates and repeatable annotations

Inline annotations are intended to work as reusable defaults while still permitting local refinement.

For a **non-repeatable** annotation type, a directly written annotation takes precedence over occurrences contributed by inline annotations:

```kotlin
annotation class Cache(val seconds: Int)

@Cache(seconds = 60)
inline annotation class Cached

@Cache(seconds = 5)
@Cached
fun fastChangingValue() = Unit
```

The effective annotation is `@Cache(seconds = 5)`.

This rule avoids turning a reusable annotation recipe into an abstraction that cannot be locally overridden.

If two different inline annotations contribute the same non-repeatable annotation and there is no direct annotation to disambiguate them, normal duplicate-annotation diagnostics apply after expansion. The compiler does not silently choose one bundle over another.

Repeatable annotations accumulate instead:

```kotlin
@Repeatable
annotation class Tag(val value: String)

@Tag("generated")
inline annotation class Generated

@Tag("public")
@Generated
fun api() = Unit
```

The effective annotation sequence contains both `@Tag("public")` and `@Tag("generated")`, preserving source/expansion order.

These precedence rules are already exercised by the prototype.

### Parameters and argument forwarding

Inline annotation classes should be able to expose parameters and forward them into recipe annotation arguments. Without forwarding, composition is useful for fixed presets but cannot cover abstractions such as Spring's composed request mappings or configurable project annotations.

Proposed syntax uses ordinary annotation constructor parameters:

```kotlin
@A(name = name, enabled = enabled)
inline annotation class Feature(
    val name: String,
    val enabled: Boolean = true,
)

@Feature(name = "search")
fun search() = Unit
```

The effective annotation is:

```kotlin
@A(name = "search", enabled = true)
fun search() = Unit
```

Within recipe annotation argument expressions on an inline annotation class, primary-constructor parameters of that inline annotation class are in scope as compile-time placeholders.

The normal restrictions on annotation parameter types remain unchanged. Forwarding expressions must be evaluable as valid annotation arguments once the inline annotation arguments have been substituted. Parameters may also be forwarded into nested inline annotations.

For example:

```kotlin
@RequestMapping(path = [path], method = [RequestMethod.POST])
inline annotation class Post(
    val path: String = "",
)
```

would make:

```kotlin
@Post("/users")
fun createUser() = Unit
```

semantically equivalent to:

```kotlin
@RequestMapping(path = ["/users"], method = [RequestMethod.POST])
fun createUser() = Unit
```

The executable prototype currently proves fixed arguments but does not yet implement parameter forwarding. Forwarding is included here because it is important to the complete language design; it should be independently prototyped before stabilization.

### Retention and generated artifacts

The inline annotation use is a compile-time abstraction and is not emitted on the target declaration.

Each expanded constituent follows its own `AnnotationRetention` exactly as if directly written:

* `SOURCE` constituents remain source/compiler-only.
* `BINARY` constituents are emitted to the platform artifact where applicable.
* `RUNTIME` constituents remain available to runtime reflection where the backend supports it.

An inline annotation recipe itself must be retained in Kotlin metadata sufficiently for downstream Kotlin compilation, regardless of whether the bundle use is emitted to the platform artifact. This is compiler metadata, not runtime retention of the annotation use.

Consequently, runtime frameworks do not need to learn about inline annotations in order to observe the result. They see ordinary expanded runtime annotations.

### Cross-module behavior and metadata

Inline annotation classes are part of a library's compile-time API.

A consumer module must be able to use an inline annotation declared in a dependency without recompiling the dependency from source:

```kotlin
// library
@A
@B
inline annotation class LibraryFeature
```

```kotlin
// consumer
@LibraryFeature
fun operation() = Unit
```

The library's Kotlin metadata therefore needs to encode:

* that `LibraryFeature` is an inline annotation class;
* its recipe annotations;
* recipe argument expressions / parameter mappings;
* enough target and origin information to expand and diagnose the consumer source.

Kotlin 2.4 enables storing annotations in JVM Kotlin metadata by default, providing useful existing infrastructure for the prototype and a natural implementation direction. The language feature should nevertheless define backend-independent metadata semantics for Kotlin Multiplatform.

The current prototype proves cross-module expansion on JVM from a separately compiled library module.

Changing the recipe of a published inline annotation class is therefore analogous to changing inline function behavior: existing already-compiled consumers retain their previously expanded artifact behavior, while recompilation observes the new recipe. Tooling such as binary-compatibility validators may wish to treat recipe changes as compile-time behavioral API changes even when the JVM annotation class signature itself does not change.

### Compiler and tooling visibility

The key design requirement is that inline annotation expansion is a **frontend language semantic**, not an IR trick.

After expansion, consumers that ask for the effective annotations of a declaration should observe the constituent annotations:

* Kotlin frontend checks;
* compiler plugins;
* Analysis API;
* IDE inspections and intentions;
* KSP/compiler-symbol consumers where their semantic model exposes effective annotations;
* backend lowerings;
* documentation/tooling that deliberately requests expanded annotations.

Tooling should additionally be able to distinguish annotation origin:

* directly written;
* expanded from a specific inline annotation use;
* recursively expanded through a chain of inline annotations.

PSI/source-oriented APIs should continue to expose what was actually written in source. Semantic symbol APIs should expose effective annotations, ideally with origin metadata rather than forcing tools to repeat expansion themselves.

### Diagnostics

Diagnostics should be reported at the source location that the developer can act on.

For example, if `@Feature` expands to an annotation that is invalid on a property getter, the primary diagnostic should point to the `@Feature` use and identify the offending constituent:

```text
@Feature expands to @FunctionOnly, which is not applicable to property getter
```

IDE navigation should allow jumping from the diagnostic to the constituent annotation in the inline annotation declaration.

The compiler should diagnose at least:

* cyclic inline annotation expansion;
* invalid constituent target after expansion;
* unresolved or non-constant forwarded argument expressions;
* duplicate non-repeatable annotations after direct-annotation precedence is applied;
* invalid forwarding type or missing required inline-annotation argument;
* use of an inline annotation where its own declared target does not permit the source position.

## Examples

### Project-level annotation bundles

An inline annotation declaration can itself carry annotation-class-only declaration metadata while composing annotations intended for its consumers:

```kotlin
@RequiresOptIn(level = RequiresOptIn.Level.ERROR) // declaration metadata
@MustBeDocumented                                // declaration metadata
inline annotation class ExperimentalPaymentsApi
```

This example does not contain recipe annotations; it shows why the design must preserve ordinary annotation-declaration metadata even for an inline annotation class.

A more typical project bundle combines ordinary declaration controls and a recipe:

```kotlin
@Target(AnnotationTarget.FUNCTION)
@Audit("payments")
@RequiresPermission("payments:write")
inline annotation class PaymentOperation
```

### Compose compiler annotations

```kotlin
@Composable
@ReadOnlyComposable
inline annotation class ReadOnlyUi

@ReadOnlyUi
fun strings(): Strings = LocalStrings.current
```

This is not expressible as an ordinary annotation class today because neither `Composable` nor `ReadOnlyComposable` targets `ANNOTATION_CLASS`.

### Preview presets

```kotlin
@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
inline annotation class LightDarkPreview

@LightDarkPreview
@Composable
fun CardPreview() {
    Card()
}
```

This produces the same effective `@Preview` annotations without requiring Preview tooling to define a special recursive composition rule.

### Request mapping style composition

```kotlin
@RequestMapping(method = [RequestMethod.POST], path = [path])
inline annotation class Post(
    val path: String,
)

@Post("/users")
fun createUser() = Unit
```

The example mirrors the class of problem solved by Spring composed annotations and `@AliasFor`, while keeping the basic substitution visible to every compiler/tooling consumer.

## Compatibility and interoperability

### Source compatibility

`inline annotation class` is currently rejected by the compiler, so making it legal does not change the meaning of valid Kotlin source.

The old `inline class` spelling historically used for value classes is a separate declaration form. `inline annotation class` is syntactically unambiguous because `annotation class` is already a distinct declaration kind. The proposal should nevertheless be reviewed for parser, formatter, and diagnostic interactions with legacy inline-class syntax.

### Binary compatibility

Constituent annotations are emitted as ordinary annotations, so downstream runtime consumers do not require a new binary protocol.

The inline annotation recipe does require Kotlin metadata support for downstream compilation. Metadata-versioning behavior must follow the ordinary Kotlin rules: a compiler that does not understand the feature must not silently compile a use while dropping its semantics.

### Java interoperability

The semantic expansion is a Kotlin compiler feature. Compiled Kotlin declarations annotated through an inline annotation expose their expanded JVM annotations normally to Java and Java reflection.

Using an inline annotation class directly from Java source is more difficult: `javac` does not know Kotlin's inlining semantics. The first version of the feature should therefore treat **Java-source consumption of inline annotation classes as unsupported semantics**, rather than implying that a Java `@Feature` use will expand.

Possible JVM encodings and IDE diagnostics for accidental Java use require design work. This limitation is preferable to silently promising cross-language behavior that `javac` cannot implement.

### Multiplatform

The semantics are not JVM-specific. Expansion belongs in the common frontend and constituent annotations are then lowered by each backend according to their normal rules. Inline annotation recipes must therefore be represented in common/KLIB metadata as well as JVM metadata.

## Prototype

A Kotlin 2.4.10 / K2 proof of concept is available at:

https://github.com/macgills/Inline-Annotations-

Because an ordinary compiler plugin cannot make `inline` a legal modifier on annotation classes, the executable prototype uses a binary-retained `@InlineAnnotations` marker as a bootstrap encoding. The desired `inline annotation class` syntax is kept as a separate language-boundary fixture and intentionally still produces Kotlin's built-in modifier diagnostic.

The prototype performs expansion in FIR so that the experiment exercises the required frontend model rather than proving only a late JVM transformation.

Current executable evidence:

| Semantic property | Prototype |
| --- | --- |
| FIR-level expansion | Proven |
| Fixed constituent arguments | Proven |
| Nested/recursive bundles | Proven |
| Bundle use absent from emitted declaration | Proven |
| Class target | Proven |
| Constructor target | Proven |
| Function target | Proven |
| Field target | Proven |
| Property getter target | Proven |
| Value parameter target | Proven |
| Type parameter target | Proven |
| Constituent without `ANNOTATION_CLASS` target | Proven |
| Direct non-repeatable annotation overrides bundled value | Proven |
| Repeatable direct + bundled annotations accumulate | Proven |
| Cross-module bundle consumption | Proven |
| `inline annotation class` accepted by stock compiler | Requires language change |
| Parameter forwarding | Not yet prototyped |
| Recipe/declaration annotation disambiguation | Design required |
| Full target matrix including type/expression/file/typealias/local-variable cases | Not yet complete |
| Multiplatform metadata/backends | Not yet prototyped |
| Java-source consumption | Open design problem |

The point of the prototype is not to ship the compiler plugin as the feature. It demonstrates that annotation substitution fits K2's semantic model, identifies the precise language boundary that a plugin cannot cross, and provides executable tests for proposed precedence and cross-module behavior.

## Alternatives considered

### Ordinary meta-annotations

Rejected as insufficient. Meta-annotation presence is not substitution. Every consumer must opt into recursive discovery, constituents generally need `ANNOTATION_CLASS` as a legal target, and compiler-semantic annotations may need to be visible much earlier than runtime/framework discovery.

### A standard `@ComposeAnnotations` meta-annotation

For example:

```kotlin
@ComposeAnnotations
annotation class Feature
```

This could mark a bundle without adding a modifier. It still requires a special compiler-recognized annotation and makes a fundamental declaration semantic look like a library convention. `inline` communicates substitution directly in the language and matches the fact that the bundle use disappears from the effective annotation set.

The prototype uses such a marker only because compiler plugins cannot alter modifier applicability.

### Compiler plugins / KSP

Rejected as the language solution. A plugin can implement parts of the behavior but cannot establish universal ordering relative to other compiler plugins, cannot make all IDE/Analysis API consumers agree on the semantic annotation set, and cannot cleanly make constituent annotations legal in recipe position when they do not target `ANNOTATION_CLASS` without fighting built-in checks.

The prototype exists to validate implementation feasibility, not as a replacement for language support.

### Runtime recursive annotation lookup

This is the Spring-style model. It is appropriate when runtime merging itself is the desired framework feature, but too late for compiler-semantic annotations and requires every runtime/framework to agree on its own traversal, precedence, aliasing, and synthesis rules.

### Require every composable annotation to target `ANNOTATION_CLASS`

Rejected. Targeting an annotation declaration and being substitutable through a recipe are different semantics. It would broaden APIs solely to work around composition and would still not make ordinary Kotlin tooling treat meta-annotations as direct annotations.

### Annotation type aliases

A type alias can rename one annotation but does not naturally model a sequence of annotation applications, arguments, repeatability, or target-specific expansion. Inline annotation classes are closer to an inline function containing a small declarative recipe than to a type alias.

## Open design questions

The following do not block the core motivation but need resolution before stabilization:

1. **Recipe vs declaration annotations.** The target-based default proposed above handles common cases and lets `@Preview` naturally become a recipe constituent, but a syntax/semantic escape hatch is needed for ambiguous multi-target meta-annotations and rare annotation-class-only constituents.
2. **Parameter forwarding scope.** Bare primary-constructor parameter names are proposed inside recipe annotation arguments. The compiler prototype should validate name-resolution and constant-evaluation ergonomics before this syntax is finalized.
3. **Java source usage.** The JVM representation should make accidental `javac` use as unsurprising as possible, or tooling should strongly diagnose it.
4. **Recipe changes and binary tooling.** Kotlin should decide whether public inline-annotation recipe changes need explicit ABI/API reporting comparable to other inline implementation changes.
5. **Complete annotation-target matrix.** Type-use, expression, file, typealias, setter, local-variable, receiver/context-related, and backend-specific targets need specification tests.
6. **Semantic API shape.** Analysis API/KSP should expose both effective annotation values and expansion origin without conflating semantic annotations with raw PSI annotations.
7. **Ordering.** Repeatable annotation ordering should be specified across nested bundles and multiple bundle uses so every backend/tool observes a deterministic sequence.

## References

* Kotlin KEEP process: https://github.com/Kotlin/KEEP
* KEEP-0402, Improvements to annotation use-site targets on properties: https://github.com/Kotlin/KEEP/blob/main/proposals/KEEP-0402-annotation-target-in-properties.md
* AndroidX `@Preview` API: https://developer.android.com/reference/kotlin/androidx/compose/ui/tooling/preview/Preview
* AndroidX Compose MultiPreview documentation: https://developer.android.com/develop/ui/compose/tooling/previews#preview-multipreview
* AndroidX `@PreviewLightDark`: https://developer.android.com/reference/kotlin/androidx/compose/ui/tooling/preview/PreviewLightDark
* AndroidX `@Composable`: https://developer.android.com/reference/kotlin/androidx/compose/runtime/Composable
* AndroidX `@ReadOnlyComposable`: https://developer.android.com/reference/kotlin/androidx/compose/runtime/ReadOnlyComposable
* Spring MVC composed request mapping annotations: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-requestmapping.html
* Spring `@AliasFor`: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/core/annotation/AliasFor.html
* Spring `MergedAnnotations`: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/core/annotation/MergedAnnotations.html
* Spring meta-annotation support for testing: https://docs.spring.io/spring-framework/reference/testing/annotations/integration-meta.html
* Kotlin annotations-in-metadata issue KT-75736: https://youtrack.jetbrains.com/issue/KT-75736
