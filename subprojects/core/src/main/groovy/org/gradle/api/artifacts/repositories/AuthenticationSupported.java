/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.artifacts.repositories;

import groovy.lang.Closure;

/**
 * An artifact repository which supports username/password authentication.
 */
public interface AuthenticationSupported {

    /**
     * Provides the Credentials used to authenticate to this repository.
     * @return The credentials
     */
    PasswordCredentials getCredentials();

    /**
     * Configure the Credentials for this repository using the supplied Closure.
     *
     * <pre autoTested=''>
     * repositories {
     *     maven {
     *         credentials {
     *             userName = 'joe'
     *             password = 'secret'
     *         }
     *     }
     * }
     * </pre>
     */
    void credentials(Closure closure);
}
