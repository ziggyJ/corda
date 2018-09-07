package net.corda.serialization.internal.amqp.testutils;

import net.corda.core.serialization.AMQPSerializationContext;
import net.corda.serialization.internal.AMQPSerializationContextImpl;
import net.corda.serialization.internal.AllWhitelist;


import java.util.HashMap;
import java.util.Map;

public class TestSerializationContext {

    private static Map<Object, Object> serializationProperties = new HashMap<>();

    public static AMQPSerializationContext testSerializationContext = new AMQPSerializationContextImpl(
        ClassLoader.getSystemClassLoader(),
        AllWhitelist.INSTANCE,
        serializationProperties,
        false,
        AMQPSerializationContext.UseCase.Testing,
        null);
}
