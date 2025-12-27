package net.refractored.aceemployments.commands.framework

/**
 * Annotation for command methods
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class CommandAnnotation(
    vararg val aliases: String
)

/**
 * Annotation for command permissions
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class PermissionAnnotation(
    val permission: String
)

/**
 * Annotation for command descriptions
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class DescriptionAnnotation(
    val description: String
)

/**
 * Annotation for tab completion
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class AutoCompleteAnnotation(
    val key: String
)

