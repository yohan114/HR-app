# Data Model

PostgreSQL 16. Conventions used throughout:

- Every tenant-scoped table has `tenant_id uuid NOT NULL` as the **leading column of every index**, plus an RLS policy.
- PKs are `uuid` (UUIDv7 — time-ordered, index-friendly).
- Audit columns on every table: `created_at`, `created_by`, `updated_at`, `updated_by`, `version` (optimistic lock).
- Soft delete via `deleted_at timestamptz` only where history matters; hard delete elsewhere.
- Money: `numeric(19,6)` internally, rounded to currency scale at presentation. **Never `float`/`double`.**
- Effective-dated entities use `(valid_from, valid_to)` with `valid_to` nullable = current.
- Custom fields: `custom_fields jsonb` + GIN index, driven by `field_definition`.

---

## 1. Platform & tenancy

```sql
tenant(id, code, name, legal_name, country_code, timezone, default_currency,
       locale, data_region, tier, status, subscription_plan, feature_flags jsonb,
       branding jsonb, created_at)

tenant_module(tenant_id, module_key, enabled, config jsonb)
-- module_key: core_hr | time | absence | payroll | talent | performance |
--             engagement | documents | learning | analytics

field_definition(id, tenant_id, entity_type, field_key, label_i18n jsonb,
                 data_type, validation jsonb, options jsonb, section, position,
                 required, permissions jsonb, active)
-- entity_type: employee | leave_application | expense_claim | ...

label_override(tenant_id, label_key, locale, value)   -- Label Configurator

sequence_config(tenant_id, sequence_key, prefix, suffix, padding, next_value,
                reset_policy)  -- employee codes, requisition numbers, claim numbers

audit_log(id, tenant_id, entity_type, entity_id, action, actor_user_id,
          actor_ip, occurred_at, changes jsonb, request_id)
-- partitioned monthly; append-only; never updated

audit_config(tenant_id, entity_type, field_key, enabled, alias)
```

---

## 2. Identity, access & devices

```sql
app_user(id, tenant_id, employee_id, username, email, password_hash,
         password_changed_at, must_change_password, mfa_enabled, mfa_secret_enc,
         status, locale, timezone, last_login_at, failed_attempts, locked_until)

role(id, tenant_id, key, name, description, is_system)
permission(key, module, description)                  -- global catalogue
role_permission(role_id, permission_key)
user_role(user_id, role_id, valid_from, valid_to)

capability_group(id, tenant_id, name)                 -- bundles of roles/menus
menu_item(key, module, parent_key, route, icon, order)
capability_group_menu(capability_group_id, menu_item_key, allowed)

data_scope(id, tenant_id, name, expression)
-- e.g. employee.cost_centre IN :user_cost_centres
user_data_scope(user_id, data_scope_id)

field_permission(tenant_id, role_id, entity_type, field_key, access)
-- access: HIDDEN | MASKED | READ | WRITE

user_device(id, tenant_id, user_id, device_id, platform, model, os_version,
            app_version, push_token, biometric_enrolled, attestation_verified,
            trusted, last_seen_at, revoked_at)

refresh_token(id, tenant_id, user_id, device_id, token_hash, family_id,
              issued_at, expires_at, used_at, revoked_at, revoked_reason)

login_event(id, tenant_id, user_id, device_id, method, result, ip, geo,
            occurred_at)   -- method: PASSWORD | BIOMETRIC | SSO | MFA

sso_config(tenant_id, provider, protocol, metadata jsonb, enabled,
           domain_hints text[])

password_policy(tenant_id, min_length, complexity jsonb, history_count,
                max_age_days, lockout_threshold, lockout_minutes)
```

---

## 3. Organisation master data (EIM)

```sql
company(id, tenant_id, code, name, legal_name, parent_id, country_code,
        currency, tax_registration, custom_fields)
location(id, tenant_id, company_id, code, name, address jsonb, timezone,
         geo_lat, geo_lng, geofence_radius_m, parent_id)
sub_location(id, tenant_id, location_id, code, name)
job_location(id, tenant_id, code, name, location_id)
cost_centre(id, tenant_id, code, name, parent_id, company_id, gl_code)
department(id, tenant_id, code, name, parent_id, head_employee_id, cost_centre_id)

salary_grade(id, tenant_id, code, name, min_amount, max_amount, mid_amount,
             currency, sequence)
corporate_title(id, tenant_id, code, name, rank)
designation(id, tenant_id, code, name, salary_grade_id, corporate_title_id,
            job_description_id)

job_description(id, tenant_id, code, title, category_id, type_id, summary,
                industry_type_id, custom_fields)
job_kra(id, tenant_id, job_description_id, description, weight, sequence)

-- Reference taxonomies (all: id, tenant_id, code, name, active, sequence)
employee_category · employee_group · employment_type · employee_title
statutory_classification · function · functional_role · classification
gender_type · marital_status · blood_group · attachment_type · currency_type
nationality · religion · race · relationship · dwelling_type · route · station
qualification_type · qualification_classification · qualification
qualification_property · subject · language · rating_method
membership_type · membership_title · bargaining_unit
extracurricular_category · extracurricular_type

-- Geography (self-referencing hierarchy)
geo_region(id, tenant_id, level, code, name, parent_id)
-- level: COUNTRY | PROVINCE | DISTRICT | ELECTORATE | DS_DIVISION | GN_DIVISION

bank(id, tenant_id, code, name, swift, country_code)
bank_branch(id, tenant_id, bank_id, code, name, routing_code)

wps_company(tenant_id, company_id, employer_id, bank_code, routing_code)  -- UAE
```

