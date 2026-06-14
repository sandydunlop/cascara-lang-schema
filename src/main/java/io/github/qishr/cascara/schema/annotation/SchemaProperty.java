package io.github.qishr.cascara.schema.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface SchemaProperty {
    String title() default "";
    String titleKey() default "";
    String description() default "";
    String descriptionKey() default "";
    boolean required() default false;
}
