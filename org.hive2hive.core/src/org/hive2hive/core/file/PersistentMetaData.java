package org.hive2hive.core.file;

import java.io.Serializable;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;

/**
 * Holds all meta objects that need to be stored when a client goes offline. These data is used when a client
 * comes online again to compare the changes during absence. This document is stored in a meta file in the
 * root directory.
 * 
 * @author Nico
 * 
 */
public class PersistentMetaData implements Serializable {

	private static final long serialVersionUID = -1069468683019402537L;

	private Map<String, byte[]> fileTree;
	private Map<String, PublicKey> publicKeyCache;

	public PersistentMetaData() {
		fileTree = new HashMap<String, byte[]>(0);
		publicKeyCache = new HashMap<String, PublicKey>(0);
	}

	public Map<String, byte[]> getFileTree() {
		return fileTree;
	}

	public void setFileTree(Map<String, byte[]> fileTree) {
		this.fileTree = fileTree;
	}

	public Map<String, PublicKey> getPublicKeyCache() {
		return publicKeyCache;
	}

	public void setPublicKeyCache(Map<String, PublicKey> publicKeyCache) {
		this.publicKeyCache = publicKeyCache;
	}
}