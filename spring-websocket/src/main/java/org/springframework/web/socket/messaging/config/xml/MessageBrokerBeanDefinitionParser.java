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

package org.springframework.web.socket.messaging.config.xml;

import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.parsing.CompositeComponentDefinition;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.beans.factory.support.ManagedMap;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.handler.AbstractBrokerMessageHandler;
import org.springframework.messaging.simp.handler.DefaultUserDestinationResolver;
import org.springframework.messaging.simp.handler.DefaultUserSessionRegistry;
import org.springframework.messaging.simp.handler.SimpAnnotationMethodMessageHandler;
import org.springframework.messaging.simp.handler.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.handler.UserDestinationMessageHandler;
import org.springframework.messaging.simp.stomp.StompBrokerRelayMessageHandler;
import org.springframework.messaging.support.channel.ExecutorSubscribableChannel;
import org.springframework.messaging.support.converter.ByteArrayMessageConverter;
import org.springframework.messaging.support.converter.CompositeMessageConverter;
import org.springframework.messaging.support.converter.DefaultContentTypeResolver;
import org.springframework.messaging.support.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.support.converter.StringMessageConverter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.ClassUtils;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.xml.DomUtils;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;
import org.springframework.web.socket.messaging.StompSubProtocolHandler;
import org.springframework.web.socket.messaging.SubProtocolWebSocketHandler;
import org.springframework.web.socket.server.config.xml.AbstractWebSocketBeanDefinitionParser;
import org.springframework.web.socket.server.support.WebSocketHttpRequestHandler;
import org.springframework.web.socket.sockjs.SockJsHttpRequestHandler;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.List;


/**
 * A {@link org.springframework.beans.factory.xml.BeanDefinitionParser}
 * that provides the configuration for the
 * {@code <message-broker/>} WebSocket namespace element.
 *
 * <p>This class registers a {@link org.springframework.web.servlet.handler.SimpleUrlHandlerMapping}
 * ordered at 1 by default for mapping requests to:</p>
 * <ul>
 *  <li>{@link WebSocketHttpRequestHandler}
 * 	by default for each <stomp-endpoint/>.
 * 	<li>{@link SockJsHttpRequestHandler}
 * 	if the <stomp-endpoint/> element registers a SockJs service.
 * </ul>
 *
 * <p>For each <message-broker/>, this class registers several {@link ExecutorSubscribableChannel}s:
 * <ul>
 * 	<li>a clientInboundChannel (channel for receiving messages from clients, e.g. WebSocket clients).
 * 	<li>a clientOutboundChannel (channel for messages to clients, e.g. WebSocket clients).
 * 	<li>a brokerChannel (channel for messages from within the application to the
 * the respective message handlers).
 * </ul>
 *
 * <p>Depending on the chosen configuration, one of those {@link AbstractBrokerMessageHandler}s is registered:
 * <ul>
 *     <li> a {@link SimpleBrokerMessageHandler} if the <simple-broker/> is used
 *     <li> a {@link StompBrokerRelayMessageHandler} if the <stomp-broker-relay/> is used
 * </ul>
 *
 * <p>This class registers a {@link DefaultUserSessionRegistry} to be used by other beans to store user sessions.
 *
 * TODO: document other registered beans
 *
 * @author Brian Clozel
 * @author Rossen Stoyanchev
 * @since 4.0
 */
public class MessageBrokerBeanDefinitionParser extends AbstractWebSocketBeanDefinitionParser {

	protected static final String TASK_SCHEDULER_BEAN_NAME = "messageBrokerSockJsTaskScheduler";

	private static final boolean jackson2Present= ClassUtils.isPresent(
			"com.fasterxml.jackson.databind.ObjectMapper", MessageBrokerBeanDefinitionParser.class.getClassLoader());