---

## 4. Employee

```sql
employee(id, tenant_id, employee_code, company_id, status,
         -- personal
         first_name, middle_name, last_name, display_name, preferred_name,
         date_of_birth, gender_type_id, marital_status_id, blood_group_id,
         nationality_id, religion_id, race_id, national_id_enc, photo_key,
         -- employment
         join_date, confirmation_date, probation_end_date, resign_date,
         last_working_date, employment_type_id, employee_category_id,
         employee_group_id, statutory_classification_id,
         -- workstation
         department_id, designation_id, salary_grade_id, corporate_title_id,
         location_id, sub_location_id, job_location_id, cost_centre_id,
         function_id, supervisor_id, dotted_line_supervisor_id,
         -- contact
         personal_email, work_email, mobile, work_phone,
         permanent_address jsonb, current_address jsonb,
         -- system
         custom_fields jsonb, search_vector tsvector,
         created_at, updated_at, version)

-- supervisor_path is maintained as a materialised ltree for fast subtree queries
employee_hierarchy(tenant_id, employee_id, path ltree, depth)

employee_history(id, tenant_id, employee_id, field_key, old_value, new_value,
                 effective_date, movement_id, changed_by, changed_at)

-- Census
employee_dependent(id, tenant_id, employee_id, name, relationship_id,
                   date_of_birth, gender, is_beneficiary, share_pct, custom_fields)
employee_emergency_contact(id, tenant_id, employee_id, name, relationship_id,
                           phone, alt_phone, address, priority)
employee_nominee(id, tenant_id, employee_id, name, relationship_id, share_pct,
                 nominee_type)
employee_transport(id, tenant_id, employee_id, route_id, station_id,
                   dwelling_type_id, pickup_point)

-- Qualifications & experience
employee_qualification(id, tenant_id, employee_id, qualification_id,
                       institution, from_date, to_date, grade, attachment_key,
                       verified)
employee_work_experience(id, tenant_id, employee_id, employer, designation,
                         from_date, to_date, reason_for_leaving, reference_contact)
employee_language(id, tenant_id, employee_id, language_id, speak_level,
                  read_level, write_level)
employee_extracurricular(id, tenant_id, employee_id, type_id, description,
                         from_date, to_date)
employee_membership(id, tenant_id, employee_id, membership_type_id,
                    membership_no, title_id, from_date, to_date)
employee_bargaining_unit(id, tenant_id, employee_id, bargaining_unit_id,
                         from_date, to_date)

-- Financial
employee_bank_account(id, tenant_id, employee_id, bank_id, branch_id,
                      account_no_enc, account_name, is_primary, split_type,
                      split_value, currency, valid_from, valid_to)
employee_credit_card(id, tenant_id, employee_id, card_type, last_four_enc,
                     issuer, expiry)

-- Documents / legal
employee_document(id, tenant_id, employee_id, doc_type, doc_number_enc,
                  issue_date, expiry_date, issuing_country, attachment_key,
                  alert_days_before, status)
-- doc_type: PASSPORT | VISA | WORK_PERMIT | LABOUR_CARD | DRIVING_LICENCE | ...

employee_attachment(id, tenant_id, employee_id, attachment_type_id, file_key,
                    file_name, mime_type, size_bytes, uploaded_by, uploaded_at)

employee_contract(id, tenant_id, employee_id, contract_type, start_date,
                  end_date, extension_of_id, terms jsonb, document_key, status)

employee_covering(id, tenant_id, employee_id, covering_for_employee_id,
                  from_date, to_date, scope, approved_by)
```

---

## 5. Employee life cycle

```sql
movement_group(id, tenant_id, code, name)
movement_type(id, tenant_id, movement_group_id, code, name,
              affects_designation, affects_salary, affects_location,
              affects_supervisor, requires_approval, workflow_type_key,
              eligibility_expression)
movement_reason(id, tenant_id, movement_type_id, code, name)

movement(id, tenant_id, employee_id, movement_type_id, reason_id,
         effective_date, requested_by, status,
         from_values jsonb, to_values jsonb,
         workflow_instance_id, applied_at, rolled_back_at, remarks)
-- status: DRAFT | SUBMITTED | SHORTLISTED | APPROVED | APPLIED | REJECTED | ROLLED_BACK
```

---

## 6. Time & attendance

