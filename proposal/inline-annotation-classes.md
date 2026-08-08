# Inline annotation classes

* Type: Design proposal
* Author: Seán Mac Gillicuddy
* Status: Draft
* Discussion: TBD
* Related YouTrack issue: TBD
* Prototype: https://github.com/macgills/Inline-Annotations

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

The inline annotation use itself is absent from the effective annotation set and generated declaration. Its constituents then follow the same target, retention, duplicate, repeatable, compiler-plugin, tooling, and backend rules as directly written annotations.

This proposal is intentionally narrow. It **does not** introduce parameter forwarding, aliasing, merging, or an annotation that exposes a new parameter surface assembled from its constituents. Constituent annotation arguments are fixed where the inline annotation class is declared.

## Motivation

Annotations are semantic inputs to compilers, IDEs, static analyzers, frameworks, test engines, dependency-injection systems, serializers, and runtime reflection. Repeated groups of annotations are therefore a natural target for abstraction.

Kotlin can place annotations on an annotation class, but that is only a meta-annotation relationship. Applying the outer annotation does not make its consumers directly annotated with the inner annotations. Every consumer that wants composition must invent its own recursive interpretation.

A language-level substitution primitive would make the simplest form of reusable annotation composition part of Kotlin semantics instead of a convention implemented independently by each tool.

### AndroidX Compose Preview already implements fixed composition in tooling

AndroidX Compose MultiPreview is a direct example of demand for this abstraction.

`@Preview` can annotate an annotation class, and Android Studio interprets a function using that custom annotation as indirectly carrying the contained previews. AndroidX ships reusable presets such as `@PreviewLightDark`, `@PreviewScreenSizes`, `@PreviewFontScales`, and `@PreviewDynamicColors`.

```kotlin
@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class PreviewLightDark

@PreviewLightDark
@Composable
fun GreetingPreview() = Greeting()
```

This works because Preview and Android Studio define a specialized recursive annotation model. Other annotation consumers do not automatically inherit those semantics.

With this proposal the fixed preset itself can be a Kotlin semantic:

```kotlin
@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
inline annotation class PreviewLightDark
```

After expansion Preview tooling sees the ordinary `@Preview` annotations it already understands.

### Spring independently built a broader composed-annotation model

Spring Framework is evidence of the same ecosystem pressure at a larger scale. Its composed annotations, `@AliasFor`, and `MergedAnnotations` provide recursive meta-annotation discovery, attribute overrides, aliasing, merging, and synthesis.

Spring solves a **broader problem** than this proposal. This proposal does not attempt to standardize Spring's parameter-aliasing or merged-annotation semantics. The relevant observation is simply that annotation composition is useful enough that a major ecosystem built another semantic annotation layer above the language model.

### Ordinary meta-annotations cannot cover compiler-semantic annotations

The strongest Kotlin-specific limitation is annotation targeting.

For example, Compose `@Composable` targets functions, types, type parameters, and property getters; `@ReadOnlyComposable` targets functions and property getters. Neither targets `ANNOTATION_CLASS`.

Therefore this cannot be expressed as an ordinary annotation class today:

```kotlin
@Composable
@ReadOnlyComposable
annotation class ReadOnlyUi // invalid today
```

But the intended fixed abstraction is meaningful:

```kotlin
@Composable
@ReadOnlyComposable
inline annotation class ReadOnlyUi

@ReadOnlyUi
fun currentTheme(): Theme = LocalTheme.current
```

The recipe annotations are not semantically being applied to `ReadOnlyUi`; they are stored for substitution onto the eventual use site. They therefore should not need to broaden their own APIs with `AnnotationTarget.ANNOTATION_CLASS` merely to participate in composition.

## Proposal

### Syntax

An annotation class may be marked `inline`:

```kotlin
@A("fixed")
@B(7)
inline annotation class Feature
```

`inline` communicates the same central idea as other Kotlin inline constructs: this declaration is a compile-time abstraction whose recipe is substituted at use sites rather than remaining as another semantic layer there.

Kotlin 2.4.10 already parses this declaration shape far enough for FIR to observe the modifier. The stock modifier-applicability checker currently rejects `inline` on annotation classes, so making it valid without suppression requires a language/frontend change rather than new grammar.

### Fixed recipe semantics

The recipe consists of concrete annotation applications written on the inline annotation declaration:

```kotlin
@A("fixed")
@B(7)
inline annotation class Feature
```

Every use of `@Feature` expands to the same applications and arguments.

