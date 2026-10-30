import com.auth0.jwk.JwkProviderBuilder
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import java.net.URI
import java.util.concurrent.TimeUnit

fun Application.configureAuth(){
    val supabaseUrl = environment.config.property("supabase.url").getString().trimEnd('/')
    val isUser = "$supabaseUrl/auth/v1"

    val jwkProvider = JwkProviderBuilder(URI("$isUser/.well-known/jwks.json").toURL())
        .cached(10,24, TimeUnit.HOURS)
        .rateLimited(10,1, TimeUnit.MINUTES)
        .build()

    install(Authentication) {
        jwt ("supabase"){
            realm = "lost-and-found"
            verifier (jwkProvider, isUser){
                withAudience("authenticated")
            }
            validate { credential ->
                if (credential.payload.subject != null) JWTPrincipal(credential.payload) else null

            }
        }
    }
}