```sql
shift(id, tenant_id, code, name, shift_type, start_time, end_time,
      break_minutes, crosses_midnight, working_minutes,
      grace_in_minutes, grace_out_minutes, half_day_threshold_minutes,
      ot_eligible, ot_start_after_minutes, min_ot_minutes, color)
-- shift_type: FIXED | ROTATING | SPLIT | NIGHT | FLEXIBLE | OPEN

roster_group(id, tenant_id, code, name)
roster(id, tenant_id, roster_group_id, code, name, cycle_days, pattern jsonb)
-- pattern: [{day:1, shift_id:...}, {day:2, shift_id:null /* off */}, ...]

roster_assignment(id, tenant_id, employee_id, roster_id, from_date, to_date,
                  cycle_offset)

employee_shift_schedule(id, tenant_id, employee_id, work_date, shift_id,
                        source, is_holiday, is_weekly_off, day_type_id)
-- source: ROSTER | MANUAL | ADJUSTMENT | SWAP ; one row per employee per day

shift_adjustment(id, tenant_id, employee_id, work_date, from_shift_id,
                 to_shift_id, reason_id, workflow_instance_id, status)
shift_swap(id, tenant_id, requester_employee_id, target_employee_id,
           requester_date, target_date, status, workflow_instance_id)

attendance_policy(id, tenant_id, name, location_capture,
                  geofence_enforcement, selfie_required, mock_location_action,
                  rounding_rule jsonb, allow_offline_punch, max_offline_hours)
-- location_capture: OFF | OPTIONAL | REQUIRED   ← fixes the forced-GPS complaint
-- geofence_enforcement: OFF | WARN | BLOCK
-- mock_location_action: IGNORE | FLAG | BLOCK
employee_attendance_policy(tenant_id, employee_id, policy_id, from_date, to_date)

raw_punch(id, tenant_id, employee_id, punched_at, punch_type, source,
          device_id, location_id, geo_lat, geo_lng, geo_accuracy_m,
          geofence_status, is_mock_location, selfie_key, face_match_score,
          client_idempotency_key, recorded_offline, synced_at, raw jsonb)
-- punch_type: IN | OUT | BREAK_IN | BREAK_OUT | AUTO
-- source: MOBILE | BIOMETRIC_DEVICE | KIOSK | WEB | NFC | QR | IMPORT
-- geofence_status: INSIDE | OUTSIDE | UNKNOWN | NOT_APPLICABLE
-- PARTITION BY RANGE (punched_at) monthly

attendance_session(id, tenant_id, employee_id, work_date, in_punch_id,
                   out_punch_id, in_at, out_at, duration_minutes, session_type)

daily_attendance(tenant_id, employee_id, work_date, shift_id,
                 first_in_at, last_out_at, worked_minutes, break_minutes,
                 late_minutes, early_leave_minutes, short_minutes,
                 ot_minutes, approved_ot_minutes, ot_cap_code,
                 leave_type_id, leave_days, day_status, anomaly_flags text[],
                 computed_at, computed_version)
-- day_status: PRESENT | ABSENT | LEAVE | HOLIDAY | WEEKLY_OFF | HALF_DAY | NO_SHOW
-- DERIVED: always recomputable from raw_punch + config. PK (tenant_id, employee_id, work_date)

manual_attendance_request(id, tenant_id, employee_id, work_date, requested_in_at,
                          requested_out_at, reason_id, remarks, attachment_key,
                          workflow_instance_id, status)

overtime_request(id, tenant_id, employee_id, work_date, request_type,
                 planned_minutes, actual_minutes, approved_minutes, reason_id,
                 workflow_instance_id, status)
-- request_type: PRIOR | POST

ot_cap(id, tenant_id, code, name, period_type, max_minutes, action_on_breach)

-- Timesheets
client(id, tenant_id, code, name, currency, active)
project(id, tenant_id, client_id, code, name, start_date, end_date,
        budget_amount, budget_hours, billable, status)
activity(id, tenant_id, project_id, code, name, billable, default_rate)
employee_billing_rate(tenant_id, employee_id, project_id, rate, currency,
                      valid_from, valid_to)

timesheet(id, tenant_id, employee_id, period_start, period_end, status,
          submitted_at, workflow_instance_id, total_hours, billable_hours)
timesheet_entry(id, tenant_id, timesheet_id, work_date, project_id, activity_id,
                hours, description, billable, rate, amount)
```

---

## 7. Absence / leave

```sql
leave_year(id, tenant_id, code, start_date, end_date, status)
leave_group(id, tenant_id, code, name)
day_type(id, tenant_id, code, name, is_working, weight)
calendar_group(id, tenant_id, code, name, country_code)
holiday_calendar(id, tenant_id, calendar_group_id, holiday_date, name,
                 day_type_id, is_optional, location_ids uuid[])

leave_type(id, tenant_id, leave_group_id, code, name, unit,
           paid, requires_attachment, attachment_after_days,
           min_days, max_consecutive_days, notice_days, allow_half_day,
           allow_backdate, backdate_limit_days, counts_holidays,
           counts_weekly_off, gender_restriction, eligibility_expression,
           workflow_type_key, color, sequence)
-- unit: DAY | HOUR

short_leave_type(id, tenant_id, code, name, max_minutes_per_instance,
                 max_instances_per_month, deduct_from_leave_type_id)

leave_entitlement_rule(id, tenant_id, leave_type_id, applies_to jsonb,
                       accrual_method, accrual_rate, accrual_frequency,
                       prorate_on_join, prorate_on_exit,
                       carry_forward_enabled, carry_forward_max,
                       carry_forward_expiry_months,
                       encashment_enabled, encashment_max,
                       max_balance, service_based_slabs jsonb, valid_from)
-- accrual_method: ANNUAL_UPFRONT | MONTHLY | PER_WORKED_DAY | SERVICE_SLAB | EARNED

employee_leave_entitlement(tenant_id, employee_id, leave_year_id, leave_type_id,
                           opening_balance, accrued, taken, adjusted,
                           carried_forward, encashed, expired, balance,
                           last_accrued_at)
-- `balance` is a generated/maintained column; every mutation writes a ledger row

leave_ledger(id, tenant_id, employee_id, leave_year_id, leave_type_id,
             entry_type, days, reference_type, reference_id, effective_date,
             balance_after, remarks, created_at)
-- entry_type: OPENING | ACCRUAL | TAKEN | CANCELLED | ADJUSTMENT |
--             CARRY_FORWARD | ENCASHMENT | EXPIRY
-- APPEND-ONLY. Balance is always reconstructible. This is how we make
-- "why is my balance X?" answerable (feature: explainability).

leave_application(id, tenant_id, employee_id, leave_type_id, from_date, to_date,
                  from_half, to_half, days, reason_id, remarks, attachment_key,
                  contact_during_leave, covering_employee_id,
                  workflow_instance_id, status, applied_at, applied_by,
                  cancelled_at, cancel_reason)
-- status: DRAFT | PENDING | APPROVED | REJECTED | CANCELLED | WITHDRAWN
leave_application_day(tenant_id, leave_application_id, leave_date, portion, days)

short_leave_application(id, tenant_id, employee_id, short_leave_type_id,
                        leave_date, from_time, to_time, minutes, reason_id,
                        workflow_instance_id, status)

leave_plan(id, tenant_id, employee_id, leave_year_id, status)
leave_plan_item(id, tenant_id, leave_plan_id, leave_type_id, from_date, to_date,
                days)

leave_reason(id, tenant_id, leave_type_id, code, name)
```

