package com.mephys.attic.service;

import com.mephys.attic.repository.DocumentRepository;

import java.util.UUID;

import org.springframework.stereotype.Component;

/**
 * What other parts of the application may do with an heir's documents.
 */
@Component
public class HeirDocuments {

	private final DocumentRepository repository;

	public HeirDocuments(DocumentRepository repository) {
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
