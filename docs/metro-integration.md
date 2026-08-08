# Metro integration proof

This repository includes a real integration fixture against **Metro 1.3.2** on Kotlin **2.4.10**.

The goal is not to hide Metro's structural DI annotations. It is to show a project-level policy that is otherwise repeated verbatim across bindings and let Metro consume the expanded annotations normally.

## The repeated policy

The sample has two `ApiClient` bindings: a public client and an authenticated client. The authenticated client must be both:

- qualified as `@Authenticated`, so Metro can distinguish it from the public `ApiClient`; and
- single in `AppScope`, so Metro caches it for the application graph lifetime.

Without inline annotation classes the provider repeats that policy directly:

```kotlin
@Provides
@Authenticated
@SingleIn(AppScope::class)
fun provideAuthenticatedApiClient(): ApiClient =
    RealApiClient(authorizationHeader = "Bearer demo-token")
```

The proposal lets the project name that fixed policy once:

```kotlin
@Authenticated
@SingleIn(AppScope::class)
inline annotation class AuthenticatedAppSingleton
```

and the provider becomes:

```kotlin
@Provides
@AuthenticatedAppSingleton
fun provideAuthenticatedApiClient(): ApiClient =
    RealApiClient(authorizationHeader = "Bearer demo-token")
```

`@Provides` remains direct because it describes the provider declaration itself. `AuthenticatedAppSingleton` expresses the reusable policy applied to that provider.

The graph is ordinary Metro code:

```kotlin
@DependencyGraph(AppScope::class)
interface AppGraph {
    val publicApiClient: ApiClient

    @Authenticated
    val authenticatedApiClient: ApiClient

    @Provides
    fun providePublicApiClient(): ApiClient =
        RealApiClient(authorizationHeader = null)

    @Provides
    @AuthenticatedAppSingleton
    fun provideAuthenticatedApiClient(): ApiClient =
        RealApiClient(authorizationHeader = "Bearer demo-token")
}
```

## What the executable test proves

`:metro-recipes` and `:metro-poc` are separate modules. The recipe is therefore recovered from a compiled dependency, not from the consumer source file.

The `metro-poc` test creates the generated Metro graph and verifies all of the observable semantics that matter:

1. Metro resolves the unqualified `ApiClient` to the public provider.
2. Metro resolves the `@Authenticated ApiClient` to the provider carrying the expanded qualifier.
3. repeated access to the authenticated client returns the same instance, proving Metro consumed the expanded `@SingleIn(AppScope::class)` lifetime;
4. the emitted provider method contains `@Authenticated`; and
5. the inline `@AuthenticatedAppSingleton` abstraction itself is absent from the emitted provider method.

Metro is not patched or taught about `AuthenticatedAppSingleton`. It receives the ordinary effective Metro annotations produced by the inline-annotation prototype.

## Prototype boundary exposed by Metro

Metro is also a useful stress test because some annotations participate in very early FIR indexing and synthetic declaration generation. The current implementation is an ordinary compiler plugin, so it cannot perfectly emulate a language feature that would expand annotations before those compiler-plugin indexes are built.

For that reason this fixture deliberately does **not** claim that every structural Metro annotation can already be hidden behind the prototype. `@DependencyGraph` and `@Provides` stay direct in the executable integration.

That limitation reinforces a requirement already present in the proposal: production inline-annotation expansion must be a frontend language semantic that happens before annotation-sensitive compiler plugins consume declarations. A Kotlin implementation would own that ordering; an ordinary third-party compiler plugin does not.

The integration sources are in [`metro-recipes`](../metro-recipes) and [`metro-poc`](../metro-poc).
