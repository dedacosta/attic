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
	 * Keep the documents of an heir who is deleted, as documents without heir.
	 * @return the number of changed documents
	 */
	public int unlinkAllOf(UUID heirId) {
		return repository.unlinkAllOfHeir(heirId);
	}

}