	@Override
	public BeanDefinition parse(Element element, ParserContext parserCxt) {

		Object source = parserCxt.extractSource(element);
		CompositeComponentDefinition compDefinition = new CompositeComponentDefinition(element.getTagName(), source);
		parserCxt.pushContainingComponent(compDefinition);

		String orderAttribute = element.getAttribute("order");
		int order = orderAttribute.isEmpty() ? DEFAULT_MAPPING_ORDER : Integer.valueOf(orderAttribute);

		ManagedMap<String, Object> urlMap = new ManagedMap<String, Object>();
		urlMap.setSource(source);

		RootBeanDefinition handlerMappingDef = new RootBeanDefinition(SimpleUrlHandlerMapping.class);
		handlerMappingDef.getPropertyValues().add("order", order);
		handlerMappingDef.getPropertyValues().add("urlMap", urlMap);

		String channelName = "clientInboundChannel";
		Element channelElem = DomUtils.getChildElementByTagName(element, "client-inbound-channel");
		RuntimeBeanReference clientInChannelRef = getMessageChannel(channelName, channelElem, parserCxt, source);

		channelName = "clientOutboundChannel";
		channelElem = DomUtils.getChildElementByTagName(element, "client-outbound-channel");
		RuntimeBeanReference clientOutChannelDef = getMessageChannel(channelName, channelElem, parserCxt, source);

		RootBeanDefinition userSessionRegistryDef = new RootBeanDefinition(DefaultUserSessionRegistry.class);
		registerBeanDef(userSessionRegistryDef, parserCxt, source);

		RuntimeBeanReference userDestinationResolverRef = registerUserDestinationResolver(element,
				userSessionRegistryDef, parserCxt, source);

		List<Element> stompEndpointElements = DomUtils.getChildElementsByTagName(element, "stomp-endpoint");
		for(Element stompEndpointElement : stompEndpointElements) {

			RuntimeBeanReference requestHandlerRef = getHttpRequestHandlerBeanDefinition(stompEndpointElement,
					clientInChannelRef, clientOutChannelDef, userSessionRegistryDef, parserCxt, source);

			List<String> paths = Arrays.asList(stompEndpointElement.getAttribute("path").split(","));
			for(String path : paths) {
				if (DomUtils.getChildElementByTagName(stompEndpointElement, "sockjs") != null) {
					path = path.endsWith("/") ? path + "**" : path + "/**";
				}
				urlMap.put(path, requestHandlerRef);
			}
		}

		registerBeanDef(handlerMappingDef, parserCxt, source);

		channelName = "brokerChannel";
		channelElem = DomUtils.getChildElementByTagName(element, "broker-channel");
		RuntimeBeanReference brokerChannelRef = getMessageChannel(channelName, channelElem, parserCxt, source);
		registerMessageBroker(element, clientInChannelRef, clientOutChannelDef, brokerChannelRef, parserCxt, source);

		RuntimeBeanReference messageConverterRef = getBrokerMessageConverter(parserCxt, source);
		RuntimeBeanReference messagingTemplateRef = getBrokerMessagingTemplate(element, brokerChannelRef,
				messageConverterRef, parserCxt, source);

		registerAnnotationMethodMessageHandler(element, clientInChannelRef, clientOutChannelDef,
				messageConverterRef, messagingTemplateRef, parserCxt, source);

		registerUserDestinationMessageHandler(clientInChannelRef, clientOutChannelDef, brokerChannelRef,
				userDestinationResolverRef, parserCxt, source);

		parserCxt.popAndRegisterContainingComponent();

		return null;
	}

	protected RuntimeBeanReference registerDefaultTaskScheduler(ParserContext parserContext, Object source) {
		if (!parserContext.getRegistry().containsBeanDefinition(TASK_SCHEDULER_BEAN_NAME)) {
			RootBeanDefinition taskSchedulerDef = new RootBeanDefinition(ThreadPoolTaskScheduler.class);
			taskSchedulerDef.getPropertyValues().add("threadNamePrefix","MessageBrokerSockJS-");
			registerBeanDefByName(TASK_SCHEDULER_BEAN_NAME, taskSchedulerDef, parserContext, source);
		}
		return new RuntimeBeanReference(TASK_SCHEDULER_BEAN_NAME);
	}

	private RuntimeBeanReference registerUserDestinationResolver(Element messageBrokerElement,
			BeanDefinition userSessionRegistryDef, ParserContext parserCxt, Object source) {

		ConstructorArgumentValues cavs = new ConstructorArgumentValues();
		cavs.addIndexedArgumentValue(0, userSessionRegistryDef);
		RootBeanDefinition userDestinationResolverDef =
				new RootBeanDefinition(DefaultUserDestinationResolver.class, cavs, null);
		String prefix = messageBrokerElement.getAttribute("user-destination-prefix");
		if (!prefix.isEmpty()) {
			userDestinationResolverDef.getPropertyValues().add("userDestinationPrefix", prefix);
		}
		String userDestinationResolverName = registerBeanDef(userDestinationResolverDef, parserCxt, source);
		return new RuntimeBeanReference(userDestinationResolverName);
	}

