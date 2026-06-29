package com.aiworkspace.documents.client;

@FunctionalInterface
public interface RestResponseMapper<T> {

    T map(String url, String body);
}
