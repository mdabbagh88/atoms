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
package org.jboss.aerogear.unifiedpush.rest.sender;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.jboss.aerogear.unifiedpush.api.PushApplication;
import org.jboss.aerogear.unifiedpush.document.DocumentDeployMessage;
import org.jboss.aerogear.unifiedpush.document.MessagePayload;
import org.jboss.aerogear.unifiedpush.message.InternalUnifiedPushMessage;
import org.jboss.aerogear.unifiedpush.message.NotificationRouter;
import org.jboss.aerogear.unifiedpush.message.UnifiedPushMessage;
import org.jboss.aerogear.unifiedpush.rest.AbstractEndpoint;
import org.jboss.aerogear.unifiedpush.rest.EmptyJSON;
import org.jboss.aerogear.unifiedpush.rest.util.HttpRequestUtil;
import org.jboss.aerogear.unifiedpush.rest.util.PushAppAuthHelper;
import org.jboss.aerogear.unifiedpush.service.DocumentService;
import org.jboss.aerogear.unifiedpush.service.PushApplicationService;
import org.jboss.aerogear.unifiedpush.utils.AeroGearLogger;

import com.qmino.miredot.annotations.BodyType;
import com.qmino.miredot.annotations.ReturnType;

@Path("/sender")
public class PushNotificationSenderEndpoint extends AbstractEndpoint {

    private final AeroGearLogger logger = AeroGearLogger.getInstance(PushNotificationSenderEndpoint.class);
    @Inject
    private PushApplicationService pushApplicationService;
    @Inject
    private NotificationRouter notificationRouter;
	@Inject
	private DocumentService documentService;

    /**
     * RESTful API for sending Push Notifications.
     * The Endpoint is protected using <code>HTTP Basic</code> (credentials <code>PushApplicationID:masterSecret</code>).
     * <p/><p/>
     *
     * Messages are submitted as flexible JSON maps. Below is a simple example:
     * <pre>
     * curl -u "PushApplicationID:MasterSecret"
     *   -v -H "Accept: application/json" -H "Content-type: application/json"
     *   -X POST
     *   -d '{
     *     "message": {
     *      "alert": "HELLO!",
     *      "sound": "default",
     *      "user-data": {
     *          "key": "value",
     *      }
     *   }'
     *   https://SERVER:PORT/CONTEXT/rest/sender
     * </pre>
     *
     * Details about the Message Format can be found HERE!
     *
     * @HTTP 202 (Accepted) Indicates the Job has been accepted and is being process by the AeroGear UnifiedPush Server.
     * @HTTP 401 (Unauthorized) The request requires authentication.
     * @RequestHeader aerogear-sender The header to identify the used client. If the header is not present, the standard "user-agent" header is used.
     *
     * @param message   message to send
     * @return          empty JSON body
     *
     * @responseheader WWW-Authenticate Basic realm="AeroGear UnifiedPush Server" (only for 401 response)
     *
     * @statuscode 202 Indicates the Job has been accepted and is being process by the AeroGear UnifiedPush Server
     * @statuscode 401 The request requires authentication
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @BodyType("org.jboss.aerogear.unifiedpush.message.UnifiedPushMessage")
    @ReturnType("org.jboss.aerogear.unifiedpush.rest.EmptyJSON")
    public Response send(final InternalUnifiedPushMessage message, @Context HttpServletRequest request) {

        final PushApplication pushApplication = PushAppAuthHelper.loadPushApplicationWhenAuthorized(request, pushApplicationService);
        if (pushApplication == null) {
            return Response.status(Status.UNAUTHORIZED)
                    .header("WWW-Authenticate", "Basic realm=\"Atoms UnifiedPush Server\"")
                    .entity("Unauthorized Request")
                    .build();
        }

        // submit http request metadata:
        message.setIpAddress(HttpRequestUtil.extractIPAddress(request));

        // add the client identifier
        message.setClientIdentifier(HttpRequestUtil.extractAeroGearSenderInformation(request));

        // submitted to EJB:
        notificationRouter.submit(pushApplication, message);
        logger.fine(String.format("Push Message Request from [%s] API was internally submitted for further processing", message.getClientIdentifier()));

        return Response.status(Status.ACCEPTED).entity(EmptyJSON.STRING).build();
    }

    /**
     * RESTful API for sending Push Notifications with large payload.
     * The Endpoint is protected using <code>HTTP Basic</code> (credentials <code>PushApplicationID:masterSecret</code>).</BR>
     * Accepts large payload and stores it for later retrieval by a device of the push application.
     * If {@link UnifiedPushMessage.Message.alert} is passed, then a push notification will also be trigger.</BR></BR>
     *
     * Details about the Message Format can be found HERE!
     *
     * @HTTP 202 (Accepted) Indicates the Job has been accepted and is being process by the Atoms UnifiedPush Server.
     * @HTTP 401 (Unauthorized) The request requires authentication.
     * @RequestHeader aerogear-sender The header to identify the used client. If the header is not present, the standard "user-agent" header is used.
     *
     * @param payloadRequest   {@link DocumentDeployMessage} list of {@link MessagePayload}
     * @return          empty JSON body
     *
     * @responseheader WWW-Authenticate Basic realm="Atoms UnifiedPush Server" (only for 401 response)
     *
     * @statuscode 202 Indicates the Job has been accepted and is being process by the Atoms UnifiedPush Server
     * @statuscode 401 The request requires authentication
     */
	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/payload")
	public Response sendLargePayload(DocumentDeployMessage payloadRequest, @Context HttpServletRequest request) {
		final PushApplication pushApplication = PushAppAuthHelper.loadPushApplicationWhenAuthorized(request,
				pushApplicationService);
		return sendLargePayload(pushApplication, payloadRequest, false, request);

	}

