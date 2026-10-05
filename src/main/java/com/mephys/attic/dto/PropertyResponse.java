package com.mephys.attic.dto;

import com.mephys.attic.model.PictureInfo;
import com.mephys.attic.model.Property;
import com.mephys.attic.model.PropertyFact;
import com.mephys.attic.model.PropertyKind;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Response body for a property, with its details, photos (cover first) and official documents.
 */
public record PropertyResponse(UUID id, PropertyKind kind, String name, @Nullable String address,
		@Nullable BigDecimal valueEur, @Nullable String comments, List<PropertyFact> facts,
		List<PictureResponse> pictures, List<PropertyDocumentResponse> documents) {

	public static PropertyResponse of(Property property, List<PictureInfo> pictures,
			List<PropertyDocumentResponse> documents) {
		return new PropertyResponse(property.id(), property.kind(), property.name(), property.address(),
				property.valueEur(), property.comments(), property.facts(),
				PictureResponse.list(base(property.id()), pictures), documents);
	}

	/** Where the property's photos live */
	public static String base(UUID propertyId) {
		return "/api/properties/" + propertyId;
	}

}
