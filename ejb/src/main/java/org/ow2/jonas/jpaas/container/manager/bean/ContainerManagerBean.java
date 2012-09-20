/**
 * JPaaS
 * Copyright (C) 2012 Bull S.A.S.
 * Contact: jasmine@ow2.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307
 * USA
 *
 * --------------------------------------------------------------------------
 * $Id$
 * --------------------------------------------------------------------------
 */
package org.ow2.jonas.jpaas.container.manager.bean;

import org.ow2.jonas.agent.management.api.xml.App;
import org.ow2.jonas.jpaas.catalog.api.PaasCatalogException;
import org.ow2.jonas.jpaas.container.manager.api.ContainerManager;
import org.ow2.jonas.jpaas.container.manager.api.ContainerManagerBeanException;

import org.ow2.easybeans.osgi.annotation.OSGiResource;
import org.ow2.jonas.jpaas.catalog.api.IPaasCatalogFacade;
import org.ow2.jonas.jpaas.catalog.api.PaasConfiguration;
import org.ow2.jonas.jpaas.sr.facade.api.ISrPaasJonasContainerFacade;
import org.ow2.jonas.jpaas.sr.facade.api.ISrPaasAgentFacade;
import org.ow2.jonas.jpaas.sr.facade.api.ISrPaasResourcePaasAgentLink;
import org.ow2.jonas.jpaas.sr.facade.vo.JonasVO;
import org.ow2.jonas.jpaas.sr.facade.vo.PaasAgentVO;
import org.ow2.jonas.jpaas.sr.facade.vo.PaasResourceVO;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

import org.ow2.jonas.agent.management.api.task.Status;
import org.ow2.jonas.agent.management.api.xml.Server;
import org.ow2.jonas.agent.management.api.xml.Task;
import org.ow2.util.log.Log;
import org.ow2.util.log.LogFactory;

import javax.ejb.Local;
import javax.ejb.Remote;
import javax.ejb.Stateless;
import javax.ws.rs.core.MediaType;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;

@Stateless(mappedName="ContainerManagerBean")
@Local(ContainerManager.class)
@Remote(ContainerManager.class)
public class ContainerManagerBean implements ContainerManager {

    /**
     * The logger
     */
    private Log logger = LogFactory.getLog(ContainerManagerBean.class);

    /**
     * The context of the application
     */
    private static String CONTEXT = "/jonas-api";

    /**
     * Http accepted status
     */
    private static final int HTTP_STATUS_ACCEPTED = 202;

    /**
     * Http Ok status
     */
    private static final int HTTP_STATUS_OK = 200;

    /**
     * Http no content status
     */
    private static final int HTTP_STATUS_NO_CONTENT = 204;

    /**
     * Expected paas type
     */
    private static final String PAAS_TYPE = "container";

    /**
     * Expected paas subtype
     */
    private static final String PAAS_SUB_TYPE = "jonas";

    /**
     * Sleeping period for async operation
     */
    private static final int SLEEPING_PERIOD = 1000;

    /**
     * REST request type
     */
    private enum REST_TYPE {
        PUT,POST,GET,DELETE
    }


    /**
     * Catalog facade
     */
    @OSGiResource
    private IPaasCatalogFacade catalogEjb;

    /**
     * SR facade jonas container
     */
    @OSGiResource
    private ISrPaasJonasContainerFacade srJonasContainerEjb;

    /**
     * SR facade agent
     */
    @OSGiResource
    private ISrPaasAgentFacade srAgentEjb;

    /**
     * SR facade jonas - agent link
     */
    @OSGiResource
    private ISrPaasResourcePaasAgentLink srJonasAgentLinkEjb;

    /**
     * Constructor
     */
    public ContainerManagerBean() {
    }

