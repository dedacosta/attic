-- A room keeps the name it has on its building; the inventory can give it another title for
-- the PDF.

ALTER TABLE inventory_room ADD COLUMN pdf_title TEXT;
