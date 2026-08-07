# Inline annotations

A proof of concept for treating an annotation declaration as a source-level bundle of annotations.

The proposed Kotlin syntax is:

```kotlin
@A("default")
@B
inline annotation class MyMeta

@MyMeta
fun operation() = Unit
```

with semantics equivalent to writing:

```kotlin
@A("default")
@B
fun operation() = Unit
```

The current prototype uses `@InlineAnnotations` instead of the `inline` modifier because a compiler plugin cannot add language syntax or change Kotlin's built-in annotation-target validation. It lowers bundle usages in IR and deliberately removes the bundle annotation itself, which lets the project prove binary/runtime substitution semantics before proposing the frontend language change.

## What the proof currently exercises

- fixed annotation arguments declared by the bundle
- recursive bundles
- removal of the bundle annotation from generated JVM output
- expansion on classes, constructors, functions, fields, getters, value parameters, and type parameters
- Kotlin 2.4.10 / K2

The eventual KEEP should make expansion a frontend language feature so target checking, overload resolution, compiler plugins, Analysis API, IDE inspections, and every Kotlin backend observe the expanded annotations as if they had been written directly at the use site.