---

## 8. Payroll

```sql
pay_group(id, tenant_id, code, name, company_id, currency, pay_frequency,
          period_start_day, cutoff_day, pay_day_rule, calendar_group_id)
pay_period(id, tenant_id, pay_group_id, code, start_date, end_date, pay_date,
           status, locked_at, locked_by)
-- status: OPEN | LOCKED | PROCESSING | REVIEW | COMMITTED | CLOSED

pay_process(id, tenant_id, pay_group_id, code, name, process_type, sequence)
-- process_type: SALARY | OTHER_PAYMENT | FINAL_PAYMENT | BONUS | ARREARS

pay_item(id, tenant_id, code, name, name_i18n jsonb, item_type, category,
         calculation_method, formula_id, fixed_amount, percentage_of,
         taxable, statutory_base jsonb, gl_code, prorate_rule,
         show_on_payslip, payslip_group, sequence, active)
-- item_type: EARNING | DEDUCTION | EMPLOYER_CONTRIBUTION | INFORMATION
-- calculation_method: FIXED | FORMULA | PERCENTAGE | ATTENDANCE_BASED | IMPORTED

formula(id, tenant_id, code, name, expression, return_type, version,
        published, published_at)
-- IMMUTABLE once published; new version = new row

pay_group_pay_item(tenant_id, pay_group_id, pay_item_id, overrides jsonb)

employee_pay_item(id, tenant_id, employee_id, pay_item_id, amount, percentage,
                  currency, valid_from, valid_to, reference, remarks)

employee_salary(id, tenant_id, employee_id, basic_amount, currency,
                salary_grade_id, effective_from, effective_to,
                amendment_id, reason)

salary_amendment(id, tenant_id, employee_id, amendment_type, old_basic,
                 new_basic, effective_date, reason, workflow_instance_id,
                 status, cancelled_at)
-- amendment_type: INCREMENT | REVISION | PROMOTION | CORRECTION

tax_config(id, tenant_id, country_code, tax_year, code, name, method,
           brackets jsonb, reliefs jsonb, annualisation_rule, effective_from)
employee_tax_profile(tenant_id, employee_id, tax_config_id, tax_number_enc,
                     exemptions jsonb, additional_deduction, valid_from, valid_to)
tax_adjustment(id, tenant_id, employee_id, pay_period_id, amount, reason)

statutory_scheme(id, tenant_id, country_code, code, name, scheme_type,
                 employee_rate, employer_rate, ceiling, floor, rules jsonb,
                 effective_from)
-- e.g. LK: EPF/ETF · PH: SSS/PhilHealth/Pag-IBIG · ID: BPJS · AE: Pension
employee_statutory(tenant_id, employee_id, statutory_scheme_id, member_no_enc,
                   opt_out, valid_from, valid_to)

payroll_run(id, tenant_id, pay_group_id, pay_period_id, pay_process_id,
            run_type, status, phase, initiated_by, initiated_at,
            approved_by, approved_at, committed_at, rolled_back_at,
            employee_count, gross_total, net_total, snapshot_ref,
            formula_versions jsonb, error_summary jsonb)
-- phase: LOCK | VALIDATE | CALCULATE | REVIEW | COMMIT

payroll_run_input_snapshot(run_id, tenant_id, payload_key)
-- immutable S3 blob: employee state, pay item assignments, attendance/leave
-- aggregates, tax tables, formula versions. Guarantees reproducibility.

payroll_result(id, tenant_id, payroll_run_id, employee_id,
               gross, total_deductions, total_employer_cost, net,
               currency, working_days, paid_days, lop_days, status)

payroll_result_line(id, tenant_id, payroll_result_id, pay_item_id, amount,
                    quantity, rate, calculation_trace jsonb, sequence)
-- calculation_trace: the resolved formula + inputs. Powers the payslip explainer.

payroll_validation(id, tenant_id, payroll_run_id, employee_id, severity,
                   code, message, details jsonb)
payroll_anomaly(id, tenant_id, payroll_run_id, employee_id, anomaly_type,
                current_value, prior_value, variance_pct, acknowledged_by)

payslip(id, tenant_id, employee_id, payroll_run_id, pay_period_id,
        file_key, published_at, first_viewed_at, view_count, locale)

bank_file_template(id, tenant_id, bank_id, code, name, format, spec jsonb,
                   encoding, password_protected)
bank_file(id, tenant_id, payroll_run_id, template_id, file_key, record_count,
          total_amount, generated_at, generated_by, rolled_back_at)

gl_mapping(id, tenant_id, pay_item_id, cost_centre_id, debit_account,
           credit_account, dimension jsonb)
gl_batch(id, tenant_id, payroll_run_id, file_key, status, posted_at,
         rolled_back_at)
gl_entry(id, tenant_id, gl_batch_id, account, cost_centre_id, debit, credit,
         description, reference)

payroll_simulation(id, tenant_id, name, scenario_type, parameters jsonb,
                   result jsonb, created_by, created_at)
-- scenario_type: INCREMENT | NEW_JOINER | EXIT | BONUS
```