    /**
     * RESTful API for sending Push Notifications with large payload.
     * The Endpoint is protected using <code>HTTP Basic</code> (credentials <code>PushApplicationID:masterSecret</code>).</BR>
     * Accepts large payload and stores it for later retrieval by a device of the push application.
     * Payload stored using @PUT can be overridden with a following identical call.</BR>
     * If {@link @link UnifiedPushMessage.Message.alert} is passed, then a push notification will also be trigger. </BR></BR>
     *
     * Details about the Message Format can be found HERE!
     *
     * @HTTP 202 (Accepted) Indicates the Job has been accepted and is being process by the Atoms UnifiedPush Server.
     * @HTTP 401 (Unauthorized) The request requires authentication.
     * @RequestHeader aerogear-sender The header to identify the used client. If the header is not present, the standard "user-agent" header is used.
     *
     * @param payloadRequest   {@link DocumentDeployMessage} to be stored and notify devices.
     * @return          empty JSON body
     *
     * @responseheader WWW-Authenticate Basic realm="Atoms UnifiedPush Server" (only for 401 response)
     *
     * @statuscode 202 Indicates the Job has been accepted and is being process by the Atoms UnifiedPush Server
     * @statuscode 401 The request requires authentication
     */
	@PUT
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/payload")
	public Response updateLargePayload(DocumentDeployMessage payloadRequest, @Context HttpServletRequest request) {
		final PushApplication pushApplication = PushAppAuthHelper.loadPushApplicationWhenAuthorized(request,
				pushApplicationService);
		return sendLargePayload(pushApplication, payloadRequest, true, request);
	}

	private Response sendLargePayload(PushApplication pushApplication, DocumentDeployMessage payloadRequest, boolean override, @Context HttpServletRequest request) {
		if (pushApplication == null) {
			return Response.status(Status.UNAUTHORIZED)
					.header("WWW-Authenticate", "Basic realm=\"Atoms UnifiedPush Server\"")
					.entity("Unauthorized Request").build();
		}

		try {
			// Save payload for aliases
			if (payloadRequest.getPayloads() != null && !payloadRequest.getPayloads().isEmpty()) {

				for (MessagePayload payload : payloadRequest.getPayloads()) {
					documentService.savePayload(pushApplication, payload, override);

					// Send push message only if alert exists
					if (payload.getPushMessage() != null && payload.getPushMessage().getMessage() != null && payload.getPushMessage().getMessage().getAlert() != null){
						push(payload.getPushMessage(), pushApplication, request);
					}
				}
			}

		} catch (Exception e) {
			logger.severe("Cannot store payload and send notification", e);
			return Response.status(Status.INTERNAL_SERVER_ERROR).build();
		}

		return Response.status(Status.ACCEPTED).entity(EmptyJSON.STRING).build();
	}

	private void push(UnifiedPushMessage pushMessage, PushApplication pushApplication, HttpServletRequest request) {
		if (pushMessage != null) {
			InternalUnifiedPushMessage message = new InternalUnifiedPushMessage(pushMessage);
			// submit http request metadata:
			message.setIpAddress(HttpRequestUtil.extractIPAddress(request));
			// add the client identifier
			message.setClientIdentifier(HttpRequestUtil.extractAeroGearSenderInformation(request));

			notificationRouter.submit(pushApplication, message);
		}
	}
}