There is no substitution from parameters of `Feature` into `A` or `B`. Parameter forwarding, aliasing, and annotation-parameter amalgamation are separate design problems and are not part of this proposal.

### Expansion semantics

Given:

```kotlin
@A("a")
@B(2)
inline annotation class Feature

@Feature
fun operation() = Unit
```

Kotlin behaves as though the function had been written:

```kotlin
@A("a")
@B(2)
fun operation() = Unit
```

Expansion is semantic rather than literal source rewriting. PSI still records `@Feature`, while semantic APIs should be able to expose the effective annotations and their expansion origin.

The compiler should:

1. resolve the inline annotation use;
2. recursively expand its recipe annotations;
3. remove the inline annotation use from the effective annotation set;
4. apply Kotlin's **ordinary annotation rules** to the resulting set;
5. preserve origin information for diagnostics and IDE navigation.

Inline annotation classes do not introduce a second precedence system. If expansion produces duplicate non-repeatable annotations, the same rules and diagnostics should apply as if those annotations had been written directly. Repeatable annotations accumulate according to existing language semantics.

Expansion must occur before annotation-sensitive compiler plugins and frontend tooling consume the declaration. An IR-only transformation is insufficient for compiler-semantic annotations such as `@Composable`.

### Targets

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

`FunctionOnly` is validated at the expanded function use, not as an annotation on the `FunctionFeature` declaration.

The `@Target` on the inline annotation class constrains where the bundle itself may be used. Each constituent is independently validated after expansion at its effective use site.

This is a required language change. The prototype crosses today's checker with an explicitly suppressed `WRONG_ANNOTATION_TARGET` diagnostic in the fixture that deliberately contains a `FUNCTION`-only constituent; the final feature must make recipe position legal directly.

### Use-site targets

The conceptual rule should remain substitutional: a use-site target on the bundle affects its expanded applications as though those applications had been written at that targeted source position.

```kotlin
@get:Feature
val value: String
```

The exact interaction with Kotlin's default target selection, `@all:`, receiver-related targets, and constituents with different applicable targets needs specification tests before stabilization. The current prototype does not claim the complete target matrix is proven.

### Nested inline annotations

Inline annotation recipes may contain other inline annotations:

```kotlin
@A
inline annotation class First

@First
@B
inline annotation class Second

@Second
fun operation() = Unit
```

expands recursively to `@A` and `@B`.

Expansion cycles must be a compile-time diagnostic. The prototype currently detects cycles with an internal assertion rather than a polished user-facing diagnostic; that is implementation scaffolding, not the proposed final behavior.

### Recipe annotations versus declaration annotations

An inline annotation class can also need annotations that describe **the annotation declaration itself**, rather than form part of its recipe. `@Target`, `@Retention`, `@MustBeDocumented`, and `@Repeatable` are examples of declaration controls that need defined behavior.

A final design must deterministically distinguish these roles.

The prototype hardcodes Kotlin's standard annotation-declaration controls as infrastructure and excludes them from expansion. That is sufficient to prove substitution but is **not** proposed as the complete language rule.

There is a genuine ambiguous case when a user-defined annotation is valid on both `ANNOTATION_CLASS` and ordinary declaration targets. It might be intended as declaration metadata or as a recipe constituent. The final design may need an explicit way to disambiguate these cases. This is the largest unresolved semantic question in the current draft.

### Retention and generated artifacts

The inline annotation use itself is a compile-time abstraction and should not be emitted on the target declaration.

Each expanded constituent follows its own retention exactly as if directly written:

* `SOURCE` remains source/compiler-only;
* `BINARY` is emitted where applicable;
* `RUNTIME` remains visible to runtime reflection where supported.

The **recipe**, however, must be available to downstream Kotlin compilation independently of those retention choices. A final implementation therefore needs dedicated compile-time recipe metadata rather than relying on runtime/binary visibility of constituent annotations.

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

The current JVM prototype proves that FIR can discover and expand a fixed recipe from a separately compiled module whose source declaration itself uses `inline annotation class`.

The prototype's artifact representation is intentionally temporary. Current Kotlin metadata does not encode this proposed declaration semantic, so the compiled-library fixture retains a prototype-only `@InlineAnnotations` **binary discovery marker** and stores recipe constituents as ordinary retained annotations that a downstream compiler plugin can recover.

That proof does **not** establish:

* cross-module expansion of `SOURCE`-retained recipe constituents;
* a dedicated metadata encoding for inline-annotation recipes;
* KLIB/common metadata;
* non-JVM backends.

