package org.lantern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.jboss.netty.handler.traffic.TrafficCounter;
import org.junit.Test;
import org.lantern.state.Peer;
import org.lantern.util.GlobalLanternServerTrafficShapingHandler;
import org.lantern.util.HttpClientFactory;
import org.lantern.util.LanternTrafficCounterHandler;

public class SslHttpProxyServerTest {

    @Test
    public void test() throws Exception {
        //Launcher.configureCipherSuites();
        //System.setProperty("javax.net.debug", "ssl");
        TestUtils.getModel().getPeerCollector().setPeers(new ConcurrentHashMap<String, Peer>());
        final SslHttpProxyServer server = TestUtils.getSslHttpProxyServer();
        thread(server);
        
        LanternUtils.waitForServer(server.getPort());
        
        final String testId = "127.0.0.1";//"test@gmail.com/somejidresource";
        TestUtils.getTrustStore().addBase64Cert(testId, 
            TestUtils.getKsm().getBase64Cert(testId));
        
        final HttpClientFactory httpFactory = TestUtils.getHttpClientFactory();
        
        final HttpHost host = new HttpHost(
                "127.0.0.1", server.getPort(), "https");
        
        final HttpClient client = httpFactory.newClient(host, true);
        
        final HttpGet get = new HttpGet("https://www.google.com");
        
        final HttpResponse response = client.execute(get);
        final HttpEntity entity = response.getEntity();
        final String body = 
            IOUtils.toString(entity.getContent()).toLowerCase();

        assertTrue("No response?", StringUtils.isNotBlank(body));
        EntityUtils.consume(entity);
        get.reset();
        
        // We have to wait for the peer geo IP lookup, so keep polling for
        // the peer being added.
        Collection<Peer> peers = TestUtils.getModel().getPeers();
        int tries = 0;
        while (peers.isEmpty() && tries < 60) {
            Thread.sleep(100);
            peers = TestUtils.getModel().getPeers();
            tries++;
        }
        
        assertEquals(1, peers.size());
        
        final Peer peer = peers.iterator().next();
        final LanternTrafficCounterHandler tch = peer.getTrafficCounter();
        final TrafficCounter tc = tch.getTrafficCounter();
        
        final long readBytes = tc.getCumulativeReadBytes();
        assertTrue(readBytes > 1000);
        final GlobalLanternServerTrafficShapingHandler traffic = 
                TestUtils.getGlobalTraffic();
        
        // We should have two total sockets because the "waitForServer" call
        // above polls for the socket. At the same time, we only have one
        // total peer because both sockets are from localhost and we 
        // consolidate Peers by address.
        assertEquals(2, traffic.getNumSocketsTotal());
        
    }

    private void thread(final SslHttpProxyServer server) {
        final Runnable runner = new Runnable() {

            @Override
            public void run() {
                server.start(true, true);
            }
        };
        final Thread t = new Thread(runner, "test-tread-"+getClass());
        t.setDaemon(true);
        t.start();
        
    }

}