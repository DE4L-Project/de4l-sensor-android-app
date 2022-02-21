package io.de4l.app.auth

class UserInfo(val username: String, val roles: Map<String, List<String>>) {

    fun hasResourceRole(resource: String, role: String): Boolean {
        return roles[resource]?.contains(role) == true
    }

    fun isTrackOnlyUser(): Boolean {
        return true
    }
}