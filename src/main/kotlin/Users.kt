package com.tahir.lost_and_found_app

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.sql.Connection
import java.sql.SQLException
import kotlinx.serialization.Serializable

@Serializable
data class DepartmentDto(val deptCode: String, val deptName: String)

@Serializable
data class SyncRequest(
    val userName: String,
    val deptCode: String,
    val userType: String = "STUDENT"
)

@Serializable
data class UserDto(
    val userId: String,
    val authUserId: String,
    val userName: String,
    val userEmail: String,
    val userType: String,
    val deptCode: String,
    val isVerified: Boolean
)

private fun findByAuthId(conn: Connection, authId: String): UserDto? =
    conn.prepareStatement(
        "SELECT user_id, auth_user_id, user_name, user_email, user_type, dept_code, is_verified " +
                "FROM user_profile WHERE auth_user_id = ?"
    ).use { ps ->
        ps.setString(1, authId)
        ps.executeQuery().use { rs ->
            if (rs.next()) UserDto(
                userId = rs.getString(1),
                authUserId = rs.getString(2),
                userName = rs.getString(3),
                userEmail = rs.getString(4),
                userType = rs.getString(5),
                deptCode = rs.getString(6),
                isVerified = rs.getInt(7) == 1
            ) else null
        }
    }

// Public: the sign-up screen needs this before anyone is logged in.
fun Route.departmentRoutes() {
    get("/departments") {
        val list = Db.query { conn ->
            conn.createStatement().use { st ->
                st.executeQuery("SELECT dept_code, dept_name FROM department ORDER BY dept_name").use { rs ->
                    buildList { while (rs.next()) add(DepartmentDto(rs.getString(1), rs.getString(2))) }
                }
            }
        }
        call.respond(list)
    }
}

// Protected: every route here needs a valid Supabase token.
fun Route.userRoutes() {
    post("/users/sync") {
        val p = call.principal<JWTPrincipal>()!!
        val authId = p.payload.subject
        val email = p.payload.getClaim("email").asString()?.trim()?.lowercase()

        if (email.isNullOrBlank() || !email.endsWith("@ue.edu.pk")) {
            call.respond(HttpStatusCode.Forbidden, ErrorResponse("Only @ue.edu.pk emails are allowed"))
            return@post
        }

        val req = call.receive<SyncRequest>()
        val name = req.userName.trim()
        val dept = req.deptCode.trim().uppercase()
        val type = req.userType.trim().uppercase()
        val userId = email.substringBefore('@')

        if (name.isEmpty() || name.length > 50 || type !in setOf("STUDENT", "STAFF") || userId.length > 30) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid name, type or email"))
            return@post
        }

        try {
            val user = Db.query { conn ->
                findByAuthId(conn, authId) ?: run {
                    conn.prepareStatement(
                        "INSERT INTO user_profile " +
                                "(user_id, auth_user_id, user_name, user_email, user_type, is_verified, dept_code) " +
                                "VALUES (?, ?, ?, ?, ?, 1, ?)"
                    ).use { ps ->
                        ps.setString(1, userId)
                        ps.setString(2, authId)
                        ps.setString(3, name)
                        ps.setString(4, email)
                        ps.setString(5, type)
                        ps.setString(6, dept)
                        ps.executeUpdate()
                    }
                    findByAuthId(conn, authId)!!
                }
            }
            call.respond(user)
        } catch (e: SQLException) {
            when (e.errorCode) {
                2291 -> call.respond(HttpStatusCode.BadRequest, ErrorResponse("Unknown department"))
                1 -> call.respond(HttpStatusCode.Conflict, ErrorResponse("This account already exists"))
                else -> throw e
            }
        }
    }

    get("/users/me") {
        val authId = call.principal<JWTPrincipal>()!!.payload.subject
        val user = Db.query { conn -> findByAuthId(conn, authId) }
        if (user == null) call.respond(HttpStatusCode.NotFound, ErrorResponse("No profile yet"))
        else call.respond(user)
    }
}