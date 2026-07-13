# Product Specification & Technical Roadmap: Expense & Trip Tracker

This document is structured as a comprehensive technical specification blueprint. You can feed this directly into **Claude Code** or any developer agent to systematically implement, refactor, or scale your application.

---

## 1. Context & Application Audit (System Context)

### Current Architecture & Features
* **Home Dashboard:** Tracks total expenses, exhibits 30-day and 6-month visual trends via bar graphs, and includes a "Top Merchant" widget (currently under development).
* **Transaction Ledger & Geolocation:** Displays recent transactions accompanied by category icons (Food, Travel, Fuel, etc.). Features an embedded map showing transaction locations (currently static/non-editable; backfilled data lacks coordinates).
* **Trip Management Module:** Dedicated view displaying trip name, total trip expenditure, category allocation, and a daily transaction timeline with a secondary bar graph. Long-press action triggers trip deletion. Supports initiating new trips via a naming modal. Map integration is currently static.
* **Settings & System Utilities:** Data lifecycle features (Import/Export data, Purge data lake). Test utilities (Random test transaction generator, manual transaction logger). SMS scraping utility to backfill historical transaction data from the past 180 days.

---

## 2. Enhancements to Existing Features

### A. Map Interactive Viewport & Clustering
* **Problem:** Maps are currently static, non-editable, and fail to handle backfilled data gracefully due to missing coordinates.
* **Solution:** Transform the map into a dynamic, interactive component using bounding-box optimization. 
* **Implementation Steps for Claude Code:**
    * Enable map gestures (pan, pinch-to-zoom).
    * Implement marker clustering for dense transaction zones to prevent UI lag.
    * For backfilled transactions lacking precise GPS data, implement a fallback geocoding pipeline using the merchant name or location string parsed from the SMS text. If no location data exists, place a default indicator at the home city baseline.

### B. Empty State Optimization & UI Guardrails
* **Problem:** "Top Merchant" is blank; trip deletion via long-press is prone to accidental triggers.
* **Solution:** Introduce fallback components and confirmation layers.
* **Implementation Steps for Claude Code:**
    * Add an elegant skeleton loader or informational empty-state view for "Top Merchant" (e.g., *"Not enough data yet. Add 3 more transactions to see your top merchants"*).
    * Introduce a destructive action guardrail: when a user holds to delete a trip, intercept the action with a confirmation bottom sheet or modal requesting verification before executing the database purge.

---

## 3. New Advanced Feature Specifications

### A. Smart Budgeting & Forecasting Engine
Integrate a budget tracking matrix that monitors spending thresholds per category and trip.

* **Proactive Burn Rate Alerts:** Calculate daily velocity to notify users if they are on track to overshoot their budget.
* **Predictive Forecasting:** Implement a simple moving average calculation to project month-end spend based on current cycle velocity.
* **Mathematical Model:** Use a linear velocity tracking formula to calculate the projected month-end spend ($E_{\text{projected}}$):

$$E_{\text{projected}} = E_{\text{current}} + \left( \frac{E_{\text{current}}}{D_{\text{current}}} \times (D_{\text{total}} - D_{\text{current}}) \right)$$

*Where $E_{\text{current}}$ is current expenditure, $D_{\text{current}}$ is the current day of the month, and $D_{\text{total}}$ is the total number of days in the month.*

### B. Advanced SMS Scraper & Deduplication Pipeline
Enhance the existing 180-day SMS backfill utility into a robust, automated background service.

* **Regex Pattern Matrix:** Expand parsing rules to accommodate complex transaction strings from various financial institutions, catching variables like currency, transaction type (debit vs. credit), merchant names, and account balances.
* **Deduplication Engine:** Prevent duplicate entries during multiple backfill runs by creating a unique transactional hash for every incoming record.
* **Hash Logic:** Generate a unique identifier utilizing SHA-256 string concatenation:

$$\text{Transaction Hash} = \text{SHA256}(\text{Amount} + \text{Timestamp} + \text{Merchant})$$

### C. Trip Shared Expenses (Split Bill Utility)
Expand the trip module to support group travel dynamics.

* **Multi-User Ledger:** Allow users to add "Participants" to a specific trip.
* **Debt Simplification Engine:** Implement a balance sheet calculator that aggregates who paid for what and computes the minimum number of transactions required to settle up at the end of the trip.

---

## 4. Technical Roadmap & Database Schema Recommendations

To support these features, ensure your local relational database schema (SQLite/Room/CoreData) is structured to support relational integrity.

| Table | Column Name | Data Type | Constraints / Key Type |
| :--- | :--- | :--- | :--- |
| **Transactions** | `id` | TEXT / UUID | PRIMARY KEY |
| | `amount` | REAL | NOT NULL |
| | `category_id` | TEXT | FOREIGN KEY -> Categories(id) |
| | `trip_id` | TEXT (Nullable) | FOREIGN KEY -> Trips(id) |
| | `timestamp` | INTEGER | Epoch Time |
| | `latitude` / `longitude` | REAL | Nullable (for backfills) |
| | `tx_hash` | TEXT | UNIQUE INDEX (Deduplication) |
| **Trips** | `id` | TEXT / UUID | PRIMARY KEY |
| | `name` | TEXT | NOT NULL |
| | `budget_limit` | REAL | Default: 0.00 |
| | `status` | TEXT | 'ACTIVE', 'COMPLETED' |
| **Budgets** | `id` | TEXT / UUID | PRIMARY KEY |
| | `category_id` | TEXT | FOREIGN KEY -> Categories(id) |
| | `monthly_limit` | REAL | NOT NULL |

---

## 5. Modular Claude Code Prompts

Copy and paste the following markdown blocks directly into **Claude Code** to begin development on specific sections.

### Prompt 1: Implementing the Map Enhancements
Review the current map component configuration in the application. Update the map view to be interactive, enabling smooth pan, zoom, and tilt gestures. Implement a marker clustering mechanism for transaction pins located within close geographical proximity to optimize UI rendering performance. If a transaction lacks latitude and longitude coordinates (such as backfilled historical data), parse the merchant string to approximate coordinates, or gracefully omit the marker while keeping the transaction listed in the chronological text ledger.

### Prompt 2: Adding the Trip Deletion Guardrail & Empty States
Locate the long-press gesture handler for deleting trips in the trip management module. Refactor this behavior to intercept the delete action: instead of instantly purging the record, display a confirmation bottom sheet detailing the trip name and the number of associated transactions that will be permanently lost. Only execute the database deletion if the user explicitly confirms. Additionally, locate the "Top Merchant" UI component on the dashboard and implement an empty-state fallback layout featuring a text asset that displays when transaction counts are insufficient.

### Prompt 3: Engineering the Budget Forecasting System
Create a new Budgeting system within the application. Add a 'Budgets' table to the database that maps category IDs to monthly monetary limits. Write a calculation service that computes the current month's velocity (burn rate) and applies a linear projection formula to predict total month-end spend. Expose this data via a visual progress bar on the Home Dashboard that transitions to a warning state if the projected spend exceeds the user-defined budget limit.