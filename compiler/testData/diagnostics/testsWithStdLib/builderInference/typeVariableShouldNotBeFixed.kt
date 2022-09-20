// FILE: spr/Expr.java
package spr;

import org.jetbrains.annotations.NotNull;

public interface Exec<E> {
    boolean run(@NotNull Model model, @NotNull Processor<? super E> params);
}

// FILE: spr/foo.kt
package spr

open class Processor<P> {
    fun process(t: P): Boolean {
        return true
    }
}

class Model

fun <C> context(p: Processor<in C>, exec: Exec<C>) {}

fun <M> materialize(): Processor<M> = TODO()

private fun foo(model: Model) {
    materialize().apply {
        context(
            this,
            Exec { m, p -> p.process(m) } // Note: Builder inference
        )
    }
}