    /**
     * Create a new JOnAS container
     * @param containerName Name of the container
     * @param paasAgentName Name of the PaaS Agent
     * @param paasConfigurationName Name of the PaasConfiguration
     * @param portRange the port range
     * @throws ContainerManagerBeanException
     */
    public void createContainer(String containerName, String paasAgentName,
            String paasConfigurationName, Integer portRange)
            throws ContainerManagerBeanException {

        logger.info("Container '" + containerName + "' creation ....");

        // Get the agent
        PaasAgentVO agent = null;
        List<PaasAgentVO> paasAgentVOList = srAgentEjb.findAgents();
        for (PaasAgentVO tmp : paasAgentVOList) {
            if (tmp.getName().equals(paasAgentName)) {
                agent = tmp;
                break;
            }
        }
        if (agent == null) {
            throw new ContainerManagerBeanException("Unable to get the agent '" + paasAgentName + "' !");
        }

        // Get configuration from catalog
        PaasConfiguration containerConf = null;
        try {
            containerConf = catalogEjb
                    .getPaasConfiguration(paasConfigurationName);
        } catch (PaasCatalogException e) {
            throw new ContainerManagerBeanException("Error to find the PaaS Configuration named " +
                    paasConfigurationName + ".", e);
        }
        if (!containerConf.getType().equals(PAAS_TYPE)) {
            throw new ContainerManagerBeanException("Invalid paas type : "
                    + containerConf.getType().equals(PAAS_TYPE) + " - expected : "
                    + PAAS_TYPE);
        }
        if (!containerConf.getSubType().equals(PAAS_SUB_TYPE)) {
            throw new ContainerManagerBeanException("Invalid paas sub type : "
                    + containerConf.getType().equals(PAAS_SUB_TYPE) + " - expected : "
                    + PAAS_SUB_TYPE);
        }

        // Create the container in the SR
        List<JonasVO> jonasVOList = srJonasContainerEjb.findJonasContainers();
        for (JonasVO tmp : jonasVOList) {
            if (tmp.getName().equals(containerName)) {
                throw new ContainerManagerBeanException("JOnAS container '" + containerName + "' already exist!");
            }
        }
        JonasVO jonasContainer = new JonasVO();
        jonasContainer.setName(containerName);
        jonasContainer.setState("Init");
        jonasContainer.setProfile(containerConf.getName());
        jonasContainer = srJonasContainerEjb.createJonasContainer(jonasContainer);

        // if the link doesn't exist between agent and jonas, create it
        boolean alreadyExist = false;
        List <PaasResourceVO> paasResources = srJonasAgentLinkEjb.findPaasResourcesByAgent(agent.getId());
        for (PaasResourceVO paasResourceVO : paasResources) {
            if (paasResourceVO instanceof JonasVO) {
                JonasVO jonasResourceVO = (JonasVO) paasResourceVO;
                if (jonasResourceVO.getId().equals(jonasContainer.getId())) {
                    logger.debug("Link between container '"  + containerName + "' and agent '" + paasAgentName +
                            "' already exist!");
                    alreadyExist = true;
                    break;
                }
            }
        }
        if (!alreadyExist) {
            srJonasAgentLinkEjb.addPaasResourceAgentLink(jonasContainer.getId(), agent.getId());
        }

        // TODO use port range to customize topology file

        // Load the topology file
        String topology;
        try {
            topology = getTopologyFromFile(containerConf.getSpecificConfig());
        } catch (Exception e) {
            throw new ContainerManagerBeanException("Error when reading JOnAS topology file '" +
                    containerConf.getSpecificConfig() + "' for paas conf '" + paasConfigurationName +"' - e=" + e);
        }

        // Create the REST request
        Task task = sendRequestWithReply(
                REST_TYPE.PUT,
                getUrl(agent.getApiUrl(), CONTEXT + "/server/" + containerName),
                topology,
                Task.class);

        Long idTask = task.getId();

        // Wait until async task is completed
        while (!task.getStatus().equals(Status.SUCCESS.toString())) {

            if (task.getStatus().equals(Status.ERROR.toString())) {
                throw new ContainerManagerBeanException("Error on JOnAS agent task, id=" + task.getId());
            }
            try {
                Thread.sleep(SLEEPING_PERIOD);
            } catch (InterruptedException e) {
                throw new ContainerManagerBeanException(e.getMessage(), e.getCause());
            }

            task = sendRequestWithReply(
                    REST_TYPE.GET,
                    getUrl(agent.getApiUrl(), CONTEXT + "/task/" + String.valueOf(idTask)),
                    null,
                    Task.class);

        }

        // check that the status of the new container is ok
        Server server = sendRequestWithReply(
                REST_TYPE.GET,
                getUrl(agent.getApiUrl(), CONTEXT + "/server/" + containerName),
                null,
                Server.class);

        // update state in sr
        jonasContainer.setState(server.getStatus());
        srJonasContainerEjb.updateJonasContainer(jonasContainer);

        logger.info("Container '" + server.getName() + "' created. Status=" + server.getStatus());

    }

