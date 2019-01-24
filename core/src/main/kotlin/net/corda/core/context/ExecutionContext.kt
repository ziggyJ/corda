package net.corda.core.context

import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Contains all of the data for an execution context.
 */
sealed class ContextData {

    /**
     * Read a property from the context's data.
     */
    abstract fun <T : Any> read(property: KProperty<T>): T?

    /**
     * Create new [ContextData] which extends this data with new values.
     */
    fun extend(newLocal: Map<String, Any?>): ContextData = ContextData.Populated(newLocal, this)

    /**
     * Create a [ContextDataDelta] which represents a set of changes to apply to [ContextData].
     */
    fun newDelta(): ContextDataDelta = ContextDataDelta(mutableMapOf(), this)

    /**
     * [ContextData] with no values.
     */
    object Empty : ContextData() {
        override fun <T : Any> read(property: KProperty<T>): T? = null
    }

    /**
     * [ContextData] populated with some values.
     *
     * @param local The values set within the current context.
     * @param previous Values set within previous contexts.
     */
    data class Populated(private val local: Map<String, Any?>, private val previous: ContextData): ContextData() {
        override fun <T : Any> read(property: KProperty<T>): T? = local[property.name] as? T ?: previous.read(property)
    }
}

/**
 * Represents a set of changes to a context's [ContextData]
 *
 * @param delta A mutable map of the values to be changed.
 * @param data The [ContextData] to which the changes will be applied.
 */
data class ContextDataDelta(private val delta: MutableMap<String, Any?>, private val data: ContextData) {
    /**
     * Read a value from the delta, or from the original context data if not present.
     */
    fun <T : Any> read(property: KProperty<T>): T? = delta[property.name] as? T ?: data.read(property)

    /**
     * Write a value into the delta.
     */
    fun <T : Any> write(property: KProperty<T>, value: T) {
        delta[property.name] = value
    }

    /**
     * Merge the delta into its source [ContextData], producing a new [ContextData] set.
     */
    fun merge(): ContextData = data.extend(delta)
}

/**
 * Represents a context of execution, together with its associated [ContextData].
 *
 * @param data The [ContextData] associated with this execution context.
 */
data class ExecutionContext(val data: ContextData) {

    companion object {
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
         * Obtain the current [ExecutionContext], mapping its [ContextData] into a typesafe wrapper, and do something with the wrapper.
         */
        inline fun <T : Any, R> withCurrent(wrapper: (ContextData) -> T, block: T.() -> R): R = withCurrent { read(wrapper, block) }

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
     * Wrap this context's [ContextData] in a typesafe wrapper, and read values from it via the wrapper.
     */
    inline fun <T : Any, R> read(wrapper: (ContextData) -> T, block: T.() -> R): R =
        wrapper(data).block()

    /**
     * Obtain a new [ExecutionContext] whose [ContextData] has been extended using an [Extender].
     */
    inline fun write(block: Extender.() -> Unit): ExecutionContext {
        val extender = Extender(data.newDelta())
        extender.block()
        return extender.createExtended()
    }

    /**
     * An [Extender] captures changes to an [ExecutionContext]'s [ContextData] via a [ContextDataDelta]
     */
    class Extender(val delta: ContextDataDelta) {

        /**
         * Add modified values to the [ContextDataDelta] via a wrapper.
         */
        inline fun <T : Any> write(wrapper: (ContextDataDelta) -> T, configure: T.() -> Unit) {
            wrapper(delta).configure()
        }

        /**
         * Obtain an [ExecutionContext] with the [ContextDataDelta] merged into its [ContextData].
         */
        fun createExtended(): ExecutionContext = ExecutionContext(delta.merge())
    }
}

/**
 * A delegate through which a property of a typesafe wrapper can be read from [ContextData].
 */
class ContextPropertyReader<R : Any, T : Any>(private val data: ContextData, val defaultValue: T? = null): ReadOnlyProperty<R, T> {

    private lateinit var actualValue: T

    override fun getValue(thisRef: R, property: KProperty<*>): T =
            if (::actualValue.isInitialized) actualValue
            else (data.read(property as KProperty<T>) ?: defaultValue ?:
                throw IllegalArgumentException("No context value found for property $property")).apply { actualValue = this }
}

/**
 * A delegate through which a property of a typesafe wrapper can be read from [ContextData], and written into a [ContextDataDelta].
 */
class ContextPropertyWriter<R : Any, T : Any>(private val delta: ContextDataDelta, val defaultValue: T? = null): ReadWriteProperty<R, T> {

    private lateinit var actualValue: T

    override fun getValue(thisRef: R, property: KProperty<*>): T =
            if (::actualValue.isInitialized) actualValue
            else (delta.read(property as KProperty<T>) ?: defaultValue ?:
            throw IllegalArgumentException("No context value found for property $property")).apply { actualValue = this }

    override fun setValue(thisRef: R, property: KProperty<*>, value: T) {
        delta.write(property as KProperty<T>, value)
        actualValue = value
    }
}