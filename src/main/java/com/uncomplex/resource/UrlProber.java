package com.uncomplex.resource;

/** Abstraction over the network so link-health logic is testable without real HTTP. */
public interface UrlProber {

    boolean isReachable(String url);
}