    /**
     * Remove a JOnAS container
     * @param containerName Name of the container
     * @throws ContainerManagerBeanException
     */
    public void removeContainer(String containerName)
            throws ContainerManagerBeanException {

        logger.info("Container '" + containerName + "' deleting ....");

        // get the container from SR
        JonasVO jonasContainer = null;
        List<JonasVO> jonasVOList = srJonasContainerEjb.findJonasContainers();
        for (JonasVO tmp : jonasVOList) {
            if (tmp.getName().equals(containerName)) {
                jonasContainer = tmp;
                break;
            }
        }
        if (jonasContainer == null) {
            throw new ContainerManagerBeanException("JOnAS container '" + containerName + "' doesn't exist !");
        }

        jonasContainer.setState("DELETING");
        srJonasContainerEjb.updateJonasContainer(jonasContainer);

        // Get the agent
        PaasAgentVO agent = srJonasAgentLinkEjb.findAgentByPaasResource(jonasContainer.getId());

        if (agent == null) {
            throw new ContainerManagerBeanException("Unable to get the agent for container '" + containerName + "' !");
        }

        // Create the REST request
        sendRequestWithReply(
                REST_TYPE.DELETE,
                getUrl(agent.getApiUrl(), CONTEXT + "/server/" + containerName),
                null,
                null);

        // update state in sr
        srJonasContainerEjb.deleteJonasContainer(jonasContainer.getId());

        logger.info("Container '" + containerName + "' deleted.");

    }

    /**
     * Start a JOnAS container
     * @param containerName Name of the Container
     * @throws ContainerManagerBeanException
     */
    public void startContainer(String containerName)
            throws ContainerManagerBeanException {

        logger.info("Container '" + containerName + "' starting ....");

        // get the container from SR
        JonasVO jonasContainer = null;
        List<JonasVO> jonasVOList = srJonasContainerEjb.findJonasContainers();
        for (JonasVO tmp : jonasVOList) {
            if (tmp.getName().equals(containerName)) {
                jonasContainer = tmp;
                break;
            }
        }
        if (jonasContainer == null) {
            throw new ContainerManagerBeanException("JOnAS container '" + containerName + "' doesn't exist !");
        }

        jonasContainer.setState("STARTING");
        srJonasContainerEjb.updateJonasContainer(jonasContainer);

        // Get the agent
        PaasAgentVO agent = srJonasAgentLinkEjb.findAgentByPaasResource(jonasContainer.getId());

        if (agent == null) {
            throw new ContainerManagerBeanException("Unable to get the agent for container '" + containerName + "' !");
        }

        // Create the REST request
        Task task = sendRequestWithReply(
                REST_TYPE.POST,
                getUrl(agent.getApiUrl(), CONTEXT + "/server/" + containerName + "/action/start"),
                null,
                Task.class);

        Long idTask = task.getId();


        // Wait until async task is completed
        while (!task.getStatus().equals(Status.SUCCESS.toString())) {

            if (task.getStatus().equals(Status.ERROR.toString())) {
                throw new ContainerManagerBeanException("Error on JOnAS agent task, id=" + task.getId());
            }
            try {
                Thread.sleep(SLEEPING_PERIOD);
            } catch (InterruptedException e) {
                throw new ContainerManagerBeanException(e.getMessage(), e.getCause());
            }

            task = sendRequestWithReply(
                    REST_TYPE.GET,
                    getUrl(agent.getApiUrl(), CONTEXT + "/task/" + String.valueOf(idTask)),
                    null,
                    Task.class);
        }

        // check that the status of the new container is ok
        Server server = sendRequestWithReply(
                REST_TYPE.GET,
                getUrl(agent.getApiUrl(), CONTEXT + "/server/" + containerName),
                null,
                Server.class);

        // update state in sr
        jonasContainer.setState(server.getStatus());
        srJonasContainerEjb.updateJonasContainer(jonasContainer);

        logger.info("Container '" + server.getName() + "' started. Status=" + server.getStatus());

    }

