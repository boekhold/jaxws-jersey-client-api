package maarten.jaxws.jersey.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.ResponseProcessingException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.handler.MessageContext;
import javax.xml.ws.soap.SOAPBinding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.istack.internal.NotNull;
import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.pipe.Codec;
import com.sun.xml.internal.ws.api.pipe.ContentType;
import com.sun.xml.internal.ws.api.pipe.NextAction;
import com.sun.xml.internal.ws.api.pipe.TubeCloner;
import com.sun.xml.internal.ws.api.pipe.helper.AbstractTubeImpl;
import com.sun.xml.internal.ws.client.ClientTransportException;
import com.sun.xml.internal.ws.resources.ClientMessages;

@SuppressWarnings("restriction")
public class JerseyClientTransportTube extends AbstractTubeImpl {
    private static final Logger LOG = LoggerFactory.getLogger(JerseyClientTransportTube.class);
    
    private final Codec codec;
    private final WSBinding binding;
    private final Client client;
    
    JerseyClientTransportTube(Codec codec, WSBinding binding) {
        this.codec = codec;
        this.binding = binding;
        this.client = binding.getFeature(JerseyClientFeature.class).getClient();
    }
    
    // Copy-constructor
    JerseyClientTransportTube(JerseyClientTransportTube that, TubeCloner cloner) {
        this(that.codec, that.binding);
        cloner.add(that, this); // original, copy
    }
    
    @Override
    public void preDestroy() {
        // Nothing to do, intentionally left blank        
    }

    @Override
    public NextAction processException(@NotNull Throwable t) {
        throw new IllegalStateException(
                    "JerseyClientTransportTube processException should not be called"
                );
    }

    @Override
    public NextAction processRequest(@NotNull Packet request) {        
        return doReturnWith(process(request));
    }

    @Override
    public NextAction processResponse(@NotNull Packet response) {
        throw new IllegalStateException(
                "JerseyClientTransportTube processResponse should not be called"
            );
    }

    @Override
    public AbstractTubeImpl copy(TubeCloner cloner) {
        return new JerseyClientTransportTube(this, cloner);
    }

    public Packet process(Packet request) throws WebServiceException {
        try {        
            // We're not going to optimize the conversion from OutputStream to InputStream
            // for now...
            // Also, seems to be that we don't need to do this for GET/HEAD/DELETE
            // requests... to be investigated.
            ByteArrayOutputStream baout = new ByteArrayOutputStream();
            ContentType ct = codec.getStaticContentType(request);
            if (ct == null) {
                ct = codec.encode(request, baout);
            } else {
                codec.encode(request, baout);
            }
            
            MediaType mtype = MediaType.valueOf(ct.getContentType());
            String method = (String)request.invocationProperties.get(MessageContext.HTTP_REQUEST_METHOD);
            method = method != null ? method : "POST";
            
            // Create Invocation
            final Invocation.Builder invocation = client
                    .target(request.endpointAddress.getURI())
                    .request(mtype);

            // Add message headers
            @SuppressWarnings("unchecked")
            Map<String, List<String>> userHeaders = (Map<String, List<String>>)
                    request.invocationProperties.get(MessageContext.HTTP_REQUEST_HEADERS);
            
            if (userHeaders != null) {
                for (Map.Entry<String, List<String>> entry : userHeaders.entrySet()) {
                    String key = entry.getKey();
                    for (String value : entry.getValue()) {
                        if (!key.equals("User-Agent"))
                            invocation.header(key, value);
                    }
                }
            }
            
            String acceptHeader = ct.getAcceptHeader();
            if (acceptHeader != null) {
                /*
                 * Seems like we get "text/xml, multipart/related" from JAX-WS
                 * here, and MediaType doesn't like that, so just take the
                 * first part
                 */
                String[] ahdrs = acceptHeader.split(",", 2);
                invocation.accept(MediaType.valueOf(ahdrs[0].trim()));
            }
            
            if (binding instanceof SOAPBinding) {
                // No SOAPAction for SOAP_12
                if (SOAPVersion.SOAP_11.equals(binding.getSOAPVersion())) {
                    String action = ct.getSOAPActionHeader();

                    if (action == null) {
                        action = "\"\"";
                    }
                    invocation.header("SOAPAction", action);
                }
            }
            
            Response response;
            
            try {
                LOG.trace("{} request", method);
                if (method.equalsIgnoreCase("GET") ||
                    method.equalsIgnoreCase("HEAD") ||
                    method.equalsIgnoreCase("DELETE")) {
                    // no request body for these request methods!
                    response = invocation.method(method);
                } else {
                    response = invocation.method(method, Entity.entity(baout.toByteArray(), mtype));
                }                
            } catch (ProcessingException e) {
                throw new ClientTransportException(e);
            }
            
            try (InputStream instream = response.readEntity(InputStream.class)) {
                checkStatusCode(instream, response); // throws exception in case of invalid status
                
                Packet reply = request.createClientResponse(null);
                reply.wasTransportSecure = request.endpointAddress.getURI().getScheme().equals("https");
                
                // protect against text/html response
                MediaType replyType = response.getMediaType();
                if (replyType != null && replyType.equals(MediaType.TEXT_HTML) && binding instanceof SOAPBinding) {
                    throw new ClientTransportException(
                            ClientMessages.localizableHTTP_STATUS_CODE(
                                    response.getStatus(), response.getStatusInfo().getReasonPhrase()));
                }
                
                codec.decode(instream, replyType.toString(), reply);
                return reply;
            } catch (IllegalStateException ise) {
                /*
                 * If the response is not backed by an InputStream, the stream has already
                 * been closed or it has already been consumed
                 */
                checkStatusCode(null, response);
            } catch (ProcessingException pe) {
                /*
                 * If the response cannot be mapped to an InputStream, or in case
                 * of zero-length data (possibly, it's also possible that readEntity()
                 * returns null in this case, testing will need to confirm)
                 */
                checkStatusCode(null, response);
            }
            
            return null;
        } catch (WebServiceException wex) {
            // propagate WebServiceExceptions thrown inside our own code,
            // we don't want to wrap those inside a new WebServiceException
            throw wex;
        } catch (Exception ex) {
            throw new WebServiceException(ex);
        }
    }
    
