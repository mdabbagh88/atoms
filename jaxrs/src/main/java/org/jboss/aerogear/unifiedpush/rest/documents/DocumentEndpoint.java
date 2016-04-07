package org.jboss.aerogear.unifiedpush.rest.documents;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.lang.StringUtils;
import org.jboss.aerogear.unifiedpush.api.DocumentMessage;
import org.jboss.aerogear.unifiedpush.api.DocumentMetadata;
import org.jboss.aerogear.unifiedpush.api.PushApplication;
import org.jboss.aerogear.unifiedpush.api.Variant;
import org.jboss.aerogear.unifiedpush.rest.AbstractEndpoint;
import org.jboss.aerogear.unifiedpush.rest.EmptyJSON;
import org.jboss.aerogear.unifiedpush.rest.util.ClientAuthHelper;
import org.jboss.aerogear.unifiedpush.service.ClientInstallationService;
import org.jboss.aerogear.unifiedpush.service.DocumentService;
import org.jboss.aerogear.unifiedpush.service.GenericVariantService;
import org.jboss.aerogear.unifiedpush.service.PushApplicationService;
import org.jboss.aerogear.unifiedpush.utils.AeroGearLogger;

import com.qmino.miredot.annotations.ReturnType;

@Path("/document")
public class DocumentEndpoint extends AbstractEndpoint {
    private final AeroGearLogger logger = AeroGearLogger.getInstance(DocumentEndpoint.class);

	@Inject
    private ClientInstallationService clientInstallationService;
    @Inject
    private GenericVariantService genericVariantService;
    @Inject
    private DocumentService documentService;
    @Inject
    private PushApplicationService pushApplicationService;

    /**
     * Cross Origin for Installations
     *
     * @param headers   "Origin" header
     * @return          "Access-Control-Allow-Origin" header for your response
     *
     * @responseheader Access-Control-Allow-Origin      With host in your "Origin" header
     * @responseheader Access-Control-Allow-Methods     POST, DELETE
     * @responseheader Access-Control-Allow-Headers     accept, origin, content-type, authorization
     * @responseheader Access-Control-Allow-Credentials true
     * @responseheader Access-Control-Max-Age           604800
     *
     * @statuscode 200 Successful response for your request
     */
    @OPTIONS
    @ReturnType("java.lang.Void")
    public Response crossOriginForInstallations(@Context HttpHeaders headers) {
        return appendPreflightResponseHeaders(headers, Response.ok()).build();
    }

    /**
     * RESTful API to enable devices to store data
     * The Endpoint is protected using <code>HTTP Basic</code> (credentials <code>VariantID:secret</code>).</BR>
     * @POST data and stores it for later retrieval by the push application.
     *
     * <pre>
     * curl -u "variantID:secret" -H "device-token:base64 encoded device token"
     *   -v -X POST -d {ANY JSON}
     *   https://SERVER:PORT/context/rest/{alias}/{qualifier}{id}"
     * </pre>
     *
     * @HTTP 200 (OK) if store document went through.
     * @HTTP 400 (Bad Request) deviceToken header not sent.
     * @HTTP 401 (Unauthorized) The request requires authentication.
     *
     * @param entity any JSON body to be stored.
     * @param alias device alias
     * @param qualifier any document qualified
     * @param id any document id (optional)
     * @return	empty JSON body
     *
     * @responseheader Access-Control-Allow-Origin      With host in your "Origin" header
     * @responseheader Access-Control-Allow-Credentials true
     * @responseheader WWW-Authenticate Basic realm="Atoms UnifiedPush Server" (only for 401 response)
     *
     * @statuscode 200 store document went through.
     * @statuscode 400 deviceToken header required.
     * @statuscode 401 The request requires authentication.
     */
	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/{alias}/{qualifier}{id : (/[^/]+?)?}")
	@ReturnType("org.jboss.aerogear.unifiedpush.rest.EmptyJSON")
	public Response newDocument(String entity,
			@PathParam("alias") String alias, @PathParam("qualifier") String qualifier,
			@PathParam("id") String id,
			@Context HttpServletRequest request) {

		// Store new document according to path params.
		// If document exists a newer version will be stored.
		return deployDocument(entity, alias, qualifier, id, false, request);
	}

	 /**
     * RESTful API to enable devices to store data
     * The Endpoint is protected using <code>HTTP Basic</code> (credentials <code>VariantID:secret</code>).</BR>
     * @PUT data and stores it for later retrieval by the push application. This API will override existing documents.
     *
     * <pre>
     * curl -u "variantID:secret" -H "device-token:base64 encoded device token"
     *   -v -X PUT -d {ANY JSON}
     *   https://SERVER:PORT/context/rest/{alias}/{qualifier}{id}"
     * </pre>
     *
     * @HTTP 200 (OK) if store document went through.
     * @HTTP 400 (Bad Request) deviceToken header not sent.
     * @HTTP 401 (Unauthorized) The request requires authentication.
     *
     * @param entity any JSON body to be stored.
     * @param alias device alias
     * @param qualifier any document qualified
     * @param id any document id (optional)
     * @return	empty JSON body
     *
     * @responseheader Access-Control-Allow-Origin      With host in your "Origin" header
     * @responseheader Access-Control-Allow-Credentials true
     * @responseheader WWW-Authenticate Basic realm="Atoms UnifiedPush Server" (only for 401 response)
     *
     * @statuscode 200 store document went through.
     * @statuscode 400 deviceToken header required.
     * @statuscode 401 The request requires authentication.
     */
	@PUT
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/{alias}/{qualifier}{id : (/[^/]+?)?}")
	@ReturnType("org.jboss.aerogear.unifiedpush.rest.EmptyJSON")
	public Response storeDocument(String entity, @PathParam("alias") String alias,
			@PathParam("qualifier") String qualifier, @PathParam("id") String id, @Context HttpServletRequest request) {

		// Store new document according to path params.
		// If document exists update stored version.
		return deployDocument(entity, alias, qualifier, id, true, request);
	}

