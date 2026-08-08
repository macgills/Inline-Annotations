package dev.inlineannotations.metropoc

import dev.inlineannotations.metrorecipes.AppScope
import dev.inlineannotations.metrorecipes.AppScopedBinding
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.createGraph

public data class User(
    public val id: String,
    public val displayName: String,
)

public interface UserRepository {
    public fun currentUser(): User
}

@Inject
@AppScopedBinding
public class RealUserRepository : UserRepository {
    private val user = User(id = "42", displayName = "Ada")

    override fun currentUser(): User = user
}

public interface Analytics {
    public fun currentUserLabel(): String
}

@Inject
@AppScopedBinding
public class DefaultAnalytics(
    private val userRepository: UserRepository,
) : Analytics {
    override fun currentUserLabel(): String =
        "signed-in:${userRepository.currentUser().displayName}"
}

@DependencyGraph(AppScope::class)
public interface AppGraph {
    public val userRepository: UserRepository
    public val analytics: Analytics
}

public fun createAppGraph(): AppGraph = createGraph<AppGraph>()
