/*
 * Copyright 2012-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.autoconfigure.web;

import java.io.File;
import java.net.InetAddress;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.SessionCookieConfig;
import javax.servlet.SessionTrackingMode;

import org.apache.catalina.Valve;
import org.apache.catalina.valves.RemoteIpValve;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.MockitoAnnotations;

import org.springframework.beans.MutablePropertyValues;
import org.springframework.boot.bind.RelaxedDataBinder;
import org.springframework.boot.context.embedded.ConfigurableEmbeddedServletContainer;
import org.springframework.boot.context.embedded.ServletContextInitializer;
import org.springframework.boot.context.embedded.jetty.JettyEmbeddedServletContainerFactory;
import org.springframework.boot.context.embedded.tomcat.TomcatEmbeddedServletContainerFactory;
import org.springframework.boot.context.embedded.undertow.UndertowEmbeddedServletContainerFactory;
import org.springframework.mock.env.MockEnvironment;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link ServerProperties}.
 *
 * @author Dave Syer
 * @author Stephane Nicoll
 * @author Andy Wilkinson
 * @author Phillip Webb
 * @author Eddú Meléndez
 */
public class ServerPropertiesTests {

	private final ServerProperties properties = new ServerProperties();

	@Captor
	private ArgumentCaptor<ServletContextInitializer[]> initializersCaptor;

	@Before
	public void setup() {
		MockitoAnnotations.initMocks(this);
	}

	@Test
	public void testAddressBinding() throws Exception {
		RelaxedDataBinder binder = new RelaxedDataBinder(this.properties, "server");
		binder.bind(new MutablePropertyValues(
				Collections.singletonMap("server.address", "127.0.0.1")));
		assertFalse(binder.getBindingResult().hasErrors());
		assertEquals(InetAddress.getByName("127.0.0.1"), this.properties.getAddress());
	}

	@Test
	public void testPortBinding() throws Exception {
		new RelaxedDataBinder(this.properties, "server").bind(new MutablePropertyValues(
				Collections.singletonMap("server.port", "9000")));
		assertEquals(9000, this.properties.getPort().intValue());
	}

	@Test
	public void testServerHeaderDefault() throws Exception {
		assertNull(this.properties.getServerHeader());
	}

	@Test
	public void testServerHeader() throws Exception {
		RelaxedDataBinder binder = new RelaxedDataBinder(this.properties, "server");
		binder.bind(new MutablePropertyValues(
				Collections.singletonMap("server.server-header", "Custom Server")));
		assertEquals("Custom Server", this.properties.getServerHeader());
	}

	@Test
	public void testServletPathAsMapping() throws Exception {
		RelaxedDataBinder binder = new RelaxedDataBinder(this.properties, "server");
		binder.bind(new MutablePropertyValues(
				Collections.singletonMap("server.servletPath", "/foo/*")));
		assertFalse(binder.getBindingResult().hasErrors());
		assertEquals("/foo/*", this.properties.getServletMapping());
		assertEquals("/foo", this.properties.getServletPrefix());
	}

	@Test
	public void testServletPathAsPrefix() throws Exception {
		RelaxedDataBinder binder = new RelaxedDataBinder(this.properties, "server");
		binder.bind(new MutablePropertyValues(
				Collections.singletonMap("server.servletPath", "/foo")));
		assertFalse(binder.getBindingResult().hasErrors());
		assertEquals("/foo/*", this.properties.getServletMapping());
		assertEquals("/foo", this.properties.getServletPrefix());
	}

	@Test
	public void testTomcatBinding() throws Exception {
		Map<String, String> map = new HashMap<String, String>();
		map.put("server.tomcat.accesslog.pattern", "%h %t '%r' %s %b");
		map.put("server.tomcat.accesslog.prefix", "foo");
		map.put("server.tomcat.accesslog.suffix", "-bar.log");
		map.put("server.tomcat.protocol_header", "X-Forwarded-Protocol");
		map.put("server.tomcat.remote_ip_header", "Remote-Ip");
		map.put("server.tomcat.internal_proxies", "10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}");
		bindProperties(map);
		ServerProperties.Tomcat tomcat = this.properties.getTomcat();
		assertEquals("%h %t '%r' %s %b", tomcat.getAccesslog().getPattern());
		assertEquals("foo", tomcat.getAccesslog().getPrefix());
		assertEquals("-bar.log", tomcat.getAccesslog().getSuffix());
		assertEquals("Remote-Ip", tomcat.getRemoteIpHeader());
		assertEquals("X-Forwarded-Protocol", tomcat.getProtocolHeader());
		assertEquals("10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}", tomcat.getInternalProxies());
	}

