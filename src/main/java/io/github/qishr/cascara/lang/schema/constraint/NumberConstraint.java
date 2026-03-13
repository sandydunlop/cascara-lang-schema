package io.github.qishr.cascara.lang.schema.constraint;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface NumberConstraint {
    double min() default Double.NEGATIVE_INFINITY;
    double max() default Double.POSITIVE_INFINITY;
}