package com.mephys.attic.property;

import java.util.List;

/**
 * Response body for the heritage: the houses and the land parcels, each by name.
 */
record HeritageResponse(List<PropertyResponse> houses, List<PropertyResponse> lands) {
}
