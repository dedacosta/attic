package com.mephys.attic.property;

import java.util.List;

/**
 * Response body for the estate: the buildings and the land parcels, each by name.
 */
record EstateResponse(List<PropertyResponse> buildings, List<PropertyResponse> lands) {
}
