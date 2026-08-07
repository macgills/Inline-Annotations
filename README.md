# Inline annotation classes

Executable language-design evidence for compile-time annotation composition in Kotlin.

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

This is **annotation substitution**, not ordinary meta-annotation lookup. Constituent annotations are expanded in the frontend and behave as though they were written directly at the use site. The inline annotation use itself is not part of the emitted/effective annotation set.

## Proof in one test

The strongest small proof is deliberately cross-module.

`:bundle-library` declares a bundle with two independently retained annotations:

```kotlin
@InlineAnnotations
@CrossModuleFirst("library")
@CrossModuleSecond(42)
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class LibraryBundle
```

`:sample` depends on that already-compiled library and writes only the bundle:

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

That establishes three important semantics at once:

1. the recipe survives a separately compiled library boundary;
2. the consumer receives the constituent annotations and their arguments; and
3. the bundle itself is absent from the emitted consumer declaration.

See [`LibraryBundle.kt`](bundle-library/src/main/kotlin/dev/inlineannotations/library/LibraryBundle.kt), [`CrossModuleFixture.kt`](sample/src/main/kotlin/dev/inlineannotations/sample/CrossModuleFixture.kt), and [`CrossModuleInlineAnnotationsTest.kt`](sample/src/test/kotlin/dev/inlineannotations/sample/CrossModuleInlineAnnotationsTest.kt).

## Proposal

* [KEEP-shaped design proposal](proposal/inline-annotation-classes.md)
* [Language Design YouTrack submission draft](proposal/youtrack-language-design.md)
* [Submission checklist](proposal/submission-checklist.md)
* [Why the prototype cannot make `inline annotation class` legal](docs/language-boundary.md)

The current Kotlin KEEP process asks new language ideas to begin as a **Language Design YouTrack issue with concrete real-world use cases**, rather than as an unsolicited PR adding a new KEEP. The documents above are structured so the YouTrack submission can lead into a formal KEEP if the language team accepts the direction.

## Why this exists

Major frameworks repeatedly need annotation composition and have to implement it themselves:

* **AndroidX Compose Preview** supports MultiPreview annotations by allowing `@Preview` on annotation classes and teaching Android Studio to treat their consumers as *indirectly annotated* with the contained previews.
* **Spring Framework** implements composed annotations, `@AliasFor`, and the `MergedAnnotations` model to recursively discover, merge, override, and synthesize annotation semantics at runtime.
* Compiler-semantic annotations expose the limitation of ordinary meta-annotations even more clearly. For example, Compose `@Composable` and `@ReadOnlyComposable` do not target `ANNOTATION_CLASS`, so they cannot be bundled using the ordinary annotation model at all.

The proposal makes the reusable composition itself a Kotlin semantic so compiler plugins, Analysis API, IDE tooling, backends, KSP/symbol consumers, and runtime frameworks do not each need to rediscover the same recipe.

## Prototype

An ordinary compiler plugin cannot change Kotlin's built-in modifier applicability rules, so the executable proof uses `@InlineAnnotations` as a temporary bootstrap marker. The intended `inline annotation class` form is retained in a language-boundary fixture and intentionally remains rejected by stock Kotlin.

The semantic prototype is implemented in **K2 FIR**, not as an IR-only trick.

Currently proven by executable tests on Kotlin 2.4.10:

- FIR-level annotation expansion
- fixed constituent arguments
- recursive/nested bundles
- bundle removal from emitted declarations
- class, constructor, function, field, getter, value-parameter, and type-parameter targets
- constituents that do **not** target `ANNOTATION_CLASS`
- direct non-repeatable annotations overriding bundled values
- repeatable annotations accumulating across direct and bundled uses
- cross-module expansion from a separately compiled library
- compiler-plugin discovery and CI

Still explicit proposal/prototype work:

- parameter forwarding from an inline annotation into constituent annotation arguments
- recipe/declaration annotation disambiguation for ambiguous multi-target meta-annotations
- the complete annotation-target matrix
- multiplatform metadata/backends
- Java-source consumption semantics

## Run the proof

```bash
gradle --no-daemon clean test
```

CI runs the same command on every push and pull request.

## License

This repository is licensed under the **BSD Zero Clause License (`0BSD`)**. You may use, copy, modify, and distribute the code for any purpose, with no attribution requirement. See [LICENSE](LICENSE).
