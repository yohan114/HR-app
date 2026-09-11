package com.hr.performance.internal

import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

@Component
class AppraisalScoringEngine {

    data class ScoreCalculationResult(
        val finalScore: BigDecimal,
        val finalRating: String,
    )

    fun calculateScore(
        goalsScore: BigDecimal?,
        goalWeight: BigDecimal,
        competencyScore: BigDecimal?,
        competencyWeight: BigDecimal,
        mraScore: BigDecimal?,
        mraWeight: BigDecimal,
    ): ScoreCalculationResult {
        var totalWeight = BigDecimal.ZERO
        var weightedSum = BigDecimal.ZERO

        if (goalsScore != null) {
            weightedSum += goalsScore.multiply(goalWeight)
            totalWeight += goalWeight
        }

        if (competencyScore != null) {
            weightedSum += competencyScore.multiply(competencyWeight)
            totalWeight += competencyWeight
        }

        if (mraScore != null) {
            weightedSum += mraScore.multiply(mraWeight)
            totalWeight += mraWeight
        }

        val finalScore = if (totalWeight > BigDecimal.ZERO) {
            weightedSum.divide(totalWeight, 2, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }

        val rating = resolveRatingBand(finalScore)
        return ScoreCalculationResult(finalScore, rating)
    }

    fun resolveRatingBand(score: BigDecimal): String {
        return when {
            score >= BigDecimal("90.00") -> "Outstanding"
            score >= BigDecimal("75.00") -> "Exceeds Expectations"
            score >= BigDecimal("60.00") -> "Meets Expectations"
            score >= BigDecimal("45.00") -> "Needs Improvement"
            else -> "Unsatisfactory"
        }
    }
}
