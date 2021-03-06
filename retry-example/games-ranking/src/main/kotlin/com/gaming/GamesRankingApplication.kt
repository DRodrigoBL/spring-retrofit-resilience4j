package com.gaming

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.Optional

@SpringBootApplication
class GamesRankingApplication

fun main(args: Array<String>) {
    runApplication<GamesRankingApplication>(*args)
}

@RestController
class DummyOrderRouter() {

    private val logger by lazy { LoggerFactory.getLogger(DummyOrderRouter::class.java.simpleName) }

    @GetMapping("/api/order-router/order/detail/{orderId}")
    fun getOrderDetail(@PathVariable(name = "orderId") orderId: Long): ResponseEntity<String> {

        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build()

    }

}

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

        if (gameTitleId == 4L) {
            logger.info("Response as ${HttpStatus.BAD_REQUEST} for gameTitleId=$gameTitleId")
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build()
        }

        if (gameTitleId == 5L) {
            logger.info("Response as ${HttpStatus.REQUEST_TIMEOUT} for gameTitleId=$gameTitleId")
            return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).build()
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
}

@Service
class GameRankingService() {
    private val gamesRankingList = listOf(
        GameRanking(3L, 1),
        GameRanking(100L, 2),
        GameRanking(200L, 5),
        GameRanking(300L, null)
    )

    fun getGameRanking(gameTitleId: Long): Optional<GameRanking> {
        return Optional.ofNullable(gamesRankingList.firstOrNull { it.gameTitleId == gameTitleId })
    }
}

data class GameRanking(val gameTitleId: Long, val stars: Int?)
