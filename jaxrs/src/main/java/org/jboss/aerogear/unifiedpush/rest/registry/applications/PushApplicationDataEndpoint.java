/**
 * JBoss, Home of Professional Open Source
 * Copyright Red Hat, Inc., and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.aerogear.unifiedpush.rest.registry.applications;

import java.util.List;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.jboss.aerogear.unifiedpush.api.PushApplication;
import org.jboss.aerogear.unifiedpush.rest.AbstractBaseEndpoint;
import org.jboss.aerogear.unifiedpush.rest.EmptyJSON;
import org.jboss.aerogear.unifiedpush.rest.util.PushAppAuthHelper;
import org.jboss.aerogear.unifiedpush.service.DocumentService;
import org.jboss.aerogear.unifiedpush.service.PushApplicationService;
import org.jboss.resteasy.annotations.providers.multipart.PartType;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataOutput;

import com.qmino.miredot.annotations.ReturnType;

@Path("/applicationsData")
public class PushApplicationDataEndpoint extends AbstractBaseEndpoint {

	@Inject
	private PushApplicationService pushAppService;

	@Inject
	private DocumentService documentService;

	/**
	 * Map aliases to push application.
     * The Endpoint is protected using <code>HTTP Basic</code> (credentials <code>PushApplicationID:masterSecret</code>).
	 *
	 * @param aliases {@link List<String>} of aliases
	 * @return Empty JSON {}
	 *
	 * @statuscode 200 Successful storage of the aliases list
     * @statuscode 400 The format of the client request was incorrect (e.g. missing required values)
     * @statuscode 401 The request requires authentication
     * @statuscode 500 Internal server error
	 */
	@POST
	@Path("/aliases")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@ReturnType("org.jboss.aerogear.unifiedpush.rest.EmptyJSON")
	public Response updateAliases(List<String> aliases, @Context HttpServletRequest request) {
		final PushApplication pushApp = PushAppAuthHelper.loadPushApplicationWhenAuthorized(request, pushAppService);
		if (pushApp == null) {
			return Response.status(Status.UNAUTHORIZED)
					.header("WWW-Authenticate", "Basic realm=\"Atoms UnifiedPush Server\"")
					.entity("Unauthorized Request").build();
		}

		try {
			pushAppService.updateAliasesAndInstallations(pushApp, aliases);
			return Response.ok(EmptyJSON.STRING).build();
		} catch (Exception e) {
			logger.severe("Cannot update aliases", e);
			return Response.status(Status.INTERNAL_SERVER_ERROR).build();
		}
	}

	/**
	 * Retrieve documents stored by installations.
     * The Endpoint is protected using <code>HTTP Basic</code> (credentials <code>PushApplicationID:masterSecret</code>).
	 *
	 * @param alias user specific alias (Use 'NULL' value if unknown)
	 * @param qualifier any document qualifier
	 * @param id any document id (Use 'NULL' value if unknown)
	 * @return {@link MultipartFormDataOutput}
	 *
	 * @statuscode 200 Successful storage of the aliases list
     * @statuscode 400 The format of the client request was incorrect (e.g. missing required values)
     * @statuscode 401 The request requires authentication
     * @statuscode 500 Internal server error
	 */
	@GET
	@Path("/document/{alias}{qualifier}/{id}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces("multipart/form-data")
	@PartType(MediaType.TEXT_PLAIN)
	@ReturnType("org.jboss.aerogear.unifiedpush.rest.EmptyJSON")
	public Response retrieveDocumentsForPushApp(@PathParam("alias") String alias, @PathParam("qualifier") String qualifier, @PathParam("id") String id,
			@Context HttpServletRequest request) {
		final PushApplication pushApp = PushAppAuthHelper.loadPushApplicationWhenAuthorized(request, pushAppService);

		if (pushApp == null) {
			return Response.status(Status.UNAUTHORIZED)
					.header("WWW-Authenticate", "Basic realm=\"Atoms UnifiedPush Server\"")
					.entity("Unauthorized Request").build();
		}

		try {
			MultipartFormDataOutput mdo = new MultipartFormDataOutput();
			List<String> documents = documentService.getLatestDocumentsForApplication(pushApp, alias, qualifier, id);
			for (int i = 0; i < documents.size(); i++) {
				mdo.addFormData("file" + i, documents.get(i), MediaType.TEXT_PLAIN_TYPE);
			}
			return Response.ok(mdo).build();
		} catch (Exception e) {
			logger.severe("Cannot retrieve documents for push application", e);
			return Response.status(Status.INTERNAL_SERVER_ERROR).build();
		}
	}
}
