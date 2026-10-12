package io.github.zigorminsk.spoty.player

import io.github.zigorminsk.spoty.data.repo.RecommendationRepository
import io.github.zigorminsk.spoty.data.repo.WaveSeed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * «Моя волна» — бесконечное радио: подбирает треки по вкусам, настроению, жанру или исполнителю
 * и незаметно дописывает очередь, пока вы слушаете.
 */
class WaveController(
    private val player: PlayerController,
    private val recommendations: RecommendationRepository,
    private val scope: CoroutineScope,
) {
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _failures = MutableSharedFlow<Unit>(extraBufferCapacity = 2)
    /** Срабатывает, если волну не удалось запустить (нет сети и пустой каталог). */
    val failures: SharedFlow<Unit> = _failures.asSharedFlow()

    private var topUpJob: Job? = null
    private var startJob: Job? = null

    init {
        scope.launch {
            player.state.collect { st ->
                val origin = player.origin.value
                if (origin !is PlayOrigin.Wave) return@collect
                val remaining = st.queue.size - st.currentIndex - 1
                if (st.connected && st.currentIndex >= 0 && remaining <= 3 && topUpJob?.isActive != true) {
                    topUp(origin.seed, st.queue.map { it.id }.toSet())
                }
            }
        }
    }

    fun start(seed: WaveSeed) {
        startJob?.cancel()
        _loading.value = true
        startJob = scope.launch {
            try {
                val batch = recommendations.nextWaveBatch(seed, emptySet(), 14)
                if (batch.isEmpty()) _failures.tryEmit(Unit) else player.play(batch, origin = PlayOrigin.Wave(seed))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _failures.tryEmit(Unit)
            } finally {
                _loading.value = false
            }
        }
    }

    private fun topUp(seed: WaveSeed, exclude: Set<String>) {
        topUpJob = scope.launch {
            try {
                val batch = recommendations.nextWaveBatch(seed, exclude, 10)
                if (player.origin.value is PlayOrigin.Wave) player.append(batch)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Сети нет — волна доиграет то, что есть.
            }
        }
    }
}