---

## 9. Benefits, loans & claims

```sql
benefit_category(id, tenant_id, code, name, benefit_kind)  -- CASH | NON_CASH
benefit(id, tenant_id, category_id, code, name, description, unit,
        default_amount, currency, taxable, requires_approval,
        eligibility_expression, workflow_type_key, max_per_year,
        localisation jsonb, active)
salary_grade_benefit(tenant_id, salary_grade_id, benefit_id, amount, quantity)
employee_benefit(id, tenant_id, employee_id, benefit_id, amount, quantity,
                 valid_from, valid_to, assigned_by, source)
benefit_application(id, tenant_id, employee_id, benefit_id, amount, quantity,
                    request_date, remarks, attachment_key,
                    workflow_instance_id, status, cancelled_at)

loan_type(id, tenant_id, code, name, currency, max_amount, max_amount_formula,
          min_service_months, interest_rate, interest_method, max_instalments,
          max_concurrent_loans, checklist jsonb, eligibility_expression,
          workflow_type_key, deduction_pay_item_id)
loan(id, tenant_id, employee_id, loan_type_id, loan_number, principal,
     interest_rate, instalment_count, instalment_amount, currency,
     disbursed_at, first_deduction_period_id, status,
     outstanding_principal, outstanding_interest,
     workflow_instance_id, stopped_from_period_id, settled_at)
loan_schedule(id, tenant_id, loan_id, instalment_no, due_period_id,
              principal_due, interest_due, total_due, status,
              paid_amount, paid_period_id)
loan_settlement(id, tenant_id, loan_id, settlement_type, amount, settled_at,
                remarks)

expense_category(id, tenant_id, code, name, gl_code, requires_receipt,
                 max_amount, per_diem_amount, eligibility_expression)
expense_claim(id, tenant_id, employee_id, claim_number, claim_date, currency,
              total_amount, approved_amount, status, workflow_instance_id,
              paid_via_payroll_run_id, remarks)
expense_claim_line(id, tenant_id, claim_id, category_id, expense_date,
                   description, amount, currency, fx_rate, receipt_key,
                   ocr_extracted jsonb, approved_amount, rejection_reason)

travel_request(id, tenant_id, employee_id, purpose, from_date, to_date,
               destination, estimated_cost, advance_amount, status,
               workflow_instance_id, settled_claim_id)
```

---

## 10. Workflow (cross-cutting)

```sql
workflow_type(key, module, name, description)         -- global catalogue
workflow_definition(id, tenant_id, workflow_type_key, name, version, active,
                    condition_expression, published_at)
workflow_step(id, tenant_id, workflow_definition_id, sequence, name,
              resolver_type, resolver_config jsonb, approval_mode,
              quorum_count, sla_hours, escalation_config jsonb,
              can_edit, can_return, skip_if_expression)
-- resolver_type: NAMED_USER | ROLE | SUPERVISOR_LEVEL | DEPARTMENT_HEAD |
--                EXPRESSION | GROUP | INITIATOR_MANAGER
-- approval_mode: ALL | ANY | QUORUM

workflow_instance(id, tenant_id, workflow_definition_id, workflow_type_key,
                  entity_type, entity_id, initiator_user_id, initiator_employee_id,
                  current_step_sequence, status, started_at, completed_at,
                  context jsonb)
-- status: RUNNING | APPROVED | REJECTED | WITHDRAWN | CANCELLED | EXPIRED

workflow_task(id, tenant_id, workflow_instance_id, step_id, assignee_user_id,
              assignee_employee_id, delegated_from_user_id, status,
              assigned_at, due_at, acted_at, action, comment, attachment_key,
              action_token_hash)
-- status: PENDING | APPROVED | REJECTED | RETURNED | SKIPPED | REASSIGNED | EXPIRED

workflow_delegation(id, tenant_id, from_user_id, to_user_id,
                    workflow_type_keys text[], from_date, to_date, reason,
                    active)
workflow_history(id, tenant_id, workflow_instance_id, step_sequence, actor_user_id,
                 action, comment, occurred_at, metadata jsonb)
```

---

## 11. Performance

