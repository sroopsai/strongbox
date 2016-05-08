package org.carlspring.strongbox.client;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.metadata.Metadata;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader;
import org.carlspring.maven.commons.util.ArtifactUtils;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.glassfish.jersey.apache.connector.ApacheConnectorProvider;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.client.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author mtodorov
 */
public class MavenArtifactClient implements Closeable
{

    private static final Logger logger = LoggerFactory.getLogger(MavenArtifactClient.class);

    private String repositoryBaseUrl;

    private String username;

    private String password;

    private Client client;


    public MavenArtifactClient()
    {
    }

    public static MavenArtifactClient getTestInstance(String repositoryBaseUrl,
                                                      String username,
                                                      String password)
    {
        MavenArtifactClient client = new MavenArtifactClient();
        client.setUsername(username);
        client.setPassword(password);
        client.setRepositoryBaseUrl(repositoryBaseUrl);

        return client;
    }

    public Client getClientInstance()
    {
        if (client == null)
        {
            ClientConfig config = getClientConfig();
            client = ClientBuilder.newClient(config);

            return client;
        }
        else
        {
            return client;
        }
    }

    private ClientConfig getClientConfig()
    {
        ClientConfig config = new ClientConfig();
        config.connectorProvider(new ApacheConnectorProvider());

        return config;
    }

    @Override
    public void close()
    {
        if (client != null)
        {
            client.close();
        }
    }

    public void getArtifact(Artifact artifact,
                            String repository)
            throws ArtifactTransportException,
                   IOException
    {
        String url = getRepositoryBaseUrl() + "/" + repository + "/" + ArtifactUtils.convertArtifactToPath(artifact);

        logger.debug("Getting " + url + "...");

        WebTarget webResource = getClientInstance().target(url);
        setupAuthentication(webResource);
        Response response = webResource.request(MediaType.TEXT_PLAIN).get();

        final InputStream is = response.readEntity(InputStream.class);

        int total = 0;
        int len;
        final int size = 4096;
        byte[] bytes = new byte[size];

        while ((len = is.read(bytes, 0, size)) != -1)
        {
            total += len;
        }

        logger.debug("Response code: " + response.getStatus() + ". Read: " + total + " bytes.");

        int status = response.getStatus();
        if (status != 200)
        {
            throw new ArtifactTransportException("Failed to resolve artifact!");
        }

        if (total == 0)
        {
            throw new ArtifactTransportException("Artifact size was zero!");
        }
    }

    public InputStream getResource(String path)
            throws ArtifactTransportException,
                   IOException
    {
        return getResource(path, 0);
    }

    public InputStream getResource(String path, long offset)
            throws ArtifactTransportException,
                   IOException
    {
        String url = getRepositoryBaseUrl() + (!path.startsWith("/") ? "/" : "") + path;

        logger.debug("Getting " + url + "...");

        WebTarget resource = getClientInstance().target(url);
        setupAuthentication(resource);

        Invocation.Builder request = resource.request();
        Response response;

        if (offset > 0)
        {
            response = request.header("Range", "bytes=" + offset + "-").get();
        }
        else
        {
            response = request.get();
        }

        return response.readEntity(InputStream.class);
    }

    public Response getResourceWithResponse(String path)
            throws ArtifactTransportException,
                   IOException
    {
        String url = getRepositoryBaseUrl() + (!path.startsWith("/") ? "/" : "") + path;

        logger.debug("Getting " + url + "...");

        WebTarget resource = getClientInstance().target(url);
        setupAuthentication(resource);

        return resource.request(MediaType.TEXT_PLAIN).get();
    }

    public boolean artifactExists(Artifact artifact,
                                  String storageId,
                                  String repositoryId)
            throws ResponseException
    {
        Response response = artifactExistsStatusCode(artifact, storageId, repositoryId);

        if (response.getStatus() == 200)
        {
            return true;
        }
        else if (response.getStatus() == 404)
        {
            return false;
        }
        else
        {
            throw new ResponseException(response.getStatusInfo().getReasonPhrase(), response.getStatus());
        }
    }

    public Response artifactExistsStatusCode(Artifact artifact,
                                             String storageId,
                                             String repositoryId)
            throws ResponseException
    {
        String url = getUrlForArtifact(artifact, storageId, repositoryId);

        logger.debug("Path to artifact: " + url);

        WebTarget resource = getClientInstance().target(url);
        setupAuthentication(resource);

        return resource.request(MediaType.TEXT_PLAIN).get();
    }

    public boolean pathExists(String path)
    {
        String url = getRepositoryBaseUrl() + (path.startsWith("/") ? path : '/' + path);

        logger.debug("Path to artifact: " + url);

        WebTarget resource = getClientInstance().target(url);
        setupAuthentication(resource);

        Response response = resource.request(MediaType.TEXT_PLAIN).get();

        return response.getStatus() == 200;
    }

    public String getUrlForArtifact(Artifact artifact,
                                    String storageId,
                                    String repositoryId)
    {
        return getRepositoryBaseUrl() + "/storages/" + storageId + "/" + repositoryId + "/" +
               ArtifactUtils.convertArtifactToPath(artifact);
    }

    public void setupAuthentication(WebTarget target)
    {
        if (username != null && password != null)
        {
            target.register(HttpAuthenticationFeature.basic(username, password));
        }
    }

    public Metadata retrieveMetadata(String path) throws ArtifactTransportException, IOException, XmlPullParserException
    {
        if (pathExists(path))
        {
            InputStream is = getResource(path);
            MetadataXpp3Reader reader = new MetadataXpp3Reader();
            return reader.read(is);
        }
        return null;
    }

    public String getRepositoryBaseUrl()
    {
        return repositoryBaseUrl;
    }

    public void setRepositoryBaseUrl(String repositoryBaseUrl)
    {
        this.repositoryBaseUrl = repositoryBaseUrl;
    }

    public String getUsername()
    {
        return username;
    }

    public void setUsername(String username)
    {
        this.username = username;
    }

    public String getPassword()
    {
        return password;
    }

    public void setPassword(String password)
    {
        this.password = password;
    }

}
