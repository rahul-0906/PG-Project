# PG CRM Calculations Engine Documentation

This document serves as the single source of truth for all mathematical logic, billing pipelines, allocation models, and data aggregation algorithms implemented within the PG CRM application. It defines the formulas, database fields, and logic constraints used by the Java Spring Boot backend services and React frontend UI visualizations.

> [!NOTE]
> **System Scope & Billing Boundaries**
> This Calculations Engine governs **Tenant Operations** (guest rent, utility bill splits, daily addon logs, and guest invoicing). **SaaS-level billing** (e.g. client subscriptions, platform setup fees, and annual maintenance contract payments) is managed externally by the centralized **B2B SaaS Control Plane** and is outside the scope of individual single-tenant calculation databases.

---

## 1. The Monthly Invoice Generation Pipeline (Arrears Billing)

The billing pipeline operates on an **arrears model** where monthly base rents, utility shares, and daily add-on consumptions are calculated and invoiced in the subsequent month.

### 1.1 The "Look-Back" Logic
When a monthly invoice generation runs (either automatically via the `MonthlyBillingScheduler` cron task or manually triggered by a Tier 2 Manager / Branch Admin (PG Manager) on the 1st of a month), the system executes a "look-back" query to capture data from the preceding calendar month. 

For an invoice generated on Month $M$, Day 1:
* The billing period begins on **Day 1 of Month $M-1$** (at `00:00:00.000` UTC).
* The billing period ends on the **Last Day of Month $M-1$** (at `23:59:59.999` UTC).

```
   [ May 1st ]                              [ May 31st ]       [ June 1st ]
        |----------------------------------------|                  |
        |<======== Consumer Billing Period ======>|                  |
        |          (Rent + EB + Add-ons)         |                  |
        |                                                           |
        +-----------------------------------------------------------+ (Invoice Generated)
```

#### Example (Look-Back Timeline):
An invoice generated on **June 1st, 2026** targets the billing month of **May 2026**.
* **Billing Period Start**: May 1st, 2026, 00:00:00
* **Billing Period End**: May 31st, 2026, 23:59:59
* Base Rent, Sub-Meter readings, and Daily Log usage (breakfast, lunch, dinner, omelettes, laundry) are parsed strictly for dates starting `2026-05-01` through `2026-05-31`.

---

### 1.2 Rent Pro-ration
When a guest checks in during the middle of a billing period, their rent is prorated based on their active occupancy duration. The base rent is calculated using a daily rate determined by dividing the monthly rate by the actual number of days in that billing month.

#### Mathematical Formula:
$$\text{Daily Rate} = \frac{\text{Monthly Base Rent}}{\text{Total Days in Billing Month}}$$

$$\text{Active Occupancy Days} = (\text{End Date of Occupancy}) - (\text{Start Date of Occupancy}) + 1$$

$$\text{Prorated Rent} = \text{Daily Rate} \times \text{Active Occupancy Days}$$

*Note: If the guest stays for the entire month, the calculation evaluates to the flat Monthly Base Rent.*

#### Numerical Example:
A guest checks into a room with a monthly base rent of **₹9,000** on **May 10th, 2026**.
* **Monthly Base Rent**: ₹9,000
* **Total Days in Billing Month (May)**: 31
* **Check-In Date**: May 10th, 2026
* **Active Occupancy Days (May 10 to May 31)**: 
  $$\text{Active Days} = 31 - 10 + 1 = 22\text{ Days}$$
* **Daily Rate Calculation**:
  $$\text{Daily Rate} = \frac{₹9,000}{31} \approx ₹290.3225\text{ per day}$$
* **Prorated Rent Calculation**:
  $$\text{Prorated Rent} = ₹290.3225 \times 22 \approx ₹6,387.09\text{ (Rounded to ₹6,387)}$$

---

### 1.2b Rent Impact of Multi-Bed & Whole Room Bookings
When a guest is assigned multiple beds or checks in with the **Whole Room Booking** toggle enabled (`isBookEntireRoom = true`), the monthly base rent is calculated by multiplying the room's base rent by the sharing capacity (sharing type) of that room:

