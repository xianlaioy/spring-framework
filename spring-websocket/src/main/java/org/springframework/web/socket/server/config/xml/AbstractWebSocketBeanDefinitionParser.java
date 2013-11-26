/*
 * Copyright 2002-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.web.socket.server.config.xml;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.beans.factory.support.ManagedMap;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.xml.BeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.util.xml.DomUtils;
import org.springframework.web.socket.server.DefaultHandshakeHandler;
import org.springframework.web.socket.sockjs.transport.handler.DefaultSockJsService;
import org.w3c.dom.Element;

import java.util.Collections;

/**
 * @author Brian Clozel
 * @since 4.0
 */
public abstract class AbstractWebSocketBeanDefinitionParser implements BeanDefinitionParser {

	protected static final int DEFAULT_MAPPING_ORDER = 1;

	protected RuntimeBeanReference registerHandshakeHandler(Element element, ParserContext parserContext, Object source) {

		RuntimeBeanReference handshakeHandlerReference;

		Element handshakeHandlerElement = DomUtils.getChildElementByTagName(element,"handshake-handler");
		if(handshakeHandlerElement != null) {
			handshakeHandlerReference = new RuntimeBeanReference(handshakeHandlerElement.getAttribute("ref"));
		}
		else {
			RootBeanDefinition defaultHandshakeHandlerDef = new RootBeanDefinition(DefaultHandshakeHandler.class);
			defaultHandshakeHandlerDef.setSource(source);
			defaultHandshakeHandlerDef.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
			String defaultHandshakeHandlerName = parserContext.getReaderContext().registerWithGeneratedName(defaultHandshakeHandlerDef);
			handshakeHandlerReference = new RuntimeBeanReference(defaultHandshakeHandlerName);
		}

		return handshakeHandlerReference;
	}

	protected abstract RuntimeBeanReference registerDefaultTaskScheduler(ParserContext parserContext, Object source);

	protected RuntimeBeanReference registerSockJsService(Element element, ParserContext parserContext, Object source) {

		Element sockJsElement = DomUtils.getChildElementByTagName(element, "sockjs");

		if(sockJsElement != null) {
			ConstructorArgumentValues cavs = new ConstructorArgumentValues();

			// TODO: polish the way constructor arguments are set

			String customTaskSchedulerName = sockJsElement.getAttribute("task-scheduler");
			if(!customTaskSchedulerName.isEmpty()) {
				cavs.addIndexedArgumentValue(0, new RuntimeBeanReference(customTaskSchedulerName));
			}
			else {
				cavs.addIndexedArgumentValue(0, registerDefaultTaskScheduler(parserContext,source));
			}

			Element transportHandlersElement = DomUtils.getChildElementByTagName(sockJsElement, "transport-handlers");
			boolean registerDefaults = true;
			if(transportHandlersElement != null) {
				String registerDefaultsAttribute = transportHandlersElement.getAttribute("register-defaults");
				registerDefaults = !registerDefaultsAttribute.equals("false");
			}

			ManagedList<?> transportHandlersList = parseBeanSubElements(transportHandlersElement, parserContext);

			if(registerDefaults) {
				cavs.addIndexedArgumentValue(1, Collections.emptyList());
				if(transportHandlersList.isEmpty()) {
					cavs.addIndexedArgumentValue(2, new ConstructorArgumentValues.ValueHolder(null));
				}
				else {
					cavs.addIndexedArgumentValue(2, transportHandlersList);
				}
			}
			else {
				if(transportHandlersList.isEmpty()) {
					cavs.addIndexedArgumentValue(1, new ConstructorArgumentValues.ValueHolder(null));
				}
				else {
					cavs.addIndexedArgumentValue(1, transportHandlersList);
				}
				cavs.addIndexedArgumentValue(2, new ConstructorArgumentValues.ValueHolder(null));
			}

			RootBeanDefinition sockJsServiceDef = new RootBeanDefinition(DefaultSockJsService.class, cavs, null);
			sockJsServiceDef.setSource(source);

			String nameAttribute = sockJsElement.getAttribute("name");
			if(!nameAttribute.isEmpty()) {
				sockJsServiceDef.getPropertyValues().add("name", nameAttribute);
			}
			String websocketEnabledAttribute = sockJsElement.getAttribute("websocket-enabled");
			if(!websocketEnabledAttribute.isEmpty()) {
				sockJsServiceDef.getPropertyValues().add("webSocketsEnabled", Boolean.valueOf(websocketEnabledAttribute));
			}
			String sessionCookieNeededAttribute = sockJsElement.getAttribute("session-cookie-needed");
			if(!sessionCookieNeededAttribute.isEmpty()) {
				sockJsServiceDef.getPropertyValues().add("sessionCookieNeeded", Boolean.valueOf(sessionCookieNeededAttribute));
			}
			String streamBytesLimitAttribute = sockJsElement.getAttribute("stream-bytes-limit");
			if(!streamBytesLimitAttribute.isEmpty()) {
				sockJsServiceDef.getPropertyValues().add("streamBytesLimit", Integer.valueOf(streamBytesLimitAttribute));
			}
			String disconnectDelayAttribute = sockJsElement.getAttribute("disconnect-delay");
			if(!disconnectDelayAttribute.isEmpty()) {
				sockJsServiceDef.getPropertyValues().add("disconnectDelay", Long.valueOf(disconnectDelayAttribute));
			}
			String httpMessageCacheSizeAttribute = sockJsElement.getAttribute("http-message-cache-size");
			if(!httpMessageCacheSizeAttribute.isEmpty()) {
				sockJsServiceDef.getPropertyValues().add("httpMessageCacheSize", Integer.valueOf(httpMessageCacheSizeAttribute));
			}
			String heartBeatTimeAttribute = sockJsElement.getAttribute("heartbeat-time");
			if(!heartBeatTimeAttribute.isEmpty()) {
				sockJsServiceDef.getPropertyValues().add("heartbeatTime", Long.valueOf(heartBeatTimeAttribute));
			}

			sockJsServiceDef.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
			String sockJsServiceName = parserContext.getReaderContext().registerWithGeneratedName(sockJsServiceDef);
			return new RuntimeBeanReference(sockJsServiceName);
		}

		return null;
	}

	protected ManagedList<? super Object> parseBeanSubElements(Element parentElement, ParserContext parserContext) {

		ManagedList<? super Object> beans = new ManagedList<Object>();
		if (parentElement != null) {
			beans.setSource(parserContext.extractSource(parentElement));
			for (Element beanElement : DomUtils.getChildElementsByTagName(parentElement, new String[] { "bean", "ref" })) {
				Object object = parserContext.getDelegate().parsePropertySubElement(beanElement, null);
				beans.add(object);
			}
		}

		return beans;
	}

	protected interface HandlerMappingStrategy {

		public ManagedMap<String, Object> createMappings(Element mappingElement, ParserContext parserContext);

	}

}