    /**
     * Stop a JOnAS container
     * @param containerName Name of the container
     * @throws ContainerManagerBeanException
     */
    public void stopContainer(String containerName)
            throws ContainerManagerBeanException {

        logger.info("Container '" + containerName + "' stopping ....");

        // Get the container from SR
        JonasVO jonasContainer = null;
        List<JonasVO> jonasVOList = srJonasContainerEjb.findJonasContainers();
        for (JonasVO tmp : jonasVOList) {
            if (tmp.getName().equals(containerName)) {
                jonasContainer = tmp;
                break;
            }
        }
        if (jonasContainer == null) {
            throw new ContainerManagerBeanException("JOnAS container '" + containerName + "' doesn't exist !");
        }

        jonasContainer.setState("STOPPING");
        srJonasContainerEjb.updateJonasContainer(jonasContainer);

        // Get the agent
        PaasAgentVO agent = srJonasAgentLinkEjb.findAgentByPaasResource(jonasContainer.getId());

        if (agent == null) {
            throw new ContainerManagerBeanException("Unable to get the agent for container '" + containerName + "' !");
        }

        // Create the REST request
        Task task = sendRequestWithReply(
                REST_TYPE.POST,
                getUrl(agent.getApiUrl(), CONTEXT + "/server/" + containerName + "/action/stop"),
                null,
                Task.class);

        Long idTask = task.getId();


        // Wait until async task is completed
        while (!task.getStatus().equals(Status.SUCCESS.toString())) {

            if (task.getStatus().equals(Status.ERROR.toString())) {
                throw new ContainerManagerBeanException("Error on JOnAS agent task, id=" + task.getId());
            }
            try {
                Thread.sleep(SLEEPING_PERIOD);
            } catch (InterruptedException e) {
                throw new ContainerManagerBeanException(e.getMessage(), e.getCause());
            }

            task = sendRequestWithReply(
                    REST_TYPE.GET,
                    getUrl(agent.getApiUrl(), CONTEXT + "/task/" + String.valueOf(idTask)),
                    null,
                    Task.class);
        }

        // check that the status of the new container is ok
        Server server = sendRequestWithReply(
                REST_TYPE.GET,
                getUrl(agent.getApiUrl(), CONTEXT + "/server/" + containerName),
                null,
                Server.class);

        // update state in sr
        jonasContainer.setState(server.getStatus());
        srJonasContainerEjb.updateJonasContainer(jonasContainer);

        logger.info("Container '" + server.getName() + "' stopped. Status=" + server.getStatus());
    }

    /**
     * Deploy a deployable in a container
     * @param containerName Name of the Container
     * @param deployable Url of the deployable to deploy
     * @throws ContainerManagerBeanException
     */
    public void deploy(String containerName, URL deployable)
            throws ContainerManagerBeanException {
        logger.info("Deploying application '" + deployable.toString() + "' on container " + containerName + " ....");

        // get the container from SR
        JonasVO jonasContainer = null;
        List<JonasVO> jonasVOList = srJonasContainerEjb.findJonasContainers();
        for (JonasVO tmp : jonasVOList) {
            if (tmp.getName().equals(containerName)) {
                jonasContainer = tmp;
                break;
            }
        }
        if (jonasContainer == null) {
            throw new ContainerManagerBeanException("JOnAS container '" + containerName + "' doesn't exist !");
        }

        // Get the agent
        PaasAgentVO agent = srJonasAgentLinkEjb.findAgentByPaasResource(jonasContainer.getId());

        if (agent == null) {
            throw new ContainerManagerBeanException("Unable to get the agent for container '" + containerName + "' !");
        }

        // Get the application name
        String stringUrl = deployable.toString();
        String appName = stringUrl.substring(stringUrl.lastIndexOf('/')+1, stringUrl.length());

        // Create the REST request
        Client client = Client.create();
        File applicationFile = null;
        try {
            applicationFile = new File(deployable.toURI());
        } catch (URISyntaxException e) {
            throw new ContainerManagerBeanException("Unable to get the file from URL '" + deployable + "' !\n" + e);
        }
        InputStream inputStream = null;
        try {
            inputStream = new FileInputStream(applicationFile);
        } catch (FileNotFoundException e) {
            throw new ContainerManagerBeanException("Unable to get the file from URL '" + deployable + "' !\n" + e);
        }
        WebResource webResource = client.resource(removeRedundantForwardSlash(getUrl(agent.getApiUrl(), CONTEXT +
                "/server/" + containerName + "/app/" + appName + "/action/deploy")));
        WebResource.Builder builder =
                webResource.type(MediaType.APPLICATION_OCTET_STREAM).accept(MediaType.APPLICATION_XML);

        ClientResponse clientResponse = builder.post(ClientResponse.class, inputStream);

        int status = clientResponse.getStatus();
        Task task = null;
        if (status != HTTP_STATUS_ACCEPTED && status != HTTP_STATUS_OK && status != HTTP_STATUS_NO_CONTENT) {
            throw new ContainerManagerBeanException("Error on JOnAS agent request : " + status);
        }
        if (status != HTTP_STATUS_NO_CONTENT) {
            if (!clientResponse.getType().equals(MediaType.APPLICATION_XML_TYPE)) {
                throw new ContainerManagerBeanException("Error on JOnAS agent response, unexpected type : " +
                        clientResponse.getType());
            }

            task = clientResponse.getEntity(Task.class);
        }

        Long idTask = task.getId();
        client.destroy();

        // Wait until async task is completed
        while (!task.getStatus().equals(Status.SUCCESS.toString())) {

            if (task.getStatus().equals(Status.ERROR.toString())) {
                throw new ContainerManagerBeanException("Error on JOnAS agent task, id=" + task.getId());
            }
            try {
                Thread.sleep(SLEEPING_PERIOD);
            } catch (InterruptedException e) {
                throw new ContainerManagerBeanException(e.getMessage(), e.getCause());
            }

            task = sendRequestWithReply(
                    REST_TYPE.GET,
                    getUrl(agent.getApiUrl(), CONTEXT + "/task/" + String.valueOf(idTask)),
                    null,
                    Task.class);
        }

        // check that the status of the application is DEPLOYED
        App app = sendRequestWithReply(
                REST_TYPE.GET,
                getUrl(agent.getApiUrl(), CONTEXT + "/server/" + containerName + "/app/" + appName),
                null,
                App.class);

        logger.info("Application '" + app.getName() + "' deployed. Status=" + app.getStatus());
    }

