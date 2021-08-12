package com.gaming.config.retrofit

import com.fasterxml.jackson.databind.ObjectMapper
import com.gaming.GameRankingApi
import io.github.resilience4j.core.IntervalFunction
import io.github.resilience4j.retrofit.RetryCallAdapter
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.jackson.JacksonConverterFactory
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Duration
import java.util.concurrent.TimeUnit

@Configuration
class RetrofitConfiguration {

    private val retry: Retry = Retry.of(
        "id",
        RetryConfig.custom<Response<String>>()
            .maxAttempts(3)
            .intervalFunction(IntervalFunction.ofExponentialBackoff(Duration.ofMillis(4000L), 2.0))
            .retryOnResult { response: Response<String> -> response.code() in listOf(HttpStatus.TOO_MANY_REQUESTS.value(), HttpStatus.BAD_GATEWAY.value()) }
//            .retryOnException(Predicate { e: Throwable? -> e is SocketTimeoutException  })
            .retryExceptions(IOException::class.java, SocketTimeoutException::class.java, ConnectException::class.java, UnknownHostException::class.java)
//            .ignoreExceptions(BusinessException::class.java, OtherBusinessException::class.java)
            .failAfterMaxAttempts(false)
            .build()
    )

    @Bean
    fun okHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS)
            .readTimeout(DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
            .build()
    }

    @Bean
    fun retrofit(okHttpClient: OkHttpClient, objectMapper: ObjectMapper): Retrofit {
        return Retrofit.Builder()
            .baseUrl("http://localhost:8080")
            .addCallAdapterFactory(RetryCallAdapter.of(retry))
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .addConverterFactory(JacksonConverterFactory.create(objectMapper))
            .client(okHttpClient)
            .build()
    }

    @Bean
    fun gameRankingApi(retrofit: Retrofit): GameRankingApi = retrofit.create(GameRankingApi::class.java)

    companion object {
        private const val DEFAULT_TIMEOUT = 200L
    }
}
