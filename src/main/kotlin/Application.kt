package com.tahir.lost_and_found_app

import configureAuth
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(val status: String, val db: String)

@Serializable
data class ErrorResponse(val error: String)

@Serializable
data class MeResponse(val authUserId: String?, val email: String?)

fun Application.module() {
    Db.init(environment.config)
    configureAuth()

    install(ContentNegotiation) { json() }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Unhandled error", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Server error"))
        }
    }

    routing {
        get("/health") {
            val dbReply = Db.query { conn ->
                conn.createStatement().use { st ->
                    st.executeQuery("SELECT 'OK' FROM dual").use { rs ->
                        rs.next()
                        rs.getString(1)
                    }
                }
            }
            call.respond(HealthResponse(status = "up", db = dbReply))
        }

        departmentRoutes()
        authenticate ("supabase"){
            userRoutes()
        }

        authenticate("supabase") {
            get("/me") {
                val p = call.principal<JWTPrincipal>()!!
                call.respond(
                    MeResponse(
                        authUserId = p.payload.subject,
                        email = p.payload.getClaim("email").asString()
                    )
                )
            }
        }
    }
}