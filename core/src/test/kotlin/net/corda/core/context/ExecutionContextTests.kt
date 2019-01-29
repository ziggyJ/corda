package net.corda.core.context

import org.junit.Test
import kotlin.test.assertEquals

class ExecutionContextTests {

    // Enables us to store a set of tags in the execution context.
    object ContextTags : ContextWrapper<ContextTags.Reader, ContextTags.Writer>(::Reader, ::Writer) {

        /**
         * The Reader is responsible for pulling values out of the [ContextData] and making them readable
         * in a type-safe, null-safe way.
         */
        class Reader(data: ContextData) {
            // By convention, we use a reference to the property itself as the key into the data.
            val tags: Set<String> = data.read(::tags, emptySet())
        }

        /**
         * The Writer is responsible for providing a type-safe interface for updating values in a
         * [ContextDataDelta], that will then be merged into the [ContextData] of a child-context.
         */
        class Writer(delta: ContextDataDelta, reader: Reader) {
            // The same property reference is used as a key for updating the context data.
            var tags: Set<String> by delta.updating(reader::tags)
        }
    }

    @Test
    fun nestedContexts() {
        // Initialise a context with some tags
        ExecutionContext.initialize {
            write(ContextTags) { tags = setOf("foo", "bar") }
        }

        // Read the tags out of the current context
        assertEquals(setOf("foo", "bar"), ContextTags.current { tags })

        // Enter an extended context, writing more tags, and obtain the new tags from that context.
        ContextTags.withExtended({ tags += "baz" }) {
            assertEquals(setOf("foo", "bar", "baz"), ContextTags.current { tags })

            // Create a nested context, with one of the tags removed.
            ContextTags.withExtended({ tags -= "bar" }) {
                assertEquals(setOf("foo", "baz"), ContextTags.current { tags })
            }

            // Outside of the nested context, all of the tags are present.
            assertEquals(setOf("foo", "bar", "baz"), ContextTags.current { tags })
        }

        // Now we are outside of the extended context, tags revert to their originally configured value
        assertEquals(setOf("foo", "bar"), ContextTags.current { tags })
    }

    object Serialization : ContextWrapper<Serialization.Reader, Serialization.Writer>(::Reader, ::Writer) {

        class Reader(data: ContextData) {
            val classLoader: ClassLoader = data.read(::classLoader, ClassLoader.getSystemClassLoader())
            val lenientCarpenter: Boolean = data.read(::lenientCarpenter, false)
        }

        class Writer(delta: ContextDataDelta, reader: Reader) {
            var classLoader: ClassLoader by delta.updating(reader::classLoader)
            var lenientCarpenter: Boolean by delta.updating(reader::lenientCarpenter)
        }
    }

    @Test
    fun multipleContexts() {
        ExecutionContext.initialize {
            write(ContextTags) { tags += "serialization" }
            write(Serialization) { lenientCarpenter = false }
        }

        ExecutionContext.withExtended({
            write(ContextTags) { tags += "lenient carpentry enabled" }
            write(Serialization) { lenientCarpenter = true }
        }) {
            assertEquals(setOf("serialization", "lenient carpentry enabled"), ContextTags.current { tags })
            assertEquals(true, Serialization.current { lenientCarpenter })
        }

        assertEquals(setOf("serialization"), ContextTags.current { tags })
        assertEquals(false, Serialization.current { lenientCarpenter })
    }
}