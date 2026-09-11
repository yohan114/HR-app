package com.hr.app.ui.payroll

import com.hr.client.model.PayslipComparisonResponse
import com.hr.client.model.PayslipDetailResponse
import com.hr.client.model.PayslipLineItem
import com.hr.client.model.PayslipSummaryItem
import java.util.UUID

data class PayslipState(
    val payslips: List<PayslipSummaryItem> = emptyList(),
    val selectedPayslipId: UUID? = null,
    val selectedPayslipDetail: PayslipDetailResponse? = null,
    val comparison: PayslipComparisonResponse? = null,
    val selectedExplainerLine: PayslipLineItem? = null,
    val isExplainerOpen: Boolean = false,
    val isComparisonOpen: Boolean = false,
    val isMasked: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
)
