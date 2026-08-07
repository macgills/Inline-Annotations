# Inline annotation classes

* Type: Design proposal
* Author: Seán Mac Gillicuddy
* Status: Draft
* Discussion: TBD
* Related YouTrack issue: TBD
* Prototype: https://github.com/macgills/Inline-Annotations-

## Abstract

Kotlin has no general way to define one annotation as a compile-time bundle of other annotation applications.

This proposal introduces **inline annotation classes**. An inline annotation class contains a fixed recipe of already-formed annotation applications. Applying the inline annotation causes the compiler to substitute those applications at the use site early in the frontend.

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

The inline annotation use itself is absent from the effective annotation set and from generated declarations. Its constituent annotations behave as if they had been written directly, including their own targets, retention and compiler-plugin semantics.

This proposal is intentionally narrow. It **does not** introduce parameter forwarding, aliasing, merging, or an annotation that exposes a new parameter surface assembled from its constituents. Constituent annotation arguments are fixed where the inline annotation class is declared.

AndroidX Compose Preview and Spring Framework are motivating evidence because both ecosystems have independently needed annotation composition. They are not claimed to be feature-equivalent: Spring's `@AliasFor`/`MergedAnnotations` model solves a broader runtime merging and parameter-aliasing problem that remains out of scope here.

## Motivation

Annotations are increasingly semantic inputs to compilers, IDEs, static analyzers, frameworks, test engines, dependency injection systems and runtime reflection.

The same groups of annotations are often repeated across declarations. Kotlin can place annotations on an annotation class, but that is only a meta-annotation relationship. It does not mean that consumers of the outer annotation are directly annotated with the inner annotations.

Frameworks that need composition therefore implement their own recursive interpretation. This produces multiple annotation models: Kotlin sees one thing, a compiler plugin may see another, an IDE special-cases another, and a runtime framework may synthesize yet another.

A language-level substitution primitive would make the simplest form of reusable annotation composition part of Kotlin semantics.

### AndroidX Compose Preview reinvented composition in tooling

AndroidX `@Preview` explicitly targets both `FUNCTION` and `ANNOTATION_CLASS`. Android Studio's MultiPreview feature treats a function annotated with a custom preview annotation as indirectly annotated with each contained `@Preview`.

For example:

```kotlin
@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class PreviewLightDark

@PreviewLightDark
@Composable
fun GreetingPreview() = Greeting()
```

AndroidX itself publishes reusable preview annotations using this model.

This is effectively a specialized annotation-composition system implemented by Preview tooling. Other annotation consumers do not automatically inherit those semantics.

With inline annotation classes the same fixed preset can be expressed as a Kotlin semantic:

```kotlin
@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
inline annotation class PreviewLightDark
```

The two `@Preview` applications are simply substituted at each use site.

### Spring reinvented composition in a runtime framework

Spring Framework has a much broader notion of composed annotations. APIs such as `@GetMapping` and `@PostMapping` are composed over `@RequestMapping`; Spring also provides `@AliasFor` and `MergedAnnotations` to forward, merge, override and synthesize annotation values at runtime.

That is strong evidence that reusable annotation composition is an important abstraction, but this proposal intentionally stops before Spring's parameter-aliasing model.

Inline annotation classes would solve only the fixed-recipe subset:

```kotlin
@RequestMapping(method = [RequestMethod.POST])
inline annotation class PostRequest
```

A parameterized abstraction such as a hypothetical `@Post("/users")` forwarding its argument into `@RequestMapping(path = ...)` is **not** proposed here.

Spring may continue to provide richer framework-specific merging semantics where needed.

### Ordinary meta-annotations cannot cover compiler-semantic annotations

A recipe constituent should not need to target `ANNOTATION_CLASS` merely because it is stored in an annotation bundle.

For example, Compose `@Composable` targets `FUNCTION`, `TYPE`, `TYPE_PARAMETER`, and `PROPERTY_GETTER`; `@ReadOnlyComposable` targets `FUNCTION` and `PROPERTY_GETTER`. Neither targets `ANNOTATION_CLASS`.

This therefore cannot be written today:

```kotlin
@Composable
@ReadOnlyComposable
annotation class ReadOnlyUi // invalid
```

With inline annotation classes:

```kotlin
@Composable
@ReadOnlyComposable
inline annotation class ReadOnlyUi

@ReadOnlyUi
fun currentTheme(): Theme = LocalTheme.current
```

is checked as if both annotations were written directly on `currentTheme`.

