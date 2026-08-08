# Metro integration proof

This module proves that inline annotation expansion is visible to another annotation-sensitive Kotlin compiler plugin: [Metro](https://zacsweers.github.io/metro/).

The interesting application-level pattern is a scoped contributed binding. Written directly, each implementation repeats Metro's scope twice:

```kotlin
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class RealAnalytics : Analytics

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class SystemClock : Clock
```

With an inline annotation class, the fixed project policy is declared once:

```kotlin
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Target(AnnotationTarget.CLASS)
inline annotation class AppSingletonBinding
```

and every implementation becomes:

```kotlin
@AppSingletonBinding
class RealAnalytics : Analytics

@AppSingletonBinding
class SystemClock : Clock
```

This does not require parameter forwarding. Metro already infers each contributed bound type from the class at the final use site, so the same fixed recipe can bind unrelated interfaces.

The module also proves two other Metro compiler paths:

```kotlin
@DependencyGraph(AppScope::class)
@Target(AnnotationTarget.CLASS)
inline annotation class AppGraph

@ContributesTo(AppScope::class)
@BindingContainer
@Target(AnnotationTarget.CLASS)
inline annotation class AppBindingContainer
```

`DemoGraph` is annotated only with `@AppGraph`, and `NetworkBindings` only with `@AppBindingContainer`. Metro must therefore observe the expanded `@DependencyGraph`, `@ContributesTo`, and `@BindingContainer` annotations in FIR for the module to compile and for `createGraph<DemoGraph>()` to work.

The runtime test additionally verifies that `@SingleIn` survived expansion by checking repeated graph accessors return the same instances, and that the inline bundle annotations themselves are absent from emitted consumer declarations.
