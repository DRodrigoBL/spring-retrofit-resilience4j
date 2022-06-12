package com.gaming

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import io.reactivex.Maybe
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.retry.support.RetryTemplate
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path
import java.util.Optional

@SpringBootApplication
class GamingTitlesApplication

fun main(args: Array<String>) {
    runApplication<GamingTitlesApplication>(*args)
}

@RestController
@RequestMapping("api/games")
class GamesRestController(private val service: GameService) {

    @GetMapping("/{gameTitleId}")
    fun getGameInformation(@PathVariable("gameTitleId") gameTitleId: Long): ResponseEntity<GameInformationResponse> {

        val response = service.getGameInformation(gameTitleId)

        return if (response.isPresent) {
            ResponseEntity.ok(response.get())
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @GetMapping("/async/{gameTitleId}")
    fun getGameInformationAsync(@PathVariable("gameTitleId") gameTitleId: Long): ResponseEntity<GameInformationResponse> {
        val response = service.getGameInformationAsync(gameTitleId)

        return if (response.isPresent) {
            ResponseEntity.ok(response.get())
        } else {
            ResponseEntity.notFound().build()
        }
    }
}

@Service
class GameService(private val gameRankingApi: GameRankingApi) {

    private val logger by lazy { LoggerFactory.getLogger(GameService::class.java.simpleName) }

    private val games = listOf(
            GameInformation(0L, "TOO_MANY_REQUESTS_FLOW", ""),
            GameInformation(1L, "INTERNAL_SERVER_ERROR_FLOW", ""),
            GameInformation(2L, "BAD_GATEWAY_FLOW", ""),
            GameInformation(3L, "SOCKET_TIME_OUT_FLOW", ""),
            GameInformation(4L, "BAD_REQUEST_FLOW", ""),
            GameInformation(5L, "REQUEST_TIMEOUT_FLOW", ""),
            GameInformation(100L, "GTA V", "Rockstar"),
            GameInformation(200L, "Doom Eternal", "Bethesda"),
            GameInformation(300L, "Battlefield 2042", "EA")
    )

    fun getGameInformation(gameTitleId: Long): Optional<GameInformationResponse> {
        val game = games.firstOrNull { it.id == gameTitleId } ?: return Optional.empty()
        val gameRanking = runCatching { getGameRanking(gameTitleId) }
                .onFailure { logger.error("Exception occurred while fetching game ranking. Exception: ${it.message} - ${it.cause}") }
                .getOrNull()
        return Optional.of(GameInformationResponse(game.id, game.name, game.publisher, gameRanking))
    }

    fun getGameInformationAsync(gameTitleId: Long): Optional<GameInformationResponse> {
        val game = games.firstOrNull { it.id == gameTitleId } ?: return Optional.empty()
        val gameRanking = getGameRankingAsync(gameTitleId)
        return Optional.of(GameInformationResponse(game.id, game.name, game.publisher, gameRanking))
    }

    private fun getGameRankingAsync(gameTitleId: Long): GameRanking? {
        return gameRankingApi.getGameRankAsync(gameTitleId)
                .onErrorResumeNext { exc: Throwable ->
                    logger.error("Exception occurred while fetching game ranking. Exception: ${exc.message} - ${exc.cause}")
                    Maybe.empty()
                }
                .blockingGet()
    }

    private fun getGameRanking(gameTitleId: Long): GameRanking? {
        val response = gameRankingApi.getGameRank(gameTitleId).execute()
        return if (response.isSuccessful) {
            response.body()
        } else if (response.code() == HttpStatus.NOT_FOUND.value()) {
            logger.warn("GameRanking NOT_FOUND for gameTitleId=$gameTitleId")
            null
        } else {
            logger.error("Error from GameRanking external service - HTTP_CODE=${response.code()} - HTTP_BODY=${response.body()} - HTTP_HEADERS=${response.headers()}")
            null
        }
    }

}

interface GameRankingApi {

    @GET("/api/game-ranking/rank/{gameTitleId}")
    fun getGameRank(@Path("gameTitleId") gameTitleId: Long): Call<GameRanking>

    @GET("/api/game-ranking/rank/{gameTitleId}")
    fun getGameRankAsync(@Path("gameTitleId") gameTitleId: Long): Maybe<GameRanking>
}

data class GameInformationResponse(val id: Long, val name: String, val publisher: String, val ranking: GameRanking?)

data class GameInformation(val id: Long, val name: String, val publisher: String)

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class GameRanking(val gameTitleId: Long, val stars: Int?)