    /**
     * Undeploy a deployable in a container
     * @param containerName Name of the Container
     * @param deployable Url of the deployable to undeploy
     * @throws ContainerManagerBeanException
     */
    public void undeploy(String containerName, URL deployable)
            throws ContainerManagerBeanException {
        logger.info("Undeploying application '" + deployable.toString() + "' on container " + containerName + " ....");

        // Get the container from SR
        JonasVO jonasContainer = null;
        List<JonasVO> jonasVOList = srJonasContainerEjb.findJonasContainers();
        for (JonasVO tmp : jonasVOList) {
            if (tmp.getName().equals(containerName)) {
                jonasContainer = tmp;
                break;
            }
        }
        if (jonasContainer == null) {
            throw new ContainerManagerBeanException("JOnAS container '" + containerName + "' doesn't exist !");
        }

        // Get the agent
        PaasAgentVO agent = srJonasAgentLinkEjb.findAgentByPaasResource(jonasContainer.getId());

        if (agent == null) {
            throw new ContainerManagerBeanException("Unable to get the agent for container '" + containerName + "' !");
        }

        // Get the application name
        String stringUrl = deployable.toString();
        String appName = stringUrl.substring(stringUrl.lastIndexOf('/')+1, stringUrl.length());

        // Create the REST request
        Task task = sendRequestWithReply(
                REST_TYPE.POST,
                getUrl(agent.getApiUrl(), CONTEXT + "/server/" + containerName + "/app/" + appName + "/action/undeploy"),
                null,
                Task.class);

        Long idTask = task.getId();


        // Wait until async task is completed
        while (!task.getStatus().equals(Status.SUCCESS.toString())) {

            if (task.getStatus().equals(Status.ERROR.toString())) {
                throw new ContainerManagerBeanException("Error on JOnAS agent task, id=" + task.getId());
            }
            try {
                Thread.sleep(SLEEPING_PERIOD);
            } catch (InterruptedException e) {
                throw new ContainerManagerBeanException(e.getMessage(), e.getCause());
            }

            task = sendRequestWithReply(
                    REST_TYPE.GET,
                    getUrl(agent.getApiUrl(), CONTEXT + "/task/" + String.valueOf(idTask)),
                    null,
                    Task.class);
        }

        // check that the status of the application is NOT_DEPLOYED
        App app = sendRequestWithReply(
                REST_TYPE.GET,
                getUrl(agent.getApiUrl(), CONTEXT + "/server/" + containerName + "/app/" + appName),
                null,
                App.class);

        logger.info("Application '" + app.getName() + "' undeployed. Status=" + app.getStatus());
    }

