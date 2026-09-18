package com.projectmodule.integration.port;

/** The minimal information this module needs about a document it does not own or store. */
public record DocumentReference(String id, String name, String url) {
}