	@Test
	public void testTrailingSlashOfContextPathIsRemoved() {
		new RelaxedDataBinder(this.properties, "server").bind(new MutablePropertyValues(
				Collections.singletonMap("server.contextPath", "/foo/")));
		assertThat(this.properties.getContextPath(), equalTo("/foo"));
	}

	@Test
	public void testSlashOfContextPathIsDefaultValue() {
		new RelaxedDataBinder(this.properties, "server").bind(new MutablePropertyValues(
				Collections.singletonMap("server.contextPath", "/")));
		assertThat(this.properties.getContextPath(), equalTo(""));
	}

	@Test
	public void testCustomizeTomcat() throws Exception {
		ConfigurableEmbeddedServletContainer factory = mock(
				ConfigurableEmbeddedServletContainer.class);
		this.properties.customize(factory);
		verify(factory, never()).setContextPath("");
	}

	@Test
	public void testDefaultDisplayName() throws Exception {
		ConfigurableEmbeddedServletContainer factory = mock(
				ConfigurableEmbeddedServletContainer.class);
		this.properties.customize(factory);
		verify(factory).setDisplayName("application");
	}

	@Test
	public void testCustomizeDisplayName() throws Exception {
		ConfigurableEmbeddedServletContainer factory = mock(
				ConfigurableEmbeddedServletContainer.class);
		this.properties.setDisplayName("TestName");
		this.properties.customize(factory);
		verify(factory).setDisplayName("TestName");
	}

	@Test
	public void customizeSessionProperties() throws Exception {
		Map<String, String> map = new HashMap<String, String>();
		map.put("server.session.timeout", "123");
		map.put("server.session.tracking-modes", "cookie,url");
		map.put("server.session.cookie.name", "testname");
		map.put("server.session.cookie.domain", "testdomain");
		map.put("server.session.cookie.path", "/testpath");
		map.put("server.session.cookie.comment", "testcomment");
		map.put("server.session.cookie.http-only", "true");
		map.put("server.session.cookie.secure", "true");
		map.put("server.session.cookie.max-age", "60");
		bindProperties(map);
		ConfigurableEmbeddedServletContainer factory = mock(
				ConfigurableEmbeddedServletContainer.class);
		ServletContext servletContext = mock(ServletContext.class);
		SessionCookieConfig sessionCookieConfig = mock(SessionCookieConfig.class);
		given(servletContext.getSessionCookieConfig()).willReturn(sessionCookieConfig);
		this.properties.customize(factory);
		triggerInitializers(factory, servletContext);
		verify(factory).setSessionTimeout(123);
		verify(servletContext).setSessionTrackingModes(
				EnumSet.of(SessionTrackingMode.COOKIE, SessionTrackingMode.URL));
		verify(sessionCookieConfig).setName("testname");
		verify(sessionCookieConfig).setDomain("testdomain");
		verify(sessionCookieConfig).setPath("/testpath");
		verify(sessionCookieConfig).setComment("testcomment");
		verify(sessionCookieConfig).setHttpOnly(true);
		verify(sessionCookieConfig).setSecure(true);
		verify(sessionCookieConfig).setMaxAge(60);
	}

	private void triggerInitializers(ConfigurableEmbeddedServletContainer container,
			ServletContext servletContext) throws ServletException {
		verify(container, atLeastOnce())
				.addInitializers(this.initializersCaptor.capture());
		for (Object initializers : this.initializersCaptor.getAllValues()) {
			if (initializers instanceof ServletContextInitializer) {
				((ServletContextInitializer) initializers).onStartup(servletContext);
			}
			else {
				for (ServletContextInitializer initializer : (ServletContextInitializer[]) initializers) {
					initializer.onStartup(servletContext);
				}
			}
		}
	}

