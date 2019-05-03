package net.corda.core.utilities

import net.corda.core.DeleteForDJVM
import net.corda.core.internal.concurrent.get
import net.corda.core.internal.uncheckedCast
import net.corda.core.serialization.CordaSerializable
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import kotlin.reflect.KProperty

/**
 * Usually you won't need this method:
 * * If you're in a companion object, use [contextLogger]
 * * If you're in an object singleton, use [LoggerFactory.getLogger] directly on javaClass
 *
 * Otherwise, this gets the [Logger] for a class using the syntax
 *
 * `private val log = loggerFor<MyClass>()`
 */
inline fun <reified T : Any> loggerFor(): Logger = LoggerFactory.getLogger(T::class.java)

/** When called from a companion object, returns the logger for the enclosing class. */
fun Any.contextLogger(): Logger = LoggerFactory.getLogger(javaClass.enclosingClass)

/** Log a TRACE level message produced by evaluating the given lamdba, but only if TRACE logging is enabled. */
inline fun Logger.trace(msg: () -> String) {
    if (isTraceEnabled) trace(msg())
}

/** Log a DEBUG level message produced by evaluating the given lamdba, but only if DEBUG logging is enabled. */
inline fun Logger.debug(msg: () -> String) {
    if (isDebugEnabled) debug(msg())
}

/**
 * A simple wrapper that enables the use of Kotlin's `val x by transient { ... }` syntax. Such a property
 * will not be serialized, and if it's missing (or the first time it's accessed), the initializer will be
 * used to set it up.
 */
fun <T> transient(initializer: () -> T): PropertyDelegate<T> = TransientProperty(initializer)



@CordaSerializable
private class TransientProperty<out T> internal constructor(private val initialiser: () -> T) : PropertyDelegate<T> {
    @Transient
    private var initialised = false
    @Transient
    private var value: T? = null

    @Synchronized
    override operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        if (!initialised) {
            value = initialiser()
            initialised = true
        }
        return uncheckedCast(value)
    }
}

/** @see NonEmptySet.copyOf */
fun <T> Collection<T>.toNonEmptySet(): NonEmptySet<T> = NonEmptySet.copyOf(this)

/** Same as [Future.get] except that the [ExecutionException] is unwrapped. */
@DeleteForDJVM
fun <V> Future<V>.getOrThrow(timeout: Duration? = null): V = try {
    get(timeout)
} catch (e: ExecutionException) {
    throw e.cause!!
}
