package net.corda.serialization.internal.amqp.custom

import net.corda.core.serialization.SerializationCustomSerializer
import net.corda.core.utilities.ProgressTracker
import net.corda.serialization.internal.amqp.LocalSerializerFactory
import net.corda.serialization.internal.model.LocalPropertyInformation
import net.corda.serialization.internal.model.LocalTypeInformation
import java.io.NotSerializableException
import java.lang.reflect.Field
import java.lang.reflect.Method

class ProgressTrackerSerializer(val serializerFactory: LocalSerializerFactory) : SerializationCustomSerializer<ProgressTracker, SerializedProgressTracker> {
    override fun toProxy(obj: ProgressTracker): SerializedProgressTracker {
        val steps = obj.steps.map(::serializeStep)
        return SerializedProgressTracker(steps, obj.stepIndex)
    }

    private fun serializeStep(step: ProgressTracker.Step): SerializedStep {
        val stepClassName = step::class.java.name
        val typeInfo = serializerFactory.getTypeInformation(step::class.java)
                as? LocalTypeInformation.Composable ?:
                throw NotSerializableException("Cannot create instance of step class $stepClassName")

        val readers = typeInfo.properties.asSequence()
                .mapNotNull { (name, property) ->
                    when (property) {
                        is LocalPropertyInformation.GetterSetterProperty -> name to readWith(property.observedGetter)
                        is LocalPropertyInformation.ConstructorPairedProperty -> name to readWith(property.observedGetter)
                        is LocalPropertyInformation.PrivateConstructorPairedProperty -> name to readWith(property.observedField)
                        else -> null
                    }
                }.toMap()

        val values = readers.mapValues { (name, reader) -> reader(step) }
        return SerializedStep(stepClassName, values)
    }

    private fun readWith(method: Method): (Any) -> Any? {
        return { target -> method.invoke(target) }
    }

    private fun readWith(field: Field): (Any) -> Any? {
        return { target -> field.get(target) }
    }

    override fun fromProxy(proxy: SerializedProgressTracker): ProgressTracker {
        val steps = proxy.steps.map(::deserializeStep)
        val currentStep = steps[proxy.stepIndex]
        val result = ProgressTracker(*proxy.steps.map(::deserializeStep).toTypedArray())
        result.steps.forEach {
            val child = it.childProgressTracker()
            if (child != null) result.setChildProgressTracker(it, child)
        }
        result.currentStep = currentStep

        return result
    }

    private fun deserializeStep(step: SerializedStep): ProgressTracker.Step {
        val typeInfo = serializerFactory.getTypeInformation(step.concreteClassName)
            as? LocalTypeInformation.Composable ?:
            throw NotSerializableException("Cannot create instance of step class ${step.concreteClassName}")

        val constructorArgs = typeInfo.constructor.parameters.map { step.stepData[it.name] }.toTypedArray()
        val setters = typeInfo.properties.asSequence()
                .filter { (_, property) -> property is LocalPropertyInformation.GetterSetterProperty }
                .map { (name, property) -> name to (property as LocalPropertyInformation.GetterSetterProperty).observedSetter }
                .toMap()

        val instance = typeInfo.constructor.observedMethod.newInstance(*constructorArgs)
        setters.forEach { (name, setter) -> setter.invoke(instance, step.stepData[name]) }

        return instance as ProgressTracker.Step
    }
}

data class SerializedProgressTracker(val steps: List<SerializedStep>, val stepIndex: Int)

data class SerializedStep(val concreteClassName: String, val stepData: Map<String, Any?>)