	@Test
	public void testCustomizeTomcatPort() throws Exception {
		ConfigurableEmbeddedServletContainer factory = mock(
				ConfigurableEmbeddedServletContainer.class);
		this.properties.setPort(8080);
		this.properties.customize(factory);
		verify(factory).setPort(8080);
	}

	@Test
	public void testCustomizeUriEncoding() throws Exception {
		Map<String, String> map = new HashMap<String, String>();
		map.put("server.tomcat.uriEncoding", "US-ASCII");
		bindProperties(map);
		assertEquals(Charset.forName("US-ASCII"),
				this.properties.getTomcat().getUriEncoding());
	}

	@Test
	public void testCustomizeTomcatHeaderSize() throws Exception {
		Map<String, String> map = new HashMap<String, String>();
		map.put("server.tomcat.maxHttpHeaderSize", "9999");
		bindProperties(map);
		assertEquals(9999, this.properties.getTomcat().getMaxHttpHeaderSize());
	}

	@Test
	public void customizeTomcatDisplayName() throws Exception {
		Map<String, String> map = new HashMap<String, String>();
		map.put("server.display-name", "MyBootApp");
		bindProperties(map);
		TomcatEmbeddedServletContainerFactory container = new TomcatEmbeddedServletContainerFactory();
		this.properties.customize(container);
		assertEquals("MyBootApp", container.getDisplayName());
	}

	@Test
	public void disableTomcatRemoteIpValve() throws Exception {
		Map<String, String> map = new HashMap<String, String>();
		map.put("server.tomcat.remote_ip_header", "");
		map.put("server.tomcat.protocol_header", "");
		bindProperties(map);
		TomcatEmbeddedServletContainerFactory container = new TomcatEmbeddedServletContainerFactory();
		this.properties.customize(container);
		assertEquals(0, container.getValves().size());
	}

	@Test
	public void defaultTomcatRemoteIpValve() throws Exception {
		Map<String, String> map = new HashMap<String, String>();
		// Since 1.1.7 you need to specify at least the protocol
		map.put("server.tomcat.protocol_header", "X-Forwarded-Proto");
		map.put("server.tomcat.remote_ip_header", "X-Forwarded-For");
		bindProperties(map);
		testRemoteIpValveConfigured();
	}

	@Test
	public void setUseForwardHeadersTomcat() throws Exception {
		// Since 1.3.0 no need to explicitly set header names if use-forward-header=true
		this.properties.setUseForwardHeaders(true);
		testRemoteIpValveConfigured();
	}

	@Test
	public void deduceUseForwardHeadersTomcat() throws Exception {
		this.properties.setEnvironment(new MockEnvironment().withProperty("DYNO", "-"));
		testRemoteIpValveConfigured();
	}

	private void testRemoteIpValveConfigured() {
		TomcatEmbeddedServletContainerFactory container = new TomcatEmbeddedServletContainerFactory();
		this.properties.customize(container);
		assertEquals(1, container.getValves().size());
		Valve valve = container.getValves().iterator().next();
		assertThat(valve, instanceOf(RemoteIpValve.class));
		RemoteIpValve remoteIpValve = (RemoteIpValve) valve;
		assertEquals("X-Forwarded-Proto", remoteIpValve.getProtocolHeader());
		assertEquals("https", remoteIpValve.getProtocolHeaderHttpsValue());
		assertEquals("X-Forwarded-For", remoteIpValve.getRemoteIpHeader());
		String expectedInternalProxies = "10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|" // 10/8
				+ "192\\.168\\.\\d{1,3}\\.\\d{1,3}|" // 192.168/16
				+ "169\\.254\\.\\d{1,3}\\.\\d{1,3}|" // 169.254/16
				+ "127\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|" // 127/8
				+ "172\\.1[6-9]{1}\\.\\d{1,3}\\.\\d{1,3}|" // 172.16/12
				+ "172\\.2[0-9]{1}\\.\\d{1,3}\\.\\d{1,3}|"
				+ "172\\.3[0-1]{1}\\.\\d{1,3}\\.\\d{1,3}";
		assertEquals(expectedInternalProxies, remoteIpValve.getInternalProxies());
	}

