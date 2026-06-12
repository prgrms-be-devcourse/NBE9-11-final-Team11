package com.fxflow

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class FxflowApplication

fun main(args: Array<String>) {
	runApplication<FxflowApplication>(*args)
}
