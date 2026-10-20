-- A catalog item belongs to the Heritage, or to one or more heirs. The owner typed so far becomes
-- the heir of that name; one that is neither an heir nor "Heritage" is kept in the comments.

CREATE TABLE catalog_item_owner (
	item_id TEXT NOT NULL REFERENCES catalog_item (id) ON DELETE CASCADE,
	heir_id TEXT NOT NULL REFERENCES heir (id) ON DELETE CASCADE,
	PRIMARY KEY (item_id, heir_id)
);

CREATE INDEX catalog_item_owner_heir_id ON catalog_item_owner (heir_id);

INSERT INTO catalog_item_owner (item_id, heir_id)
SELECT i.id, h.id FROM catalog_item i JOIN heir h ON lower(trim(h.name)) = lower(trim(i.owner));

UPDATE catalog_item SET comments = coalesce(comments || char(10), '') || 'Owner: ' || owner
WHERE owner <> 'Heritage'
	AND NOT EXISTS (SELECT 1 FROM catalog_item_owner o WHERE o.item_id = catalog_item.id);

ALTER TABLE catalog_item DROP COLUMN owner;
