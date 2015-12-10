/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.undertow.security.impl;

import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Establish what encoding should be used to read browser's Basic authentication challenge.
 */
class BasicAuthenticationEncodingDetector {

    private final String operaUserAgent = "Mozilla/5\\.0 \\(.*\\) .* OPR/.*";
    // chrome UA is very similar to Opera - the only difference is lack of "OPR" element
    private final String chromeUserAgent = "(?!.*OPR)Mozilla/5\\.0 \\(.*\\) Chrome/.*";
    private final String firefoxUserAgent = "Mozilla/5\\.0 \\(.*\\) Gecko/.* Firefox/.*";
    private final String ie11UserAgent = "Mozilla/5\\.0 \\(.*; Trident/.*; rv:.*\\).*";
    private final String ie10UserAgent = "Mozilla/5\\.0 \\(.* MSIE.* Trident/.*\\)";
    private final String safariUserAgent = "(?!.*OPR)(?!.*Chrome)Mozilla/5\\.0 \\(.*\\).* Safari/.*";

    private final Pattern operaPattern = Pattern.compile(operaUserAgent);
    private final Pattern chromePattern = Pattern.compile(chromeUserAgent);
    private final Pattern firefoxPattern = Pattern.compile(firefoxUserAgent);
    private final Pattern ie11Pattern = Pattern.compile(ie11UserAgent);
    private final Pattern ie10Pattern = Pattern.compile(ie10UserAgent);
    private final Pattern safariPattern = Pattern.compile(safariUserAgent);

    private final Charset defaultCharset;

    public BasicAuthenticationEncodingDetector(Charset defaultCharset) {
        this.defaultCharset = defaultCharset;
    }

    /**
     * returns charset used by the browser to encode BASIC auth credentials.
     * If the browser cannot be determined, returns default encoding.
     *
     * @param headers request headers
     * @return charset used to encode the credentials
     */
    public Charset detectBasicAuthEncoding(HeaderMap headers) {

        final String userAgent = headers.getFirst(Headers.USER_AGENT);

        if (operaPattern.matcher(userAgent).matches()) {
            return StandardCharsets.UTF_8;
        }

        if (chromePattern.matcher(userAgent).matches()) {
            return StandardCharsets.UTF_8;
        }

        if (firefoxPattern.matcher(userAgent).matches()) {
            return StandardCharsets.ISO_8859_1;
        }

        if (ie11Pattern.matcher(userAgent).matches()) {
            return StandardCharsets.ISO_8859_1;
        }

        if (ie10Pattern.matcher(userAgent).matches()) {
            return StandardCharsets.ISO_8859_1;
        }

        if (safariPattern.matcher(userAgent).matches()) {
            return StandardCharsets.ISO_8859_1;
        }

        return defaultCharset;
    }
}
