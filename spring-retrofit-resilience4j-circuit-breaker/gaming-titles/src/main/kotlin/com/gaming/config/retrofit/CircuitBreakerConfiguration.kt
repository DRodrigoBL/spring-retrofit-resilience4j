package com.gaming.config.retrofit

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
class CircuitBreakerConfiguration {

    @Bean
    fun circuitBreakerGlobalConfig(): CircuitBreakerConfig = CircuitBreakerConfig.custom()
        .failureRateThreshold(50F)
        .slowCallRateThreshold(70F)
        .slowCallDurationThreshold(Duration.ofMillis(15000))
        .permittedNumberOfCallsInHalfOpenState(5)
        .maxWaitDurationInHalfOpenState(Duration.ofMillis(0))
        .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
        .slidingWindowSize(50)
        .minimumNumberOfCalls(10)
        .waitDurationInOpenState(Duration.ofMillis(200))
        .automaticTransitionFromOpenToHalfOpenEnabled(true)
        .build()

    @Bean
    fun circuitBreakerGlobalRegistry(circuitBreakerGlobalConfig: CircuitBreakerConfig) = CircuitBreakerRegistry.of(circuitBreakerGlobalConfig)

    @Bean("gamingCircuitBreaker")
    fun gamingCircuitBreaker(circuitBreakerRegistry: CircuitBreakerRegistry) : CircuitBreaker {
        val circuitBreaker = circuitBreakerRegistry.circuitBreaker("gaming-circuit-breaker")
        circuitBreaker.eventPublisher.onStateTransition {
            println("CircuitBreaker - ${it.circuitBreakerName} state_transition from ${it.stateTransition.fromState.name} to ${it.stateTransition.toState.name}")
        }
        return circuitBreaker
    }

}