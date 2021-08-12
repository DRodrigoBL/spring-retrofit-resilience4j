# Spring Boot + Retrofit + Resilience4j Retry

This project briefly shows how you can make use of Spring Boot, Retrofit & Resilience4j Retry module to have a more resilient service-to-service communication within a microservices environment.

## Architecture
The project architecture is quite simple. Just spring boot applications **gaming-titles** & **games-ranking** that communicate each other via HTTP with help of **Retrofit & OkHttp** technologies.

IMAGE HERE

## games-ranking
This service will only expose one REST API that responds with the corresponding stars ranking for a given **gameTitleId**
```kotlin
GET /api/game-ranking/rank/{gameTitleId}

data class GameRanking(val gameTitleId: Long, val stars: Int?)
```
But to simulate the several responses such as
> HTTP_TOO_MANY_REQUESTS(429)
> HTTP_BAD_GATEWAY(502)
> HTTP_INTERNAL_SERVER_ERROR(500)

The controller will generate this responses if certain **gameTitleId** is received or even sleep for a given milliseconds to trigger a **timeout**
```kotlin
@RestController  
@RequestMapping("/api/game-ranking")  
class GamesRankingController(private val gameRankingService: GameRankingService) {  
  
    private val logger by lazy { LoggerFactory.getLogger(GamesRankingController::class.java.simpleName) }  
  
  @GetMapping("rank/{gameTitleId}")  
    fun getGameRanking(@PathVariable("gameTitleId") gameTitleId: Long): ResponseEntity<GameRanking> {  
  
        if (gameTitleId == 0L) {  
            logger.info("Response as ${HttpStatus.TOO_MANY_REQUESTS} for gameTitleId=$gameTitleId")  
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build()  
        }  
        if (gameTitleId == 1L) {  
            logger.info("Response as ${HttpStatus.INTERNAL_SERVER_ERROR} for gameTitleId=$gameTitleId")  
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()  
        }  
        if (gameTitleId == 2L) {  
            logger.info("Response as ${HttpStatus.BAD_GATEWAY} for gameTitleId=$gameTitleId")  
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build()  
        }  
  
        if (gameTitleId == 3L) {  
            val sleepMillis = 15000L  
            logger.info("Sleeping for $sleepMillis ms")  
            Thread.sleep(sleepMillis)  
        }  
  
        val result = gameRankingService.getGameRanking(gameTitleId)  
        return if (result.isPresent) {  
            logger.info("Response as ${HttpStatus.OK} for gameTitleId=$gameTitleId")  
            ResponseEntity.ok(result.get())  
        } else {  
            logger.info("Response as ${HttpStatus.NOT_FOUND} for gameTitleId=$gameTitleId")  
            ResponseEntity.notFound().build()  
        }  
    }
```
## gaming-titles
This other service will consume the REST API listed above with help of **Retrofit** configured with **Resilience4j Retry**
For such integration we need to have in our classpath the following dependencies:
```kotlin
implementation("com.squareup.retrofit2:retrofit:2.9.0")  
implementation("com.squareup.retrofit2:converter-jackson:2.9.0")  
implementation("com.squareup.retrofit2:adapter-rxjava2:2.9.0")  
implementation("com.squareup.okhttp3:logging-interceptor:4.9.1")  
implementation("com.squareup.okhttp3:okhttp:4.9.1")  
  
implementation("io.github.resilience4j:resilience4j-retrofit:1.7.1")  
implementation("io.github.resilience4j:resilience4j-retry:1.7.1")
```

This service will consume the **games-ranking** when a request on the following API is received

```kotlin
GET /api/games/{gameTitleId}
data class GameInformationResponse(val id: Long, 
								   val name: String,
								   val publisher: String,
								   val ranking: GameRanking?)
								   
data class GameRanking(val gameTitleId: Long, val stars: Int?)
```

When the **games-ranking** REST API is consumed it will make use of **Resilienc4j Retry** to make up to 3 requests if any of the following occurs:
> HTTP RESPONSE CODE is (429 || 502)
> Any of the following is thrown ( IOException, SocketTimeoutException, ConnectException, UnknownHostException )
 
We can achieve this by configuring **RetryCallAdapter** in our **Retrofit.Builder** configuration
```kotlin
@Configuration  
class RetrofitConfiguration {  
  
    private val retry: Retry = Retry.of(  
        "id",  
        RetryConfig.custom<Response<String>>()  
            .maxAttempts(3)  
            .intervalFunction(IntervalFunction.ofExponentialBackoff(Duration.ofMillis(4000L), 2.0))  
            .retryOnResult { response: Response<String> -> response.code() in
            listOf(HttpStatus.TOO_MANY_REQUESTS.value(), HttpStatus.BAD_GATEWAY.value()) }  
			//.retryOnException(Predicate { e: Throwable? -> e is SocketTimeoutException  })  
			.retryExceptions(IOException::class.java, SocketTimeoutException::class.java,
			ConnectException::class.java, UnknownHostException::class.java)  
			//.ignoreExceptions(BusinessException::class.java, OtherBusinessException::class.java)  
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
```

As you can see we can not only specify a list of HTTP_RESPONSE_CODE or a list of Exceptions on when the **Resilience4j Retry** module will act but also specify a business exception that we would want to ignore.

You can specify specific time to wait between retry calls or even an `IntervalFunction` that implements the ***exponential back off algorithm*** to let the dependency breath between any other external calls

## Runtime example
In this example we will call the **gaming-titles** application to handle the `HTTP_TOO_MANY_REQUESTS` response code.
We will use the following cURL
```bash
curl --location --request GET 'http://localhost:9090/api/games/0'
```
Since it is using the ***exponential back off*** with base of 4seconds and a multiplier param of 2.0 we can expect the following results from the **games-ranking** application logs

IMAGE HERE

Service response with total response time of ***12.3 seconds***
```json
{
"id": 0,
"name": "TOO_MANY_REQUESTS_FLOW",
"publisher": "",
"ranking": null
}
```