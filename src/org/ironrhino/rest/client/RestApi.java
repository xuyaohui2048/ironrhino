package org.ironrhino.rest.client;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.springframework.core.annotation.AliasFor;
import org.springframework.validation.annotation.Validated;

@Validated
@Retention(RUNTIME)
@Target(TYPE)
@Inherited
public @interface RestApi {

	@AliasFor("name")
	String value() default "";

	@AliasFor("value")
	String name() default "";

	String restTemplate() default "";

	String restClient() default "";

	String apiBaseUrl() default "";

	RequestHeader[] requestHeaders() default {};

	boolean treatNotFoundAsNull() default false;

	String dateFormat() default "";

}