A production language feature must encode the recipe independently of constituent retention and version it so older compilers cannot silently consume an inline annotation while dropping its semantics.

Changing the recipe of a published inline annotation is analogous to changing inline implementation behavior: already-compiled consumers retain their previously generated annotations, while recompilation observes the new recipe.

### Compiler and tooling visibility

Expansion is a frontend language semantic.

Semantic consumers should observe the effective constituent annotations, including:

* Kotlin frontend checks;
* compiler plugins;
* Analysis API;
* IDE inspections;
* symbol-processing APIs where they expose semantic annotations;
* backend lowerings.

Source-oriented APIs should continue to expose what was literally written. Semantic APIs should ideally preserve whether an annotation was direct or expanded and from which inline annotation use it originated.

### Diagnostics

Diagnostics should point at the actionable inline annotation use while identifying the offending constituent where useful.

The final compiler should diagnose at least:

* cyclic expansion;
* invalid constituent target after expansion;
* ordinary duplicate non-repeatable annotations produced by expansion;
* use of an inline annotation where its own target does not permit the source position.

The feature should reuse ordinary diagnostics wherever substitution naturally produces an existing Kotlin error rather than inventing bundle-specific semantics.

## Executable prototype

The executable Kotlin 2.4.10 / K2 proof is available at:

https://github.com/macgills/Inline-Annotations

The prototype compiles the **literal proposed source syntax** in ordinary `.kt` files. For example:

```kotlin
@file:Suppress("WRONG_MODIFIER_TARGET")

@First("expanded")
@Second(7)
inline annotation class Bundle

@Bundle
inline annotation class NestedBundle
```

The suppression is a prototype boundary, not proposed syntax. Kotlin already parses the modifier and exposes `status.isInline` in FIR; the built-in applicability checker is the remaining stock-language rejection. The FIR plugin observes that actual modifier, records the annotation class as a recipe, expands uses in the frontend, and normalizes the otherwise-invalid class status before later phases.

Same-module fixtures therefore do **not** use `@InlineAnnotations` as a source substitute.

The strongest cross-module fixture is also a real inline annotation class:

```kotlin
@file:Suppress("WRONG_MODIFIER_TARGET", "WRONG_ANNOTATION_TARGET")

@InlineAnnotations // prototype-only binary discovery metadata
@CrossModuleFirst("library") // FUNCTION-only target
@CrossModuleSecond(42)
inline annotation class LibraryBundle
```

`:bundle-library` is compiled with the plugin. `:sample` depends on that compiled module and uses only `@LibraryBundle`. Reflection verifies that the consumer receives `@CrossModuleFirst("library")` and `@CrossModuleSecond(42)` while `LibraryBundle` itself is absent.

The semantic implementation is FIR-first, with an IR backstop.

| Semantic property | Prototype |
| --- | --- |
| Literal `inline annotation class` source under the prototype plugin | **Proven** |
| FIR-level expansion | Proven |
| Fixed constituent arguments | Proven |
| Nested inline annotation recipes | Proven |
| Bundle absent from emitted declaration | Proven |
| Class target | Proven |
| Constructor target | Proven |
| Function target | Proven |
| Field target | Proven |
| Property getter target | Proven |
| Value parameter target | Proven |
| Type parameter target | Proven |
| Constituent without `ANNOTATION_CLASS` target | Proven |
| Repeatable accumulation | Proven |
| Cross-module consumption from a real inline annotation declaration | Proven on JVM for recoverable retained recipe annotations |
| Stock compiler accepts `inline annotation class` without the prototype suppression/plugin | Requires language change |
| Parameter forwarding / amalgamated parameter surface | **Out of scope** |
| Proper cycle diagnostic | Not yet implemented |
| Full target/use-site-target matrix | Not yet complete |
| Dedicated retention-independent recipe metadata | Not yet prototyped |
| Multiplatform metadata/backends | Not yet prototyped |
| Java-source consumption | Open interoperability problem |
| Recipe/declaration annotation disambiguation | Open language-design problem |

The important distinction is that the prototype now proves the **source shape and semantics together**. What remains unimplemented is the clean language-level removal of the two stock diagnostics and the production metadata representation—not the ability of the FIR plugin pipeline to consume the proposed syntax.

## Explicit non-goals and limitations

This proposal does **not** provide:

* parameters on an inline annotation that are forwarded into constituent annotation arguments;
* aliases between bundle parameters and constituent parameters;
* merging multiple constituent parameter sets into one synthesized annotation API;
* Spring-style attribute override, aliasing, distance, or merged-annotation rules;
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