	private RuntimeBeanReference registerUserDestinationMessageHandler(RuntimeBeanReference clientInChannelDef,
			RuntimeBeanReference clientOutChannelDef, RuntimeBeanReference brokerChannelDef,
			RuntimeBeanReference userDestinationResolverRef, ParserContext parserCxt, Object source) {

		ConstructorArgumentValues cavs = new ConstructorArgumentValues();
		cavs.addIndexedArgumentValue(0, clientInChannelDef);
		cavs.addIndexedArgumentValue(1, clientOutChannelDef);
		cavs.addIndexedArgumentValue(2, brokerChannelDef);
		cavs.addIndexedArgumentValue(3, userDestinationResolverRef);

		RootBeanDefinition userDestinationMessageHandlerDef =
				new RootBeanDefinition(UserDestinationMessageHandler.class, cavs, null);

		String userDestinationMessageHandleName = registerBeanDef(userDestinationMessageHandlerDef, parserCxt, source);
		return new RuntimeBeanReference(userDestinationMessageHandleName);
	}

	private void registerMessageBroker(Element messageBrokerElement, RuntimeBeanReference clientInChannelDef,
	                                   RuntimeBeanReference clientOutChannelDef, RuntimeBeanReference brokerChannelDef,
	                                   ParserContext parserCxt, Object source) {

		Element simpleBrokerElem = DomUtils.getChildElementByTagName(messageBrokerElement, "simple-broker");
		Element brokerRelayElem = DomUtils.getChildElementByTagName(messageBrokerElement, "stomp-broker-relay");

		ConstructorArgumentValues cavs = new ConstructorArgumentValues();
		cavs.addIndexedArgumentValue(0, clientInChannelDef);
		cavs.addIndexedArgumentValue(1, clientOutChannelDef);
		cavs.addIndexedArgumentValue(2, brokerChannelDef);

		if (simpleBrokerElem != null) {

			String prefix = simpleBrokerElem.getAttribute("prefix");
			cavs.addIndexedArgumentValue(3, Arrays.asList(prefix.split(",")));
			RootBeanDefinition brokerDef = new RootBeanDefinition(SimpleBrokerMessageHandler.class, cavs, null);
			registerBeanDef(brokerDef, parserCxt, source);
		}
		else if (brokerRelayElem != null) {

			String prefix = brokerRelayElem.getAttribute("prefix");
			cavs.addIndexedArgumentValue(3, Arrays.asList(prefix.split(",")));

			MutablePropertyValues mpvs = new MutablePropertyValues();
			String relayHost = brokerRelayElem.getAttribute("relay-host");
			if(!relayHost.isEmpty()) {
				mpvs.add("relayHost",relayHost);
			}
			String relayPort = brokerRelayElem.getAttribute("relay-port");
			if(!relayPort.isEmpty()) {
				mpvs.add("relayPort", Integer.valueOf(relayPort));
			}
			String systemLogin = brokerRelayElem.getAttribute("system-login");
			if(!systemLogin.isEmpty()) {
				mpvs.add("systemLogin",systemLogin);
			}
			String systemPasscode = brokerRelayElem.getAttribute("system-passcode");
			if(!systemPasscode.isEmpty()) {
				mpvs.add("systemPasscode",systemPasscode);
			}
			String systemHeartbeatSendInterval = brokerRelayElem.getAttribute("system-heartbeat-send-interval");
			if(!systemHeartbeatSendInterval.isEmpty()) {
				mpvs.add("systemHeartbeatSendInterval",Long.parseLong(systemHeartbeatSendInterval));
			}
			String systemHeartbeatReceiveInterval = brokerRelayElem.getAttribute("system-heartbeat-receive-interval");
			if(!systemHeartbeatReceiveInterval.isEmpty()) {
				mpvs.add("systemHeartbeatReceiveInterval",Long.parseLong(systemHeartbeatReceiveInterval));
			}
			String virtualHost = brokerRelayElem.getAttribute("virtual-host");
			if(!virtualHost.isEmpty()) {
				mpvs.add("virtualHost",virtualHost);
			}

			RootBeanDefinition messageBrokerDef = new RootBeanDefinition(StompBrokerRelayMessageHandler.class, cavs, mpvs);
			registerBeanDef(messageBrokerDef, parserCxt, source);
		}

	}

