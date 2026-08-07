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

## Proof in one test

The strongest small proof is deliberately cross-module and crosses today's annotation-target boundary.

`:bundle-library` declares a bundle containing one annotation that is legal **only on functions**:

```kotlin
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class CrossModuleFirst(val value: String)

@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class CrossModuleSecond(val number: Int)

@Suppress("WRONG_ANNOTATION_TARGET") // prototype-only concession to today's compiler
@InlineAnnotations
@CrossModuleFirst("library")
@CrossModuleSecond(42)
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class LibraryBundle
```

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

That establishes four important properties at once:

1. the recipe survives a separately compiled library boundary;
2. a constituent does not need `AnnotationTarget.ANNOTATION_CLASS` merely to participate in the recipe;
3. the consumer receives the constituent annotations and their fixed arguments; and
4. the bundle itself is absent from the emitted consumer declaration.

See [`LibraryBundle.kt`](bundle-library/src/main/kotlin/dev/inlineannotations/library/LibraryBundle.kt), [`CrossModuleFixture.kt`](sample/src/main/kotlin/dev/inlineannotations/sample/CrossModuleFixture.kt), and [`CrossModuleInlineAnnotationsTest.kt`](sample/src/test/kotlin/dev/inlineannotations/sample/CrossModuleInlineAnnotationsTest.kt).

## Proposal

* [KEEP-shaped design proposal](proposal/inline-annotation-classes.md)
* [Language Design YouTrack submission draft](proposal/youtrack-language-design.md)
* [Submission checklist](proposal/submission-checklist.md)
* [Why the prototype cannot make `inline annotation class` legal](docs/language-boundary.md)

The current Kotlin KEEP process asks new language ideas to begin as a **Language Design YouTrack issue with concrete real-world use cases**, rather than as an unsolicited PR adding a new KEEP.

## Why this exists

Major frameworks repeatedly need annotation composition and have to implement it themselves:

* **AndroidX Compose Preview** supports MultiPreview annotations by allowing `@Preview` on annotation classes and teaching Android Studio to interpret their consumers as indirectly carrying the contained previews.
* **Spring Framework** implements a much richer composed-annotation model, including `@AliasFor` and `MergedAnnotations`. Spring is evidence of ecosystem demand, not the semantic scope of this proposal: parameter aliasing and merging remain outside this feature.
* Compiler-semantic annotations expose the limitation of ordinary meta-annotations even more clearly. Compose `@Composable` and `@ReadOnlyComposable` do not target `ANNOTATION_CLASS`, so they cannot be bundled using the ordinary annotation model at all.

The proposal makes fixed reusable composition itself a Kotlin semantic so compiler plugins, Analysis API, IDE tooling, backends, and symbol consumers do not each need to rediscover the same recipe.

## Prototype

An ordinary compiler plugin cannot change Kotlin's built-in modifier-applicability or annotation-target rules, so the executable proof uses `@InlineAnnotations` as a temporary bootstrap marker and one intentionally suppressed `WRONG_ANNOTATION_TARGET` diagnostic. The desired `inline annotation class` form is retained in a language-boundary fixture and intentionally remains rejected by stock Kotlin.

The semantic prototype is implemented in **K2 FIR**, not as an IR-only trick.

Currently proven by executable tests on Kotlin 2.4.10:

- FIR-level annotation expansion
- fixed constituent arguments
- recursive/nested bundles
- bundle removal from emitted declarations
- class, constructor, function, field, getter, value-parameter, and type-parameter targets
- a constituent that does **not** target `ANNOTATION_CLASS`
- repeatable annotations accumulating after expansion
- cross-module expansion from a separately compiled library
- compiler-plugin discovery and CI

Known limitations / remaining design work:

- the prototype uses a suppression to cross today's `ANNOTATION_CLASS` target check; the language feature must make recipe position legal directly
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
