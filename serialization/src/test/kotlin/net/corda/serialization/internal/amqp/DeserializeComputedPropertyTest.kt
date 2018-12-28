package net.corda.serialization.internal.amqp

import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializedBytes
import net.corda.serialization.internal.AllWhitelist
import net.corda.serialization.internal.SerializationContextImpl
import net.corda.serialization.internal.amqp.testutils.*
import net.corda.serialization.internal.carpenter.ClassCarpenterImpl
import net.corda.serialization.internal.testutils.ClassistClassLoader
import org.junit.Test

data class TestStateCustomGetter(val foo: String): ContractState {
    override val participants: List<AbstractParty> get() = listOf()
}

class DeserializeContractStateWithCustomGetterTest {

    @Test
    fun `should deserialize subclass of ContractState when participants is a custom getter`() {

        val instance = TestStateCustomGetter("bar")
        val serialized = TestSerializationOutput(false, testDefaultFactory()).serialize(instance)

        val sf = SerializerFactoryBuilder.build(AllWhitelist, ClassCarpenterImpl(AllWhitelist, ClassistClassLoader(ClassLoader.getSystemClassLoader(), listOf(TestStateCustomGetter::class.java.name))))

        val context = SerializationContextImpl(
                preferredSerializationVersion = amqpMagic,
                deserializationClassLoader = ClassistClassLoader(ClassLoader.getSystemClassLoader(), TestStateCustomGetter::class.java.name),
                whitelist = AllWhitelist,
                properties = serializationProperties,
                objectReferencesEnabled = false,
                useCase = SerializationContext.UseCase.Testing,
                encoding = null)
        val bytes = SerializedBytes<ContractState>(serialized.bytes)
        // will throw
        DeserializationInput(sf).deserialize(bytes, context)
    }
}