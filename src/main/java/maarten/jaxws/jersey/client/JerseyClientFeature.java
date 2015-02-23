package maarten.jaxws.jersey.client;

import javax.ws.rs.client.Client;
import javax.xml.ws.WebServiceFeature;

public class JerseyClientFeature extends WebServiceFeature {
    private Client client;
    
    public JerseyClientFeature(Client client) {
        this.client = client;
    }

    @Override
    public String getID() {
        return JerseyClientFeature.class.getName();
    }

    public Client getClient() {
        return client;
    }
}
