package net.corda.serialization.internal.amqp

import net.corda.core.serialization.*
import net.corda.serialization.internal.AllWhitelist
import net.corda.serialization.internal.AMQPSerializationContextImpl
import net.corda.serialization.internal.amqp.testutils.serializationProperties
import net.corda.testing.core.AMQPSerializationEnvironmentRule
import org.hamcrest.CoreMatchers
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.Matchers
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import java.net.URLClassLoader
import java.util.concurrent.ThreadLocalRandom
import java.util.stream.IntStream

class AbstractAMQPSerializationSchemeTest {

    @Rule
    @JvmField
    val testSerialization = AMQPSerializationEnvironmentRule()

    @Test
    fun `number of cached factories must be bounded by maxFactories`() {
        val genesisContext = AMQPSerializationContextImpl(
                ClassLoader.getSystemClassLoader(),
                AllWhitelist,
                serializationProperties,
                false,
                AMQPSerializationContext.UseCase.RPCClient,
                null)


        val factory = TestSerializerFactory(TESTING_CONTEXT.whitelist, TESTING_CONTEXT.deserializationClassLoader)
        val maxFactories = 512
        val backingMap = AccessOrderLinkedHashMap<Pair<ClassWhitelist, ClassLoader>, SerializerFactory>({ maxFactories })
        val scheme = object : AbstractAMQPSerializationScheme(emptySet(), backingMap, createSerializerFactoryFactory()) {
            override fun rpcClientSerializerFactory(context: AMQPSerializationContext): SerializerFactory {
                return factory
            }

            override fun rpcServerSerializerFactory(context: AMQPSerializationContext): SerializerFactory {
                return factory
            }

            override fun canDeserializeVersion(target: AMQPSerializationContext.UseCase): Boolean {
                return true
            }

        }

        IntStream.range(0, 2048).parallel().forEach {
            val context = if (ThreadLocalRandom.current().nextBoolean()) {
                genesisContext.withClassLoader(URLClassLoader(emptyArray()))
            } else {
                genesisContext
            }
            val testString = "TEST${ThreadLocalRandom.current().nextInt()}"
            val serialized = scheme.serialize(testString, context)
            val deserialized = serialized.deserialize(context = context, serializationFactory = testSerialization.serializationFactory)
            Assert.assertThat(testString, `is`(deserialized))
            Assert.assertThat(backingMap.size, `is`(Matchers.lessThanOrEqualTo(maxFactories)))
        }
        Assert.assertThat(backingMap.size, CoreMatchers.`is`(Matchers.lessThanOrEqualTo(maxFactories)))
    }
}



