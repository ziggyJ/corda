package net.corda.serialization.internal.testutils

/**
 * A class loader that excludes classes based on their [classNames].
 */
class ClassistClassLoader : ClassLoader {
    private val classNames: List<String>

    constructor(classLoader: ClassLoader, className: String) : super(classLoader) {
        this.classNames = listOf(className)
    }

    constructor(classLoader: ClassLoader, classNames: List<String>) : super(classLoader) {
        this.classNames = classNames
    }

    override fun loadClass(name: String?, resolve: Boolean): Class<*> {
        if(name != null && name in classNames) {
            throw ClassNotFoundException()
        }
        return super.loadClass(name, resolve)
    }
}