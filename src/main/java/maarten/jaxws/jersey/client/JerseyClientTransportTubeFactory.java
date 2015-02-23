package maarten.jaxws.jersey.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.xml.internal.ws.api.pipe.ClientTubeAssemblerContext;
import com.sun.xml.internal.ws.api.pipe.TransportTubeFactory;
import com.sun.xml.internal.ws.api.pipe.Tube;

@SuppressWarnings("restriction")
public class JerseyClientTransportTubeFactory extends TransportTubeFactory {
    private static final Logger LOG = LoggerFactory.getLogger(JerseyClientTransportTubeFactory.class);
    
    @Override
    public Tube doCreate(ClientTubeAssemblerContext context) {        
        final String scheme = context.getAddress().getURI().getScheme();
        
        if (scheme != null && scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")) {
            JerseyClientFeature feature = context.getBinding().getFeature(JerseyClientFeature.class);
            
            if (feature != null) {
                LOG.debug("instantiating JerseyClientTransportTube");
                return new JerseyClientTransportTube(context.getCodec(), context.getBinding());
            } else {
                LOG.debug("no JerseyClientFeature available");
            }
        } else {
            LOG.debug("scheme for requested transport tube is not http/https: {}", scheme);
        }

        /*
         *  if no JerseyClientFeature/http/https, return null, which will cause
         *  the JAX-WS RI to instantiate its default TransportTube
         */
        return null;
    }

}
