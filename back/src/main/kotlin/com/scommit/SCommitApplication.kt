package com.scommit

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaAuditing

@SpringBootApplication
@EnableJpaAuditing
class SCommitApplication

@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    runApplication<SCommitApplication>(*args)
}
