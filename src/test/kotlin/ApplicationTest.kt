package com.example
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ApplicationTest : FunSpec({
    test("simple example") {
        1 + 1 shouldBe 2
    }
})