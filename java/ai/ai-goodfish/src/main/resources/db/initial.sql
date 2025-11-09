ALTER TABLE account
    ADD COLUMN IF NOT EXISTS 	tenant_id varchar(20) DEFAULT '000000'::character varying NULL;
