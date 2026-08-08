# Inline annotation classes

Executable language-design evidence for fixed compile-time annotation composition in Kotlin.

The proposed syntax is:

```kotlin
@A("default")
@B
inline annotation class Feature

@Feature
fun operation() = Unit
```

with effective semantics equivalent to:

```kotlin
@A("default")
@B
fun operation() = Unit
```

This is **annotation substitution**, not ordinary meta-annotation lookup. Constituent annotations are expanded in the frontend and then follow Kotlin's ordinary annotation rules as though they had been written directly at the use site. The inline annotation use itself is not part of the effective/emitted annotation set.

## Scope

This proposal is deliberately limited to **fixed annotation recipes**.

The arguments of constituent annotations are written where the inline annotation class is declared and are reused unchanged at every use site.

It does **not** attempt to create a new annotation parameter API by amalgamating the parameters of its constituents. There is no parameter forwarding, aliasing, merging, or Spring-style `@AliasFor` behavior in the proposed feature.

Supported shape:

```kotlin
@A("fixed")
@B(7)
inline annotation class Feature

@Feature
fun operation() = Unit
```

Deliberately out of scope:

```kotlin
// NOT PROPOSED
@A(name = name)
inline annotation class Feature(val name: String)

@Feature("search")
fun operation() = Unit
```

Parameterized annotation composition can be considered independently later.

## Executable syntax proof

The prototype now compiles the **actual proposed source shape** in ordinary `.kt` files.

For example [`Fixtures.kt`](sample/src/main/kotlin/dev/inlineannotations/sample/Fixtures.kt) contains:

```kotlin
@First("expanded")
@Second(7)
@Target(/* ... */)
@Retention(AnnotationRetention.RUNTIME)
inline annotation class Bundle

@Bundle
@Target(/* ... */)
@Retention(AnnotationRetention.RUNTIME)
inline annotation class NestedBundle
```

Kotlin 2.4.10 already parses `inline annotation class` and exposes the modifier to FIR. Stock Kotlin then reports `WRONG_MODIFIER_TARGET`. The executable fixture suppresses that existing diagnostic at **file level** so the compiler plugin can observe `status.isInline`, register the declaration as an inline-annotation recipe, and normalize the status before later compiler phases.

That is prototype scaffolding, not proposed user syntax. A real language implementation would simply make `inline` applicable to annotation classes and require no suppression.

## Strongest cross-module proof

The strongest proof is deliberately cross-module and also crosses today's annotation-target boundary.

`:bundle-library` is itself compiled with the plugin and declares a real inline annotation class containing one annotation that is legal **only on functions**:

```kotlin
@file:Suppress("WRONG_MODIFIER_TARGET", "WRONG_ANNOTATION_TARGET")

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class CrossModuleFirst(val value: String)

@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class CrossModuleSecond(val number: Int)

@InlineAnnotations // prototype-only binary marker for downstream module discovery
@CrossModuleFirst("library")
@CrossModuleSecond(42)
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
inline annotation class LibraryBundle
```

The `@InlineAnnotations` marker is no longer a source-syntax substitute. It remains only as temporary **compiled-artifact metadata** so a separately compiled consumer can identify the recipe. A production implementation needs dedicated Kotlin recipe metadata instead.

`:sample` depends on that separately compiled library and writes only the bundle:

```kotlin
@LibraryBundle
fun crossModuleTarget() = Unit
```

The test reflects the compiled consumer:

```kotlin
@Test
fun compiledLibraryBundleExpandsInConsumerCompilation() {
    val method = Class.forName("dev.inlineannotations.sample.CrossModuleFixtureKt")
        .getDeclaredMethod("crossModuleTarget")

    assertEquals(
        "library",
        assertNotNull(method.getAnnotation(CrossModuleFirst::class.java)).value,
    )
    assertEquals(
        42,
        assertNotNull(method.getAnnotation(CrossModuleSecond::class.java)).number,
    )
    assertNull(method.getAnnotation(LibraryBundle::class.java))
}
```

That establishes five important properties at once:

1. the actual `inline annotation class` syntax is compiled by the prototype;
2. the recipe survives a separately compiled library boundary;
3. a constituent does not need `AnnotationTarget.ANNOTATION_CLASS` merely to participate in the recipe;
4. the consumer receives the constituent annotations and their fixed arguments; and
5. the bundle itself is absent from the emitted consumer declaration.

See [`LibraryBundle.kt`](bundle-library/src/main/kotlin/dev/inlineannotations/library/LibraryBundle.kt), [`CrossModuleFixture.kt`](sample/src/main/kotlin/dev/inlineannotations/sample/CrossModuleFixture.kt), and [`CrossModuleInlineAnnotationsTest.kt`](sample/src/test/kotlin/dev/inlineannotations/sample/CrossModuleInlineAnnotationsTest.kt).

## Real compiler-plugin integration: Metro

The repository also contains an executable integration against **Metro 1.3.2**, deliberately using a cohesive application policy rather than synthetic annotations.

The graph has public and authenticated `ApiClient` bindings of the same type. The authenticated binding must be both qualified and cached for the application lifetime. Without composition that policy is repeated on the provider:

```kotlin
@Provides
@Authenticated
@SingleIn(AppScope::class)
fun provideAuthenticatedApiClient(): ApiClient =
    RealApiClient(authorizationHeader = "Bearer demo-token")
```

