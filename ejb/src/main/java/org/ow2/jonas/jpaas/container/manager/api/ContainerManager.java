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
package org.ow2.jonas.jpaas.container.manager.api;


import java.net.URL;

public interface ContainerManager {

    /**
     * Create a new JOnAS container
     * @param containerName Name of the container
     * @param paasAgentName Name of the PaaS Agent
     * @param paasConfigurationName Name of the PaasConfiguration
     * @param portRange the port range
     * @throws ContainerManagerBeanException
     */
    public void createContainer(String containerName, String paasAgentName, String paasConfigurationName,
            Integer portRange) throws ContainerManagerBeanException;

    /**
     * Remove a JOnAS container
     * @param containerName Name of the container
     * @throws ContainerManagerBeanException
     */
    public void removeContainer(String containerName) throws ContainerManagerBeanException;

    /**
     * Start a JOnAS container
     * @param containerName Name of the Container
     * @throws ContainerManagerBeanException
     */
    public void startContainer(String containerName) throws ContainerManagerBeanException;

    /**
     * Stop a JOnAS container
     * @param containerName Name of the container
     * @throws ContainerManagerBeanException
     */
    public void stopContainer(String containerName) throws ContainerManagerBeanException;

    /**
     * Deploy a deployable in a container
     * @param containerName Name of the Container
     * @param deployable Url of the deployable to deploy
     * @throws ContainerManagerBeanException
     */
    public void deploy(String containerName, URL deployable) throws ContainerManagerBeanException;

    /**
     * Undeploy a deployable in a container
     * @param containerName Name of the Container
     * @param deployable Url of the deployable to undeploy
     * @throws ContainerManagerBeanException
     */
    public void undeploy(String containerName, URL deployable) throws ContainerManagerBeanException;

    /**
     * Add a connector
     * @param containerName Name of the Container
     * @param connectorName Name of the Connector
     * @param connectorConf Configuration of the Connector
     * @throws ContainerManagerBeanException
     */
    public void createConnector(String containerName, String connectorName, String connectorConf)
            throws ContainerManagerBeanException;

    /**
     * Remove a connector
     * @param containerName Name of the Container
     * @param connectorName Name of the Connector
     * @throws ContainerManagerBeanException
     */
    public void removeConnector(String containerName, String connectorName) throws ContainerManagerBeanException;

    /**
     * Add a Datasource
     * @param containerName Name of the Container
     * @param datasourceName Name of the Datasource
     * @param datasourceConf Configuration of the Datasource
     * @throws ContainerManagerBeanException
     */
    public void createDatasource(String containerName, String datasourceName, String datasourceConf)
            throws ContainerManagerBeanException;

    /**
     * Remove a Datasource
     * @param containerName Name of the Container
     * @param datasourceName Name of the Datasource
     * @throws ContainerManagerBeanException
     */
    public void removeDatasource(String containerName, String datasourceName) throws ContainerManagerBeanException;

}