	private Response deployDocument(String entity, String alias, String qualifier, String id, boolean overwrite,
			HttpServletRequest request) {

		final Variant variant = ClientAuthHelper.loadVariantWhenInstalled(genericVariantService,
				clientInstallationService, request);

		if (variant == null) {
			return create401Response(request);
		}

		if (StringUtils.isEmpty(id)) {
			id = DocumentMessage.NULL_PART;
		} else {
			id = id.substring(1); // remove first '/'
		}

		try {
			PushApplication pushApp = pushApplicationService.findByVariantID(variant.getVariantID());
			documentService.saveForPushApplication(pushApp, alias, entity, DocumentMetadata.getQualifier(qualifier), id,
					overwrite);
			return Response.ok(EmptyJSON.STRING).build();
		} catch (Exception e) {
			logger.severe("Cannot deploy file for push application", e);
			return Response.status(Status.INTERNAL_SERVER_ERROR).build();
		}
	}

	@GET
	@Produces(MediaType.TEXT_PLAIN)
	@Path("/{publisher}/{alias}/{qualifier}/latest")
	@Deprecated
	public Response retrieveTextDocument(@PathParam("publisher") String publisher, @PathParam("alias") String alias,
			@PathParam("qualifier") String qualifier, @Context HttpServletRequest request) {
		final Variant variant = ClientAuthHelper.loadVariantWhenInstalled(genericVariantService,
				clientInstallationService, request);
		if (variant == null) {
			return create401Response(request);
		}

		try {
			String document = documentService.getLatestDocumentForAlias(variant, DocumentMetadata.getPublisher(publisher), alias, DocumentMetadata.getQualifier(qualifier));
			return Response.ok(StringUtils.isEmpty(document) ? EmptyJSON.STRING: document).build();
		} catch (Exception e) {
			logger.severe("Cannot retrieve files for alias", e);
			return Response.status(Status.INTERNAL_SERVER_ERROR).build();
		}
	}

	 /**
     * RESTful API to get device data
     * The Endpoint is protected using <code>HTTP Basic</code> (credentials <code>VariantID:secret</code>).</BR>
     * Get latest (last-updated) document according to path parameters </BR></BR>
     *
     * <b>Examples:</b></br>
	 * <li>document/17327572923/test/json/latest - get alias specific document.
	 * <li>document/NULL/test/json/latest - global scope document (for any alias).
	 *
     * <pre>
     * curl -u "variantID:secret" -H "device-token:base64 encoded device token"
     *   -v -X GET
     *   https://SERVER:PORT/context/rest/{alias}/{qualifier}/json/latest"
     * </pre>
     *
     * @HTTP 200 (OK) if document retrieval went through.
     * @HTTP 400 (Bad Request) deviceToken header not sent.
     * @HTTP 401 (Unauthorized) The request requires authentication.
     *
     *
     * @param publisher either APPLICATION or INSTALLATION
     * @param alias device alias
     * @param qualifier any document qualified
     * @return	document in json format
     *
     * @responseheader Access-Control-Allow-Origin      With host in your "Origin" header
     * @responseheader Access-Control-Allow-Credentials true
     * @responseheader WWW-Authenticate Basic realm="Atoms UnifiedPush Server" (only for 401 response)
     *
     * @statuscode 200 document retrieval went through.
     * @statuscode 400 deviceToken header required.
     * @statuscode 401 The request requires authentication.
     */
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/{publisher}/{alias}/{qualifier}/json/latest")
	public Response retrieveJsonDocument(@PathParam("publisher") String publisher, @PathParam("alias") String alias,
			@PathParam("qualifier") String qualifier, @Context HttpServletRequest request) {
		final Variant variant = ClientAuthHelper.loadVariantWhenInstalled(genericVariantService,
				clientInstallationService, request);
		if (variant == null) {
			return create401Response(request);
		}

		try {
			String document = documentService.getLatestDocumentForAlias(variant,
					DocumentMetadata.getPublisher(publisher), alias, DocumentMetadata.getQualifier(qualifier));
			return Response.ok(StringUtils.isEmpty(document) ? EmptyJSON.STRING : document).build();
		} catch (Exception e) {
			logger.severe("Cannot retrieve files for alias", e);
			return Response.status(Status.INTERNAL_SERVER_ERROR).build();
		}
	}

}
