/*
 * Copyright 2002-2013 the original author or authors.
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

package org.springframework.websocket.server.support;

import java.io.IOException;
import java.util.Collections;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.util.Assert;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.util.NestedServletException;
import org.springframework.websocket.HandlerProvider;
import org.springframework.websocket.WebSocketHandler;
import org.springframework.websocket.server.HandshakeHandler;
import org.springframework.websocket.server.DefaultHandshakeHandler;


/**
 * An {@link HttpRequestHandler} that wraps the invocation of a {@link HandshakeHandler}.
 *
 * @author Rossen Stoyanchev
 * @since 4.0
 */
public class WebSocketHttpRequestHandler implements HttpRequestHandler, BeanFactoryAware {

	private HandshakeHandler handshakeHandler;

	private final HandlerProvider<WebSocketHandler> handlerProvider;


	public WebSocketHttpRequestHandler(WebSocketHandler webSocketHandler) {
		Assert.notNull(webSocketHandler, "webSocketHandler is required");
		this.handlerProvider = new HandlerProvider<WebSocketHandler>(webSocketHandler);
		this.handshakeHandler = new DefaultHandshakeHandler();
		this.handshakeHandler.registerWebSocketHandlers(Collections.singleton(webSocketHandler));
	}

	public WebSocketHttpRequestHandler(	Class<? extends WebSocketHandler> webSocketHandlerClass) {
		Assert.notNull(webSocketHandlerClass, "webSocketHandlerClass is required");
		this.handlerProvider = new HandlerProvider<WebSocketHandler>(webSocketHandlerClass);
	}

	public void setHandshakeHandler(HandshakeHandler handshakeHandler) {
		Assert.notNull(handshakeHandler, "handshakeHandler is required");
		this.handshakeHandler = handshakeHandler;
		if (this.handlerProvider.isSingleton()) {
			this.handshakeHandler.registerWebSocketHandlers(Collections.singleton(this.handlerProvider.getHandler()));
		}
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		this.handlerProvider.setBeanFactory(beanFactory);
	}

	@Override
	public void handleRequest(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		ServerHttpRequest httpRequest = new ServletServerHttpRequest(request);
		ServerHttpResponse httpResponse = new ServletServerHttpResponse(response);

		try {
			WebSocketHandler webSocketHandler = this.handlerProvider.getHandler();
			this.handshakeHandler.doHandshake(httpRequest, httpResponse, webSocketHandler);
		}
		catch (Exception e) {
			// TODO
			throw new NestedServletException("HandshakeHandler failure", e);
		}
		finally {
			httpResponse.flush();
		}
	}

}