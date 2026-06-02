-- Update the invoices_status_check constraint to allow the PENDING_CASH_VERIFICATION status
ALTER TABLE invoices DROP CONSTRAINT IF EXISTS invoices_status_check;

ALTER TABLE invoices ADD CONSTRAINT invoices_status_check 
CHECK (status::text = ANY (ARRAY['GENERATED'::text, 'PAID'::text, 'OVERDUE'::text, 'PENDING_CASH_VERIFICATION'::text]));
