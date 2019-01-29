package net.corda.core.context

import net.corda.core.utilities.contextLogger
import java.lang.reflect.Method
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlin.reflect.jvm.javaGetter

typealias ReadOnlyProperties = Map<Method, Any>
typealias WriteableProperties = MutableMap<Method, Any>

open class ContextWrapper<R : Any, W : Any>(val reader: (ContextData) -> R, val writer: (ContextDataDelta, R) -> W) {

    inline fun <V> current(block: R.() -> V): V =
        ExecutionContext.withCurrent { read(this@ContextWrapper, block) }

    inline fun <V> withExtended(extendBlock: W.() -> Unit, actionBlock: ExecutionContext.() -> V) {
        ExecutionContext.withExtended({ write(this@ContextWrapper, extendBlock) }, actionBlock)
    }

}

/**
 * Contains all of the data for an execution context.
 */
sealed class ContextData {

    /**
     * Read a property from the context's data.
     */
    abstract fun <T : Any> read(property: KProperty<T>, defaultValue: T? = null): T

    abstract val history: Sequence<ReadOnlyProperties>

    /**
     * Create new [ContextData] which extends this data with new values.
     */
    fun extend(newLocal: ReadOnlyProperties): ContextData = ContextData.Populated(newLocal, this)

    /**
     * [ContextData] with no values.
     */
    object Empty : ContextData() {
        override fun <T : Any> read(property: KProperty<T>, defaultValue: T?): T =
                defaultValue ?: throw IllegalArgumentException("No context value found for property $property")
        override val history: Sequence<ReadOnlyProperties> = emptySequence()
    }

    /**
     * [ContextData] populated with some values.
     *
     * @param local The values set within the current context.
     * @param previous Values set within previous contexts.
     */
    data class Populated(private val local: ReadOnlyProperties, private val previous: ContextData): ContextData() {
        override fun <T : Any> read(property: KProperty<T>, defaultValue: T?): T =
                local[property.javaGetter] as? T ?: previous.read(property, defaultValue)

        override val history: Sequence<ReadOnlyProperties> get() = previous.history + local
    }
}

/**
 * Represents a set of changes to a context's [ContextData]
 *
 * @param delta A mutable map of the values to be changed.
 * @param data The [ContextData] to which the changes will be applied.
 */
data class ContextDataDelta(private val delta: WriteableProperties) {

    /**
     * Read a value from the delta, or from the original context data if not present.
     */
    fun <T : Any> read(property: KProperty<T>): T? = delta[property.javaGetter] as? T

    /**
     * Write a value into the delta.
     */
    fun <T : Any> write(property: KProperty<T>, value: T) {
        delta[property.javaGetter!!] = value
    }

    /**
     * Merge the delta into its source [ContextData], producing a new [ContextData] set.
     */
    fun merge(data: ContextData): ContextData = data.extend(delta)

    /**
     * Create a delegate for reading/writing the value of this property out of a wrapped [ContextDataDelta].
     *
     * @param initializer An optional initializer that will be run on first read of the value, populating it
     * if it is `null`.
     */
    fun <R : Any, T : Any> updating(property: KProperty<T>): ReadWriteProperty<R, T> =
            ContextPropertyWriter(this, property)
}

/**
 * Represents a context of execution, together with its associated [ContextData].
 *
 * @param data The [ContextData] associated with this execution context.
 */
data class ExecutionContext(val data: ContextData) {

    companion object {

        val logger = contextLogger()

        /**
         * An [ExecutionContext] initialised with empty [ContextData]
         */
        val empty = ExecutionContext(ContextData.Empty)

        /**
         * The current [ExecutionContext].
         */
        val current = ThreadLocal<ExecutionContext>().apply { set(empty) }

        /**
         * Initialise an [ExecutionContext] using an [Extender] to write values into its [ContextData].
         *
         * This should only normally be used during program initialisation, as it completely overwrites the current context, instead of
         * storing it for the duration of an execution block and restoring it after the block has completed.
         */
        inline fun initialize(block: Extender.() -> Unit) = current.set(empty.write(block))

        /**
         * Obtain the current [ExecutionContext] and do something with it.
         */
        inline fun <R> withCurrent(block: ExecutionContext.() -> R): R = current.get().block()

        /**
         * Create an [ExecutionContext] whose [ContextData] has been modified using an [Extender], make it the current context, run the
         * provided block, and restore the original context afterwards.
         */
        inline fun <R> withExtended(configure: Extender.() -> Unit, block: ExecutionContext.() -> R): R {
            val old = current.get()
            val new = old.write(configure)
            try {
                current.set(new)
                return new.block()
            } finally {
                current.set(old)
            }
        }
    }

    /**
     * We cache readers, since readers themselves cache the results of lookups against the context data map.
     */
    private val readerCache = mutableMapOf<ContextWrapper<*, *>, Any>()

    /**
     * Obtain and cache a reader for a given [ContextWrapper].
     */
    fun <R : Any> getReader(wrapper: ContextWrapper<R, *>) =
        readerCache.getOrPut(wrapper as ContextWrapper<*, *>) { wrapper.reader(data) } as R

    /**
     * Wrap this context's [ContextData] in a typesafe wrapper, and read values from it via the wrapper.
     */
    inline fun <T : Any, R> read(wrapper: ContextWrapper<T, *>, block: T.() -> R): R =
        getReader(wrapper).block()

    /**
     * Obtain a new [ExecutionContext] whose [ContextData] has been extended using an [Extender].
     */
    inline fun write(block: Extender.() -> Unit): ExecutionContext {
        val extender = Extender(ContextDataDelta(mutableMapOf()), this)
        extender.block()
        return extender.createExtended()
    }

    /**
     * An [Extender] captures changes to an [ExecutionContext]'s [ContextData] via a [ContextDataDelta]
     */
    class Extender(val delta: ContextDataDelta, val source: ExecutionContext) {

        /**
         * Add modified values to the [ContextDataDelta] via a wrapper.
         */
        inline fun <R : Any, W: Any> write(wrapper: ContextWrapper<R, W>, configure: W.() -> Unit) {
            wrapper.writer(delta, source.getReader(wrapper)).configure()
        }

        /**
         * Obtain an [ExecutionContext] with the [ContextDataDelta] merged into its [ContextData].
         */
        fun createExtended(): ExecutionContext = ExecutionContext(delta.merge(source.data))
    }
}

/**
 * A delegate through which a property of a typesafe wrapper can be read from [ContextData], and written into a [ContextDataDelta].
 */
private class ContextPropertyWriter<R : Any, T : Any>(
        private val delta: ContextDataDelta,
        private val readerProperty: KProperty<T>): ReadWriteProperty<R, T> {

    override fun getValue(thisRef: R, property: KProperty<*>): T =
        delta.read(readerProperty) ?: readerProperty.call()

    override fun setValue(thisRef: R, property: KProperty<*>, value: T) {
        delta.write(readerProperty, value)
    }
}