	@Test
	public void customTomcatRemoteIpValve() throws Exception {
		Map<String, String> map = new HashMap<String, String>();
		map.put("server.tomcat.remote_ip_header", "x-my-remote-ip-header");
		map.put("server.tomcat.protocol_header", "x-my-protocol-header");
		map.put("server.tomcat.internal_proxies", "192.168.0.1");
		map.put("server.tomcat.port-header", "x-my-forward-port");
		map.put("server.tomcat.protocol-header-https-value", "On");
		bindProperties(map);

		TomcatEmbeddedServletContainerFactory container = new TomcatEmbeddedServletContainerFactory();
		this.properties.customize(container);

		assertEquals(1, container.getValves().size());
		Valve valve = container.getValves().iterator().next();
		assertThat(valve, instanceOf(RemoteIpValve.class));
		RemoteIpValve remoteIpValve = (RemoteIpValve) valve;
		assertEquals("x-my-protocol-header", remoteIpValve.getProtocolHeader());
		assertEquals("On", remoteIpValve.getProtocolHeaderHttpsValue());
		assertEquals("x-my-remote-ip-header", remoteIpValve.getRemoteIpHeader());
		assertEquals("x-my-forward-port", remoteIpValve.getPortHeader());
		assertEquals("192.168.0.1", remoteIpValve.getInternalProxies());
	}

	@Test
	public void defaultUseForwardHeadersUndertow() throws Exception {
		UndertowEmbeddedServletContainerFactory container = spy(
				new UndertowEmbeddedServletContainerFactory());
		this.properties.customize(container);
		verify(container).setUseForwardHeaders(false);
	}

	@Test
	public void setUseForwardHeadersUndertow() throws Exception {
		this.properties.setUseForwardHeaders(true);
		UndertowEmbeddedServletContainerFactory container = spy(
				new UndertowEmbeddedServletContainerFactory());
		this.properties.customize(container);
		verify(container).setUseForwardHeaders(true);
	}

	@Test
	public void deduceUseForwardHeadersUndertow() throws Exception {
		this.properties.setEnvironment(new MockEnvironment().withProperty("DYNO", "-"));
		UndertowEmbeddedServletContainerFactory container = spy(
				new UndertowEmbeddedServletContainerFactory());
		this.properties.customize(container);
		verify(container).setUseForwardHeaders(true);
	}

	@Test
	public void defaultUseForwardHeadersJetty() throws Exception {
		JettyEmbeddedServletContainerFactory container = spy(
				new JettyEmbeddedServletContainerFactory());
		this.properties.customize(container);
		verify(container).setUseForwardHeaders(false);
	}

	@Test
	public void setUseForwardHeadersJetty() throws Exception {
		this.properties.setUseForwardHeaders(true);
		JettyEmbeddedServletContainerFactory container = spy(
				new JettyEmbeddedServletContainerFactory());
		this.properties.customize(container);
		verify(container).setUseForwardHeaders(true);
	}

	@Test
	public void deduceUseForwardHeadersJetty() throws Exception {
		this.properties.setEnvironment(new MockEnvironment().withProperty("DYNO", "-"));
		JettyEmbeddedServletContainerFactory container = spy(
				new JettyEmbeddedServletContainerFactory());
		this.properties.customize(container);
		verify(container).setUseForwardHeaders(true);
	}

	@Test
	public void sessionStoreDir() throws Exception {
		Map<String, String> map = new HashMap<String, String>();
		map.put("server.session.store-dir", "myfolder");
		bindProperties(map);
		JettyEmbeddedServletContainerFactory container = spy(
				new JettyEmbeddedServletContainerFactory());
		this.properties.customize(container);
		verify(container).setSessionStoreDir(new File("myfolder"));
	}

	@Test
	public void skipNullElementsForUndertow() throws Exception {
		UndertowEmbeddedServletContainerFactory container = mock(
				UndertowEmbeddedServletContainerFactory.class);
		this.properties.customize(container);
		verify(container, never()).setAccessLogEnabled(anyBoolean());
	}

	private void bindProperties(Map<String, String> map) {
		new RelaxedDataBinder(this.properties, "server")
				.bind(new MutablePropertyValues(map));
	}

}