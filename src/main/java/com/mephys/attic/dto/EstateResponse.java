package com.mephys.attic.dto;

import java.util.List;

/**
 * Response body for the estate: the buildings and the land parcels, each by name.
 */
public record EstateResponse(List<PropertyResponse> buildings, List<PropertyResponse> lands) {
}