#### Mathematical Formula:
$$\text{Adjusted Base Rent} = \text{Room Base Rent} \times \text{Room Sharing Type}$$

* **Single Bed Allocation**: The guest is billed only the standard `Room Base Rent`.
* **Whole Room Booking**: The guest is billed the `Adjusted Base Rent` reflecting the cost of all beds in the room.
* **Prorated Adjusted Rent**: If checking in mid-month under a whole room booking, the daily rate uses the adjusted base rent:
  $$\text{Daily Rate} = \frac{\text{Adjusted Base Rent}}{\text{Total Days in Billing Month}}$$
  $$\text{Prorated Rent} = \text{Daily Rate} \times \text{Active Occupancy Days}$$

#### Numerical Example:
A guest checks in under a whole room booking for a double-sharing room (sharing type = 2) on **May 10th, 2026** where the room rent is **₹9,000**.
* **Room Base Rent**: ₹9,000
* **Room Sharing Type**: 2 (Double-Sharing)
* **Adjusted Base Rent Calculation**:
  $$\text{Adjusted Base Rent} = ₹9,000 \times 2 = ₹18,000\text{ per month}$$
* **Active Occupancy Days (May 10 to May 31)**: 22 Days
* **Daily Rate Calculation**:
  $$\text{Daily Rate} = \frac{₹18,000}{31} \approx ₹580.6452\text{ per day}$$
* **Prorated Rent Calculation**:
  $$\text{Prorated Rent} = ₹580.6452 \times 22 \approx ₹12,774.19\text{ (Rounded to ₹12,774)}$$

---

### 1.3 Line Item Aggregation
The total amount of a generated invoice (`totalAmount` field of the `Invoice` entity) is compile-aggregated as the sum of all individual service and utility line items.

#### Mathematical Formula:
$$\text{Invoice Total Amount} = \text{Base / Prorated Rent} + \text{EB Utility Dues} + \sum (\text{Add-on Quantity} \times \text{Add-on Rate})$$

#### Numerical Example:
For the billing month of May 2026, a guest has the following charges:
* **Prorated Rent**: ₹6,387
* **EB Utility Split Dues**: ₹450
* **Food Add-ons (Omelettes)**: ₹300
* **Service Add-ons (Washing Machine)**: ₹200
* **Invoice Total Calculation**:
  $$\text{Total Amount} = ₹6,387 + ₹450 + ₹300 + ₹200 = ₹7,337$$

---

## 2. Electricity (EB) Bill Split Algorithms

The system supports two distinct calculations for charging guests for electricity usage, configured via the property's settings interface.

### 2.1 Equal Split Method
Under this configuration, the building's utility consumption cost is divided equally among all residents who had active occupancy during the billing period.

#### Mathematical Formula:
$$\text{Consumed Units} = \text{End Meter Reading} - \text{Start Meter Reading}$$

$$\text{Total Bill Cost} = \text{Consumed Units} \times \text{Rate Per Unit}$$

$$\text{Individual Split Share} = \frac{\text{Total Bill Cost}}{\text{Count of Active Guests during Period}}$$

#### Numerical Example:
A building has **10 active guests** during May 2026. The main building sub-meter readings are:
* **Start Reading (May 1st)**: 10,500 kWh
* **End Reading (May 31st)**: 11,125 kWh
* **Rate Per Unit**: ₹8.00 per kWh
* **Active Guests count**: 10
* **Total Consumed Units**:
  $$\text{Consumed Units} = 11,125 - 10,500 = 625\text{ Units}$$
* **Total Bill Cost Calculation**:
  $$\text{Total Cost} = 625 \times ₹8.00 = ₹5,000$$
* **Individual Share Calculation**:
  $$\text{Individual Share} = \frac{₹5,000}{10} = ₹500\text{ per guest}$$

---

### 2.2 Sub-Meter Method
Under this configuration, rooms/beds are equipped with individual sub-meters. Guests are billed directly for their specific room consumption.

#### Mathematical Formula:
$$\text{Room Consumed Units} = \text{Room End Reading} - \text{Room Start Reading}$$

