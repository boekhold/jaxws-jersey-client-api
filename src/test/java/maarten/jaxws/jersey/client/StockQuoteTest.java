package maarten.jaxws.jersey.client;

import java.net.URL;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.xml.namespace.QName;

import maarten.jaxws.jersey.client.JerseyClientFeature;
import net.webservicex.StockQuote;
import net.webservicex.StockQuoteSoap;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StockQuoteTest {
    private static final Logger LOG = LoggerFactory.getLogger(StockQuoteTest.class);
    
    @Test
    public void testStockQuote() {
        Client client = ClientBuilder.newClient();
        JerseyClientFeature feature = new JerseyClientFeature(client);
                
        URL wsdlLocation = getClass().getClassLoader().getResource("stockquote.asmx.wsdl");
        
        if (wsdlLocation == null) {
            LOG.error("Failed to load WSDL file from classpath");
            return;
        }
        
        QName STOCKQUOTE_QNAME = new QName("http://www.webserviceX.NET/", "StockQuote");
        StockQuote service = new StockQuote(wsdlLocation, STOCKQUOTE_QNAME);
        StockQuoteSoap port = service.getStockQuoteSoap(feature);
        
        System.out.println(port.getQuote("TIBX.O"));
        
    }
}