This is why the proposed feature is substitution rather than ordinary meta-annotation traversal.

## Proposal

### Syntax

An annotation class may be marked `inline`:

```kotlin
@A("fixed")
@B(7)
inline annotation class Feature
```

The `inline` modifier communicates that the declaration is a compile-time abstraction whose annotation recipe is substituted at use sites.

Kotlin currently parses this declaration shape far enough for FIR to observe the modifier, but the built-in modifier checker rejects `inline` on annotation classes. Making it legal requires a language/frontend change.

### Fixed recipe semantics

The recipe consists of concrete annotation applications written on the inline annotation declaration:

```kotlin
@A("fixed")
@B(7)
inline annotation class Feature
```

There is no parameter substitution from `Feature` into `A` or `B`.

In the initial proposal, an inline annotation class therefore has **no user-supplied annotation constructor parameters used to configure its recipe**. Every use of `@Feature` expands to the same recipe.

```kotlin
@Feature
fun first() = Unit

@Feature
fun second() = Unit
```

both expand to:

```kotlin
@A("fixed")
@B(7)
```

This limitation is deliberate. Parameter forwarding, aliasing and annotation amalgamation are separate design problems and can be proposed independently if there is sufficient demand.

### Recipe annotations versus declaration annotations

An inline annotation declaration may also need annotations that describe the annotation class itself rather than form part of the recipe, such as `@Target`, `@Retention`, `@MustBeDocumented`, and `@Repeatable`.

A final language design must deterministically distinguish these declaration-control annotations from recipe constituents.

A plausible baseline is:

| Annotation on inline annotation declaration | Default role |
| --- | --- |
| Kotlin declaration-control annotations such as `@Target`, `@Retention`, `@MustBeDocumented`, `@Repeatable` | Declaration annotation |
| Annotation whose allowed target is only `ANNOTATION_CLASS` | Declaration annotation |
| Annotation that does not allow `ANNOTATION_CLASS` | Recipe annotation |
| Annotation that allows `ANNOTATION_CLASS` and ordinary targets | Recipe annotation by default |

The last case lets existing annotations such as `@Preview` naturally participate in recipes.

Ambiguous user-defined meta-annotations may require an explicit escape hatch in the final language design. This proposal does not commit to syntax for that escape hatch.

### Expansion semantics

Given:

```kotlin
@A("a")
@B(2)
inline annotation class Feature

@Feature
fun operation() = Unit
```

Kotlin behaves as though the function were written:

```kotlin
@A("a")
@B(2)
fun operation() = Unit
```

Expansion is semantic rather than literal text rewriting. PSI still records `@Feature`; semantic APIs should be able to expose the expanded annotations and their origin.

The compiler should:

1. resolve the inline annotation use;
2. recursively expand its recipe annotations;
3. remove the inline annotation use from the declaration's effective annotation set;
4. run normal annotation target validation, compiler-plugin processing, retention and backend lowering on the expanded annotations;
5. retain origin information for diagnostics and IDE navigation.

Expansion must occur in the frontend before annotation-sensitive compiler plugins consume the declaration. An IR-only transformation is insufficient for compiler-semantic annotations such as `@Composable`.

### Targets and use-site targets

Recipe constituents do **not** need `AnnotationTarget.ANNOTATION_CLASS`.

```kotlin
@Target(AnnotationTarget.FUNCTION)
annotation class FunctionOnly

@FunctionOnly
@Target(AnnotationTarget.FUNCTION)
inline annotation class FunctionFeature

@FunctionFeature
fun valid() = Unit
```

`FunctionOnly` is validated at the expanded function use, not against the inline annotation declaration.

The `@Target` on the inline annotation class constrains where the bundle itself may be used. Each expanded constituent is then independently validated at that effective use site.

Explicit use-site targets propagate to constituents:

```kotlin
@get:Feature
val value: String
```

behaves as though each recipe annotation were directly written with `@get:` at that source position, subject to each constituent's ordinary applicability rules.

The same principle should apply to Kotlin's `all` use-site target.

### Nested inline annotations

Inline annotation recipes may include other inline annotations:

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

Cycles are compile-time errors.

### Duplicates and repeatable annotations

For a non-repeatable annotation, a directly written annotation takes precedence over the same annotation contributed by an inline recipe:

```kotlin
annotation class Cache(val seconds: Int)

@Cache(60)
inline annotation class Cached

@Cache(5)
@Cached
fun value() = Unit
```

The effective `@Cache` is `@Cache(5)`.

