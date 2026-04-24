package io.github.qishr.cascara.schema.constraint;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface StringConstraint {
    String pattern() default ""; // Regex
    String[] options() default {}; // For EnumRule
    int minLength() default -1;
    int maxLength() default -1;
}