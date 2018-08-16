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

package io.undertow.servlet.test.multipart.formparser;

import io.undertow.io.Receiver;
import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.AuthenticationMechanismFactory;
import io.undertow.security.api.SecurityContext;
import io.undertow.server.Connectors;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.form.FormData;
import io.undertow.server.handlers.form.FormDataParser;
import io.undertow.server.handlers.form.FormParserFactory;
import io.undertow.servlet.ServletExtension;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.LoggingExceptionHandler;
import io.undertow.servlet.api.SecurityConstraint;
import io.undertow.servlet.api.WebResourceCollection;
import io.undertow.servlet.test.multipart.AddMultipartServetListener;
import io.undertow.servlet.test.multipart.MultiPartServlet;
import io.undertow.servlet.test.multipart.MultiPartTestCase;
import io.undertow.servlet.test.util.DeploymentUtils;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.ImmediatePooledByteBuffer;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.HttpParams;
import org.jboss.logging.Logger;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static io.undertow.servlet.Servlets.multipartConfig;
import static io.undertow.servlet.Servlets.servlet;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class MultiPartFormParserTestCase {

    private static boolean parserFlag = true;

    @BeforeClass
    public static void setup() throws ServletException {
        DeploymentUtils.setupServlet(new ServletExtension() {
                                         @Override
                                         public void handleDeployment(DeploymentInfo deploymentInfo, ServletContext servletContext) {
                                             deploymentInfo.addListener(Servlets.listener(AddMultipartServetListener.class));
                                             deploymentInfo.addFirstAuthenticationMechanism("BASIC",
                                                     new AuthenticationMechanism() {
                                                         @Override
                                                         public AuthenticationMechanismOutcome authenticate(HttpServerExchange exchange, SecurityContext securityContext) {
                                                             parserFlag = false;
                                                             CompletableFuture<String> future = new CompletableFuture<>();
                                                             exchange.getRequestReceiver().receiveFullBytes((exchange1, message) -> {
                                                                 Connectors.ungetRequestBytes(exchange1, new ImmediatePooledByteBuffer(ByteBuffer.wrap(message, 0, (int) exchange1.getRequestContentLength())));
                                                                 Connectors.resetRequestChannel(exchange1);
                                                                 FormDataParser formDataParser = FormParserFactory.builder(true).build().createParser(exchange1);
                                                                 try {
                                                                     FormData strings = formDataParser.parseBlocking();
                                                                     Assert.assertNotNull(strings);
                                                                     parserFlag = true;
                                                                     future.complete(null);
                                                                 } catch (IOException e) {
                                                                     e.printStackTrace();
                                                                     future.complete(null);
                                                                     parserFlag = false;
                                                                 }
                                                             });
                                                             try {
                                                                 future.get();
                                                             } catch (InterruptedException e) {
                                                                 e.printStackTrace();
                                                             } catch (ExecutionException e) {
                                                                 e.printStackTrace();
                                                             }
                                                             return AuthenticationMechanismOutcome.AUTHENTICATED;
                                                         }

                                                         @Override
                                                         public ChallengeResult sendChallenge(HttpServerExchange exchange, SecurityContext securityContext) {
                                                             return null;
                                                         }
                                                     });
                                             SecurityConstraint securityConstraint = new SecurityConstraint();
                                             WebResourceCollection webResourceCollection = new WebResourceCollection();
                                             webResourceCollection.addUrlPattern("/*");
                                             securityConstraint.addWebResourceCollection(webResourceCollection);

                                             deploymentInfo.addSecurityConstraint(securityConstraint);
                                             deploymentInfo.setExceptionHandler(LoggingExceptionHandler.builder().add(RuntimeException.class, "io.undertow", Logger.Level.DEBUG).build());

                                         }
                                     },
                servlet("mp0", MultiPartServlet.class)
                        .addMapping("/0"),
                servlet("mp1", MultiPartServlet.class)
                        .addMapping("/1")
                        .setMultipartConfig(multipartConfig(null, 0, 0, 0)),
                servlet("mp2", MultiPartServlet.class)
                        .addMapping("/2")
                        .setMultipartConfig(multipartConfig(null, 0, 3, 0)),
                servlet("mp3", MultiPartServlet.class)
                        .addMapping("/3")
                        .setMultipartConfig(multipartConfig(null, 3, 0, 0)));
    }

    @Test
    public void testMultiPartRequest() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            String uri = DefaultServer.getDefaultServerURL() + "/servletContext/1";
            HttpPost post = new HttpPost(uri);

            MultipartEntity entity = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE, null, StandardCharsets.UTF_8);

            entity.addPart("formValue", new StringBody("myValue", "text/plain", StandardCharsets.UTF_8));
            entity.addPart("file", new FileBody(new File(MultiPartTestCase.class.getResource("uploadfile.txt").getFile())));

            post.setEntity(entity);
            client.execute(post);

            Assert.assertTrue(parserFlag);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }


    @Test
    public void testUrlEncodedFormRequest() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            String uri = DefaultServer.getDefaultServerURL() + "/servletContext/1";
            HttpPost post = new HttpPost(uri);

            ArrayList<NameValuePair> postParameters = new ArrayList<>();
            postParameters.add(new BasicNameValuePair("param1", "param1_value"));
            post.setEntity(new UrlEncodedFormEntity(postParameters));
            post.setHeader("Content-Type", "application/x-www-form-urlencoded");

            client.execute(post);

            Assert.assertTrue(parserFlag);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }
}
