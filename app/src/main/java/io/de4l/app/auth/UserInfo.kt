package io.de4l.app.auth

import io.de4l.app.BuildConfig

class UserInfo(val username: String, val roles: Map<String, List<String>>) {

    fun hasResourceRole(resource: String, role: String): Boolean {
        return roles[resource]?.contains(role) == true
    }

    fun isTrackOnlyUser(): Boolean {
        return hasResourceRole(BuildConfig.APP_CLIENT_RESOURCE, BuildConfig.TRACKING_ONLY_ROLE)
    }
}