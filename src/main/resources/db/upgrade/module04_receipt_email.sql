-- Fee receipt email goes through Settings → Integrations (the Finance mailbox, else the school's main email account).
-- status_detail: why a dispatch was not sent, or which mailbox sent it.
ALTER TABLE fee_receipt_dispatches ADD COLUMN IF NOT EXISTS status_detail character varying(500);