Fixed substitution has useful independent value: MultiPreview-style presets, compiler-semantic annotation bundles, project policy bundles, testing/configuration presets, and cross-module reusable annotation recipes all work without introducing a new parameter-binding language.

## Compatibility and interoperability

### Source compatibility

`inline annotation class` is currently rejected by the stock modifier checker, so making it legal does not change the meaning of currently valid Kotlin source.

### Binary compatibility

Expanded constituents are ordinary backend annotations. Runtime consumers therefore require no new representation for the **result** of expansion.

Downstream Kotlin compilation does require a new metadata representation for the **recipe**. Metadata-versioning rules must ensure an older compiler cannot silently ignore the feature.

### Java interoperability

Compiled Kotlin declarations expose their expanded JVM annotations normally to Java reflection and Java consumers.

`javac` cannot perform Kotlin inline-annotation expansion when Java source directly uses an inline annotation class. Java-source use therefore remains an explicit interoperability problem for the language design. The initial implementation may need to prevent or strongly diagnose such use rather than silently give it different semantics.

### Multiplatform

The semantics belong in the common frontend rather than the JVM backend. A final implementation needs recipe metadata and tests for KLIB/common metadata and each relevant backend.

## Alternatives considered

### Ordinary meta-annotations

Insufficient. Every consumer must opt into recursive discovery, recipe constituents generally need `ANNOTATION_CLASS`, and compiler-semantic annotations need to be visible earlier than runtime traversal.

### Compiler-recognized marker annotation

A marker such as `@ComposeAnnotations` could identify bundles, but a modifier expresses substitution as a declaration semantic more directly.

The executable prototype uses the literal `inline` modifier in source. Its remaining `@InlineAnnotations` marker is only temporary **cross-module binary discovery metadata**, because current Kotlin metadata has no dedicated representation for the proposed recipe semantic. It is not evidence that the source feature needs a marker.

### Compiler plugins / KSP

A compiler plugin is sufficient to prototype the parsed source shape and substantial frontend semantics, as this repository demonstrates. It is not a complete language solution: a plugin cannot cleanly change Kotlin's built-in modifier/target language rules for all compilation contexts, define versioned language metadata, or establish semantics that every compiler, IDE, Analysis API consumer, backend, and older compiler must understand.

KSP is later still and cannot provide frontend semantics for compiler-sensitive annotations.

### Runtime recursive annotation lookup

Useful for frameworks such as Spring but too late for compiler-semantic annotations and forces each framework to define its own traversal and precedence model.

### Full Spring-style composed annotations

Explicitly outside the initial feature. Parameter aliasing, merging, and synthesized annotation APIs are significantly larger design problems. Fixed substitution is coherent and useful independently.

## Open design questions

1. How should ambiguous recipe annotations versus declaration/meta-annotations be explicitly disambiguated?
2. What exact semantic API should expose direct versus expanded annotation origin?
3. What is the precise target/use-site-target behavior across all Kotlin annotation targets?
4. How should Java source be prevented from silently using an inline annotation class without expansion semantics?
5. How should public recipe changes be represented in API/compatibility tooling?
6. What metadata representation cleanly preserves fixed recipes independently of constituent retention across JVM and KLIB?

Parameter forwarding is **not** an open question for this proposal; it is a non-goal.

## References

* Kotlin KEEP process: https://github.com/Kotlin/KEEP
* KEEP-0402, Improvements to annotation use-site targets on properties: https://github.com/Kotlin/KEEP/blob/main/proposals/KEEP-0402-annotation-target-in-properties.md
* AndroidX `@Preview`: https://developer.android.com/reference/kotlin/androidx/compose/ui/tooling/preview/Preview
* AndroidX MultiPreview: https://developer.android.com/develop/ui/compose/tooling/previews#preview-multipreview
* AndroidX `@PreviewLightDark`: https://developer.android.com/reference/kotlin/androidx/compose/ui/tooling/preview/PreviewLightDark
* AndroidX `@Composable`: https://developer.android.com/reference/kotlin/androidx/compose/runtime/Composable
* AndroidX `@ReadOnlyComposable`: https://developer.android.com/reference/kotlin/androidx/compose/runtime/ReadOnlyComposable
* Spring annotation package / merged annotation support: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/core/annotation/package-summary.html
* Spring `@AliasFor`: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/core/annotation/AliasFor.html
* Spring `MergedAnnotations`: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/core/annotation/MergedAnnotations.html
