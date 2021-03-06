/*
 * Copyright 2013-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.xd.dirt.server;

import java.util.Properties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportResource;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.integration.monitor.IntegrationMBeanExporter;
import org.springframework.util.StringUtils;
import org.springframework.xd.dirt.util.ConfigLocations;
import org.springframework.xd.dirt.zookeeper.EmbeddedZooKeeper;
import org.springframework.xd.dirt.zookeeper.ZooKeeperConnection;

/**
 * Beans defined and imported here are shared by the XD Admin Server and Container Server.
 * 
 * @author David Turanski
 * @author Mark Fisher
 * @author Ilayaperumal Gopinathan
 */
@Configuration
@Import(PropertyPlaceholderAutoConfiguration.class)
@ImportResource({ ConfigLocations.XD_CONFIG_ROOT + "bus/*.xml",
	ConfigLocations.XD_CONFIG_ROOT + "store/${XD_STORE}-store.xml",
	ConfigLocations.XD_CONFIG_ROOT + "analytics/${XD_ANALYTICS}-analytics.xml" })
public class SharedServerContextConfiguration {

	private static final String MBEAN_EXPORTER_BEAN_NAME = "XDSharedServerMBeanExporter";

	@ConditionalOnExpression("${XD_JMX_ENABLED:false}")
	@EnableMBeanExport(defaultDomain = "xd.shared.server")
	protected static class JmxConfiguration {

		@Bean(name = MBEAN_EXPORTER_BEAN_NAME)
		public IntegrationMBeanExporter integrationMBeanExporter() {
			IntegrationMBeanExporter exporter = new IntegrationMBeanExporter();
			exporter.setDefaultDomain("xd.shared.server");
			return exporter;
		}
	}


	@Configuration
	@Profile(SingleNodeApplication.SINGLE_PROFILE)
	static class SingleNodeZooKeeperConfig extends ZookeeperConnectionConfig {

		@Bean
		@ConditionalOnExpression("'${zk.client.connect}'.isEmpty()")
		EmbeddedZooKeeper embeddedZooKeeper() {
			return new EmbeddedZooKeeper();
		}

		// This is autowired, but not required, since the EmbeddedZooKeeper instance is conditional.
		@Autowired(required = false)
		EmbeddedZooKeeper embeddedZooKeeper;

		@Bean
		public ZooKeeperConnection zooKeeperConnection() {
			// the embedded server accepts client connections on a dynamically determined port
			if (embeddedZooKeeper != null) {
				zkClientConnect = "localhost:" + embeddedZooKeeper.getClientPort();
			}
			return setupZookeeperPropertySource(zkClientConnect);
		}
	}


	@Configuration
	@Profile("!" + SingleNodeApplication.SINGLE_PROFILE)
	static class DistributedZooKeeperConfig extends ZookeeperConnectionConfig {

		@Bean
		public ZooKeeperConnection zooKeeperConnection() {
			return setupZookeeperPropertySource(zkClientConnect);
		}
	}

	protected static class ZookeeperConnectionConfig implements EnvironmentAware {

		@Value("${zk.client.connect:}")
		protected String zkClientConnect;

		private ConfigurableEnvironment environment;

		private Properties zkProperties = new Properties();

		protected ZooKeeperConnection setupZookeeperPropertySource(String zkClientConnect) {
			if (!StringUtils.hasText(zkClientConnect)) {
				zkClientConnect = ZooKeeperConnection.DEFAULT_CLIENT_CONNECT_STRING;
			}
			zkProperties.put("zk.client.connect", zkClientConnect);
			this.environment.getPropertySources().addFirst(new PropertiesPropertySource("zk-properties", zkProperties));
			return new ZooKeeperConnection(zkClientConnect);
		}

		@Override
		public void setEnvironment(Environment environment) {
			this.environment = (ConfigurableEnvironment) environment;
		}

	}
}
