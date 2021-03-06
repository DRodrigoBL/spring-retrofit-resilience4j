package com.gaming.config.retrofit

import org.slf4j.LoggerFactory
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.classify.Classifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpRequest
import org.springframework.http.HttpStatus
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse
import org.springframework.retry.RetryPolicy
import org.springframework.retry.backoff.ExponentialBackOffPolicy
import org.springframework.retry.policy.ExceptionClassifierRetryPolicy
import org.springframework.retry.policy.NeverRetryPolicy
import org.springframework.retry.policy.SimpleRetryPolicy
import org.springframework.retry.support.RetryTemplate
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestTemplate
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.Arrays
import java.util.Objects
import java.util.stream.Collectors


@Configuration
class SpringRetryConfig {

    @Bean
    fun restTemplate(restTemplateBuilder: RestTemplateBuilder): RestTemplate? {
        return restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(8))
                .interceptors(LoggingInterceptor())
                .build()
    }

    @Bean
    fun retryTemplate(): RetryTemplate {
        val retryPolicy = SimpleRetryPolicy()
        retryPolicy.maxAttempts = 3
        val backOffPolicy = ExponentialBackOffPolicy()
        backOffPolicy.initialInterval = 2000L
        backOffPolicy.maxInterval = 10000L
        backOffPolicy.multiplier = 2.0
        val template = RetryTemplate()
        template.setRetryPolicy(CustomRetryPolicy())
        template.setBackOffPolicy(backOffPolicy)
        return template
    }
}

internal class CustomRetryPolicy : ExceptionClassifierRetryPolicy() {
    init {
        val simpleRetryPolicy = SimpleRetryPolicy()
        simpleRetryPolicy.maxAttempts = 3
        this.setExceptionClassifier(object : Classifier<Throwable, RetryPolicy> {
            override fun classify(classifiable: Throwable): RetryPolicy {
                val cause = classifiable.cause.takeIf { it != null } ?: return NeverRetryPolicy()
                if (cause is  ResourceAccessException && cause.cause is SocketTimeoutException || cause.cause is ConnectException){
                    return simpleRetryPolicy
                }
                if (cause is HttpStatusCodeException && shouldRetryOnHttpStatusCodeException(cause)) {
                    return simpleRetryPolicy
                }
                return NeverRetryPolicy()
            }

            private fun shouldRetryOnHttpStatusCodeException(exc: HttpStatusCodeException): Boolean {
                return exc.rawStatusCode == HttpStatus.REQUEST_TIMEOUT.value() ||
                        exc.rawStatusCode == HttpStatus.TOO_MANY_REQUESTS.value() ||
                        exc.rawStatusCode == HttpStatus.BAD_GATEWAY.value() ||
                        exc.rawStatusCode == HttpStatus.GATEWAY_TIMEOUT.value()
            }

        })
    }
}

class LoggingInterceptor : ClientHttpRequestInterceptor {
    @Throws(IOException::class)
    override fun intercept(httpRequest: HttpRequest, bytes: ByteArray, clientHttpRequestExecution: ClientHttpRequestExecution): ClientHttpResponse {
        traceRequest(httpRequest, bytes)
        return clientHttpRequestExecution.execute(httpRequest, bytes)
    }

    /**
     * log request data.
     * @param request
     * @param body
     */
    @Throws(IOException::class)
    private fun traceRequest(request: HttpRequest, body: ByteArray) {
        Objects.requireNonNull(request, "HttpRequest cannot be null")
        val excludeHeaders = Arrays.asList(
                "AUTH_USER",
                "AUTHORIZATION"
        )
        val headers = request.headers.entries.stream()
                .filter { (key): Map.Entry<String, List<String?>?> -> !excludeHeaders.contains(key.toUpperCase()) }
                .collect(Collectors.toSet())
        log.debug("""
            ==================REQUEST BEGIN=================
            URI: {}
            METHOD: {}
            HEADERS: {}
            BODY: {}
            =================REQUEST END=================""",
                request.uri,
                request.method,
                headers,
                String(body, StandardCharsets.UTF_8))
    }

    companion object {
        private val log = LoggerFactory.getLogger(LoggingInterceptor::class.java)
    }
}