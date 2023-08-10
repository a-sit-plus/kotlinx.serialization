/*
 * Copyright 2017-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.serialization.internal

import kotlinx.serialization.*
import java.lang.reflect.*
import kotlin.reflect.*

@Suppress("NOTHING_TO_INLINE")
internal actual inline fun <T> Array<T>.getChecked(index: Int): T {
    return get(index)
}

@Suppress("NOTHING_TO_INLINE")
internal actual inline fun BooleanArray.getChecked(index: Int): Boolean {
    return get(index)
}

internal actual fun <T : Any> KClass<T>.compiledSerializerImpl(): KSerializer<T>? =
    this.constructSerializerForGivenTypeArgs()

@Suppress("UNCHECKED_CAST")
internal actual fun <T : Any, E : T?> ArrayList<E>.toNativeArrayImpl(eClass: KClass<T>): Array<E> =
    toArray(java.lang.reflect.Array.newInstance(eClass.java, size) as Array<E>)

internal actual fun KClass<*>.platformSpecificSerializerNotRegistered(): Nothing = serializerNotRegistered()

internal fun Class<*>.serializerNotRegistered(): Nothing {
    throw SerializationException(this.kotlin.notRegisteredMessage())
}

internal actual fun <T : Any> KClass<T>.constructSerializerForGivenTypeArgs(vararg args: KSerializer<Any?>): KSerializer<T>? {
    return java.constructSerializerForGivenTypeArgs(*args)
}

internal fun <T: Any> Class<T>.constructSerializerForGivenTypeArgs(vararg args: KSerializer<Any?>): KSerializer<T>? {
    if (isEnum && isNotAnnotated()) {
        return createEnumSerializer()
    }
    // Fall-through if the serializer is not found -- lookup on companions (for sealed interfaces) or fallback to polymorphic if applicable
    if (isInterface) interfaceSerializer()?.let { return it }
    // Search for serializer defined on companion object.
    val serializer = invokeSerializerOnDefaultCompanion<T>(this, *args)
    if (serializer != null) return serializer
    // Check whether it's serializable object
    findObjectSerializer()?.let { return it }
    // Search for default serializer if no serializer is defined in companion object.
    // It is required for named companions
    val fromNamedCompanion = findInNamedCompanion(*args)
    if (fromNamedCompanion != null) return fromNamedCompanion
    // Check for polymorphic
    return if (isPolymorphicSerializer()) {
        PolymorphicSerializer(this.kotlin)
    } else {
        null
    }
}

@Suppress("UNCHECKED_CAST")
private fun <T: Any> Class<T>.findInNamedCompanion(vararg args: KSerializer<Any?>): KSerializer<T>? {
    val namedCompanion = findNamedCompanionByAnnotation()
    if (namedCompanion != null) {
        invokeSerializerOnCompanion<T>(namedCompanion, *args)?.let { return it }
    }

    // fallback strategy for old compiler - try to locate plugin-generated singleton (without type parameters) serializer
    return try {
        declaredClasses.singleOrNull { it.simpleName == ("\$serializer") }
            ?.getField("INSTANCE")?.get(null) as? KSerializer<T>
    } catch (e: NoSuchFieldException) {
        null
    }
}

private fun <T: Any> Class<T>.findNamedCompanionByAnnotation(): Any? {
    val companionClass = declaredClasses.firstOrNull { clazz ->
        clazz.getAnnotation(NamedCompanion::class.java) != null
    } ?: return null

    return companionOrNull(companionClass.simpleName)
}

private fun <T: Any> Class<T>.isNotAnnotated(): Boolean {
    /*
     * For annotated enums search serializer directly (or do not search at all?)
     */
    return getAnnotation(Serializable::class.java) == null &&
            getAnnotation(Polymorphic::class.java) == null
}

private fun <T: Any> Class<T>.isPolymorphicSerializer(): Boolean {
    /*
     * Last resort: check for @Polymorphic or Serializable(with = PolymorphicSerializer::class)
     * annotations.
     */
    if (getAnnotation(Polymorphic::class.java) != null) {
        return true
    }
    val serializable = getAnnotation(Serializable::class.java)
    if (serializable != null && serializable.with == PolymorphicSerializer::class) {
        return true
    }
    return false
}

private fun <T: Any> Class<T>.interfaceSerializer(): KSerializer<T>? {
    /*
     * Interfaces are @Polymorphic by default.
     * Check if it has no annotations or `@Serializable(with = PolymorphicSerializer::class)`,
     * otherwise bailout.
     */
    val serializable = getAnnotation(Serializable::class.java)
    if (serializable == null || serializable.with == PolymorphicSerializer::class) {
        return PolymorphicSerializer(this.kotlin)
    }
    return null
}

private fun <T : Any> invokeSerializerOnDefaultCompanion(jClass: Class<*>, vararg args: KSerializer<Any?>): KSerializer<T>? {
    val companion = jClass.companionOrNull("Companion") ?: return null
    return invokeSerializerOnCompanion(companion, *args)
}

@Suppress("UNCHECKED_CAST")
private fun <T : Any> invokeSerializerOnCompanion(companion: Any, vararg args: KSerializer<Any?>): KSerializer<T>? {
    return try {
        val types = if (args.isEmpty()) emptyArray() else Array(args.size) { KSerializer::class.java }
        companion.javaClass.getDeclaredMethod("serializer", *types)
            .invoke(companion, *args) as? KSerializer<T>
    } catch (e: NoSuchMethodException) {
        null
    } catch (e: InvocationTargetException) {
        val cause = e.cause ?: throw e
        throw InvocationTargetException(cause, cause.message ?: e.message)
    }
}

private fun Class<*>.companionOrNull(companionName: String) =
    try {
        val companion = getDeclaredField(companionName)
        companion.isAccessible = true
        companion.get(null)
    } catch (e: Throwable) {
        null
    }

@Suppress("UNCHECKED_CAST")
private fun <T : Any> Class<T>.createEnumSerializer(): KSerializer<T> {
    val constants = enumConstants
    return EnumSerializer(canonicalName, constants as Array<out Enum<*>>) as KSerializer<T>
}

private fun <T : Any> Class<T>.findObjectSerializer(): KSerializer<T>? {
    // Check it is an object without using kotlin-reflect
    val field =
        declaredFields.singleOrNull { it.name == "INSTANCE" && it.type == this && Modifier.isStatic(it.modifiers) }
            ?: return null
    // Retrieve its instance and call serializer()
    val instance = field.get(null)
    val method =
        methods.singleOrNull { it.name == "serializer" && it.parameterTypes.isEmpty() && it.returnType == KSerializer::class.java }
            ?: return null
    val result = method.invoke(instance)
    @Suppress("UNCHECKED_CAST")
    return result as? KSerializer<T>
}

internal actual fun isReferenceArray(rootClass: KClass<Any>): Boolean = rootClass.java.isArray
