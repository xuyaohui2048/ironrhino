package org.ironrhino.core.metadata;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Target(METHOD)
@Retention(RUNTIME)
public @interface Hidden {

	boolean value() default false;

	boolean hideWhenBlank() default false;

	String expression() default "";

}