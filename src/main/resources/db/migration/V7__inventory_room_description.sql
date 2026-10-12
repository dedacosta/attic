-- A room has a description, entered with its name on the building, to tell the rooms apart.

ALTER TABLE inventory_room ADD COLUMN description TEXT;
