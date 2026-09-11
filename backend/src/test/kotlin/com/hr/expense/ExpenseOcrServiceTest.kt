package com.hr.expense

import com.hr.expense.internal.ExpenseOcrService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

@DisplayName("Expense OCR Service Unit Tests")
class ExpenseOcrServiceTest {

    private val ocrService = ExpenseOcrService()

    @Test
    fun `parses receipt text with merchant, date, and amount correctly`() {
        val sampleText = """
            KEELLS SUPER - COLOMBO 03
            RECEIPT #: 20260301-884
            DATE: 2026-03-01 14:32
            
            1x Sandwich LKR 850.00
            1x Mineral Water LKR 150.00
            1x Fruit Pack LKR 500.00
            
            TOTAL DUE: LKR 1,500.00
            PAID VIA VISA: LKR 1,500.00
            THANK YOU FOR SHOPPING WITH US!
        """.trimIndent()

        val req = ReceiptOcrRequest(receiptText = sampleText)
        val result = ocrService.parseReceipt(req)

        assertThat(result.merchantName).contains("Keells")
        assertThat(result.expenseDate).isEqualTo(LocalDate.of(2026, 3, 1))
        assertThat(result.amount).isEqualByComparingTo(BigDecimal("1500.00"))
        assertThat(result.currency).isEqualTo("LKR")
        assertThat(result.suggestedCategoryCode).isEqualTo("MEALS")
        assertThat(result.confidence).isGreaterThanOrEqualTo(0.90)
    }

    @Test
    fun `parses Uber transit receipt with mileage category`() {
        val sampleText = """
            Uber Technologies
            Trip Date: 15/02/2026
            Pickup: Fort Station
            Dropoff: Colombo Port City
            
            Fare Breakdown:
            Base Fare: LKR 400.00
            Distance (8.4 km): LKR 840.00
            Toll: LKR 300.00
            
            TOTAL: LKR 1,540.00
        """.trimIndent()

        val req = ReceiptOcrRequest(receiptText = sampleText)
        val result = ocrService.parseReceipt(req)

        assertThat(result.merchantName).contains("Uber")
        assertThat(result.expenseDate).isEqualTo(LocalDate.of(2026, 2, 15))
        assertThat(result.amount).isEqualByComparingTo(BigDecimal("1540.00"))
        assertThat(result.suggestedCategoryCode).isEqualTo("TRAVEL_MILEAGE")
    }

    @Test
    fun `parses Hotel lodging receipt with hotel category`() {
        val sampleText = """
            Hilton Colombo
            Sir Chittampalam A Gardiner Mawatha
            Invoice Date: 2026-01-20
            
            1 Night Deluxe King: LKR 35,000.00
            Service Charge 10%: LKR 3,500.00
            VAT 18%: LKR 6,930.00
            
            GRAND TOTAL: LKR 45,430.00
        """.trimIndent()

        val req = ReceiptOcrRequest(receiptText = sampleText)
        val result = ocrService.parseReceipt(req)

        assertThat(result.merchantName).contains("Hilton")
        assertThat(result.expenseDate).isEqualTo(LocalDate.of(2026, 1, 20))
        assertThat(result.amount).isEqualByComparingTo(BigDecimal("45430.00"))
        assertThat(result.suggestedCategoryCode).isEqualTo("HOTEL")
    }

    @Test
    fun `falls back gracefully when input has minimal information`() {
        val req = ReceiptOcrRequest(fileName = "taxi_expense.png")
        val result = ocrService.parseReceipt(req)

        assertThat(result.confidence).isGreaterThan(0.0)
        assertThat(result.suggestedCategoryCode).isEqualTo("TRAVEL_MILEAGE")
        assertThat(result.expenseDate).isNotNull()
    }
}
