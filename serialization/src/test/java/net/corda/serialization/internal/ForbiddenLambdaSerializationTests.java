package net.corda.serialization.internal;

import com.google.common.collect.Maps;
import net.corda.core.serialization.*;
import net.corda.serialization.internal.amqp.SchemaKt;
import net.corda.testing.core.AMQPSerializationEnvironmentRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.NotSerializableException;
import java.io.Serializable;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.ThrowableAssert.catchThrowable;

public final class ForbiddenLambdaSerializationTests {
    @Rule
    public final AMQPSerializationEnvironmentRule testSerialization = new AMQPSerializationEnvironmentRule();
    private AMQPSerializationFactory factory;

    @Before
    public void setup() {
        factory = testSerialization.getSerializationFactory();
    }

    @Test
    public final void serialization_fails_for_serializable_java_lambdas() {
        AMQPSerializationContext context = testSerialization.getSerializationFactory().getDefaultContext();
        String value = "Hey";
        Callable<String> target = (Callable<String> & Serializable) () -> value;

        Throwable throwable = catchThrowable(() -> serialize(target, context));

        assertThat(throwable)
                .isNotNull()
                .isInstanceOf(NotSerializableException.class)
                .hasMessageContaining("Serializer does not support synthetic classes");
    }

    @Test
    @SuppressWarnings("unchecked")
    public final void serialization_fails_for_not_serializable_java_lambdas() {
        AMQPSerializationContext context = testSerialization.getSerializationFactory().getDefaultContext();
        String value = "Hey";
        Callable<String> target = () -> value;
        Throwable throwable = catchThrowable(() -> serialize(target, context));

        assertThat(throwable)
                .isNotNull()
                .isInstanceOf(NotSerializableException.class)
                .hasMessageContaining("Serializer does not support synthetic classes");
    }

    private <T> SerializedBytes<T> serialize(final T target, final AMQPSerializationContext context) {
        return factory.serialize(target, context);
    }
}