The separately compiled `:metro-recipes` module names that fixed policy once:

```kotlin
@Authenticated
@SingleIn(AppScope::class)
inline annotation class AuthenticatedAppSingleton
```

and the Metro consumer becomes:

```kotlin
@Provides
@AuthenticatedAppSingleton
fun provideAuthenticatedApiClient(): ApiClient =
    RealApiClient(authorizationHeader = "Bearer demo-token")
```

`@Provides` and `@DependencyGraph` remain direct because they describe structural Metro declarations. The inline annotation expresses only the reusable qualifier + lifetime policy.

The runtime test creates Metro's generated graph and proves that Metro consumes both expanded semantics: it resolves the qualified `ApiClient` to the authenticated provider, returns the same authenticated instance on repeated access because of the expanded `@SingleIn(AppScope::class)`, emits the expanded `@Authenticated`, and does not emit `@AuthenticatedAppSingleton` itself.

Metro is unmodified; it is simply another compiler plugin consuming the effective annotations. See [the Metro integration proof](docs/metro-integration.md), [`metro-recipes`](metro-recipes), and [`metro-poc`](metro-poc).

## Proposal

* [KEEP-shaped design proposal](proposal/inline-annotation-classes.md)
* [Language Design YouTrack submission draft](proposal/youtrack-language-design.md)
* [Submission checklist](proposal/submission-checklist.md)
* [Language boundaries and prototype concessions](docs/language-boundary.md)

The current Kotlin KEEP process asks new language ideas to begin as a **Language Design YouTrack issue with concrete real-world use cases**, rather than as an unsolicited PR adding a new KEEP.

## Why this exists

Major frameworks repeatedly need annotation composition and have to implement it themselves:

* **AndroidX Compose Preview** supports MultiPreview annotations by allowing `@Preview` on annotation classes and teaching Android Studio to interpret their consumers as indirectly carrying the contained previews.
* **Spring Framework** implements a much richer composed-annotation model, including `@AliasFor` and `MergedAnnotations`. Spring is evidence of ecosystem demand, not the semantic scope of this proposal: parameter aliasing and merging remain outside this feature.
* Compiler-semantic annotations expose the limitation of ordinary meta-annotations even more clearly. Compose `@Composable` and `@ReadOnlyComposable` do not target `ANNOTATION_CLASS`, so they cannot be bundled using the ordinary annotation model at all.

The proposal makes fixed reusable composition itself a Kotlin semantic so compiler plugins, Analysis API, IDE tooling, backends, and symbol consumers do not each need to rediscover the same recipe.

## Prototype

The semantic prototype is implemented in **K2 FIR**, not as an IR-only trick.

The executable source uses real `inline annotation class` declarations. Two current Kotlin checks are crossed explicitly at file level:

- `WRONG_MODIFIER_TARGET`, because stock Kotlin has not yet made `inline` applicable to annotation classes;
- `WRONG_ANNOTATION_TARGET` in the cross-module fixture, because a `FUNCTION`-only recipe constituent is still initially seen by stock Kotlin as an ordinary meta-annotation.

Those suppressions expose the exact language rules the proposal asks Kotlin to change. The plugin then performs the proposed frontend substitution semantics.

Currently proven by executable tests on Kotlin 2.4.10:

- **real `inline annotation class` source compilation under the prototype plugin**
- FIR-level annotation expansion
- fixed constituent arguments
- recursive/nested inline annotation classes
- bundle removal from emitted declarations
- class, constructor, function, field, getter, value-parameter, and type-parameter targets
- a constituent that does **not** target `ANNOTATION_CLASS`
- repeatable annotations accumulating after expansion
- cross-module expansion from a separately compiled inline annotation class
- real third-party compiler-plugin consumption: Metro 1.3.2 resolves an expanded qualifier and scope in an executable dependency graph
- compiler-plugin discovery and CI

Known limitations / remaining design work:

- stock Kotlin still requires the file-level modifier suppression; the language feature must make `inline` applicable directly
- recipe-position target legality still requires the explicit target-checker suppression; the language feature must make recipe position legal directly
- the cross-module artifact still uses `@InlineAnnotations` as a temporary binary marker instead of dedicated recipe metadata
- an ordinary compiler plugin cannot emulate language-level expansion before every third-party FIR index/synthetic-declaration phase; the Metro fixture therefore keeps structural `@DependencyGraph` and `@Provides` annotations direct
- cycle handling in the prototype is an internal assertion rather than a user-facing compiler diagnostic
- recipe/declaration annotation disambiguation for ambiguous multi-target meta-annotations
- the complete annotation-target/use-site-target matrix
- dedicated recipe metadata independent of constituent retention, especially for `SOURCE` retention and KLIB/multiplatform
- Java-source consumption semantics
- semantic API representation of direct versus expanded annotation origin

Explicit non-goal:

- parameter forwarding or amalgamation of constituent annotation parameters into a new annotation parameter surface

After expansion, Kotlin's existing duplicate/repeatable rules should apply. The feature does not invent a separate bundle-precedence model.

## Run the proof

```bash
gradle --no-daemon clean test
```

CI runs the same command on every push and pull request.

## License

This repository is licensed under the **BSD Zero Clause License (`0BSD`)**. You may use, copy, modify, and distribute the code for any purpose, with no attribution requirement. See [LICENSE](LICENSE).