$$\text{Individual Utility Charge} = \text{Room Consumed Units} \times \text{Rate Per Unit}$$

#### Numerical Example:
A guest resides in Room 102, which is configured with an active sub-meter:
* **Room Start Reading (May 1st)**: 1,200 kWh
* **Room End Reading (May 31st)**: 1,256 kWh
* **Rate Per Unit**: ₹8.00 per kWh
* **Room Consumed Units**:
  $$\text{Room Consumed Units} = 1,256 - 1,200 = 56\text{ Units}$$
* **Individual Utility Charge Calculation**:
  $$\text{Individual Charge} = 56 \times ₹8.00 = ₹448$$

---

## 3. Daily Log & Add-on Aggregations

During the billing cycle, unbilled items from the daily ledger logs (`DailyLog` entity) are fetched, aggregated, and compiled as separate invoice line items. These rates are defined globally in `PricingConfig` (e.g. `omelette_price`, `laundry_price`).

### 3.1 Aggregation Math
The system counts the occurrences of marked items in a guest's daily logs within the billing calendar dates and multiplies the sum by the configured unit pricing.

#### Mathematical Formula:
$$\text{Add-on Charge} = \sum_{d=\text{Start Date}}^{\text{End Date}} (\text{Quantity}_d) \times \text{Unit Rate}$$

#### Numerical Example:
A guest has daily logs in May 2026 showing omelette breakfast orders and washing machine sessions.
* **Pricing Config**:
  * **Omelette Price**: ₹20.00
  * **Laundry (Washing Machine) Session Price**: ₹50.00
* **May Log Tallies**:
  * **Total Omelettes Logged**: 15
  * **Total Washing Machine Sessions Logged**: 4
* **Omelette Charge Calculation**:
  $$\text{Omelette Charge} = 15 \times ₹20.00 = ₹300$$
* **Washing Machine Charge Calculation**:
  $$\text{Laundry Charge} = 4 \times ₹50.00 = ₹200$$
* **Total Aggregated Add-ons Subtotal**:
  $$\text{Add-ons Subtotal} = ₹300 + ₹200 = ₹500$$

---

## 4. Checkout & Final Settlement Math

When a guest requests a checkout notice or final settlement, the `SettlementService` computes the final financial calculations. This aggregates the deposit refund, subtracting unpaid items and unbilled accrued dues.

### 4.1 Deduction Formula
$$\text{Total Current Unbilled Dues} = \text{Current Month Prorated Rent} + \text{Current Month Unbilled EB} + \text{Current Month Unbilled Add-ons}$$

$$\text{Deduction Balance} = \text{Unpaid Past Invoices} + \text{Total Current Unbilled Dues}$$

$$\text{Settlement Balance} = \text{Advance Deposit} - \text{Deduction Balance}$$

* **If Settlement Balance is $> 0$**: The system registers a **Refund Due** to the guest.
* **If Settlement Balance is $< 0$**: The system registers an **Outstanding Due** that the guest must pay before check-out is finalized.

#### Numerical Example (Refund Case):
A guest checks out on May 22nd, 2026.
* **Advance Deposit (Paid at Check-in)**: ₹7,000
* **Unpaid Invoices (Past Months)**: ₹1,500
* **Current Month (May) Unbilled Dues**:
  * **Prorated Rent (May 1 to May 22)**: ₹5,000
  * **Unbilled EB Utility Share**: ₹300
  * **Unbilled Add-on Meals**: ₹200
* **Total Current Unbilled Dues Calculation**:
  $$\text{Unbilled Dues} = ₹5,000 + ₹300 + ₹200 = ₹5,500$$
* **Total Deduction Balance Calculation**:
  $$\text{Deductions} = ₹1,500\text (Past Dues) + ₹5,500\text (Current Unbilled Dues) = ₹7,000$$
* **Settlement Balance Calculation**:
  $$\text{Settlement Balance} = ₹7,000\text (Deposit) - ₹7,000\text (Deductions) = ₹0$$
  *(Neutral Settlement - No refund due, no outstanding balance.)*

