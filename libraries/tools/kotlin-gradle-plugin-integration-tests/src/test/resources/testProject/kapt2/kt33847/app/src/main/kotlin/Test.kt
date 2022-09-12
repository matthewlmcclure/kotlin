package com.example

@SomeAnnotation
object SomeClass {
    init {
        println(StringFactory.generateString())
    }
}

fun main(vararg args: String) {
    GeneratedSomeClass
}