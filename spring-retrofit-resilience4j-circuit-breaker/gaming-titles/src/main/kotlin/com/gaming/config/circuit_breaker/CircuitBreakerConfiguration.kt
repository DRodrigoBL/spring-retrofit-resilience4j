package com.gaming.config.circuit_breaker

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
class CircuitBreakerConfiguration {

    private val logger by lazy { LoggerFactory.getLogger(CircuitBreakerConfiguration::class.java) }

    @Bean
    fun circuitBreakerGlobalConfig(): CircuitBreakerConfig = CircuitBreakerConfig.custom()
        .failureRateThreshold(50F)
        .slowCallRateThreshold(20F)
        .slowCallDurationThreshold(Duration.ofMillis(500))
        .permittedNumberOfCallsInHalfOpenState(5)
        .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
        .slidingWindowSize(10)
        .minimumNumberOfCalls(10)
        .waitDurationInOpenState(Duration.ofMillis(4000))
        .automaticTransitionFromOpenToHalfOpenEnabled(false)
        .build()

    @Bean
    fun circuitBreakerGlobalRegistry(circuitBreakerGlobalConfig: CircuitBreakerConfig) = CircuitBreakerRegistry.of(circuitBreakerGlobalConfig)

    @Bean("gamingCircuitBreaker")
    fun gamingCircuitBreaker(circuitBreakerGlobalRegistry: CircuitBreakerRegistry) : CircuitBreaker {
        val circuitBreaker = circuitBreakerGlobalRegistry.circuitBreaker("gaming-circuit-breaker")
        circuitBreaker.eventPublisher.onStateTransition {
            logger.warn("CircuitBreaker - ${it.circuitBreakerName} state_transition from ${it.stateTransition.fromState.name} to ${it.stateTransition.toState.name}")
        }
        circuitBreaker.eventPublisher.onFailureRateExceeded {
            logger.warn("CircuitBreaker - ${it.circuitBreakerName} failure_rate_exceeded failure_rate=${it.failureRate}")
        }
        circuitBreaker.eventPublisher.onSlowCallRateExceeded {
            logger.warn("CircuitBreaker - ${it.circuitBreakerName} slow_call_rate_exceeded slow_call_rate=${it.slowCallRate}")
        }
        circuitBreaker.eventPublisher.onEvent {
            logger.warn("CircuitBreaker - ${it.circuitBreakerName} event=${it.eventType}")
        }
        return circuitBreaker
    }

}