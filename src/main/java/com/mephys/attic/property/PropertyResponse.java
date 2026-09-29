package com.mephys.attic.property;

import com.mephys.attic.picture.PictureInfo;
import com.mephys.attic.picture.PictureResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Response body for a property, with its details, photos (cover first) and official documents.
 */
record PropertyResponse(UUID id, PropertyKind kind, String name, @Nullable String address,
		@Nullable BigDecimal valueEur, @Nullable String comments, List<PropertyFact> facts,
		List<PictureResponse> pictures, List<PropertyDocumentResponse> documents) {

	static PropertyResponse of(Property property, List<PictureInfo> pictures,
			List<PropertyDocumentResponse> documents) {
		return new PropertyResponse(property.id(), property.kind(), property.name(), property.address(),
				property.valueEur(), property.comments(), property.facts(),
				PictureResponse.list(base(property.id()), pictures), documents);
	}

	/** Where the property's photos live */
	static String base(UUID propertyId) {
		return "/api/properties/" + propertyId;
	}

}