	private void registerAnnotationMethodMessageHandler(Element messageBrokerElement,
			RuntimeBeanReference clientInChannelDef, RuntimeBeanReference clientOutChannelDef,
			RuntimeBeanReference brokerMessageConverterRef, RuntimeBeanReference brokerMessagingTemplateRef,
	        ParserContext parserCxt, Object source) {

		String applicationDestinationPrefix = messageBrokerElement.getAttribute("application-destination-prefix");

		ConstructorArgumentValues cavs = new ConstructorArgumentValues();
		cavs.addIndexedArgumentValue(0, clientInChannelDef);
		cavs.addIndexedArgumentValue(1, clientOutChannelDef);
		cavs.addIndexedArgumentValue(2, brokerMessagingTemplateRef);

		MutablePropertyValues mpvs = new MutablePropertyValues();
		mpvs.add("destinationPrefixes",Arrays.asList(applicationDestinationPrefix.split(",")));
		mpvs.add("messageConverter", brokerMessageConverterRef);

		RootBeanDefinition annotationMethodMessageHandlerDef =
				new RootBeanDefinition(SimpAnnotationMethodMessageHandler.class, cavs, mpvs);

		registerBeanDef(annotationMethodMessageHandlerDef, parserCxt, source);
	}

	private RuntimeBeanReference getBrokerMessageConverter(ParserContext parserCxt, Object source) {

		RootBeanDefinition contentTypeResolverDef = new RootBeanDefinition(DefaultContentTypeResolver.class);

		ManagedList<RootBeanDefinition> convertersDef = new ManagedList<RootBeanDefinition>();
		if (jackson2Present) {
			convertersDef.add(new RootBeanDefinition(MappingJackson2MessageConverter.class));
			contentTypeResolverDef.getPropertyValues().add("defaultMimeType", MimeTypeUtils.APPLICATION_JSON);
		}
		convertersDef.add(new RootBeanDefinition(StringMessageConverter.class));
		convertersDef.add(new RootBeanDefinition(ByteArrayMessageConverter.class));

		ConstructorArgumentValues cavs = new ConstructorArgumentValues();
		cavs.addIndexedArgumentValue(0, convertersDef);
		cavs.addIndexedArgumentValue(1, contentTypeResolverDef);

		RootBeanDefinition brokerMessage = new RootBeanDefinition(CompositeMessageConverter.class, cavs, null);
		return new RuntimeBeanReference(registerBeanDef(brokerMessage,parserCxt, source));
	}

	private RuntimeBeanReference getBrokerMessagingTemplate(
			Element element, RuntimeBeanReference brokerChannelDef, RuntimeBeanReference messageConverterRef,
			ParserContext parserCxt, Object source) {

		ConstructorArgumentValues cavs = new ConstructorArgumentValues();
		cavs.addIndexedArgumentValue(0, brokerChannelDef);
		RootBeanDefinition messagingTemplateDef = new RootBeanDefinition(SimpMessagingTemplate.class,cavs, null);

		String userDestinationPrefixAttribute = element.getAttribute("user-destination-prefix");
		if(!userDestinationPrefixAttribute.isEmpty()) {
			messagingTemplateDef.getPropertyValues().add("userDestinationPrefix", userDestinationPrefixAttribute);
		}
		messagingTemplateDef.getPropertyValues().add("messageConverter", messageConverterRef);

		return new RuntimeBeanReference(registerBeanDef(messagingTemplateDef,parserCxt, source));
	}