#### Numerical Example (Due Case):
A guest checks out on May 25th, 2026.
* **Advance Deposit**: ₹7,000
* **Unpaid Invoices (Past Months)**: ₹1,500
* **Current Month Unbilled Dues**:
  * **Prorated Rent (May 1 to May 25)**: ₹6,500
  * **Unbilled EB Utility Share**: ₹400
  * **Unbilled Add-on Meals**: ₹300
* **Total Current Unbilled Dues Calculation**:
  $$\text{Unbilled Dues} = ₹6,500 + ₹400 + ₹300 = ₹7,200$$
* **Total Deduction Balance Calculation**:
  $$\text{Deductions} = ₹1,500 + ₹7,200 = ₹8,700$$
* **Settlement Balance Calculation**:
  $$\text{Settlement Balance} = ₹7,000 - ₹8,700 = -₹1,700$$
  *(Guest owes ₹1,700 before checkout can be checked out.)*

---

## 5. Dashboard Analytics & Chart Aggregations

Dashboard analytics run optimized database counts and aggregations to display system health metrics.

> [!NOTE]
> **Branch/Building Scoped Calculation Context**
> All dashboard computations and counts are scoped to the active building branch. On the frontend, the user's selected building branch is retrieved from `sessionStorage` key `selectedBranchId` and transmitted in HTTP headers as `X-Selected-Branch-Id`. The backend interceptor validates this ID against the user's authorized branch mappings (defined in user entity) and injects it as `:buildingId` to scope the JPA/SQL aggregation queries.

### 5.1 Bed Occupancy (Pie/Donut Chart)
The React dashboard visualizes room allocations by grouping bed states: `OCCUPIED`, `VACANT`, and `NOTICE` (guests on active notice periods).

#### Mathematical Formulas:
$$\text{Occupancy Rate (\%)} = \left( \frac{\text{OCCUPIED Beds}}{\text{Total Beds}} \right) \times 100$$

$$\text{Vacant Rate (\%)} = \left( \frac{\text{VACANT Beds}}{\text{Total Beds}} \right) \times 100$$

$$\text{Notice Rate (\%)} = \left( \frac{\text{NOTICE Beds}}{\text{Total Beds}} \right) \times 100$$

#### Numerical Example:
A building has a layout comprising **112 total beds**.
* **Beds Status counts**:
  * **Occupied Beds**: 10
  * **Notice Beds**: 2
  * **Vacant Beds**: 100
* **Percentage Computations**:
  * **Occupied Rate**:
    $$\text{Occupancy \%} = \left( \frac{10}{112} \right) \times 100 \approx 8.93\%$$
  * **Notice Rate**:
    $$\text{Notice \%} = \left( \frac{2}{112} \right) \times 100 \approx 1.79\%$$
  * **Vacant Rate**:
    $$\text{Vacant \%} = \left( \frac{100}{112} \right) \times 100 \approx 89.28\%$$

---

### 5.2 Revenue Trends (Bar Chart)
The revenue visualization graph tracks monthly revenue by querying paid invoices.

#### Aggregation Query (Concept):
```sql
SELECT 
    invoice_month AS billingMonth,
    SUM(total_amount) AS revenueAmount
FROM invoices
WHERE status = 'PAID'
  AND building_id = :buildingId
GROUP BY invoice_month, invoice_year
ORDER BY invoice_year ASC, invoice_month ASC;
```

#### Graph Data Point Compilation:
The query maps records to dynamic data points used by the frontend `<BarChart>` Recharts component:
* **X-Axis Values (Categories)**: Month Labels (`5/2026`, `6/2026`, etc.)
* **Y-Axis Values (Metrics)**: Accumulated Revenue (₹)

---

### 5.3 Pending Tasks (Counters)
Dashboard warning metrics display pending items using simple database counts.

* **Open Maintenance Tickets**:
  ```sql
  SELECT COUNT(*) FROM maintenance_tickets 
  WHERE status = 'OPEN' AND building_id = :buildingId;
  ```
* **Unpaid Invoices count**:
  ```sql
  SELECT COUNT(*) FROM invoices 
  WHERE status = 'PENDING' AND building_id = :buildingId;
  ```
