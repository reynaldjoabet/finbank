# MRA (Mauritius Revenue Authority)

From MRA’s own published mandate and service pages:
- MRA administers revenue laws, assesses/collects taxes, and enforces compliance; it collects direct taxes (e.g., corporate, personal income, withholding/TDS), indirect taxes (VAT, customs duties, excise duties, gambling taxes), plus fees/levies and a variety of contributions (e.g., CSG, NPF/NSF, HRDC levy, etc.).
- MRA runs a consolidated e‑Services portal for “File and Pay”, including individual and corporate filing, VAT, customs, e‑payments, e‑objection, and other services.
- Their Customs function includes both a fiscal role (collect/protect duties/taxes) and a protection/security role.
- MRA is organized into operational tax functions (Large Taxpayer / Medium & Small, etc.) and compliance/investigations plus shared services/IS, etc.
- e‑Payment modes described publicly include Direct Debit arrangements and online card flows (implementation details vary, but the key is “payment initiation + reconciliation”).
- Their login identifier patterns include TAN / NID / BRN / ERN-style identifiers (important for identity design).

A single system with two faces:
1. Taxpayer / Agent Portal (self-service)
   - Registration / profile (TAN-like account)
   - File returns (Income Tax, VAT, PAYE/CSG/NSF, etc. as modular “return types”)
   - Pay liabilities (create payment intents; track settlement)
   - View history, notices, receipts
   - Submit objections / dispute tickets (module-ready)
2. Officer Portal (back-office)
   - Work queues: new filings, mismatches, late filers
   - Assessment workflow: approve/reject, raise liabilities, initiate refunds
   - Case management: audits/investigations/customs holds
   - Risk rules (configurable) + escalation
   - Full audit trail of every action (who did what, when, from where)

Core resources you’ll use everywhere:
- Taxpayer
- Registration
- Return
- Assessment
- Liability
- Payment / Receipt
- Refund
- Case / Task
- Document / Attachment
- Notice / Communication
- Audit log

1) Auth, identity, access control (Public + Backoffice)
MRA exposes password management and identity flows in its portal menu.
```sh
POST /api/v1/auth/login
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
POST /api/v1/auth/password/forgot
POST /api/v1/auth/password/reset
GET /api/v1/auth/me
GET /api/v1/auth/permissions
```
Delegation (agent acting on behalf of taxpayer is implied by “file on behalf of a taxpayer” patterns, e.g. VAT e‑filing service centres).

```sh
POST /api/v1/delegations
GET /api/v1/delegations
DELETE /api/v1/delegations/{delegationId}
```

2) Taxpayer account & TAN lifecycle
MRA explicitly offers retrieve TAN, apply for TAN, retrieve password, etc.
```sh
POST /api/v1/taxpayers (register)
GET /api/v1/taxpayers/{taxpayerId}
PATCH /api/v1/taxpayers/{taxpayerId} (profile changes)
POST /api/v1/taxpayers/tan/retrieve
POST /api/v1/taxpayers/tan/apply
POST /api/v1/taxpayers/{taxpayerId}/status/suspend (backoffice)
POST /api/v1/taxpayers/{taxpayerId}/status/reactivate (backoffice)
```

3) Registrations (VAT, employer, etc.)
(Your platform will need formal “registration state machines”.)

```sh
POST /api/v1/registrations (type = VAT / Employer / etc.)
GET /api/v1/registrations/{registrationId}
GET /api/v1/taxpayers/{taxpayerId}/registrations
POST /api/v1/registrations/{registrationId}/approve (officer)
POST /api/v1/registrations/{registrationId}/reject (officer)
POST /api/v1/registrations/{registrationId}/documents (upload evidence)
```
4) Returns (“File”) — draft, validate, submit, amend
MRA describes filing declarations at specified periods and paying accordingly.
For VAT, they support quarterly/monthly returns and note amendment patterns and supporting annex/doc flows.

```sh
GET /api/v1/tax-programs (IncomeTax, VAT, PAYE/CSG/NSF, etc.)
GET /api/v1/tax-programs/{program}/periods?year=2026
Core return lifecycle:
POST /api/v1/returns/drafts
PUT /api/v1/returns/{returnId} (save draft)
POST /api/v1/returns/{returnId}/validate
POST /api/v1/returns/{returnId}/submit
POST /api/v1/returns/{returnId}/amend (creates amended return/version)
GET /api/v1/returns/{returnId}
GET /api/v1/taxpayers/{taxpayerId}/returns?program=VAT&year=2026
Program-specific add-ons (VAT annex + supporting docs are explicitly described):
POST /api/v1/returns/{returnId}/attachments (generic)
POST /api/v1/returns/{returnId}/attachments/vat-annex (CSV/XML upload)
POST /api/v1/returns/{returnId}/supporting-documents (repayment/adjustment evidence)

```

5) Assessments, liabilities, penalties, interest
Self-assessment + “DG not satisfied → audit/investigation” implies assessments and adjustments.
VAT page describes penalties/interest concepts tied to late filing/payment.

