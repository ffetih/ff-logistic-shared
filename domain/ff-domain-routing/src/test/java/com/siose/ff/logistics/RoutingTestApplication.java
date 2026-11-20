package com.siose.ff.logistics;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The routing area on its own, for its tests: the same chassis as the modulith and the
 * routing service (ff-base-*), and nothing else of the business. It sits in the same package
 * as both real applications, so component scanning finds the area exactly as they do.
 */
@SpringBootApplication
public class RoutingTestApplication {
}
