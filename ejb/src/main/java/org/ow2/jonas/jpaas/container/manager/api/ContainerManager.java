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

