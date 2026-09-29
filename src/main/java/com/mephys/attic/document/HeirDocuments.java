package com.mephys.attic.document;

import java.util.UUID;

import org.springframework.stereotype.Component;

/**
 * What other parts of the application may do with an heir's documents.
 */
@Component
public class HeirDocuments {

	private final DocumentRepository repository;

	HeirDocuments(DocumentRepository repository) {
		this.repository = repository;
	}

	/**
	 * Delete all documents of an heir, with their pictures.
	 * @return the number of deleted documents
	 */
	public int deleteAllOf(UUID heirId) {
		return repository.deleteAllOfHeir(heirId);
	}

}