```sql
competency_group(id, tenant_id, code, name)
competency_area(id, tenant_id, competency_group_id, code, name)
competency(id, tenant_id, competency_area_id, code, name, description,
           behaviour_indicators jsonb)
competency_collection(id, tenant_id, code, name)
competency_collection_item(collection_id, competency_id, weight)
proficiency_level(id, tenant_id, rating_method_id, level, name, description,
                  score)
proficiency_profile(id, tenant_id, designation_id, competency_id,
                    required_level_id)

goal_group(id, tenant_id, code, name, weight_total)
evaluation_cycle(id, tenant_id, code, name, period_start, period_end,
                 self_start, self_end, manager_start, manager_end,
                 review_start, review_end, rating_method_id,
                 competency_collection_id, goal_group_id, mra_enabled,
                 bell_curve_config jsonb, status)

assessment(id, tenant_id, evaluation_cycle_id, employee_id, assessor_id,
           reviewer_id, status, self_score, manager_score, mra_score,
           final_score, final_rating_id, calibrated_score,
           submitted_at, reviewed_at, acknowledged_at)
-- status: NOT_STARTED | SELF_IN_PROGRESS | SELF_SUBMITTED | MANAGER_IN_PROGRESS
--         | MANAGER_SUBMITTED | REVIEWED | CALIBRATED | ACKNOWLEDGED

goal(id, tenant_id, assessment_id, employee_id, goal_group_id, title,
     description, metric, target_value, actual_value, unit, weight,
     start_date, due_date, parent_goal_id, status, self_rating,
     manager_rating, source)
-- source: MANUAL | CASCADED | AI_SUGGESTED | LIBRARY

assessment_competency(id, tenant_id, assessment_id, competency_id, weight,
                      self_level_id, manager_level_id, gap, comment)

rater_group(id, tenant_id, code, name, weight, anonymity)
mra_question(id, tenant_id, rater_group_id, text, question_type, options jsonb,
             competency_id, sequence)
mra_assessor(id, tenant_id, assessment_id, rater_group_id, assessor_id, status,
             submitted_at)
mra_response(id, tenant_id, mra_assessor_id, question_id, rating, comment)

critical_incident(id, tenant_id, employee_id, recorded_by, incident_date,
                  incident_type, description, competency_id, impact,
                  workflow_instance_id, status, visible_to_employee)

feedback_note(id, tenant_id, from_employee_id, to_employee_id, note_type,
              content, visibility, related_goal_id, created_at)
-- note_type: PRAISE | SUGGESTION | ONE_ON_ONE | CHECK_IN
```

---

## 12. Talent — recruitment, onboarding, offboarding

```sql
requisition(id, tenant_id, requisition_number, department_id, designation_id,
            location_id, cost_centre_id, requisition_type, headcount,
            justification, budgeted, target_date, hiring_manager_id,
            recruiter_id, workflow_instance_id, status)
-- requisition_type: NEW_POSITION | REPLACEMENT

vacancy(id, tenant_id, requisition_id, code, title, description,
        job_description_id, employment_type_id, salary_range jsonb,
        openings, filled, publish_internal, publish_external,
        open_date, close_date, status)
advertisement(id, tenant_id, vacancy_id, channel, content, published_at,
              expires_at, external_ref, cost)

candidate(id, tenant_id, first_name, last_name, email, mobile, source,
          referred_by_employee_id, current_employer, current_designation,
          total_experience_months, expected_salary, notice_period_days,
          cv_key, parsed_profile jsonb, tags text[], search_vector tsvector,
          consent_at, retention_until)
candidate_qualification(id, tenant_id, candidate_id, qualification_id,
                        institution, year, grade)

application(id, tenant_id, vacancy_id, candidate_id, applied_at, source,
            stage, status, rating, rejection_reason_id, offer_id,
            assigned_to_user_id)
-- stage: APPLIED | SCREENING | SHORTLISTED | INTERVIEW | ASSESSMENT |
--        BACKGROUND_CHECK | OFFER | HIRED | REJECTED | WITHDRAWN

interview(id, tenant_id, application_id, round, scheduled_at, duration_minutes,
          mode, location, meeting_link, status)
interview_panel(interview_id, interviewer_employee_id, role)
interview_scorecard(id, tenant_id, interview_id, interviewer_employee_id,
                    competency_id, rating, comment, recommendation,
                    submitted_at)

offer(id, tenant_id, application_id, designation_id, salary_grade_id,
      offered_ctc jsonb, join_date, expiry_date, letter_key, status,
      workflow_instance_id, signed_at, signature_ref)

background_check(id, tenant_id, application_id, check_type, vendor, status,
                 requested_at, completed_at, result, report_key)

-- Onboarding
onboarding_stage(id, tenant_id, code, name, sequence, days_offset)
onboarding_action(id, tenant_id, stage_id, code, name, action_type,
                  owner_role_id, mandatory, due_days_offset,
                  document_template_id, workflow_type_key)
onboarding_profile(id, tenant_id, code, name, applies_to jsonb)
onboarding_profile_action(profile_id, action_id, sequence, override jsonb)
onboarding_instance(id, tenant_id, candidate_id, employee_id, profile_id,
                    join_date, status, progress_pct, buddy_employee_id)
onboarding_task(id, tenant_id, onboarding_instance_id, action_id,
                assignee_user_id, due_date, status, completed_at,
                attachment_key, workflow_instance_id)

-- Offboarding
exit_type(id, tenant_id, code, name, voluntary, notice_days,
          requires_interview, requires_clearance)
exit_reason(id, tenant_id, exit_type_id, code, name, category)
exit_notice(id, tenant_id, employee_id, exit_type_id, exit_reason_id,
            notice_date, requested_last_working_date, approved_last_working_date,
            remarks, workflow_instance_id, status, reversed_at, reversal_reason)

exit_question_group(id, tenant_id, code, name)
exit_question(id, tenant_id, group_id, text, question_type, options jsonb,
              sequence)
exit_question_template(id, tenant_id, code, name, exit_type_id)
exit_template_group(template_id, group_id, sequence)
exit_interview(id, tenant_id, exit_notice_id, employee_id, interviewer_id,
               template_id, conducted_at, mode, summary, sentiment, status)
exit_interview_response(id, tenant_id, exit_interview_id, question_id,
                        answer, rating)

handover_item(id, tenant_id, code, name, category, owner_role_id)
handover_template(id, tenant_id, code, name, applies_to jsonb)
handover_template_item(template_id, item_id, sequence, mandatory)
clearance(id, tenant_id, exit_notice_id, employee_id, status, completed_at)
clearance_task(id, tenant_id, clearance_id, item_id, department_id,
               assignee_user_id, status, cleared_at, remarks, amount_recoverable,
               workflow_instance_id)
```