    /**
     * Add a connector
     * @param containerName Name of the Container
     * @param connectorName Name of the Connector
     * @param connectorConf Configuration of the Connector
     * @throws ContainerManagerBeanException
     */
    public void createConnector(String containerName, String connectorName,
            String connectorConf) throws ContainerManagerBeanException {
        // TODO
        System.out.println("JPAAS-CONTAINER-MANAGER / createConnector called");
    }

    /**
     * Remove a connector
     * @param containerName Name of the Container
     * @param connectorName Name of the Connector
     * @throws ContainerManagerBeanException
     */
    public void removeConnector(String containerName, String connectorName)
            throws ContainerManagerBeanException {
        // TODO
        System.out.println("JPAAS-CONTAINER-MANAGER / removeConnector called");
    }

    /**
     * Add a Datasource
     * @param containerName Name of the Container
     * @param datasourceName Name of the Datasource
     * @param datasourceConf Configuration of the Datasource
     * @throws ContainerManagerBeanException
     */
    public void createDatasource(String containerName, String datasourceName,
            String datasourceConf) throws ContainerManagerBeanException {
        // TODO
        System.out.println("JPAAS-CONTAINER-MANAGER / createDatasource called");
    }

    /**
     * Remove a Datasource
     * @param containerName Name of the Container
     * @param datasourceName Name of the Datasource
     * @throws ContainerManagerBeanException
     */
    public void removeDatasource(String containerName, String datasourceName)
            throws ContainerManagerBeanException {
        // TODO
        System.out.println("JPAAS-CONTAINER-MANAGER / removeDatasource called");
    }

    /**
     * @param agentApi the api url
     * @param path the path to add
     * @return the HTTP URL
     */
    private String getUrl(final String agentApi, final String path) {
        return agentApi + "/" + path;
    }

    /**
     * @return topology as String
     */
    private String getTopologyFromFile(String path) throws Exception {

        File topologyFile;
        topologyFile = new File(path);

        InputStream inputStream;
        inputStream = new FileInputStream(topologyFile);

        byte[] bytes;
        bytes = new byte[inputStream.available()];
        inputStream.read(bytes);
        inputStream.close();

        return new String(bytes);
    }

    /**
     * Send a REST request and get response
     * @param url request path
     * @param requestContent XML content of the request
     * @param responseClass response class
     * @return ResponseClass response class
     */
    private <ResponseClass> ResponseClass sendRequestWithReply(REST_TYPE type, String url, String requestContent,
            java.lang.Class <ResponseClass> responseClass) throws ContainerManagerBeanException {

        Client client = Client.create();

        WebResource webResource = client.resource(removeRedundantForwardSlash(url));

        WebResource.Builder builder = webResource.type(MediaType.APPLICATION_XML_TYPE)
                .accept(MediaType.APPLICATION_XML_TYPE);

        if (requestContent != null) {
            builder = builder.entity(requestContent);
        }
        ClientResponse clientResponse;
        switch (type) {
            case PUT:
                clientResponse = builder.put(ClientResponse.class);
                break;
            case GET:
                clientResponse = builder.get(ClientResponse.class);
                break;
            case POST:
                clientResponse = builder.post(ClientResponse.class);
                break;
            case DELETE:
                clientResponse = builder.delete(ClientResponse.class);
                break;
            default://put
                clientResponse = builder.put(ClientResponse.class);
                break;
        }

        int status = clientResponse.getStatus();

        if (status != HTTP_STATUS_ACCEPTED && status != HTTP_STATUS_OK && status != HTTP_STATUS_NO_CONTENT) {
            throw new ContainerManagerBeanException("Error on JOnAS agent request : " + status);
        }

        ResponseClass r = null;

        if (status != HTTP_STATUS_NO_CONTENT) {
            if (!clientResponse.getType().equals(MediaType.APPLICATION_XML_TYPE)) {
                throw new ContainerManagerBeanException("Error on JOnAS agent response, unexpected type : " +
                        clientResponse.getType());
            }

            if (responseClass != null)
                r = clientResponse.getEntity(responseClass);
        }

        client.destroy();

        return r;

    }

    /**
     * Remove redundant forward slash in a String url
     * @param s a String url
     * @return The String url without redundant forward slash
     */
    private String removeRedundantForwardSlash(String s) {
        String tmp = s.replaceAll("/+", "/");
        return tmp.replaceAll(":/", "://");
    }
}
 
