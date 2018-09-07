package net.corda.serialization.internal.amqp.custom

import net.corda.core.crypto.Crypto
import net.corda.core.serialization.AMQPSerializationContext
import net.corda.core.serialization.AMQPSerializationContext.UseCase.Storage
import net.corda.serialization.internal.amqp.*
import net.corda.serialization.internal.amqpCheckUseCase
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type
import java.security.PrivateKey
import java.util.*

object PrivateKeySerializer : CustomSerializer.Implements<PrivateKey>(PrivateKey::class.java) {

    private val allowedUseCases = EnumSet.of(Storage)

    override val schemaForDocumentation = Schema(listOf(RestrictedType(type.toString(), "", listOf(type.toString()), SerializerFactory.primitiveTypeName(ByteArray::class.java)!!, descriptor, emptyList())))

    override fun writeDescribedObject(obj: PrivateKey, data: Data, type: Type, output: SerializationOutput,
                                      context: AMQPSerializationContext
    ) {
        amqpCheckUseCase(allowedUseCases)
        output.writeObject(obj.encoded, data, clazz, context)
    }

    override fun readObject(obj: Any, schemas: SerializationSchemas, input: DeserializationInput,
                            context: AMQPSerializationContext
    ): PrivateKey {
        val bits = input.readObject(obj, schemas, ByteArray::class.java, context) as ByteArray
        return Crypto.decodePrivateKey(bits)
    }
}