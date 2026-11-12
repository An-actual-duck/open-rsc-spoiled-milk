package com.openrsc.server.net;
import com.openrsc.server.ServerConfiguration;
import com.openrsc.server.config.ProcessNetworkConfiguration;
/** Actual hosted-profile parser and immutable startup projection, no server/database. */
public final class PublicWebSocketConfigurationTest {
 public static void main(String[] args)throws Exception {
  System.setProperty("openrsc.layeredNativeTerrainPackagePath",args[0]);
  System.setProperty("openrsc.layeredNativeTerrainManifestSha256",args[1]);
  ServerConfiguration config=new ServerConfiguration();config.initConfig("myworld-host.conf");
  ProcessNetworkConfiguration p=config.processNetworkConfiguration();p.requireValid();
  if(p.isWebsocketsEnabled()!=PublicWebSocketPolicy.enabled()||p.getServerPort()!=43605||p.getWebsocketPort()!=43505)throw new AssertionError("Public optin/default listener projection");
  String cert=PublicWebSocketPolicy.enabled()?"/fixture/cert.pem":"",key=PublicWebSocketPolicy.enabled()?"/fixture/key.pem":"";
  if(!cert.equals(p.getSslServerCertPath())||!key.equals(p.getSslServerKeyPath()))throw new AssertionError("Public-only PEM selection");
  config.SSL_SERVER_CERT_PATH="mutated";config.SSL_SERVER_KEY_PATH="mutated";config.WANT_FEATURE_WEBSOCKETS=!p.isWebsocketsEnabled();
  if(!cert.equals(p.getSslServerCertPath())||!key.equals(p.getSslServerKeyPath())||p.isWebsocketsEnabled()!=PublicWebSocketPolicy.enabled())throw new AssertionError("Startup choices lost immutable ownership");
  System.out.println("PASS actual hosted parser and immutable projection public="+PublicWebSocketPolicy.enabled());
 }
}
