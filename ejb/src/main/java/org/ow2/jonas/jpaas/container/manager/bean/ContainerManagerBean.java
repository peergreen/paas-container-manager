/**
 * JPaaS
 * Copyright 2012 Bull S.A.S.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * $Id:$
 */ 
package org.ow2.jonas.jpaas.container.manager.bean;

import org.ow2.jonas.agent.management.api.xml.App;
import org.ow2.jonas.jpaas.catalog.api.PaasCatalogException;
import org.ow2.jonas.jpaas.container.manager.api.ContainerManager;
import org.ow2.jonas.jpaas.container.manager.api.ContainerManagerBeanException;

import org.ow2.easybeans.osgi.annotation.OSGiResource;
import org.ow2.jonas.jpaas.catalog.api.IPaasCatalogFacade;
import org.ow2.jonas.jpaas.catalog.api.PaasConfiguration;
import org.ow2.jonas.jpaas.sr.facade.api.ISrPaasAgentIaasComputeLink;
import org.ow2.jonas.jpaas.sr.facade.api.ISrPaasJonasContainerFacade;
import org.ow2.jonas.jpaas.sr.facade.api.ISrPaasAgentFacade;
import org.ow2.jonas.jpaas.sr.facade.api.ISrPaasResourceIaasComputeLink;
import org.ow2.jonas.jpaas.sr.facade.api.ISrPaasResourcePaasAgentLink;
import org.ow2.jonas.jpaas.sr.facade.vo.ConnectorVO;
import org.ow2.jonas.jpaas.sr.facade.vo.IaasComputeVO;
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
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.ws.rs.core.MediaType;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
     * SR facade agent - iaasCompute link
     */
    @OSGiResource
    private ISrPaasAgentIaasComputeLink srPaasAgentIaasComputeLink;

    /**
     * SR facade paasResource - iaasCompute link
     */
    @OSGiResource
    private ISrPaasResourceIaasComputeLink srPaasResourceIaasComputeLink;


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
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
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

        //create the link between the PaaS Container and the IaaS Compute
        IaasComputeVO iaasCompute = srPaasAgentIaasComputeLink.findIaasComputeByPaasAgent(agent.getId());
        if (iaasCompute != null) {
            srPaasResourceIaasComputeLink.addPaasResourceIaasComputeLink(jonasContainer.getId(), iaasCompute.getId());
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

        // Replace the server name
        topology = topology.replaceAll("\\$\\{serverName\\}", containerName);

        // Create the REST request
        Task task = sendRequestWithReply(
                REST_TYPE.PUT,
                getUrl(agent.getApiUrl(), CONTEXT + "/server/" + containerName),
                topology,
                Task.class);

        // Wait until async task is completed
        waitUntilAsyncTaskIsCompleted(task, agent.getApiUrl());

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
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
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
        //remove container - iaasCompute link
        IaasComputeVO iaasCompute = srPaasResourceIaasComputeLink.findIaasComputeByPaasResource(jonasContainer.getId());
        if (iaasCompute != null) {
            srPaasResourceIaasComputeLink.removePaasResourceIaasComputeLink(jonasContainer.getId(),
                    iaasCompute.getId());
        }
        //delete jonas container in SR
        srJonasContainerEjb.deleteJonasContainer(jonasContainer.getId());

        logger.info("Container '" + containerName + "' deleted.");

    }

    /**
     * Start a JOnAS container
     * @param containerName Name of the Container
     * @throws ContainerManagerBeanException
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
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

        // Wait until async task is completed
        waitUntilAsyncTaskIsCompleted(task, agent.getApiUrl());

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
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
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

        // Wait until async task is completed
        waitUntilAsyncTaskIsCompleted(task, agent.getApiUrl());

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

        //Repository operations
        String repoName = "repo-" + deployable.getAuthority();
        String repoFileName = repoName + ".xml";
        //Check if the repository file is already deployed on the container
        App repo = sendRequestWithReply(
                REST_TYPE.GET,
                getUrl(agent.getApiUrl(), CONTEXT + "/server/" + containerName + "/app/" + repoFileName),
                null,
                App.class);
        //If the repository file is not present, create the file and deploy it
        if (repo.getStatus().equals("NOT_DEPLOYED")) {
            //Use the repository Template
            String repoContent = null;
            URL repoTemplateURL = this.getClass().getClassLoader().getResource("repository-template.xml");
            try {
                String repoTemplate = convertUrlToString(repoTemplateURL);
                // Replace the template id and url
                repoContent = repoTemplate.replaceAll("\\$\\{id\\}", repoName);
                repoContent = repoContent.replaceAll("\\$\\{url\\}",
                        deployable.getProtocol() + "://" + deployable.getAuthority());
            } catch (IOException e) {
                throw new ContainerManagerBeanException("Cannot get the repository template file !");
            }
            //Deploy the repository file
            Task task = sendDeployRequestWithReply(agent.getApiUrl(), containerName, repoFileName,
                    repoContent.getBytes());
            // Wait until async task is completed
            waitUntilAsyncTaskIsCompleted(task, agent.getApiUrl());
            // check that the status of the repository file is DEPLOYED
            repo = sendRequestWithReply(
                    REST_TYPE.GET,
                    getUrl(agent.getApiUrl(), CONTEXT + "/server/" + containerName + "/app/" + repoFileName),
                    null,
                    App.class);
            if (!repo.getStatus().equals("DEPLOYED")) {
                throw new ContainerManagerBeanException("Error : the repository file " + repoFileName +
                        " is not deployed correctly!");
            }
        }


        // Get the application name
        String stringUrl = deployable.toString();
        String appName = stringUrl.substring(stringUrl.lastIndexOf('/')+1, stringUrl.length());

        //Create Deployment-Plan
        String deploymentPlanName = "plan-" + appName;
        String deploymentPlanFileName = deploymentPlanName + ".xml";
        //Use the deployment-plan Template
        String deploymentPlanContent = null;
        URL deploymentPlanTemplateURL = this.getClass().getClassLoader().getResource("deployment-plan-template.xml");
        try {
            String deploymentPlanTemplate = convertUrlToString(deploymentPlanTemplateURL);
            // Replace the template id and resource
            deploymentPlanContent = deploymentPlanTemplate.replaceAll("\\$\\{id\\}", deploymentPlanName);
            deploymentPlanContent = deploymentPlanContent.replaceAll("\\$\\{resource\\}", deployable.getPath());
            deploymentPlanContent = deploymentPlanContent.replaceAll("\\$\\{repo-id\\}", repoName);
        } catch (IOException e) {
            throw new ContainerManagerBeanException("Cannot get the deployment plan template file !");
        }
        Task task = sendDeployRequestWithReply(agent.getApiUrl(), containerName, deploymentPlanFileName,
                deploymentPlanContent.getBytes());

        // Wait until async task is completed
        waitUntilAsyncTaskIsCompleted(task, agent.getApiUrl());

        // check that the status of the application is DEPLOYED
        App app = sendRequestWithReply(
                REST_TYPE.GET,
                getUrl(agent.getApiUrl(), CONTEXT + "/server/" + containerName + "/app/" + deploymentPlanFileName),
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

        // Wait until async task is completed
        waitUntilAsyncTaskIsCompleted(task, agent.getApiUrl());

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
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void createConnector(String containerName, String connectorName,
            String connectorConf) throws ContainerManagerBeanException {
        // ToDo : use connectorConf. For now, a template is used instead.
        // ToDo : not use hard-coded port
        Integer port = 9009;
        Integer redirectPort = 9043;
        System.out.println("JPAAS-CONTAINER-MANAGER / createConnector called");
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

        //Do nothing if there is already a connector with the same name
        boolean connectorExists = false;
        List<ConnectorVO> connectorVOList = jonasContainer.getConnectorList();
        for (ConnectorVO connector : connectorVOList) {
            if (connector.getName().equals(connectorName)) {
                connectorExists = true;
                break;
            }
        }
        if (!connectorExists) {
            // Get the agent
            PaasAgentVO agent = srJonasAgentLinkEjb.findAgentByPaasResource(jonasContainer.getId());

            if (agent == null) {
                throw new ContainerManagerBeanException("Unable to get the agent for container '" + containerName + "' !");
            }

            //Use the connector Template
            String connectorConfiguration = null;
            URL connectorTemplateURL = this.getClass().getClassLoader().getResource("connector-template.xml");
            try {
                String connectorTemplate = convertUrlToString(connectorTemplateURL);
                // Replace the template ports
                connectorConfiguration = connectorTemplate.replaceAll("\\$\\{port\\}", port.toString());
                connectorConfiguration = connectorConfiguration.replaceAll("\\$\\{redirectPort\\}",
                        redirectPort.toString());
            } catch (IOException e) {
                throw new ContainerManagerBeanException("Cannot get the connector template file !");
            }


            // Create the REST request
            String connectorFileName = connectorName + ".xml";

            Task task = sendDeployRequestWithReply(agent.getApiUrl(), containerName, connectorFileName,
                    connectorConfiguration.getBytes());

            // Wait until async task is completed
            waitUntilAsyncTaskIsCompleted(task, agent.getApiUrl());

            // check that the status of the application is DEPLOYED
            App app = sendRequestWithReply(
                    REST_TYPE.GET,
                    getUrl(agent.getApiUrl(), CONTEXT + "/server/" + containerName + "/app/" + connectorFileName),
                    null,
                    App.class);

            srJonasContainerEjb.addConnector(jonasContainer.getId(), connectorName, port);

            logger.info("Connector '" + app.getName() + "' deployed. Status=" + app.getStatus());
        }

    }

    /**
     * Remove a connector
     * @param containerName Name of the Container
     * @param connectorName Name of the Connector
     * @throws ContainerManagerBeanException
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void removeConnector(String containerName, String connectorName)
            throws ContainerManagerBeanException {
        // TODO : Bug with JOnAS : JONAS-934
        System.out.println("JPAAS-CONTAINER-MANAGER / removeConnector called");

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

        // Create the REST request
        Task task = sendRequestWithReply(
                REST_TYPE.POST,
                getUrl(agent.getApiUrl(), CONTEXT + "/server/" + containerName + "/app/" + connectorName + ".xml" + "/action/undeploy"),
                null,
                Task.class);

        // Wait until async task is completed
        waitUntilAsyncTaskIsCompleted(task, agent.getApiUrl());

        // check that the status of the application is NOT_DEPLOYED
        App app = sendRequestWithReply(
                REST_TYPE.GET,
                getUrl(agent.getApiUrl(), CONTEXT + "/server/" + containerName + "/app/" + connectorName + ".xml"),
                null,
                App.class);

        srJonasContainerEjb.removeConnector(jonasContainer.getId(), connectorName);

        logger.info("Connector '" + app.getName() + "' undeployed. Status=" + app.getStatus());
    }

    /**
     * Add a Datasource
     * @param containerName Name of the Container
     * @param datasourceName Name of the Datasource
     * @param datasourceConf Configuration of the Datasource
     * @throws ContainerManagerBeanException
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
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
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
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


        ClientResponse clientResponse;
        switch (type) {
            case PUT:
                if (requestContent != null) {
                    clientResponse = builder.put(ClientResponse.class, requestContent);
                } else {
                    clientResponse = builder.put(ClientResponse.class);
                }
                break;
            case GET:
                clientResponse = builder.get(ClientResponse.class);
                break;
            case POST:
                if (requestContent != null) {
                    clientResponse = builder.post(ClientResponse.class, requestContent);
                } else {
                    clientResponse = builder.post(ClientResponse.class);
                }
                break;
            case DELETE:
                clientResponse = builder.delete(ClientResponse.class);
                break;
            default://put
                if (requestContent != null) {
                    clientResponse = builder.put(ClientResponse.class, requestContent);
                } else {
                    clientResponse = builder.put(ClientResponse.class);
                }
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
     * Send a REST request and get response
     * @param apiUrl Api URL of the Agent
     * @param containerName container Name
     * @param appFileName name of the application file
     * @param appContent application content
     * @return Task
     */
    private Task sendDeployRequestWithReply(String apiUrl, String containerName, String appFileName, Object appContent)
            throws ContainerManagerBeanException {

        Client client = Client.create();

        WebResource webResource = client.resource(removeRedundantForwardSlash(getUrl(apiUrl, CONTEXT +
                "/server/" + containerName + "/app/" + appFileName + "/action/deploy")));
        WebResource.Builder builder =
                webResource.type(MediaType.APPLICATION_OCTET_STREAM).accept(MediaType.APPLICATION_XML);

        ClientResponse clientResponse = builder.post(ClientResponse.class, appContent);

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
        client.destroy();
        return task;
    }

    private void waitUntilAsyncTaskIsCompleted(Task task, String apiUrl) throws ContainerManagerBeanException {
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
                    getUrl(apiUrl, CONTEXT + "/task/" + String.valueOf(task.getId())),
                    null,
                    Task.class);
        }
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

    private String convertUrlToString(URL file) throws IOException {
        InputStream urlStream = file.openStream();
        InputStreamReader is = new InputStreamReader(urlStream);
        BufferedReader br = new BufferedReader(is);
        String read = br.readLine();
        StringBuffer sb = new StringBuffer(read);
        while(read != null) {
            read = br.readLine();
            if (read != null) {
                sb.append(read);
                sb.append(System.getProperty("line.separator"));
            }
        }
        return sb.toString();
    }
}
 
