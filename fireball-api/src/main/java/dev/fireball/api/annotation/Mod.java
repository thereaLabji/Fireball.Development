package dev.fireball.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a Fireball mod entrypoint.
 * The class must implement {@link dev.fireball.api.ModInitializer}
 * or have a no-arg constructor and public onInitialize methods.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Mod {
    /**
     * Unique mod identifier (e.g. "my_cool_mod")
     */
    String id();

    /**
     * Human-readable name
     */
    String name() default "";

    /**
     * Version string
     */
    String version() default "1.0.0";

    /**
     * Description
     */
    String description() default "";

    /**
     * Authors
     */
    String[] authors() default {};
}