---

## 13. Learning & development

```sql
training_provider(id, tenant_id, code, name, contact jsonb, rating, active)
resource_person(id, tenant_id, name, provider_id, employee_id, expertise text[],
                bio, rate)
course(id, tenant_id, code, name, description, category, delivery_mode,
       duration_hours, cost, currency, max_participants, competency_ids uuid[],
       certification, validity_months, active)
training_schedule(id, tenant_id, course_id, provider_id, resource_person_id,
                  start_date, end_date, venue, location_id, seats, seats_filled,
                  cost_per_head, status)
training_enrollment(id, tenant_id, training_schedule_id, employee_id,
                    enrollment_type, workflow_instance_id, status,
                    attendance_pct, completion_status, score, certificate_key,
                    certificate_expiry)
-- enrollment_type: SELF | NOMINATED | MANDATORY
training_attendance(id, tenant_id, enrollment_id, session_date, present,
                    marked_by, marked_at)
trainee_evaluation(id, tenant_id, enrollment_id, evaluator_type, ratings jsonb,
                   comments, submitted_at)
training_need(id, tenant_id, employee_id, competency_id, current_level_id,
              required_level_id, source, recommended_course_ids uuid[], status)
```

---

## 14. Engagement, grievance & disciplinary

```sql
announcement(id, tenant_id, title, body, category, audience jsonb, priority,
             publish_at, expires_at, attachment_keys text[], author_id,
             pinned, requires_acknowledgement)
announcement_read(announcement_id, employee_id, read_at, acknowledged_at)

company_event(id, tenant_id, title, description, event_type, start_at, end_at,
              location, audience jsonb, rsvp_enabled)
event_rsvp(event_id, employee_id, response, responded_at)

recognition(id, tenant_id, from_employee_id, to_employee_id, badge_id, message,
            visibility, value_tag, created_at)
recognition_badge(id, tenant_id, code, name, icon, points)

survey(id, tenant_id, code, title, description, survey_type, anonymous,
       audience jsonb, start_at, end_at, status, reminder_config jsonb)
survey_question(id, tenant_id, survey_id, text, question_type, options jsonb,
                required, sequence, dimension)
survey_response(id, tenant_id, survey_id, employee_id_hash, submitted_at,
                metadata jsonb)   -- hash, not id, when anonymous
survey_answer(id, tenant_id, survey_response_id, question_id, answer, rating)

suggestion(id, tenant_id, employee_id, anonymous, category, title, body,
           attachment_key, status, assigned_to_user_id, response,
           responded_at, votes)

grievance_ground_group(id, tenant_id, code, name)
grievance_ground(id, tenant_id, group_id, code, name, severity,
                 default_handler_role_id, sla_days)
grievance_channel(id, tenant_id, code, name)
grievance(id, tenant_id, grievance_number, raised_by_employee_id,
          on_behalf_of_employee_id, anonymous, ground_id, channel_id,
          description, attachment_keys text[], status, handler_user_id,
          raised_at, resolved_at, resolution, satisfaction_rating,
          workflow_instance_id)
grievance_appeal(id, tenant_id, grievance_id, reason, appealed_at,
                 reviewer_user_id, outcome, reviewed_at)

incident_type(id, tenant_id, code, name, severity)
incident_subtype(id, tenant_id, incident_type_id, code, name)
disciplinary_incident(id, tenant_id, incident_number, employee_id,
                      reported_by_employee_id, incident_type_id, subtype_id,
                      incident_date, location, description, witnesses jsonb,
                      attachment_keys text[], status, severity,
                      workflow_instance_id)
corrective_action(id, tenant_id, incident_id, action_type, issued_at,
                  issued_by, document_key, response_due_date, employee_response,
                  responded_at, outcome, effective_from, effective_to)
-- action_type: BACKGROUND_CHECK | CHARGE_SHEET | ORAL_WARNING | WARNING_LETTER
--              | SHOW_CAUSE | DOMESTIC_INQUIRY | SUSPENSION | TERMINATION | COURT_CASE
disciplinary_appeal(id, tenant_id, incident_id, corrective_action_id, reason,
                    appealed_at, reviewer_user_id, outcome, reviewed_at)
incident_journal(id, tenant_id, incident_id, entry, entered_by, entered_at)

-- Meals / canteen
canteen(id, tenant_id, location_id, code, name, active)
food_category(id, tenant_id, code, name, sequence)
food_item(id, tenant_id, category_id, code, name, description, image_key,
          dietary_tags text[], active)
item_price(id, tenant_id, food_item_id, canteen_id, price, subsidy,
           employee_category_id, valid_from, valid_to)
meal_event(id, tenant_id, code, name, start_time, end_time, shift_ids uuid[])
daily_menu(id, tenant_id, canteen_id, menu_date, meal_event_id,
           food_item_ids uuid[])
meal_order(id, tenant_id, employee_id, canteen_id, menu_date, meal_event_id,
           status, total_amount, subsidy_amount, payable_amount, ordered_at)
meal_order_line(order_id, food_item_id, quantity, unit_price)
meal_issue(id, tenant_id, employee_id, canteen_id, issued_at, meal_event_id,
           order_id, amount, issued_via)
```

---

## 15. Documents & e-signature

