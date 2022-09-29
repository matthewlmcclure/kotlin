// FILE: Interface.java
public interface Interface {
    default void foo() {}
}

// FILE: InterfaceNoDefault.java
public interface InterfaceWithoutDefaultMethods {
    void bar()
}

// FILE: impl.kt
class Impl1: Interface {}

<!NO_OVERRIDE_FOR_DELEGATE_WITH_DEFAULT_METHOD!>class Impl2<!> : Interface by Impl1()

class Impl3 : Interface by Impl1() {
    override fun foo() {}
}

class Impl4: Interface {
    override fun foo() {}
}

<!NO_OVERRIDE_FOR_DELEGATE_WITH_DEFAULT_METHOD!>class Impl5<!> : Interface by Impl4()

class Impl6: InterfaceWithoutDefaultMethods {
    override fun bar() {}
}

class Impl7: InterfaceWithoutDefaultMethods by Impl6()