If multiple inline recipes contribute the same non-repeatable annotation and no direct annotation disambiguates them, normal duplicate-annotation diagnostics apply after expansion.

Repeatable annotations accumulate in deterministic source/expansion order.

These behaviors are already exercised by the prototype.

### Retention and generated artifacts

The inline annotation use itself is a compile-time abstraction and is not emitted on the target declaration.

Each expanded constituent follows its own retention exactly as though directly written:

* `SOURCE` remains source/compiler-only;
* `BINARY` is emitted where applicable;
* `RUNTIME` remains visible to runtime reflection where supported.

The inline recipe must be encoded in Kotlin metadata sufficiently for downstream Kotlin compilation. That compile-time metadata requirement is independent of whether the inline annotation use itself is emitted to a platform artifact.

### Cross-module behavior and metadata

A consumer must be able to use an inline annotation declared in a compiled dependency:

```kotlin
// library
@A("library")
@B(42)
inline annotation class LibraryFeature
```

```kotlin
// consumer
@LibraryFeature
fun operation() = Unit
```

Kotlin metadata therefore needs to encode:

* that `LibraryFeature` is an inline annotation class;
* its fixed recipe annotation applications and arguments;
* enough target/origin information to expand and diagnose consumer source.

No parameter-mapping expression language is required by this proposal because recipe arguments are fixed.

The current JVM prototype proves cross-module expansion from a separately compiled library module.

Changing a published inline annotation recipe is analogous to changing inline implementation behavior: already-compiled consumers retain their previous generated annotations, while recompilation observes the new recipe.

### Compiler and tooling visibility

Expansion is a frontend language semantic.

Semantic consumers should observe the effective constituent annotations, including:

* Kotlin frontend checks;
* compiler plugins;
* Analysis API;
* IDE inspections;
* symbol-processing APIs where they expose semantic annotations;
* backend lowerings.

Source-oriented APIs should continue to expose what was literally written, while semantic APIs should ideally preserve whether an annotation was direct or expanded and from which inline annotation use it originated.

### Diagnostics

Diagnostics should point at the actionable inline annotation use while identifying the offending constituent.

For example:

```text
@Feature expands to @FunctionOnly, which is not applicable to property getter
```

The compiler should diagnose at least:

* cyclic expansion;
* invalid constituent target after expansion;
* duplicate non-repeatable annotations after direct precedence;
* use of an inline annotation where its declared target does not permit the source position.

No diagnostics for forwarded parameters are needed because parameter forwarding is outside this proposal.

## Compatibility and interoperability

### Source compatibility

`inline annotation class` is currently rejected, so making it legal does not change the meaning of valid Kotlin source.

### Binary compatibility

Expanded constituents are emitted as ordinary annotations. Runtime consumers therefore require no new protocol.

Downstream Kotlin compilation does require metadata support for the inline recipe. Older compilers must not silently consume an inline annotation while dropping its semantics.

### Java interoperability

Compiled Kotlin declarations expose their expanded JVM annotations normally to Java reflection and Java consumers.

`javac` cannot perform Kotlin inline-annotation expansion when Java source directly uses an inline annotation class. Java-source consumption therefore remains an explicit interoperability limitation requiring design work.

### Multiplatform

The semantics are frontend/common rather than JVM-specific. A final implementation needs equivalent recipe metadata in KLIB/common metadata and backend tests beyond JVM.

## Prototype

The executable Kotlin 2.4.10 / K2 proof is available at:

https://github.com/macgills/Inline-Annotations-

Because a compiler plugin cannot make `inline` legal on annotation classes, the prototype uses a temporary `@InlineAnnotations` marker. The desired syntax is retained separately and intentionally still triggers Kotlin's built-in modifier diagnostic.

The semantic implementation is FIR-first.

| Semantic property | Prototype |
| --- | --- |
| FIR-level expansion | Proven |
| Fixed constituent arguments | Proven |
| Nested recipes | Proven |
| Bundle absent from emitted declaration | Proven |
| Class target | Proven |
| Constructor target | Proven |
| Function target | Proven |
| Field target | Proven |
| Property getter target | Proven |
| Value parameter target | Proven |
| Type parameter target | Proven |
| Constituent without `ANNOTATION_CLASS` target | Proven |
| Direct non-repeatable annotation precedence | Proven |
| Repeatable accumulation | Proven |
| Cross-module consumption | Proven |
| Stock compiler accepts `inline annotation class` | Requires language change |
| Parameter forwarding / amalgamated parameter surface | **Out of scope** |
| Full annotation-target matrix | Not yet complete |
| Multiplatform metadata/backends | Not yet prototyped |
| Java-source consumption | Open interoperability problem |
| Recipe/declaration annotation disambiguation | Design required |