```sql
document_folder(id, tenant_id, parent_id, name, path, owner_type, owner_id,
                permissions jsonb, archived_at)
document(id, tenant_id, folder_id, name, file_key, mime_type, size_bytes,
         version, tags text[], owner_type, owner_id, permissions jsonb,
         expires_at, workflow_instance_id, status, uploaded_by, uploaded_at,
         archived_at, deleted_at)
document_version(id, tenant_id, document_id, version, file_key, uploaded_by,
                 uploaded_at, change_note)
document_tag(id, tenant_id, name, color)

document_template(id, tenant_id, code, name, category, body, variables jsonb,
                  output_format, requires_signature, signature_config jsonb,
                  active)
document_generation(id, tenant_id, template_id, entity_type, entity_id,
                    requested_by, generated_at, file_key,
                    workflow_instance_id, status)

signature_request(id, tenant_id, document_id, requester_user_id, status,
                  created_at, completed_at, provider, provider_ref)
signature_signer(id, tenant_id, signature_request_id, employee_id, email,
                 sequence, status, signed_at, ip, signature_key, audit jsonb)
```

---

## 16. Reporting, notifications & sync

```sql
report_definition(id, tenant_id, code, name, module, base_entity,
                  fields jsonb, filters jsonb, groupings jsonb, sorts jsonb,
                  chart_config jsonb, is_system, owner_user_id, shared_with jsonb)
report_schedule(id, tenant_id, report_definition_id, cron, format, recipients jsonb,
                password_protect, active, last_run_at, next_run_at)
report_execution(id, tenant_id, report_definition_id, requested_by, params jsonb,
                 status, row_count, file_key, started_at, completed_at, error)

notification_template(id, tenant_id, event_key, channel, locale, subject, body,
                      deep_link_pattern)
notification(id, tenant_id, user_id, event_key, title, body, deep_link,
             priority, channels text[], data jsonb, created_at, read_at,
             actioned_at)
notification_preference(tenant_id, user_id, event_key, channel, enabled,
                        digest_mode)
notification_delivery(id, notification_id, channel, provider, status,
                      attempted_at, delivered_at, error, retry_count)

-- Mobile sync
sync_cursor(tenant_id, user_id, device_id, scope, cursor, last_synced_at)
change_feed(id, tenant_id, sequence bigserial, entity_type, entity_id,
            operation, scopes text[], visible_to jsonb, occurred_at)
-- the monotonic sequence the delta-sync endpoint reads from
mutation_log(id, tenant_id, user_id, device_id, idempotency_key, endpoint,
             payload_hash, status, result_ref, received_at, processed_at)
-- server-side idempotency ledger for the offline outbox

job_definition(id, tenant_id, code, name, job_type, cron, params jsonb,
               enabled, last_run_at, next_run_at)
job_execution(id, tenant_id, job_definition_id, status, started_at, finished_at,
              records_processed, error, log_key)
webhook_subscription(id, tenant_id, url, event_keys text[], secret_enc, active,
                     failure_count, disabled_at)
webhook_delivery(id, tenant_id, subscription_id, event_key, payload_hash,
                 status, attempt, response_code, next_retry_at)
```

---

## 17. Entity relationship summary

```
tenant ─┬─ company ─── location ─── sub_location
        │      └─ cost_centre        └─ geofence
        ├─ department ── designation ── salary_grade ── job_description
        │
        ├─ employee ─┬─ employee_bank_account / document / qualification /
        │            │  dependent / emergency_contact / attachment
        │            ├─ employee_hierarchy (ltree)
        │            ├─ movement                     → employee_history
        │            ├─ employee_shift_schedule ← roster_assignment ← roster
        │            ├─ raw_punch → attendance_session → daily_attendance
        │            ├─ leave_application → leave_ledger → employee_leave_entitlement
        │            ├─ employee_pay_item / employee_salary / employee_statutory
        │            │        └─→ payroll_result ← payroll_run ← pay_period ← pay_group
        │            │                  └─ payroll_result_line ← pay_item ← formula
        │            │                          └─→ payslip / bank_file / gl_entry
        │            ├─ loan → loan_schedule
        │            ├─ expense_claim → expense_claim_line
        │            ├─ assessment → goal / assessment_competency / mra_response
        │            ├─ training_enrollment ← training_schedule ← course
        │            ├─ exit_notice → clearance → clearance_task
        │            └─ grievance / disciplinary_incident / suggestion / recognition
        │
        ├─ workflow_definition → workflow_step
        │        └─ workflow_instance → workflow_task   ← every module above
        │
        ├─ field_definition ──(drives)──→ custom_fields jsonb on any entity
        ├─ formula ──(used by)──→ pay_item, leave rules, eligibility
        ├─ change_feed ──(drives)──→ mobile delta sync
        └─ audit_log ←──(written by)── every mutation
```

---

## 18. Volume & partitioning plan

| Table | Est. rows / 10k-employee tenant / year | Strategy |
|---|---|---|
| `raw_punch` | ~7.3 M | Range partition by month; archive > 24 months to cold storage |
| `daily_attendance` | ~3.65 M | Range partition by year; fully recomputable |
| `audit_log` | ~20 M | Range partition by month; 7-year retention (statutory) |
| `change_feed` | ~30 M | Range partition by month; prune > 90 days (older clients do a full resync) |
| `payroll_result_line` | ~3.6 M | Partition by `pay_period_id` range; never deleted |
| `notification` | ~15 M | Partition by month; prune > 12 months |
| `leave_ledger` | ~500 K | No partitioning; never deleted (balance reconstruction) |
| `employee` | 10 K | Small; heavily indexed |

**Retention & compliance:** payroll, leave ledger, audit, and disciplinary records are retained per statutory minimums (typically 5–7 years). Candidate data has an explicit `retention_until` for GDPR/PDPA-style right-to-erasure. Everything else is prunable.
