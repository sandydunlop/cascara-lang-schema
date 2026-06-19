package io.github.qishr.cascara.schema.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SchemaDefinition {
    String title() default "";
    String titleKey() default "";
    String description() default "";
    String descriptionKey() default "";
}