    /*
     * Copied and modified from JAX-WS Reference Implementation
     * 
     * Allows the following HTTP status codes.
     * SOAP 1.1/HTTP - 200, 202, 500
     * SOAP 1.2/HTTP - 200, 202, 400, 500
     * XML/HTTP - all
     *
     * For all other status codes, it throws an exception
     */
    private void checkStatusCode(InputStream in, Response response) throws IOException {
        int statusCode = response.getStatus();
        String statusMessage = response.getStatusInfo().getReasonPhrase();
        
        // SOAP1.1 and SOAP1.2 differ here
        if (binding instanceof SOAPBinding) {
            if (binding.getSOAPVersion() == SOAPVersion.SOAP_12) {
                //In SOAP 1.2, Fault messages can be sent with 4xx and 5xx error codes
                if (statusCode == Response.Status.OK.getStatusCode() ||
                    statusCode == Response.Status.ACCEPTED.getStatusCode() ||
                    statusCode == Response.Status.INTERNAL_SERVER_ERROR.getStatusCode() ||
                    statusCode == Response.Status.BAD_REQUEST.getStatusCode()) {

                    // but if it's an error AND we don't have any input data, then throw an exception
                    if (in == null && (
                            statusCode == Response.Status.INTERNAL_SERVER_ERROR.getStatusCode() ||
                            statusCode == Response.Status.BAD_REQUEST.getStatusCode())) {
                        // in == null so we don't have an envelope for the error
                        throw new ClientTransportException(
                                ClientMessages.localizableHTTP_STATUS_CODE(statusCode, statusMessage));
                    }
                    return; // still something we can process...
                }
            } else {
                // SOAP 1.1
                if (statusCode == Response.Status.OK.getStatusCode() ||
                    statusCode == Response.Status.ACCEPTED.getStatusCode() ||
                    statusCode == Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()) {

                    // but if it's an error AND we don't have any input data, then throw an exception
                    if (in == null && statusCode == Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()) {
                        // in == null so we don't have an envelope for the error
                        throw new ClientTransportException(
                                ClientMessages.localizableHTTP_STATUS_CODE(statusCode, statusMessage));
                    }                
                    return; // still something we can process...                }
                }
            }

            // SOAPBinding, but an unsupported statusCode
            throw new ClientTransportException(ClientMessages.localizableHTTP_STATUS_CODE(statusCode, statusMessage));
        }
        // Every status code is OK for XML/HTTP
    }
}
