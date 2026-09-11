package com.hr.performance

import com.hr.performance.internal.AppraisalScoringEngine
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal

@DisplayName("Appraisal Scoring Engine Unit Tests")
class AppraisalScoringEngineTest {

    private val engine = AppraisalScoringEngine()

    @Test
    fun `calculateScore correctly weights goals, competencies, and MRA`() {
        val result = engine.calculateScore(
            goalsScore = BigDecimal("95.00"),
            goalWeight = BigDecimal("60.00"),
            competencyScore = BigDecimal("85.00"),
            competencyWeight = BigDecimal("30.00"),
            mraScore = BigDecimal("90.00"),
            mraWeight = BigDecimal("10.00"),
        )

        // (95 * 60 + 85 * 30 + 90 * 10) / 100 = (5700 + 2550 + 900) / 100 = 9150 / 100 = 91.50
        assertEquals(BigDecimal("91.50"), result.finalScore)
        assertEquals("Outstanding", result.finalRating)
    }

    @Test
    fun `calculateScore handles null MRA score by reweighting remaining components`() {
        val result = engine.calculateScore(
            goalsScore = BigDecimal("80.00"),
            goalWeight = BigDecimal("60.00"),
            competencyScore = BigDecimal("70.00"),
            competencyWeight = BigDecimal("30.00"),
            mraScore = null,
            mraWeight = BigDecimal("10.00"),
        )

        // (80 * 60 + 70 * 30) / 90 = (4800 + 2100) / 90 = 6900 / 90 = 76.67
        assertEquals(BigDecimal("76.67"), result.finalScore)
        assertEquals("Exceeds Expectations", result.finalRating)
    }

    @Test
    fun `resolveRatingBand returns correct band across thresholds`() {
        assertEquals("Outstanding", engine.resolveRatingBand(BigDecimal("95.00")))
        assertEquals("Outstanding", engine.resolveRatingBand(BigDecimal("90.00")))
        assertEquals("Exceeds Expectations", engine.resolveRatingBand(BigDecimal("89.99")))
        assertEquals("Exceeds Expectations", engine.resolveRatingBand(BigDecimal("75.00")))
        assertEquals("Meets Expectations", engine.resolveRatingBand(BigDecimal("74.99")))
        assertEquals("Meets Expectations", engine.resolveRatingBand(BigDecimal("60.00")))
        assertEquals("Needs Improvement", engine.resolveRatingBand(BigDecimal("59.99")))
        assertEquals("Needs Improvement", engine.resolveRatingBand(BigDecimal("45.00")))
        assertEquals("Unsatisfactory", engine.resolveRatingBand(BigDecimal("44.99")))
        assertEquals("Unsatisfactory", engine.resolveRatingBand(BigDecimal("20.00")))
    }
}