	private RuntimeBeanReference getMessageChannel(String channelName, Element channelElement,
			ParserContext parserCxt, Object source) {

		RootBeanDefinition executorDef = null;

		if (channelElement != null) {
			Element executor = DomUtils.getChildElementByTagName(channelElement, "executor");
			if (executor != null) {
				executorDef = new RootBeanDefinition(ThreadPoolTaskExecutor.class);
				String attrValue = executor.getAttribute("core-pool-size");
				if (!StringUtils.isEmpty(attrValue)) {
					executorDef.getPropertyValues().add("corePoolSize", attrValue);
				}
				attrValue = executor.getAttribute("max-pool-size");
				if (!StringUtils.isEmpty(attrValue)) {
					executorDef.getPropertyValues().add("maxPoolSize", attrValue);
				}
				attrValue = executor.getAttribute("keep-alive-seconds");
				if (!StringUtils.isEmpty(attrValue)) {
					executorDef.getPropertyValues().add("keepAliveSeconds", attrValue);
				}
				attrValue = executor.getAttribute("queue-capacity");
				if (!StringUtils.isEmpty(attrValue)) {
					executorDef.getPropertyValues().add("queueCapacity", attrValue);
				}
			}
		}
		else if (!channelName.equals("brokerChannel")) {
			executorDef = new RootBeanDefinition(ThreadPoolTaskExecutor.class);
		}

		ConstructorArgumentValues values = new ConstructorArgumentValues();
		if (executorDef != null) {
			executorDef.getPropertyValues().add("threadNamePrefix", channelName + "-");
			String executorName = channelName + "Executor";
			registerBeanDefByName(executorName, executorDef, parserCxt, source);
			values.addIndexedArgumentValue(0, new RuntimeBeanReference(executorName));
		}

		RootBeanDefinition channelDef = new RootBeanDefinition(ExecutorSubscribableChannel.class, values, null);

		if (channelElement != null) {
			Element interceptorsElement = DomUtils.getChildElementByTagName(channelElement, "interceptors");
			ManagedList<?> interceptorList = parseBeanSubElements(interceptorsElement, parserCxt);
			channelDef.getPropertyValues().add("interceptors", interceptorList);
		}

		registerBeanDefByName(channelName, channelDef, parserCxt, source);
		return new RuntimeBeanReference(channelName);
	}



	private RuntimeBeanReference getHttpRequestHandlerBeanDefinition(Element stompEndpointElement,
			RuntimeBeanReference clientInChannelDef, RuntimeBeanReference clientOutChannelDef,
			RootBeanDefinition userSessionRegistryDef, ParserContext parserCxt, Object source) {

		RootBeanDefinition httpRequestHandlerDef;

		RootBeanDefinition stompHandlerDef = new RootBeanDefinition(StompSubProtocolHandler.class);
		stompHandlerDef.getPropertyValues().add("userSessionRegistry", userSessionRegistryDef);
		registerBeanDef(stompHandlerDef, parserCxt, source);

		ConstructorArgumentValues cavs = new ConstructorArgumentValues();
		cavs.addIndexedArgumentValue(0, clientInChannelDef);
		cavs.addIndexedArgumentValue(1, clientOutChannelDef);
		RootBeanDefinition subProtocolWebSocketHandlerDef =
				new RootBeanDefinition(SubProtocolWebSocketHandler.class, cavs, null);
		subProtocolWebSocketHandlerDef.getPropertyValues().addPropertyValue("protocolHandlers", stompHandlerDef);
		registerBeanDef(subProtocolWebSocketHandlerDef, parserCxt, source);

		RuntimeBeanReference handshakeHandlerRef =
				registerHandshakeHandler(stompEndpointElement, parserCxt, source);

		RuntimeBeanReference sockJsServiceRef = registerSockJsService(stompEndpointElement, parserCxt, source);
		if (sockJsServiceRef != null) {
			cavs = new ConstructorArgumentValues();
			cavs.addIndexedArgumentValue(0, sockJsServiceRef);
			cavs.addIndexedArgumentValue(1, subProtocolWebSocketHandlerDef);
			httpRequestHandlerDef = new RootBeanDefinition(SockJsHttpRequestHandler.class, cavs, null);
		}
		else {
			cavs = new ConstructorArgumentValues();
			cavs.addIndexedArgumentValue(0, subProtocolWebSocketHandlerDef);
			if(handshakeHandlerRef != null) {
				cavs.addIndexedArgumentValue(1, handshakeHandlerRef);
			}
			httpRequestHandlerDef = new RootBeanDefinition(WebSocketHttpRequestHandler.class, cavs, null);
			// TODO: httpRequestHandlerDef.getPropertyValues().add("handshakeInterceptors", ...);
		}

		String httpRequestHandlerBeanName = registerBeanDef(httpRequestHandlerDef, parserCxt, source);
		return new RuntimeBeanReference(httpRequestHandlerBeanName);
	}

	private static String registerBeanDef(RootBeanDefinition beanDef, ParserContext parserCxt, Object source) {
		String beanName = parserCxt.getReaderContext().generateBeanName(beanDef);
		registerBeanDefByName(beanName, beanDef, parserCxt, source);
		return beanName;
	}

	private static void registerBeanDefByName(String beanName, RootBeanDefinition beanDef,
			ParserContext parserCxt, Object source) {

		beanDef.setSource(source);
		beanDef.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
		parserCxt.getRegistry().registerBeanDefinition(beanName, beanDef);
		parserCxt.registerComponent(new BeanComponentDefinition(beanDef, beanName));
	}

}