```sh
POST /api/v1/assessments (officer/system)
GET /api/v1/assessments/{assessmentId}
POST /api/v1/assessments/{assessmentId}/issue-notice
GET /api/v1/taxpayers/{taxpayerId}/liabilities?status=open
GET /api/v1/liabilities/{liabilityId}
POST /api/v1/liabilities/{liabilityId}/recalculate (penalty/interest engine)
```

6) Payments (“Pay”), receipts, direct debit mandates
MRA exposes e‑payment and direct debit mandate references (PLACH direct debit).
Payment intents + reconciliation:
```sh
POST /api/v1/payments/intents (idempotent)
GET /api/v1/payments/{paymentId}
POST /api/v1/payments/{paymentId}/confirm (card redirect completion)
GET /api/v1/payments/{paymentId}/receipt
GET /api/v1/taxpayers/{taxpayerId}/payments
Direct debit:
POST /api/v1/direct-debit/mandates
GET /api/v1/direct-debit/mandates/{mandateId}
POST /api/v1/direct-debit/mandates/{mandateId}/cancel
```

7) Refunds & repayment claims
VAT flow includes repayment claims + supporting docs.
Individual menu mentions VAT refund on residential building.

```sh
POST /api/v1/refunds/claims (program = VAT / scheme)
GET /api/v1/refunds/claims/{claimId}
GET /api/v1/taxpayers/{taxpayerId}/refunds
POST /api/v1/refunds/claims/{claimId}/documents
POST /api/v1/refunds/claims/{claimId}/approve (officer)
POST /api/v1/refunds/claims/{claimId}/reject (officer)
POST /api/v1/refunds/claims/{claimId}/pay-out (finance batch)
```

8) Objections, disputes, alternative resolution
MRA provides electronic objections; requires TAN + assessment/case/refund/document number and allows attachments.
e‑ATDR exists as a module in e‑services.

```sh
POST /api/v1/objections
GET /api/v1/objections/{objectionId}
POST /api/v1/objections/{objectionId}/documents
POST /api/v1/objections/{objectionId}/withdraw
POST /api/v1/disputes/atdr/cases
GET /api/v1/disputes/atdr/cases/{caseId}
POST /api/v1/disputes/atdr/cases/{caseId}/schedule
POST /api/v1/disputes/atdr/cases/{caseId}/decision
```

9) Case management (audit/investigation/compliance)
MRA explicitly mentions audit/investigation selection in the self-assessment context.

```sh
POST /api/v1/cases
GET /api/v1/cases/{caseId}
GET /api/v1/cases?status=open&queue=VAT_REFUNDS
POST /api/v1/cases/{caseId}/assign
POST /api/v1/cases/{caseId}/status
POST /api/v1/cases/{caseId}/tasks
POST /api/v1/cases/{caseId}/notes
POST /api/v1/cases/{caseId}/documents
GET /api/v1/backoffice/queues (work allocation)

```

10) Customs module (if you truly want “holistic MRA”)
MRA describes customs functions: revenue collection (duties/excise/VAT under customs laws), protection/security, trade facilitation; plus risk management and post-control audit.
You’d typically split “customs declarations” into its own bounded context, but API-wise:

```sh
POST /api/v1/customs/declarations
GET /api/v1/customs/declarations/{declId}
POST /api/v1/customs/declarations/{declId}/assess
POST /api/v1/customs/declarations/{declId}/selectivity/run (risk)
POST /api/v1/customs/holds
POST /api/v1/customs/holds/{holdId}/release
POST /api/v1/customs/post-control-audits
GET /api/v1/customs/post-control-audits/{pcaId}
11) e‑Invoicing (program module)
MRA lists e‑invoicing in e‑services.
POST /api/v1/e-invoicing/invoices
GET /api/v1/e-invoicing/invoices/{invoiceId}
POST /api/v1/e-invoicing/invoices/{invoiceId}/cancel
GET /api/v1/e-invoicing/reports
```

12) Appointments & service centres
MRA lists slot reservation / e‑appointment.

```sh
GET /api/v1/appointments/slots?office=PORT_LOUIS&date=2026-03-10
POST /api/v1/appointments
GET /api/v1/appointments/{appointmentId}
POST /api/v1/appointments/{appointmentId}/cancel
```

13) Certificates & deregistration
MRA lists tax residence certificate application and deregistration.

```sh
POST /api/v1/certificates/tax-residence/applications
GET /api/v1/certificates/tax-residence/applications/{appId}
POST /api/v1/taxpayers/{taxpayerId}/deregistration
GET /api/v1/taxpayers/{taxpayerId}/deregistration/{reqId}
```

14) Admin/reference/rules (Backoffice)
```sh
GET /api/v1/admin/reference/tax-programs
PUT /api/v1/admin/reference/tax-programs/{program}
GET /api/v1/admin/risk-rules
POST /api/v1/admin/risk-rules
POST /api/v1/admin/risk-rules/{ruleId}/enable
GET /api/v1/backoffice/audit (like the one we already had)

```

15) Integrations (webhooks, reconciliation, batch)
```sh
POST /api/v1/integrations/payments/webhook
POST /api/v1/integrations/banks/settlements/import
POST /api/v1/integrations/documents/virus-scan-callback
POST /api/v1/integrations/identity/kyc-callback

```