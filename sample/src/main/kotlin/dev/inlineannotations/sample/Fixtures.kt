package dev.inlineannotations.sample

import dev.inlineannotations.InlineAnnotations

@Target(
    AnnotationTarget.ANNOTATION_CLASS,
    AnnotationTarget.CLASS,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.FIELD,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.TYPE_PARAMETER,
)
@Retention(AnnotationRetention.RUNTIME)
public annotation class First(val value: String)

@Target(
    AnnotationTarget.ANNOTATION_CLASS,
    AnnotationTarget.CLASS,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.FIELD,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.TYPE_PARAMETER,
)
@Retention(AnnotationRetention.RUNTIME)
public annotation class Second(val number: Int)

@InlineAnnotations
@First("expanded")
@Second(7)
@Target(
    AnnotationTarget.ANNOTATION_CLASS,
    AnnotationTarget.CLASS,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.FIELD,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.TYPE_PARAMETER,
)
@Retention(AnnotationRetention.RUNTIME)
public annotation class Bundle

@InlineAnnotations
@Bundle
@Target(
    AnnotationTarget.ANNOTATION_CLASS,
    AnnotationTarget.CLASS,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.FIELD,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.TYPE_PARAMETER,
)
@Retention(AnnotationRetention.RUNTIME)
public annotation class NestedBundle

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
public annotation class FunctionOnly(val value: String)

@Suppress("WRONG_ANNOTATION_TARGET")
@InlineAnnotations
@FunctionOnly("function-only constituent")
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
public annotation class FunctionBundle

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
public annotation class Named(val value: String)

@Suppress("WRONG_ANNOTATION_TARGET")
@InlineAnnotations
@Named("bundle")
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
public annotation class NamedBundle

@Repeatable
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
public annotation class Tag(val value: String)

@Suppress("WRONG_ANNOTATION_TARGET")
@InlineAnnotations
@Tag("bundle")
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
public annotation class TagBundle

@NestedBundle
public class Example @NestedBundle constructor(
    @NestedBundle public val input: String,
) {
    @get:NestedBundle
    @field:NestedBundle
    public val value: String = input

    @NestedBundle
    public fun run(@NestedBundle argument: String): String = argument

    public class Generic<@NestedBundle T>
}

@FunctionBundle
public fun functionOnlyTarget(): Unit = Unit

@Named("direct")
@NamedBundle
public fun directAnnotationWins(): Unit = Unit

@Tag("direct")
@TagBundle
public fun repeatableAnnotationsAccumulate(): Unit = Unit
