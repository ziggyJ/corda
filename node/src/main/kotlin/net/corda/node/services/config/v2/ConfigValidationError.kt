package net.corda.node.services.config.v2

class ConfigValidationError(val keyName: String, val type: Class<*>? = null, val message: String, val containingPath: String? = null) {

    val path: String = containingPath?.let { parent -> "$parent.$keyName" } ?: keyName

    fun withContainingPath(containingPath: String?) = ConfigValidationError(keyName, type, message, containingPath)

    override fun toString(): String {

        return "(keyName='$keyName', type='$type', containingPath=$containingPath, path=$path, message='$message')"
    }
}