The strongest proof is cross-module: `:bundle-library` declares a fixed recipe, `:sample` uses only its bundle annotation, and runtime reflection proves the compiled consumer contains the two constituent annotations with their fixed values while the bundle annotation itself is absent.

## Explicit non-goals and limitations

This proposal does **not** attempt to solve every problem associated with composed annotations.

In particular it does not provide:

* parameters on an inline annotation that are forwarded into constituent annotation arguments;
* aliases between inline-annotation parameters and constituent parameters;
* merging multiple constituent parameter sets into one synthesized annotation API;
* Spring-style attribute override, aliasing, distance or merged-annotation rules;
* arbitrary expressions for computing constituent arguments from bundle arguments;
* a general runtime annotation-synthesis API.

For example, this is intentionally **not** supported:

```kotlin
// NOT PART OF THIS PROPOSAL
@RequestMapping(path = [path])
inline annotation class Route(val path: String)

@Route("/users")
fun users() = Unit
```

The supported shape is a fixed preset:

```kotlin
@RequestMapping(path = ["/users"])
inline annotation class UsersRoute

@UsersRoute
fun users() = Unit
```

The narrower proposal has useful independent value: MultiPreview-style presets, compiler-semantic annotation bundles, project policy bundles, testing/configuration presets, and cross-module reusable annotation recipes all work without introducing a new parameter-binding language.

A future proposal could explore parameterized composition independently without making it a prerequisite for fixed annotation substitution.

## Alternatives considered

### Ordinary meta-annotations

Insufficient. Every consumer must opt into recursive discovery, constituents generally need `ANNOTATION_CLASS`, and compiler-semantic annotations need to be visible earlier than runtime traversal.

### Compiler-recognized marker annotation

A marker such as `@ComposeAnnotations` could identify bundles, but a modifier expresses substitution as a declaration semantic more directly. The prototype uses a marker only because compiler plugins cannot extend modifier applicability.

### Compiler plugins / KSP

Useful for experimentation but not a language solution. A plugin cannot make every compiler plugin, IDE consumer and semantic API agree on one effective annotation model or cleanly bypass ordinary annotation-target rules.

### Runtime recursive annotation lookup

Useful for frameworks such as Spring but too late for compiler-semantic annotations and forces every framework to invent traversal/precedence rules.

### Full Spring-style composed annotations

Explicitly rejected for the initial feature. Parameter aliasing, merging and synthesized annotation APIs are significantly larger design problems. Fixed substitution is coherent and useful on its own.

## Open design questions

1. How should ambiguous recipe annotations versus declaration/meta-annotations be explicitly disambiguated?
2. What exact semantic API should expose direct versus expanded annotation origin?
3. What is deterministic ordering across nested and repeated recipes?
4. What are the complete rules for every Kotlin annotation target, including type, expression, file, typealias, setter and local-variable cases?
5. How should Java source be prevented from silently using an inline annotation class without expansion semantics?
6. How should public recipe changes be represented in ABI/API compatibility tooling?

Parameter forwarding is **not** an open question for this proposal; it is a non-goal.

## References

* Kotlin KEEP process: https://github.com/Kotlin/KEEP
* KEEP-0402, Improvements to annotation use-site targets on properties: https://github.com/Kotlin/KEEP/blob/main/proposals/KEEP-0402-annotation-target-in-properties.md
* AndroidX `@Preview`: https://developer.android.com/reference/kotlin/androidx/compose/ui/tooling/preview/Preview
* AndroidX MultiPreview: https://developer.android.com/develop/ui/compose/tooling/previews#preview-multipreview
* AndroidX `@PreviewLightDark`: https://developer.android.com/reference/kotlin/androidx/compose/ui/tooling/preview/PreviewLightDark
* AndroidX `@Composable`: https://developer.android.com/reference/kotlin/androidx/compose/runtime/Composable
* AndroidX `@ReadOnlyComposable`: https://developer.android.com/reference/kotlin/androidx/compose/runtime/ReadOnlyComposable
* Spring request mapping composed annotations: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-requestmapping.html
* Spring `@AliasFor`: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/core/annotation/AliasFor.html
* Spring `MergedAnnotations`: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/core/annotation/MergedAnnotations.html
