package net.corda.core.context

import org.junit.Test
import kotlin.test.assertEquals

class ExecutionContextTests {

    // Enables us to store a set of tags in the execution context.
    object ContextTag {
        class Reader(data: ContextData) {
            val tags: Set<String> by ContextPropertyReader(data, emptySet())
        }

        class Writer(delta: ContextDataDelta) {
            var tags: Set<String> by ContextPropertyWriter(delta, emptySet())

            fun add(vararg newTags: String) { tags = tags.plus(newTags) }
            fun remove(vararg toRemove: String) { tags = tags.minus(toRemove) }
        }
    }

    // Some convenience methods for reading and writing tags
    private inline fun <R> ExecutionContext.readTags(block: ContextTag.Reader.() -> R): R = read(ContextTag::Reader, block)
    private fun ExecutionContext.Extender.addTags(vararg tags: String) = write(ContextTag::Writer) { add(*tags) }
    private fun ExecutionContext.Extender.removeTags(vararg tags: String) = write(ContextTag::Writer) { remove(*tags) }
    private fun getCurrentTags() = ExecutionContext.withCurrent { readTags { tags } }

    @Test
    fun nestedContexts() {
        // Initialise a context with some tags
        ExecutionContext.initialize { addTags("foo", "bar") }

        // Read the tags out of the current context
        assertEquals(setOf("foo", "bar"), getCurrentTags())

        // Enter an extended context, writing more tags, and obtain the new tags from that context.
        ExecutionContext.withExtended({ addTags("baz") }) {
            assertEquals(setOf("foo", "bar", "baz"), getCurrentTags())

            // Create a nested context, with one of the tags removed.
            ExecutionContext.withExtended({ removeTags("bar") }) {
                assertEquals(setOf("foo", "baz"), getCurrentTags())
            }

            // Outside of the nested context, all of the tags are present.
            assertEquals(setOf("foo", "bar", "baz"), getCurrentTags())
        }

        // Now we are outside of the extended context, tags revert to their originally configured value
        assertEquals(setOf("foo", "bar"), getCurrentTags())
    }

    object Serialization {
        class Reader(data: ContextData) {
            val classLoader: ClassLoader by ContextPropertyReader(data, ClassLoader.getSystemClassLoader())
            val lenientCarpenter: Boolean by ContextPropertyReader(data, true)
        }

        class Writer(delta: ContextDataDelta) {
            var classLoader: ClassLoader by ContextPropertyWriter(delta, ClassLoader.getSystemClassLoader())
            var lenientCarpenter: Boolean by ContextPropertyWriter(delta, true)
        }
    }


    // Some convenience methods for reading and writing serialization properties
    private inline fun <R> ExecutionContext.readSerialization(block: Serialization.Reader.() -> R): R = read(Serialization::Reader, block)
    private inline fun ExecutionContext.Extender.writeSerialization(block: Serialization.Writer.() -> Unit) = write(Serialization::Writer, block)

    @Test
    fun multipleContexts() {
        ExecutionContext.initialize {
            addTags("serialization")

            writeSerialization {
                lenientCarpenter = false
            }
        }

        ExecutionContext.withExtended({
            writeSerialization { lenientCarpenter = true }
            addTags("lenient carpentry")
        }) {
            assertEquals(setOf("serialization", "lenient carpentry"), readTags { tags })
            assertEquals(true, readSerialization { lenientCarpenter })
        }

        assertEquals(setOf("serialization"), getCurrentTags())
        assertEquals(false, ExecutionContext.withCurrent { readSerialization { lenientCarpenter } })
    }
}