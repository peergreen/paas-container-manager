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


  public void createContainer(String containerName, String paasAgentName, String paasConfigurationName, Integer portRange) throws ContainerManagerBeanException;

  public void removeContainer(String containerName) throws ContainerManagerBeanException;

  public void startContainer(String containerName) throws ContainerManagerBeanException;

  public void stopContainer(String containerName) throws ContainerManagerBeanException;

  public void deploy(String containerName, URL deployable) throws ContainerManagerBeanException;

  public void undeploy(String containerName, URL deployable) throws ContainerManagerBeanException;

  public void createConnector(String containerName, String connectorName, String connectorConf) throws ContainerManagerBeanException;

  public void removeConnector(String containerName, String connectorName) throws ContainerManagerBeanException;

  public void createDatasource(String containerName, String datasourceName, String datasourceConf) throws ContainerManagerBeanException;
  public void removeDatasource(String containerName, String datasourceName) throws ContainerManagerBeanException;

}

