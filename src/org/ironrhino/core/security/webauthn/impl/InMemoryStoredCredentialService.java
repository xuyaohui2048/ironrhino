package org.ironrhino.core.security.webauthn.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.ironrhino.core.security.webauthn.StoredCredentialService;
import org.ironrhino.core.security.webauthn.domain.StoredCredential;

public class InMemoryStoredCredentialService implements StoredCredentialService {

	@Override
	public void addCredential(StoredCredential credential) {
		List<StoredCredential> list = credentials.computeIfAbsent(credential.getUsername(), k -> new ArrayList<>());
		list.add(credential);
	}

	@Override
	public void updateSignCount(byte[] credentialId, int signCount) {
		credentials.values().stream().flatMap(List::stream)
				.filter(c -> Arrays.equals(c.getCredentialId(), credentialId)).forEach(c -> c.setSignCount(signCount));
	}

	@Override
	public Optional<StoredCredential> getCredentialById(byte[] credentialId) {
		return credentials.values().stream().flatMap(List::stream)
				.filter(c -> Arrays.equals(c.getCredentialId(), credentialId)).findAny();
	}

	@Override
	public boolean hasCredentials(String username) {
		return credentials.containsKey(username);
	}

	@Override
	public List<StoredCredential> getCredentials(String username) {
		return credentials.getOrDefault(username, Collections.emptyList());
	}

	private Map<String, List<StoredCredential>> credentials = new ConcurrentHashMap<>();

}
